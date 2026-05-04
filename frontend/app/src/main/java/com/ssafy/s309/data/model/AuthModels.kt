package com.ssafy.s309.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SignupRequest(val email: String, val password: String, val name: String, val phone: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class ReissueRequest(val refreshToken: String)

@Serializable
data class WithdrawRequest(val password: String)

@Serializable
data class TokenResponse(val accessToken: String, val refreshToken: String)
