package com.ssafy.s309.data.api

import com.ssafy.s309.data.model.FcmTokenRequest
import com.ssafy.s309.data.model.GuardianCreateRequest
import com.ssafy.s309.data.model.GuardianItem
import com.ssafy.s309.data.model.UserProfile
import com.ssafy.s309.data.model.UserProfileUpdateRequest
import com.ssafy.s309.data.model.UserSettings
import com.ssafy.s309.data.model.UserSettingsUpdateRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface UserApi {
    @GET("api/users/{userId}")
    suspend fun getProfile(
        @Path("userId") userId: String,
    ): UserProfile

    @PUT("api/users/{userId}")
    suspend fun updateProfile(
        @Path("userId") userId: String,
        @Body request: UserProfileUpdateRequest,
    ): UserProfile

    @PUT("api/users/{userId}/fcm-token")
    suspend fun registerFcmToken(
        @Path("userId") userId: String,
        @Body request: FcmTokenRequest,
    )

    @GET("api/user/settings")
    suspend fun getSettings(): UserSettings

    @PUT("api/user/settings")
    suspend fun updateSettings(
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
