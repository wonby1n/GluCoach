package com.ssafy.s309.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

        private val _sessionExpiredFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val sessionExpiredFlow: SharedFlow<Unit> = _sessionExpiredFlow.asSharedFlow()

        fun notifySessionExpired() {
            _sessionExpiredFlow.tryEmit(Unit)
        }

        fun saveTokens(
            accessToken: String,
            refreshToken: String,
        ) {
            prefs.edit()
                .putString(KEY_ACCESS, accessToken)
                .putString(KEY_REFRESH, refreshToken)
                .apply()
        }

        fun saveEmail(email: String) {
            prefs.edit().putString(KEY_EMAIL, email).apply()
        }

        fun saveUserId(userId: String) {
            prefs.edit().putString(KEY_USER_ID, userId).apply()
        }

        fun getAccessToken(): String? = prefs.getString(KEY_ACCESS, null)

        fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH, null)

        fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)

        fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

        fun parseUserIdFromJwt(token: String): String? =
            try {
                val payload = token.split(".").getOrNull(1) ?: return null
                val decoded =
                    android.util.Base64.decode(
                        payload,
                        android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING,
                    )
                org.json.JSONObject(String(decoded)).getString("sub")
            } catch (e: Exception) {
                null
            }

        fun saveFcmToken(token: String) {
            prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
        }

        fun getFcmToken(): String? = prefs.getString(KEY_FCM_TOKEN, null)

        fun clearTokens() {
            // FCM 토큰은 디바이스에 묶인 값이라 로그아웃과 무관하게 보존한다.
            // 같이 지우면 재로그인 시 서버 PUT 할 토큰이 없고, FcmService.onNewToken 은
            // 토큰이 바뀔 때만 발화하므로 영영 등록되지 않는 케이스가 발생한다.
            val fcmToken = prefs.getString(KEY_FCM_TOKEN, null)
            prefs.edit().clear().apply()
            if (fcmToken != null) {
                prefs.edit().putString(KEY_FCM_TOKEN, fcmToken).apply()
            }
        }

        private companion object {
            const val KEY_ACCESS = "access_token"
            const val KEY_REFRESH = "refresh_token"
            const val KEY_EMAIL = "user_email"
            const val KEY_USER_ID = "user_id"
            const val KEY_FCM_TOKEN = "fcm_token"
        }
    }
