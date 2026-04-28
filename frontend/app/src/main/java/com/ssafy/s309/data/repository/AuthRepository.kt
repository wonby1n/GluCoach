package com.ssafy.s309.data.repository

import com.ssafy.s309.data.api.AuthApi
import com.ssafy.s309.data.local.TokenManager
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
    ) {
        suspend fun signup(
            email: String,
            password: String,
        ): Result<TokenResponse> =
            runCatching {
                val response = authApi.signup(SignupRequest(email, password))
                tokenManager.saveTokens(response.accessToken, response.refreshToken)
                tokenManager.saveEmail(email)
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
                response
            }

        suspend fun refresh(): Result<TokenResponse> =
            runCatching {
                val refreshToken =
                    tokenManager.getRefreshToken()
                        ?: error("리프레시 토큰이 없습니다")
                val response = authApi.refresh(ReissueRequest(refreshToken))
                tokenManager.saveTokens(response.accessToken, response.refreshToken)
                response
            }

        suspend fun logout(): Result<Unit> =
            runCatching {
                val refreshToken =
                    tokenManager.getRefreshToken()
                        ?: error("리프레시 토큰이 없습니다")
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
