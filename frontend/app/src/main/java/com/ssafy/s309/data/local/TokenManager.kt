package com.ssafy.s309.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

        fun saveTokens(
            accessToken: String,
            refreshToken: String,
        ) {
            prefs.edit()
                .putString(KEY_ACCESS, accessToken)
                .putString(KEY_REFRESH, refreshToken)
                .apply()
        }

        fun saveEmail(email: String) {
            prefs.edit().putString(KEY_EMAIL, email).apply()
        }

        fun getAccessToken(): String? = prefs.getString(KEY_ACCESS, null)

        fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH, null)

        fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)

        fun clearTokens() {
            prefs.edit().clear().apply()
        }

        private companion object {
            const val KEY_ACCESS = "access_token"
            const val KEY_REFRESH = "refresh_token"
            const val KEY_EMAIL = "user_email"
        }
    }
