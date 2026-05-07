package com.ssafy.s309.notification

import android.util.Base64
import android.util.Log
import com.ssafy.s309.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleCloudTtsService
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
    ) {
        suspend fun synthesize(text: String): ByteArray? =
            withContext(Dispatchers.IO) {
                runCatching {
                    val body =
                        JSONObject().apply {
                            put("input", JSONObject().put("text", text))
                            put(
                                "voice",
                                JSONObject().apply {
                                    put("languageCode", "ko-KR")
                                    put("name", "ko-KR-Wavenet-A")
                                },
                            )
                            put(
                                "audioConfig",
                                JSONObject().apply {
                                    put("audioEncoding", "MP3")
                                    put("pitch", 5.0)
                                    put("speakingRate", 1.05)
                                },
                            )
                        }.toString()

                    val request =
                        Request.Builder()
                            .url("https://texttospeech.googleapis.com/v1/text:synthesize?key=${BuildConfig.GOOGLE_TTS_API_KEY}")
                            .post(body.toRequestBody("application/json".toMediaType()))
                            .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.w(TAG, "Google TTS 오류: ${response.code}")
                            return@withContext null
                        }
                        val json = JSONObject(response.body!!.string())
                        Base64.decode(json.getString("audioContent"), Base64.DEFAULT)
                    }
                }.getOrElse {
                    Log.e(TAG, "Google TTS 호출 실패", it)
                    null
                }
            }

        companion object {
            private const val TAG = "GoogleCloudTtsService"
        }
    }
