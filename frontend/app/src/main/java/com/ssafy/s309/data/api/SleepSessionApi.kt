package com.ssafy.s309.data.api

import com.ssafy.s309.data.model.SleepSessionCreateRequest
import com.ssafy.s309.data.model.SleepSessionResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface SleepSessionApi {
    /** 워치/Samsung Health 수면 세션 송신. (user_id, started_at) 중복 시 BE에서 기존 row 반환. */
    @POST("api/sleep-sessions")
    suspend fun create(
        @Body request: SleepSessionCreateRequest,
    ): SleepSessionResponse
}
