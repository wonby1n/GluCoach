package com.ssafy.s309.data.api

import com.ssafy.s309.data.model.FcmTokenRequest
import com.ssafy.s309.data.model.GuardianCreateRequest
import com.ssafy.s309.data.model.GuardianItem
import com.ssafy.s309.data.model.UserSearchResponse
import com.ssafy.s309.data.model.UserSettings
import com.ssafy.s309.data.model.UserSettingsUpdateRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface UserApi {
    @PUT("api/user/fcm-token")
    suspend fun registerFcmToken(
        @Body request: FcmTokenRequest,
    )

    /** 로그아웃 시 이 기기의 FCM 토큰만 백엔드에서 is_active=false 로 전환. */
    @HTTP(method = "DELETE", path = "api/user/fcm-token", hasBody = true)
    suspend fun deactivateFcmToken(
        @Body request: FcmTokenRequest,
    )

    @GET("api/user/settings")
    suspend fun getSettings(): UserSettings

    @PUT("api/user/settings")
    suspend fun updateSettings(
        @Body request: UserSettingsUpdateRequest,
    ): UserSettings

    /** 이메일 또는 전화번호로 앱 가입 사용자 검색 — 보호자 추가 전 userId 확인용 */
    @GET("api/user/search")
    suspend fun searchUser(
        @Query("email") email: String? = null,
        @Query("phone") phone: String? = null,
    ): UserSearchResponse

    @GET("api/user/guardians")
    suspend fun getGuardians(): List<GuardianItem>

    @POST("api/user/guardians")
    suspend fun createGuardian(
        @Body request: GuardianCreateRequest,
    ): GuardianItem

    @PUT("api/user/guardians/{wardGuardianId}")
    suspend fun updateGuardian(
        @Path("wardGuardianId") wardGuardianId: Int,
        @Body request: GuardianCreateRequest,
    ): GuardianItem

    @DELETE("api/user/guardians/{wardGuardianId}")
    suspend fun deleteGuardian(
        @Path("wardGuardianId") wardGuardianId: Int,
    )
}
