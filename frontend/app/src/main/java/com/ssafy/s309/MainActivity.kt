package com.ssafy.s309

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.ssafy.s309.data.ble.BleManager
import com.ssafy.s309.navigation.AppNavigation
import com.ssafy.s309.ui.theme.S309Theme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // TODO(BLE 테스트용): UI 연결 후 ViewModel 로 옮기고 이 필드/테스트 코드 제거.
    @Inject
    lateinit var bleManager: BleManager

    private var hasInitiatedConnect = false

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { results ->
            val allGranted = results.values.all { it }
            if (allGranted) {
                startBleTest()
            } else {
                Log.e(BLE_TEST_TAG, "권한 거부됨: $results")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            S309Theme {
                // 앱 전역 기본값: edge-to-edge 로 그려지는 상태바와 컨텐츠가 겹치지 않도록
                // 회원가입 온보딩과 동일하게 statusBarsPadding() 을 루트에 적용한다.
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                ) {
                    AppNavigation()
                }
            }
        }

        // TODO(BLE 테스트용): UI 연결 후 제거.
        permissionLauncher.launch(requiredBlePermissions())
    }

    private fun requiredBlePermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun startBleTest() {
        if (!bleManager.isBleSupported()) {
            Log.e(BLE_TEST_TAG, "❌ 이 기기는 BLE 를 지원하지 않습니다.")
            return
        }
        if (!bleManager.isBluetoothEnabled()) {
            Log.w(BLE_TEST_TAG, "⚠️ 블루투스가 꺼져있어 활성화 요청합니다.")
            bleManager.requestEnableBluetooth()
        }

        // 1. 연결 상태 변화 로깅
        lifecycleScope.launch {
            bleManager.connectionState.collect { state ->
                Log.d(BLE_TEST_TAG, "🔵 state: $state")
            }
        }

        // 2. 스캔된 기기 목록 로깅 + 첫 기기에 자동 연결
        lifecycleScope.launch {
            bleManager.scannedDevices.collect { devices ->
                if (devices.isNotEmpty()) {
                    Log.d(BLE_TEST_TAG, "📡 발견 ${devices.size}개: ${devices.map { it.name }}")
                }
                if (devices.isNotEmpty() && !hasInitiatedConnect) {
                    hasInitiatedConnect = true
                    val first = devices.first()
                    Log.d(BLE_TEST_TAG, "🔗 자동 연결 시도: name=${first.name} mac=${first.address}")
                    bleManager.connect(first)
                }
            }
        }

        // 3. 수신된 혈당 값 로깅
        lifecycleScope.launch {
            bleManager.glucoseReadings.collect { reading ->
                Log.d(
                    BLE_TEST_TAG,
                    "💉 ${reading.valueMgDl} mg/dL @ ${reading.timestampMillis}",
                )
            }
        }

        Log.d(BLE_TEST_TAG, "🔍 스캔 시작")
        bleManager.startScan()
    }

    private companion object {
        private const val BLE_TEST_TAG = "BLE_TEST"
    }
}
