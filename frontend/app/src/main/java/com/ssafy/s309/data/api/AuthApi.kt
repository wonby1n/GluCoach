package com.ssafy.s309.data.api

import com.ssafy.s309.data.model.LoginRequest
import com.ssafy.s309.data.model.ReissueRequest
import com.ssafy.s309.data.model.SignupRequest
import com.ssafy.s309.data.model.TokenResponse
import com.ssafy.s309.data.model.WithdrawRequest
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApi {
    @POST("api/auth/signup")
    suspend fun signup(
        @Body request: SignupRequest,
    ): TokenResponse

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest,
    ): TokenResponse

    @POST("api/auth/refresh")
    suspend fun refresh(
        @Body request: ReissueRequest,
    ): TokenResponse

    @POST("api/auth/logout")
    suspend fun logout(
        @Body request: ReissueRequest,
    )

    @HTTP(method = "DELETE", path = "api/auth/withdraw", hasBody = true)
    suspend fun withdraw(
        @Header("Authorization") bearerToken: String,
        @Body request: WithdrawRequest,
    )
}
