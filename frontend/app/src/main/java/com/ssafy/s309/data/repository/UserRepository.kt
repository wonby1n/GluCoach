package com.ssafy.s309.data.repository

import com.ssafy.s309.data.api.UserApi
import com.ssafy.s309.data.model.FcmTokenRequest
import com.ssafy.s309.data.model.GuardianCreateRequest
import com.ssafy.s309.data.model.GuardianItem
import com.ssafy.s309.data.model.UserSearchResponse
import com.ssafy.s309.data.model.UserSettings
import com.ssafy.s309.data.model.UserSettingsUpdateRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository
    @Inject
    constructor(
        private val userApi: UserApi,
    ) {
        suspend fun registerFcmToken(token: String): Result<Unit> = runCatching { userApi.registerFcmToken(FcmTokenRequest(token)) }

        suspend fun deactivateFcmToken(token: String): Result<Unit> = runCatching { userApi.deactivateFcmToken(FcmTokenRequest(token)) }

        suspend fun getSettings(): Result<UserSettings> = runCatching { userApi.getSettings() }

        suspend fun updateSettings(request: UserSettingsUpdateRequest): Result<UserSettings> =
            runCatching { userApi.updateSettings(request) }

        suspend fun searchUser(phone: String): Result<UserSearchResponse> = runCatching { userApi.searchUser(phone = phone) }

        suspend fun getGuardians(): Result<List<GuardianItem>> = runCatching { userApi.getGuardians() }

        suspend fun createGuardian(request: GuardianCreateRequest): Result<GuardianItem> = runCatching { userApi.createGuardian(request) }

        suspend fun updateGuardian(
            wardGuardianId: Int,
            request: GuardianCreateRequest,
        ): Result<GuardianItem> = runCatching { userApi.updateGuardian(wardGuardianId, request) }

        suspend fun deleteGuardian(wardGuardianId: Int): Result<Unit> = runCatching { userApi.deleteGuardian(wardGuardianId) }
    }
