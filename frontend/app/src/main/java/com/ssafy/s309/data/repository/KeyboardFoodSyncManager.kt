package com.ssafy.s309.data.repository

import android.content.Context
import android.util.Log
import com.ssafy.s309.data.api.FoodApi
import com.ssafy.s309.data.local.TokenManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IME 키보드용 음식 목록 동기화.
 *
 * BE에서 전체 음식(~19,600개) + 사용자 등급을 받아 filesDir에 JSON으로 저장.
 * IME(GlucoseKeyboard)는 같은 프로세스에서 이 파일을 읽어 메모리에 보유.
 *
 * 동기화 시점:
 *   - 앱 시작 시 (S309Application.onCreate) 로그인 상태면 1회
 *   - 로그인 성공 직후
 *   - TTL 24시간 내면 skip
 */
@Singleton
class KeyboardFoodSyncManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val foodApi: FoodApi,
        private val tokenManager: TokenManager,
    ) {
        private val scope = CoroutineScope(Dispatchers.IO)
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
            }
        private val syncedFile by lazy { File(context.filesDir, FILE_NAME) }

        /** 비동기 동기화 — 토큰 있을 때만, TTL 24h 내면 skip. */
        fun syncIfNeeded(force: Boolean = false) {
            if (tokenManager.getAccessToken() == null) {
                Log.d(TAG, "skip — not logged in")
                return
            }
            if (!force && isFresh()) {
                Log.d(TAG, "skip — fresh (age < ${TTL_MS / 1000}s)")
                return
            }
            scope.launch {
                runCatching { syncBlocking() }
                    .onFailure { Log.w(TAG, "sync failed", it) }
            }
        }

        private suspend fun syncBlocking() {
            val start = System.currentTimeMillis()
            val items = foodApi.getKeyboardFoods()
            val payload = json.encodeToString(items)
            syncedFile.writeText(payload)
            Log.i(
                TAG,
                "sync ok — ${items.size} foods, ${payload.length / 1024}KB, ${System.currentTimeMillis() - start}ms",
            )
        }

        private fun isFresh(): Boolean {
            if (!syncedFile.exists()) return false
            val age = System.currentTimeMillis() - syncedFile.lastModified()
            return age < TTL_MS
        }

        companion object {
            private const val TAG = "KeyboardFoodSync"
            private const val FILE_NAME = "keyboard_foods.json"
            private const val TTL_MS = 24L * 60 * 60 * 1000 // 24시간
        }
    }
