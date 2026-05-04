package com.ssafy.s309.ui.screen.health

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.data.repository.source.HealthConnectDataSource
import com.ssafy.s309.data.repository.source.SamsungHealthDataSource
import com.ssafy.s309.data.repository.source.SamsungHealthHolder
import com.ssafy.s309.feature.glucofit.health.HealthConnectManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 건강 데이터 소스 / 키보드 / 오버레이 권한 상태를 관리하는 ViewModel.
 *
 * 모든 SDK 호출은 try-catch 로 감싸 fallback 보장. ViewModel 자체는 항상 안전하게
 * 동작하며 권한 미부여 / SDK 미지원 환경에서도 화면이 깨지지 않는다.
 */
@HiltViewModel
class HealthSourceViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val hcDataSource: HealthConnectDataSource,
        private val samsungDataSource: SamsungHealthDataSource,
        private val samsungHealthHolder: SamsungHealthHolder,
        private val healthConnectManager: HealthConnectManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HealthSourceUiState())
        val uiState: StateFlow<HealthSourceUiState> = _uiState.asStateFlow()

        /** Health Connect 권한 다이얼로그에 넘길 권한 String 집합. */
        val healthConnectPermissions: Set<String> = HealthConnectManager.PERMISSIONS

        init {
            refresh()
        }

        /** 모든 소스의 가용성 / 권한 상태를 다시 조회. 화면 진입 / resume 시 호출. */
        fun refresh() {
            viewModelScope.launch {
                val hcAvailable =
                    runCatching { healthConnectManager.isAvailable() }
                        .getOrElse {
                            Log.w(TAG, "HC 가용성 확인 실패", it)
                            false
                        }
                val hcReady =
                    if (hcAvailable) hcDataSource.isReady() else false
                val samsungReady = samsungDataSource.isReady()
                val overlayGranted =
                    runCatching { Settings.canDrawOverlays(appContext) }
                        .getOrElse {
                            Log.w(TAG, "오버레이 권한 확인 실패", it)
                            false
                        }
                val imeEnabled = isGlucoFitImeEnabled()

                _uiState.update {
                    it.copy(
                        samsungSdkSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
                        samsungReady = samsungReady,
                        healthConnectAvailable = hcAvailable,
                        healthConnectReady = hcReady,
                        overlayGranted = overlayGranted,
                        imeEnabled = imeEnabled,
                    )
                }
            }
        }

        /** Samsung Health 권한 요청. 실제 다이얼로그는 SDK 가 띄우며, Activity RESUMED 상태여야 함. */
        fun requestSamsungPermissions() {
            viewModelScope.launch {
                val mgr = samsungHealthHolder.manager
                if (mgr == null) {
                    Log.w(TAG, "Samsung Health 매니저 없음 — Activity 미부착 또는 SDK 미지원")
                    return@launch
                }
                runCatching { mgr.requestPermissions() }
                    .onFailure { Log.w(TAG, "Samsung Health 권한 요청 실패", it) }
                refresh()
            }
        }

        /** Health Connect 권한 launcher 결과 처리 후 상태 갱신. */
        fun onHealthConnectPermissionResult() {
            refresh()
        }

        private fun isGlucoFitImeEnabled(): Boolean =
            try {
                val imm = appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.enabledInputMethodList?.any { info ->
                    info.packageName == appContext.packageName &&
                        info.serviceName.endsWith("GlucoseKeyboard")
                } ?: false
            } catch (t: Throwable) {
                Log.w(TAG, "IME 활성화 확인 실패", t)
                false
            }

        private companion object {
            const val TAG = "HealthSourceViewModel"
        }
    }

data class HealthSourceUiState(
    val samsungSdkSupported: Boolean = false,
    val samsungReady: Boolean = false,
    val healthConnectAvailable: Boolean = false,
    val healthConnectReady: Boolean = false,
    val overlayGranted: Boolean = false,
    val imeEnabled: Boolean = false,
)
