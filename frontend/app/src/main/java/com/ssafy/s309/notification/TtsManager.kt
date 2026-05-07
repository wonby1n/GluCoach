package com.ssafy.s309.notification

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * 앱 전역 TTS 싱글톤.
 *
 * Google Cloud TTS(어린이 느낌 WaveNet 음성)를 우선 사용하고,
 * 네트워크 실패 시 Android 내장 TTS로 폴백한다.
 *
 * - urgent = true 이면 현재 재생을 중단하고 즉시 재생 (저혈당 긴급 알림용)
 * - [isEnabled] = false 이면 전역 음소거
 */
@Singleton
class TtsManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val googleCloudTtsService: GoogleCloudTtsService,
    ) {
        @Volatile var isEnabled: Boolean = true

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val queueMutex = Mutex()
        private val queue = ArrayDeque<SpeakItem>()
        private var processingJob: Job? = null
        private var currentPlayer: MediaPlayer? = null

        private var androidTts: TextToSpeech? = null
        private var androidTtsReady = false

        private data class SpeakItem(val text: String, val urgent: Boolean)

        init {
            androidTts =
                TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val result = androidTts?.setLanguage(Locale.KOREAN)
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            androidTts?.setLanguage(Locale.getDefault())
                        }
                        androidTtsReady = true
                    } else {
                        Log.e(TAG, "Android TTS 초기화 실패: status=$status")
                    }
                }
        }

        fun speak(
            text: String,
            urgent: Boolean = false,
        ) {
            if (!isEnabled) return
            val clean = stripEmoji(text)
            if (clean.isBlank()) return

            scope.launch {
                queueMutex.withLock {
                    if (urgent) {
                        queue.clear()
                        processingJob?.cancel()
                        currentPlayer?.apply { stop(); release() }
                        currentPlayer = null
                        androidTts?.stop()
                        queue.addFirst(SpeakItem(clean, urgent))
                    } else {
                        queue.addLast(SpeakItem(clean, urgent))
                    }
                }
                ensureProcessing()
            }
        }

        fun stop() {
            scope.launch {
                queueMutex.withLock { queue.clear() }
                processingJob?.cancel()
                currentPlayer?.apply { stop(); release() }
                currentPlayer = null
                androidTts?.stop()
            }
        }

        private fun ensureProcessing() {
            if (processingJob?.isActive == true) return
            processingJob =
                scope.launch {
                    while (true) {
                        val item =
                            queueMutex.withLock {
                                if (queue.isEmpty()) null else queue.removeFirst()
                            } ?: break
                        playItem(item)
                    }
                }
        }

        private suspend fun playItem(item: SpeakItem) {
            val audioBytes = googleCloudTtsService.synthesize(item.text)
            if (audioBytes != null) {
                playMp3(audioBytes)
            } else {
                fallbackAndroidTts(item.text)
            }
        }

        private suspend fun playMp3(bytes: ByteArray) {
            val tmp =
                withContext(Dispatchers.IO) {
                    File.createTempFile("tts_", ".mp3", context.cacheDir).also { it.writeBytes(bytes) }
                }
            try {
                suspendCancellableCoroutine { cont ->
                    val player = MediaPlayer()
                    currentPlayer = player
                    try {
                        player.setDataSource(tmp.path)
                        player.setOnCompletionListener {
                            currentPlayer = null
                            player.release()
                            if (cont.isActive) cont.resume(Unit)
                        }
                        player.setOnErrorListener { _, _, _ ->
                            currentPlayer = null
                            player.release()
                            if (cont.isActive) cont.resume(Unit)
                            true
                        }
                        player.prepare()
                        player.start()
                    } catch (e: Exception) {
                        Log.e(TAG, "MediaPlayer 재생 실패", e)
                        currentPlayer = null
                        player.release()
                        if (cont.isActive) cont.resume(Unit)
                    }
                    cont.invokeOnCancellation {
                        currentPlayer = null
                        runCatching { player.stop(); player.release() }
                    }
                }
            } finally {
                tmp.delete()
            }
        }

        private fun fallbackAndroidTts(text: String) {
            if (androidTtsReady) {
                androidTts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
            }
        }

        private fun stripEmoji(text: String): String =
            text
                .replace(Regex("\\p{So}|\\p{Cs}"), "")
                .replace(Regex("\\s{2,}"), " ")
                .trim()

        companion object {
            private const val TAG = "TtsManager"
        }
    }
