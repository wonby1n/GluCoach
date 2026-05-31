package com.ssafy.s309.ui.component

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ssafy.s309.MainActivity
import com.ssafy.s309.voice.WakeWordManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Hi Kiki" wake word 가 앱 백그라운드 상태에서 감지됐을 때 [KikiVoiceOverlayContent] 를
 * [WindowManager] 시스템 오버레이로 띄운다.
 *
 * - 앱이 포그라운드일 때는 인앱 [KikiVoiceOverlay] 가 담당하므로 시스템 오버레이를 표시하지 않는다.
 *   포그라운드 상태는 [MainActivity.onStart]/[onStop] 이 [onAppForeground]/[onAppBackground] 로 전달.
 * - SYSTEM_ALERT_WINDOW 권한이 없으면 조용히 skip. [canDrawOverlays] 로 사전 확인 가능.
 * - "탭해서 채팅 열기" → Intent 로 MainActivity 포그라운드 진입 + KikiChat route.
 */
@Singleton
class SystemOverlayManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val wakeWordManager: WakeWordManager,
    ) {
        private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        private var overlayView: View? = null
        private var overlayLifecycleOwner: OverlayLifecycleOwner? = null
        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        private val attached = AtomicBoolean(false)
        private val isAppForeground = AtomicBoolean(false)

        fun onAppForeground() {
            isAppForeground.set(true)
            // 앱이 포그라운드로 돌아오면 인앱 오버레이가 담당 — 시스템 오버레이 제거
            hideOverlay()
        }

        fun onAppBackground() {
            isAppForeground.set(false)
        }

        fun attach() {
            if (!attached.compareAndSet(false, true)) return
            scope.launch {
                wakeWordManager.uiState.collect { state ->
                    if (state != WakeWordManager.UiState.IDLE && !isAppForeground.get()) {
                        showOverlay()
                    } else {
                        hideOverlay()
                    }
                }
            }
        }

        fun canDrawOverlays(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

        /** "다른 앱 위에 표시" 시스템 설정 화면을 연다. */
        fun requestOverlayPermission() {
            val intent =
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(intent)
        }

        private fun showOverlay() {
            if (overlayView != null) return
            if (!canDrawOverlays()) {
                Log.w(TAG, "SYSTEM_ALERT_WINDOW 권한 없음 — 설정 화면 안내")
                requestOverlayPermission()
                return
            }

            val lifecycleOwner = OverlayLifecycleOwner().also { overlayLifecycleOwner = it }

            val composeView =
                ComposeView(context).apply {
                    setContent {
                        val uiState by wakeWordManager.uiState.collectAsState()
                        val partial by wakeWordManager.partialTranscript.collectAsState()
                        val response by wakeWordManager.responseText.collectAsState()
                        val thinkingHint by wakeWordManager.thinkingHint.collectAsState()
                        KikiVoiceOverlayContent(
                            uiState = uiState,
                            partial = partial,
                            response = response,
                            thinkingHint = thinkingHint,
                            onResponseTapped = {
                                wakeWordManager.dismissResponse()
                                launchApp()
                            },
                            onClose = { wakeWordManager.abort() },
                        )
                    }
                }

            // FrameLayout 루트: 뒤로가기 키 이벤트 수신 + ViewTree 설정 대상.
            // ComposeView 는 final 클래스라 상속 불가 → 래퍼로 dispatchKeyEvent 처리.
            val view =
                FrameLayout(context).apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setOnKeyListener { _, keyCode, event ->
                        if (keyCode == KeyEvent.KEYCODE_BACK &&
                            event.action == KeyEvent.ACTION_UP
                        ) {
                            wakeWordManager.abort()
                            true
                        } else {
                            false
                        }
                    }
                    addView(
                        composeView,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
            // ComposeView 가 WindowManager 에 붙을 때 ViewTree lifecycle 을 찾지 못하면
            // 첫 layout pass 에서 IllegalStateException 이 발생해 composition 이 시작되지 않음.
            // 루트 뷰에 설정하면 자식 ComposeView 까지 전파된다.
            view.setViewTreeLifecycleOwner(lifecycleOwner)
            view.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            val params =
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    },
                    // 포커스 허용: 뒤로가기 키 이벤트를 오버레이가 직접 수신해 abort() 처리.
                    // FLAG_NOT_FOCUSABLE 제거로 오버레이 등장 시 키보드가 내려가지만,
                    // 음성 모드 진입 후이므로 UX상 자연스럽다.
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                }

            try {
                windowManager.addView(view, params)
                view.requestFocus()
                overlayView = view
                Log.i(TAG, "시스템 오버레이 표시")
            } catch (e: Exception) {
                Log.e(TAG, "WindowManager.addView 실패", e)
                overlayLifecycleOwner?.teardown()
                overlayLifecycleOwner = null
            }
        }

        private fun hideOverlay() {
            val v = overlayView ?: return
            overlayView = null
            overlayLifecycleOwner?.teardown()
            overlayLifecycleOwner = null
            try {
                windowManager.removeView(v)
                Log.i(TAG, "시스템 오버레이 제거")
            } catch (e: Exception) {
                Log.e(TAG, "WindowManager.removeView 실패", e)
            }
        }

        private fun launchApp() {
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MainActivity.EXTRA_NAVIGATE_TO, "kiki_chat")
                }
            context.startActivity(intent)
        }

        private class OverlayLifecycleOwner : SavedStateRegistryOwner {
            private val lifecycleRegistry = LifecycleRegistry(this)
            private val controller = SavedStateRegistryController.create(this)

            override val lifecycle: Lifecycle = lifecycleRegistry
            override val savedStateRegistry: SavedStateRegistry = controller.savedStateRegistry

            init {
                controller.performAttach()
                controller.performRestore(null)
                lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            }

            fun teardown() {
                lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            }
        }

        private companion object {
            const val TAG = "SystemOverlayManager"
        }
    }
