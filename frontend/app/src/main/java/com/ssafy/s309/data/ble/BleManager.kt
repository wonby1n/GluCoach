package com.ssafy.s309.data.ble

import android.app.Application
import android.bluetooth.BluetoothGatt
import android.util.Log
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
 * - [glucoseReadings] : 표시용 혈당 값 스트림 (display gather 주기에 따라 실시간/평균)
 * - [glucoseHistory]  : 최근 [BleConfig.Processing.IN_MEMORY_HISTORY_SIZE] 개 표시값 (UI 그래프용)
 * - [aggregatedReadings] : DB 저장용 [BleConfig.Processing.DB_AGGREGATION_INTERVAL_SECONDS] 초 평균값 스트림
 * - [processingSettings] / [patchControlSettings] : 데이터 처리 / 패치 제어 설정 스냅샷
 *
 * 데이터 파이프라인은 두 갈래로 분리된다:
 * 1. 표시용 (glucoseReadings / glucoseHistory):
 *    - 실시간 (gatherIntervalSeconds == 0): 매 valid 패킷마다 즉시 emit
 *    - 주기 평균 (gatherIntervalSeconds > 0): N초마다 평균값 1번 emit
 *    표시 주기는 사용자가 런타임에 변경 가능 ([updateProcessingSettings]).
 * 2. DB 저장용 (aggregatedReadings):
 *    - 표시 주기와 무관하게 항상 [BleConfig.Processing.DB_AGGREGATION_INTERVAL_SECONDS] 초마다 평균값 emit.
 *    - 백엔드 업로드 / AI 예측 입력 / 장기 그래프 용도로 쓸 값.
 *    - 현재 collect 하는 곳은 없음. DB 스키마 확정 후 Repository 가 collect 하도록 연결 예정.
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

        private val _glucoseHistory = MutableStateFlow<List<GlucoseReading>>(emptyList())
        val glucoseHistory: StateFlow<List<GlucoseReading>> = _glucoseHistory.asStateFlow()

        /**
         * DB 저장용 [BleConfig.Processing.DB_AGGREGATION_INTERVAL_SECONDS] 초 평균값 스트림.
         * 표시 주기와 별개로 항상 동작. 현재 collect 하는 곳 없음 (DB 미구축).
         */
        private val _aggregatedReadings =
            MutableSharedFlow<GlucoseReading>(
                replay = 0,
                extraBufferCapacity = 8,
            )
        val aggregatedReadings: SharedFlow<GlucoseReading> = _aggregatedReadings.asSharedFlow()

        private val _processingSettings = MutableStateFlow(BleProcessingSettings())
        val processingSettings: StateFlow<BleProcessingSettings> = _processingSettings.asStateFlow()

        private val _patchControlSettings = MutableStateFlow(BlePatchControlSettings())
        val patchControlSettings: StateFlow<BlePatchControlSettings> = _patchControlSettings.asStateFlow()

        // ────────────────────────────────────────
        // 내부 상태
        // ────────────────────────────────────────

        private var connectedBleDevice: BleDevice? = null
        private var connectedModule: BleModule? = null

        /** 표시 주기 평균 모드의 raw 값 버퍼 (timestamp, raw). */
        private val gatherBuffer = ArrayDeque<GatherEntry>()

        /** 표시 주기 평균 타이머 코루틴. gatherIntervalSeconds 변경 시 재시작. */
        /** 패치 패킷 도착 빈도 측정용 — 직전 패킷 timestamp. */
        private var lastArrivalMs: Long? = null

        /** 주기 평균 타이머 코루틴. gatherIntervalSeconds 변경 시 재시작. */
        private var gatherJob: Job? = null

        /** DB 저장용 5분 평균 raw 값 버퍼. 표시 주기와 무관하게 항상 누적. */
        private val dbAggregationBuffer = ArrayDeque<GatherEntry>()

        /** DB 저장용 5분 평균 타이머 코루틴. 연결되어 있는 동안만 동작. */
        private var dbAggregationJob: Job? = null

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
                .setReConnectCount(1, BleConfig.Connection.RECONNECT_INTERVAL_MS)
                .setSplitWriteNum(BleConfig.Connection.SPLIT_WRITE_NUM)
                .setConnectOverTime(BleConfig.Connection.CONNECT_TIMEOUT_MS)
                .setOperateTimeout(BleConfig.Connection.OPERATE_TIMEOUT_MS)

            val scanRule =
                BleScanRuleConfig
                    .Builder()
                    .setScanTimeOut(BleConfig.Connection.SCAN_TIMEOUT_MS)
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
            synchronized(gatherBuffer) { gatherBuffer.clear() }
            synchronized(dbAggregationBuffer) { dbAggregationBuffer.clear() }
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
                        startDbAggregationTimer()
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
                        stopDbAggregationTimer()
                        // 연결 끊기기 직전까지 모인 partial window 도 emit해서 데이터 유실 최소화.
                        // (의료 데이터 특성상 짧은 단절이라도 이미 받은 건 살리는 게 맞음.)
                        // flushDbAggregationBuffer 내부에서 buffer 를 clear 한다.
                        flushDbAggregationBuffer()
                        // gatherBuffer 는 표시용이라 disconnect 시 굳이 emit 안 함.
                        synchronized(gatherBuffer) { gatherBuffer.clear() }
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

        /**
         * notify 등록. 실패 시 [BleConfig.Connection.MAX_NOTIFY_RETRY] 만큼 자동 재시도하고,
         * 그래도 실패하면 GATT 연결을 끊어 사용자가 인지하도록 한다.
         *
         * @param attempt 현재 시도 횟수 (0부터 시작, 재귀 호출 시 +1).
         */
        private fun subscribeToNotify(
            bleDevice: BleDevice,
            module: BleModule,
            attempt: Int = 0,
        ) {
            FastBleManager.getInstance().notify(
                bleDevice,
                module.serviceUuid,
                module.notifyUuid,
                object : BleNotifyCallback() {
                    override fun onNotifySuccess() = Unit

                    override fun onNotifyFailure(exception: BleException?) {
                        if (attempt < BleConfig.Connection.MAX_NOTIFY_RETRY) {
                            // 재시도. 그 사이 다른 기기로 바뀌었거나 끊겼으면 중단.
                            scope.launch {
                                delay(BleConfig.Connection.NOTIFY_RETRY_DELAY_MS)
                                if (connectedBleDevice == bleDevice && connectedModule == module) {
                                    subscribeToNotify(bleDevice, module, attempt + 1)
                                }
                            }
                        } else {
                            // 모든 재시도 실패 — 연결을 끊어 사용자에게 명확히 알림.
                            _connectionState.value =
                                BleConnectionState.Error(
                                    "데이터 수신 등록 실패: ${exception?.description ?: "알 수 없는 오류"}",
                                )
                            FastBleManager.getInstance().disconnect(bleDevice)
                        }
                    }

                    override fun onCharacteristicChanged(data: ByteArray) {
                        handleIncomingPacket(data)
                    }
                },
            )
        }

        /**
         * 한 패킷을 처리:
         * 1. 이동평균에 반영 (모든 raw)
         * 2. spike 필터 통과 여부 확인
         * 3. spike 통과한 raw 만 DB 저장용 5분 버퍼에 누적 (노이즈 평균 오염 방지)
         * 4. 표시 모드에 따라 즉시 emit(실시간) 또는 표시 버퍼에 누적(주기 평균)
         *
         * 표시 버퍼(gatherBuffer)는 spike 와 무관하게 모든 raw 를 누적한다 — flush 시점에
         * 평균값에 대해 spike 필터를 적용하는 기존 동작 유지.
         */
        private fun handleIncomingPacket(data: ByteArray) {
            // 패치 도착 빈도 측정용 로그. logcat -s BleRate 로 두 timestamp 차이 보면 곧 패치 송신 주기.
            val arrivalMs = System.currentTimeMillis()
            val gap = lastArrivalMs?.let { arrivalMs - it }
            lastArrivalMs = arrivalMs
            Log.d("BleRate", "packet @ $arrivalMs ${data.size}B" + (gap?.let { " (+${it}ms)" } ?: ""))

            val raw = parser.extractRaw(data) ?: return
            val now = arrivalMs

            parser.trackMovingAverage(raw)

            // 한 번 호출해서 spike 통과 여부 + 변환된 reading 둘 다 얻는다.
            val filteredReading = parser.applyFilterAndConvert(raw, now)
            val passedSpike = filteredReading != null

            // DB 저장용 버퍼는 spike 통과한 raw 만 누적 (표시 주기와 무관).
            if (passedSpike) {
                synchronized(dbAggregationBuffer) {
                    dbAggregationBuffer.addLast(GatherEntry(timestampMillis = now, raw = raw))
                }
            }

            if (_processingSettings.value.gatherIntervalSeconds == 0) {
                // 실시간 모드: 위에서 이미 변환한 reading 재사용.
                val reading = filteredReading ?: return
                _glucoseReadings.tryEmit(reading)
                _glucoseHistory.update {
                    (it + reading).takeLast(BleConfig.Processing.IN_MEMORY_HISTORY_SIZE)
                }
            } else {
                // 주기 평균 모드: spike 와 무관하게 모든 raw 를 누적 (flush 시 평균에 대해 spike 적용).
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
            // 평균값이 "방금 계산된" 시점(윈도우 끝)을 timestamp 로 사용.
            // 윈도우 시작 시각으로 라벨링하면 그래프상 5분 전 자리에 점이 찍혀 사용자가 혼동.
            val windowEndTimestamp = snapshot.last().timestampMillis
            val reading = parser.applyFilterAndConvert(avgRaw, windowEndTimestamp) ?: return
            _glucoseReadings.tryEmit(reading)
            _glucoseHistory.update {
                (it + reading).takeLast(BleConfig.Processing.IN_MEMORY_HISTORY_SIZE)
            }
        }

        // ────────────────────────────────────────
        // DB 저장용 5분 평균 (aggregation) 타이머
        // ────────────────────────────────────────

        private fun startDbAggregationTimer() {
            stopDbAggregationTimer()
            val intervalSec = BleConfig.Processing.DB_AGGREGATION_INTERVAL_SECONDS
            dbAggregationJob =
                scope.launch {
                    while (isActive) {
                        delay(intervalSec * 1000L)
                        flushDbAggregationBuffer()
                    }
                }
        }

        private fun stopDbAggregationTimer() {
            dbAggregationJob?.cancel()
            dbAggregationJob = null
        }

        private fun flushDbAggregationBuffer() {
            val snapshot =
                synchronized(dbAggregationBuffer) {
                    if (dbAggregationBuffer.isEmpty()) return
                    val list = dbAggregationBuffer.toList()
                    dbAggregationBuffer.clear()
                    list
                }

            val avgRaw = snapshot.map { it.raw }.average().toInt()
            // 평균값이 "방금 계산된" 시점(윈도우 끝)을 timestamp 로 사용.
            val windowEndTimestamp = snapshot.last().timestampMillis
            val reading = parser.applyFilterAndConvert(avgRaw, windowEndTimestamp) ?: return
            _aggregatedReadings.tryEmit(reading)
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
         * RF 주파수(MHz) 전송. 유효 범위는 [BleConfig.Frequency.MIN_MHZ] ~ [BleConfig.Frequency.MAX_MHZ].
         * 패킷: `[0xA2, val2, val1, val0]` (freq = mhz * NUMERATOR / DENOMINATOR → 3바이트 big-endian)
         */
        fun sendFrequency(mhz: Int) {
            require(mhz in BleConfig.Frequency.MIN_MHZ..BleConfig.Frequency.MAX_MHZ) {
                "주파수는 ${BleConfig.Frequency.MIN_MHZ} ~ ${BleConfig.Frequency.MAX_MHZ} MHz 범위여야 합니다."
            }
            val freq = mhz * BleConfig.Frequency.SCALE_NUMERATOR / BleConfig.Frequency.SCALE_DENOMINATOR
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
         * 시작 주파수부터 [BleConfig.Frequency.MAX_MHZ] 까지 1MHz 씩 증가시키며 [intervalMs] 간격으로 전송.
         * 진행 중인 스캔이 있으면 자동으로 중단된다.
         *
         * 호출 즉시 백그라운드에서 실행되며, [stopFrequencyScan] 또는 연결 끊김 시 중단된다.
         */
        fun runFrequencyScan(
            startFreqMhz: Int,
            intervalMs: Long,
        ) {
            require(intervalMs >= BleConfig.Frequency.MIN_SCAN_INTERVAL_MS) {
                "주파수 스캔 간격은 ${BleConfig.Frequency.MIN_SCAN_INTERVAL_MS}ms 이상이어야 합니다."
            }
            require(startFreqMhz in BleConfig.Frequency.MIN_MHZ..BleConfig.Frequency.MAX_MHZ)

            stopFrequencyScan()
            frequencyScanJob =
                scope.launch {
                    var freq = startFreqMhz
                    while (isActive && freq <= BleConfig.Frequency.MAX_MHZ) {
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

        /** gather / DB aggregation 버퍼 항목. */
        private data class GatherEntry(
            val timestampMillis: Long,
            val raw: Int,
        )
    }
