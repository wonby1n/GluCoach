package com.ssafy.s309.data.api

import com.ssafy.s309.data.model.FcmTokenRequest
import com.ssafy.s309.data.model.GuardianCreateRequest
import com.ssafy.s309.data.model.GuardianItem
import com.ssafy.s309.data.model.UserSettings
import com.ssafy.s309.data.model.UserSettingsUpdateRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface UserApi {
    @PUT("api/users/{userId}/fcm-token")
    suspend fun registerFcmToken(
        @Path("userId") userId: String,
        @Body request: FcmTokenRequest,
    )

    @GET("api/users/{userId}/settings")
    suspend fun getSettings(
        @Path("userId") userId: String,
    ): UserSettings

    @PUT("api/users/{userId}/settings")
    suspend fun updateSettings(
        @Path("userId") userId: String,
        @Body request: UserSettingsUpdateRequest,
    ): UserSettings

    @GET("api/users/{userId}/guardians")
    suspend fun getGuardians(
        @Path("userId") userId: String,
    ): List<GuardianItem>

    @POST("api/users/{userId}/guardians")
    suspend fun createGuardian(
        @Path("userId") userId: String,
        @Body request: GuardianCreateRequest,
    ): GuardianItem

    @PUT("api/users/{userId}/guardians/{guardianId}")
    suspend fun updateGuardian(
        @Path("userId") userId: String,
        @Path("guardianId") guardianId: String,
        @Body request: GuardianCreateRequest,
    ): GuardianItem

    @DELETE("api/users/{userId}/guardians/{guardianId}")
    suspend fun deleteGuardian(
        @Path("userId") userId: String,
        @Path("guardianId") guardianId: String,
    )
}
