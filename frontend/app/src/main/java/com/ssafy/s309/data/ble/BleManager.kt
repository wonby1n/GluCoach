package com.ssafy.s309.data.ble

import android.app.Application
import android.bluetooth.BluetoothGatt
import com.clj.fastble.callback.BleGattCallback
import com.clj.fastble.callback.BleNotifyCallback
import com.clj.fastble.callback.BleScanCallback
import com.clj.fastble.callback.BleWriteCallback
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.clj.fastble.scan.BleScanRuleConfig
import com.ssafy.s309.data.model.GlucoseReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import com.clj.fastble.BleManager as FastBleManager

/**
 * 패치와의 BLE 통신을 담당하는 싱글톤 매니저.
 *
 * 외부(Repository / ViewModel)는 다음 Flow 만 구독한다:
 * - [connectionState] : 현재 연결 상태
 * - [scannedDevices]  : 스캔으로 발견된 기기 목록
 * - [glucoseReadings] : 실시간 또는 주기 평균 혈당 값 스트림
 * - [processingSettings] / [patchControlSettings] : 데이터 처리 / 패치 제어 설정 스냅샷
 *
 * 데이터 수신 모드:
 * - 실시간 (gatherIntervalSeconds == 0) : 패킷 도착마다 [BleDataParser.parse] 후 즉시 emit
 * - 주기 평균 (gatherIntervalSeconds > 0) : 매 패킷의 raw 값을 이동평균에 반영하면서 버퍼에 누적,
 *   주기마다 평균값을 한 번 emit
 */
@Singleton
class BleManager
    @Inject
    constructor(
        application: Application,
        private val parser: BleDataParser,
    ) {
        // ────────────────────────────────────────
        // 상태 노출용 Flow
        // ────────────────────────────────────────

        private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Idle)
        val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

        private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
        val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

        private val _glucoseReadings =
            MutableSharedFlow<GlucoseReading>(
                replay = 0,
                extraBufferCapacity = 64,
            )
        val glucoseReadings: SharedFlow<GlucoseReading> = _glucoseReadings.asSharedFlow()

        private val _processingSettings = MutableStateFlow(BleProcessingSettings())
        val processingSettings: StateFlow<BleProcessingSettings> = _processingSettings.asStateFlow()

        private val _patchControlSettings = MutableStateFlow(BlePatchControlSettings())
        val patchControlSettings: StateFlow<BlePatchControlSettings> = _patchControlSettings.asStateFlow()

        // ────────────────────────────────────────
        // 내부 상태
        // ────────────────────────────────────────

        private var connectedBleDevice: BleDevice? = null
        private var connectedModule: BleModule? = null

        /** 주기 평균 모드의 raw 값 버퍼 (timestamp, raw). */
        private val gatherBuffer = ArrayDeque<GatherEntry>()

        /** 주기 평균 타이머 코루틴. gatherIntervalSeconds 변경 시 재시작. */
        private var gatherJob: Job? = null

        /** 주파수 자동 스캔 코루틴 (Stop/취소 시 [Job.cancel] 로 중단). */
        private var frequencyScanJob: Job? = null

        /** 매니저 전역 코루틴 스코프. 싱글톤 수명과 동일. */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        // ────────────────────────────────────────
        // 초기화
        // ────────────────────────────────────────

        init {
            val fastBle = FastBleManager.getInstance()
            fastBle.init(application)
            fastBle
                .enableLog(true)
                .setReConnectCount(1, RECONNECT_INTERVAL_MS)
                .setSplitWriteNum(SPLIT_WRITE_NUM)
                .setConnectOverTime(CONNECT_TIMEOUT_MS)
                .setOperateTimeout(OPERATE_TIMEOUT_MS)

            val scanRule =
                BleScanRuleConfig
                    .Builder()
                    .setScanTimeOut(SCAN_TIMEOUT_MS)
                    .build()
            fastBle.initScanRule(scanRule)
        }

        // ────────────────────────────────────────
        // 환경 체크
        // ────────────────────────────────────────

        /** 현재 기기가 BLE 를 지원하는지. */
        fun isBleSupported(): Boolean = FastBleManager.getInstance().isSupportBle

        /** 블루투스가 켜져 있는지. */
        fun isBluetoothEnabled(): Boolean = FastBleManager.getInstance().isBlueEnable

        /** 블루투스 활성화 요청 (시스템 다이얼로그를 띄움). */
        fun requestEnableBluetooth() {
            FastBleManager.getInstance().enableBluetooth()
        }

        // ────────────────────────────────────────
        // 스캔
        // ────────────────────────────────────────

        fun startScan() {
            if (_connectionState.value is BleConnectionState.Scanning) return

            _scannedDevices.value = emptyList()
            _connectionState.value = BleConnectionState.Scanning

            FastBleManager.getInstance().scan(
                object : BleScanCallback() {
                    override fun onScanStarted(success: Boolean) {
                        if (!success) {
                            _connectionState.value =
                                BleConnectionState.Error("스캔을 시작할 수 없습니다.")
                        }
                    }

                    override fun onScanning(bleDevice: BleDevice) {
                        if (!BleDeviceFilter.matches(bleDevice.name)) return

                        val scanned =
                            ScannedDevice(
                                name = bleDevice.name,
                                address = bleDevice.mac,
                                rssi = bleDevice.rssi,
                            )

                        _scannedDevices.update { current ->
                            if (current.any { it.address == scanned.address }) {
                                current
                            } else {
                                current + scanned
                            }
                        }
                    }

                    override fun onScanFinished(scanResultList: List<BleDevice>) {
                        if (_connectionState.value is BleConnectionState.Scanning) {
                            _connectionState.value = BleConnectionState.Idle
                        }
                    }
                },
            )
        }

        fun stopScan() {
            FastBleManager.getInstance().cancelScan()
            if (_connectionState.value is BleConnectionState.Scanning) {
                _connectionState.value = BleConnectionState.Idle
            }
        }

        // ────────────────────────────────────────
        // 연결
        // ────────────────────────────────────────

        fun connect(device: ScannedDevice) {
            if (_connectionState.value is BleConnectionState.Scanning) {
                FastBleManager.getInstance().cancelScan()
            }

            parser.reset()
            gatherBuffer.clear()
            _connectionState.value = BleConnectionState.Connecting(device)

            FastBleManager.getInstance().connect(
                device.address,
                object : BleGattCallback() {
                    override fun onStartConnect() = Unit

                    override fun onConnectFail(
                        bleDevice: BleDevice?,
                        exception: BleException?,
                    ) {
                        _connectionState.value =
                            BleConnectionState.Error(
                                "연결 실패: ${exception?.description ?: "알 수 없는 오류"}",
                            )
                    }

                    override fun onConnectSuccess(
                        bleDevice: BleDevice,
                        gatt: BluetoothGatt,
                        status: Int,
                    ) {
                        val module =
                            gatt.services.firstNotNullOfOrNull { service ->
                                BleModule.fromServiceUuid(service.uuid.toString())
                            }

                        if (module == null) {
                            _connectionState.value =
                                BleConnectionState.Error("지원하지 않는 BLE 모듈입니다.")
                            FastBleManager.getInstance().disconnect(bleDevice)
                            return
                        }

                        connectedBleDevice = bleDevice
                        connectedModule = module
                        _connectionState.value = BleConnectionState.Connected(device, module)

                        startGatherTimerIfEnabled()
                        subscribeToNotify(bleDevice, module)
                    }

                    override fun onDisConnected(
                        isActiveDisConnected: Boolean,
                        bleDevice: BleDevice?,
                        gatt: BluetoothGatt?,
                        status: Int,
                    ) {
                        connectedBleDevice = null
                        connectedModule = null
                        stopGatherTimer()
                        gatherBuffer.clear()
                        frequencyScanJob?.cancel()
                        _connectionState.value =
                            BleConnectionState.Disconnected(
                                reason = if (isActiveDisConnected) null else "연결이 끊어졌습니다.",
                            )
                    }
                },
            )
        }

        fun disconnect() {
            val device = connectedBleDevice ?: return
            FastBleManager.getInstance().disconnect(device)
        }

        // ────────────────────────────────────────
        // 데이터 수신 (notify)
        // ────────────────────────────────────────

        private fun subscribeToNotify(
            bleDevice: BleDevice,
            module: BleModule,
        ) {
            FastBleManager.getInstance().notify(
                bleDevice,
                module.serviceUuid,
                module.notifyUuid,
                object : BleNotifyCallback() {
                    override fun onNotifySuccess() = Unit

                    override fun onNotifyFailure(exception: BleException?) {
                        _connectionState.value =
                            BleConnectionState.Error(
                                "데이터 수신 등록 실패: ${exception?.description ?: "알 수 없는 오류"}",
                            )
                    }

                    override fun onCharacteristicChanged(data: ByteArray) {
                        handleIncomingPacket(data)
                    }
                },
            )
        }

        /**
         * 한 패킷을 처리. 모든 raw 는 이동평균에 반영하고,
         * 실시간/주기 모드에 따라 emit 시점이 달라진다.
         */
        private fun handleIncomingPacket(data: ByteArray) {
            val raw = parser.extractRaw(data) ?: return
            val now = System.currentTimeMillis()

            parser.trackMovingAverage(raw)

            if (_processingSettings.value.gatherIntervalSeconds == 0) {
                val reading = parser.applyFilterAndConvert(raw, now) ?: return
                _glucoseReadings.tryEmit(reading)
            } else {
                synchronized(gatherBuffer) {
                    gatherBuffer.addLast(GatherEntry(timestampMillis = now, raw = raw))
                }
            }
        }

        // ────────────────────────────────────────
        // 주기 평균 (gather) 타이머
        // ────────────────────────────────────────

        private fun startGatherTimerIfEnabled() {
            stopGatherTimer()
            val interval = _processingSettings.value.gatherIntervalSeconds
            if (interval <= 0) return

            gatherJob =
                scope.launch {
                    while (isActive) {
                        delay(interval * 1000L)
                        flushGatherBuffer()
                    }
                }
        }

        private fun stopGatherTimer() {
            gatherJob?.cancel()
            gatherJob = null
        }

        private fun flushGatherBuffer() {
            val snapshot =
                synchronized(gatherBuffer) {
                    if (gatherBuffer.isEmpty()) return
                    val list = gatherBuffer.toList()
                    gatherBuffer.clear()
                    list
                }

            val avgRaw = snapshot.map { it.raw }.average().toInt()
            val firstTimestamp = snapshot.first().timestampMillis
            val reading = parser.applyFilterAndConvert(avgRaw, firstTimestamp) ?: return
            _glucoseReadings.tryEmit(reading)
        }

        // ────────────────────────────────────────
        // 처리 설정 변경
        // ────────────────────────────────────────

        /** 한 번에 여러 처리 설정을 갱신한다. */
        fun updateProcessingSettings(settings: BleProcessingSettings) {
            val previous = _processingSettings.value
            _processingSettings.value = settings
            parser.applySettings(settings)

            // gather 주기가 바뀌면 타이머 재시작
            if (previous.gatherIntervalSeconds != settings.gatherIntervalSeconds) {
                if (connectedBleDevice != null) startGatherTimerIfEnabled() else stopGatherTimer()
            }
        }

        // ────────────────────────────────────────
        // 패치 제어 명령 (write)
        // ────────────────────────────────────────

        /**
         * 렌즈 옵션 비트마스크 전송.
         * 패킷: `[0xA1, mask, 0x00, 0x00]`
         */
        fun sendLensOptions(flags: Set<LensOptionFlag>) {
            val mask = LensOptionFlag.toMask(flags)
            val packet = byteArrayOf(BleCommandHeader.LENS_OPTION, mask, 0x00, 0x00)
            writeIfConnected(packet) {
                _patchControlSettings.update { it.copy(lensOptions = flags) }
            }
        }

        /**
         * RF 주파수(MHz) 전송. 유효 범위: [MIN_FREQUENCY_MHZ] ~ [MAX_FREQUENCY_MHZ].
         * 패킷: `[0xA2, val2, val1, val0]` (freq = mhz * 65536 / 16 → 3바이트 big-endian)
         */
        fun sendFrequency(mhz: Int) {
            require(mhz in MIN_FREQUENCY_MHZ..MAX_FREQUENCY_MHZ) {
                "주파수는 $MIN_FREQUENCY_MHZ ~ $MAX_FREQUENCY_MHZ MHz 범위여야 합니다."
            }
            val freq = mhz * FREQUENCY_SCALE_NUMERATOR / FREQUENCY_SCALE_DENOMINATOR
            val val2 = ((freq shr 16) and 0xFF).toByte()
            val val1 = ((freq shr 8) and 0xFF).toByte()
            val val0 = (freq and 0xFF).toByte()
            val packet = byteArrayOf(BleCommandHeader.FREQUENCY, val2, val1, val0)

            writeIfConnected(packet) {
                _patchControlSettings.update { it.copy(frequencyMhz = mhz) }
            }
        }

        /**
         * RF 출력 강도 전송.
         * 패킷: `[0xA3, level, 0x00, 0x00]`
         */
        fun sendRfPower(level: RfPowerLevel) {
            val packet = byteArrayOf(BleCommandHeader.POWER, level.rawValue, 0x00, 0x00)
            writeIfConnected(packet) {
                _patchControlSettings.update { it.copy(rfPowerLevel = level) }
            }
        }

        /**
         * RF ON/OFF 모드 전송.
         * 패킷: `[0xA4, mode, 0x00, 0x00]`
         */
        fun sendRfOnOff(mode: RfOnOffMode) {
            val packet = byteArrayOf(BleCommandHeader.RF_ON_OFF, mode.rawValue, 0x00, 0x00)
            writeIfConnected(packet) {
                _patchControlSettings.update { it.copy(rfOnOffMode = mode) }
            }
        }

        /**
         * 시작 주파수부터 [MAX_FREQUENCY_MHZ] 까지 1MHz 씩 증가시키며 [intervalMs] 간격으로 전송.
         * 진행 중인 스캔이 있으면 자동으로 중단된다.
         *
         * 호출 즉시 백그라운드에서 실행되며, [stopFrequencyScan] 또는 연결 끊김 시 중단된다.
         */
        fun runFrequencyScan(
            startFreqMhz: Int,
            intervalMs: Long,
        ) {
            require(intervalMs >= MIN_FREQUENCY_SCAN_INTERVAL_MS) {
                "주파수 스캔 간격은 ${MIN_FREQUENCY_SCAN_INTERVAL_MS}ms 이상이어야 합니다."
            }
            require(startFreqMhz in MIN_FREQUENCY_MHZ..MAX_FREQUENCY_MHZ)

            stopFrequencyScan()
            frequencyScanJob =
                scope.launch {
                    var freq = startFreqMhz
                    while (isActive && freq <= MAX_FREQUENCY_MHZ) {
                        sendFrequency(freq)
                        delay(intervalMs)
                        freq++
                    }
                }
        }

        /** 진행 중인 주파수 자동 스캔 중단. */
        fun stopFrequencyScan() {
            frequencyScanJob?.cancel()
            frequencyScanJob = null
        }

        /** 연결되어 있을 때만 write. 성공 시 [onSuccess] 호출. */
        private fun writeIfConnected(
            packet: ByteArray,
            onSuccess: () -> Unit,
        ) {
            val device = connectedBleDevice ?: return
            val module = connectedModule ?: return

            FastBleManager.getInstance().write(
                device,
                module.serviceUuid,
                module.writeUuid,
                packet,
                object : BleWriteCallback() {
                    override fun onWriteSuccess(
                        current: Int,
                        total: Int,
                        justWrite: ByteArray?,
                    ) {
                        if (current == total) onSuccess()
                    }

                    override fun onWriteFailure(exception: BleException?) {
                        // write 실패는 connectionState 를 바꾸지 않는다 (일시적 실패는 흔함).
                        // 필요하면 호출자가 결과를 확인하도록 별도 Flow 를 추가할 수 있다.
                    }
                },
            )
        }

        /** gather 모드 버퍼 항목. */
        private data class GatherEntry(
            val timestampMillis: Long,
            val raw: Int,
        )

        private companion object {
            private const val RECONNECT_INTERVAL_MS = 5_000L
            private const val OPERATE_TIMEOUT_MS = 5_000
            private const val CONNECT_TIMEOUT_MS = 10_000L
            private const val SCAN_TIMEOUT_MS = 10_000L
            private const val SPLIT_WRITE_NUM = 20

            private const val MIN_FREQUENCY_MHZ = 300
            private const val MAX_FREQUENCY_MHZ = 960
            private const val FREQUENCY_SCALE_NUMERATOR = 65536
            private const val FREQUENCY_SCALE_DENOMINATOR = 16
            private const val MIN_FREQUENCY_SCAN_INTERVAL_MS = 200L
        }
    }
