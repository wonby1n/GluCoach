package com.ssafy.s309.data.api

import com.ssafy.s309.data.model.PostMealReplyRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface AgentApi {
    /** 식후 활동 유도 에이전트 — 유저 응답 전달 */
    @POST("ai/agent/post-meal")
    suspend fun postMealReply(
        @Body request: PostMealReplyRequest,
    )
}
