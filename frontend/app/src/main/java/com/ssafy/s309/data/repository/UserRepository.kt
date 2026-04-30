package com.ssafy.s309.data.repository

import com.ssafy.s309.data.api.UserApi
import com.ssafy.s309.data.local.TokenManager
import com.ssafy.s309.data.model.FcmTokenRequest
import com.ssafy.s309.data.model.GuardianCreateRequest
import com.ssafy.s309.data.model.GuardianItem
import com.ssafy.s309.data.model.UserSettings
import com.ssafy.s309.data.model.UserSettingsUpdateRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository
    @Inject
    constructor(
        private val userApi: UserApi,
        private val tokenManager: TokenManager,
    ) {
        private fun userId(): String = tokenManager.getUserId() ?: error("로그인이 필요합니다")

        suspend fun registerFcmToken(token: String): Result<Unit> =
            runCatching {
                val id = tokenManager.getUserId() ?: return@runCatching
                userApi.registerFcmToken(id, FcmTokenRequest(token))
            }

        suspend fun getSettings(): Result<UserSettings> = runCatching { userApi.getSettings(userId()) }

        suspend fun updateSettings(request: UserSettingsUpdateRequest): Result<UserSettings> =
            runCatching { userApi.updateSettings(userId(), request) }

        suspend fun getGuardians(): Result<List<GuardianItem>> = runCatching { userApi.getGuardians(userId()) }

        suspend fun createGuardian(request: GuardianCreateRequest): Result<GuardianItem> =
            runCatching { userApi.createGuardian(userId(), request) }

        suspend fun updateGuardian(
            guardianId: String,
            request: GuardianCreateRequest,
        ): Result<GuardianItem> = runCatching { userApi.updateGuardian(userId(), guardianId, request) }

        suspend fun deleteGuardian(guardianId: String): Result<Unit> = runCatching { userApi.deleteGuardian(userId(), guardianId) }
    }
