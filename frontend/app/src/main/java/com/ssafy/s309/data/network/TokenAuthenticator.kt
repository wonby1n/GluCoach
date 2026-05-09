package com.ssafy.s309.data.network

import android.util.Log
import com.ssafy.s309.data.local.TokenManager
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenAuthenticator
    @Inject
    constructor(
        private val tokenManager: TokenManager,
    ) : Authenticator {
        override fun authenticate(
            route: Route?,
            response: Response,
        ): Request? {
            // 이미 한 번 재시도한 경우 무한 루프 방지
            if (response.priorResponse != null) return null

            val refreshToken = tokenManager.getRefreshToken() ?: return null

            val newAccessToken =
                synchronized(this) {
                    // 다른 스레드가 이미 갱신했으면 저장된 토큰 재사용
                    val currentToken = tokenManager.getAccessToken()
                    val reqToken = response.request.header("Authorization")?.removePrefix("Bearer ")
                    if (currentToken != null && currentToken != reqToken) {
                        currentToken
                    } else {
                        refreshSync(refreshToken)
                    }
                } ?: run {
                    tokenManager.clearTokens()
                    return null
                }

            return response.request.newBuilder()
                .header("Authorization", "Bearer $newAccessToken")
                .build()
        }

        private fun refreshSync(refreshToken: String): String? {
            return try {
                val client = OkHttpClient()
                val body =
                    """{"refreshToken":"$refreshToken"}"""
                        .toRequestBody("application/json".toMediaType())
                val req =
                    Request.Builder()
                        .url("${BASE_URL}api/auth/refresh")
                        .post(body)
                        .build()
                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) return null
                val json = JSONObject(resp.body?.string() ?: return null)
                val access = json.optString("accessToken").takeIf { it.isNotEmpty() } ?: return null
                val refresh = json.optString("refreshToken").takeIf { it.isNotEmpty() } ?: refreshToken
                tokenManager.saveTokens(access, refresh)
                Log.d(TAG, "액세스 토큰 갱신 완료")
                access
            } catch (e: Exception) {
                Log.w(TAG, "토큰 갱신 실패", e)
                null
            }
        }

        companion object {
            private const val TAG = "TokenAuthenticator"
            private const val BASE_URL = "https://k14s309.p.ssafy.io/"
        }
    }
