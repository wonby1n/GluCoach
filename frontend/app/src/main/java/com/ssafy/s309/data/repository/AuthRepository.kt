package com.ssafy.s309.data.repository

import android.util.Log
import com.ssafy.s309.data.api.AuthApi
import com.ssafy.s309.data.api.UserApi
import com.ssafy.s309.data.local.TokenManager
import com.ssafy.s309.data.model.FcmTokenRequest
import com.ssafy.s309.data.model.LoginRequest
import com.ssafy.s309.data.model.ReissueRequest
import com.ssafy.s309.data.model.SignupRequest
import com.ssafy.s309.data.model.TokenResponse
import com.ssafy.s309.data.model.WithdrawRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository
    @Inject
    constructor(
        private val authApi: AuthApi,
        private val tokenManager: TokenManager,
        private val userApi: UserApi,
        private val keyboardFoodSync: KeyboardFoodSyncManager,
    ) {
        suspend fun signup(
            email: String,
            password: String,
            name: String,
            phone: String,
        ): Result<TokenResponse> =
            runCatching {
                val response = authApi.signup(SignupRequest(email, password, name, phone))
                tokenManager.saveTokens(response.accessToken, response.refreshToken)
                tokenManager.saveEmail(email)
                tokenManager.parseUserIdFromJwt(response.accessToken)?.let { tokenManager.saveUserId(it) }
                sendStoredFcmToken()
                keyboardFoodSync.syncIfNeeded(force = true)
                response
            }

        suspend fun login(
            email: String,
            password: String,
        ): Result<TokenResponse> =
            runCatching {
                val response = authApi.login(LoginRequest(email, password))
                tokenManager.saveTokens(response.accessToken, response.refreshToken)
                tokenManager.saveEmail(email)
                tokenManager.parseUserIdFromJwt(response.accessToken)?.let { tokenManager.saveUserId(it) }
                sendStoredFcmToken()
                keyboardFoodSync.syncIfNeeded(force = true)
                response
            }

        /** 로컬에 저장된 FCM 토큰을 서버에 등록. 실패해도 로그인 흐름은 계속된다. */
        private suspend fun sendStoredFcmToken() {
            val fcmToken = tokenManager.getFcmToken() ?: return
            runCatching {
                userApi.registerFcmToken(FcmTokenRequest(fcmToken))
            }.onFailure {
                Log.w("AuthRepository", "FCM 토큰 서버 등록 실패", it)
            }
        }

        suspend fun refresh(): Result<TokenResponse> =
            runCatching {
                val refreshToken =
                    tokenManager.getRefreshToken()
                        ?: error("리프레시 토큰이 없습니다")
                val response = authApi.refresh(ReissueRequest(refreshToken))
                tokenManager.saveTokens(response.accessToken, response.refreshToken)
                tokenManager.parseUserIdFromJwt(response.accessToken)?.let { tokenManager.saveUserId(it) }
                sendStoredFcmToken()
                response
            }

        suspend fun logout(): Result<Unit> =
            runCatching {
                val refreshToken =
                    tokenManager.getRefreshToken()
                        ?: error("리프레시 토큰이 없습니다")
                // 이 기기의 FCM 토큰을 백엔드에서 deactivate — 같은 기기에 다른 사용자가
                // 로그인했을 때 이전 사용자의 알림이 새 사용자에게 가는 사고를 막는다.
                // 실패해도 로그아웃 자체는 진행해야 하므로 결과 무시.
                tokenManager.getFcmToken()?.let { fcm ->
                    runCatching { userApi.deactivateFcmToken(FcmTokenRequest(fcm)) }
                        .onFailure { Log.w("AuthRepo", "FCM 토큰 deactivate 실패 (무시)", it) }
                }
                authApi.logout(ReissueRequest(refreshToken))
                tokenManager.clearTokens()
            }

        suspend fun withdraw(password: String): Result<Unit> =
            runCatching {
                val accessToken =
                    tokenManager.getAccessToken()
                        ?: error("액세스 토큰이 없습니다")
                authApi.withdraw("Bearer $accessToken", WithdrawRequest(password))
                tokenManager.clearTokens()
            }

        fun isLoggedIn(): Boolean = tokenManager.getAccessToken() != null

        fun getUserEmail(): String? = tokenManager.getEmail()
    }
