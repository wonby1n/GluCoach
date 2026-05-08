package com.ssafy.s309.notification

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.annotation.RawRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앱 전역 사운드 재생 싱글톤.
 *
 * - [playSound] 호출 시 res/raw 의 MP3 파일을 재생.
 * - [isEnabled] 를 false 로 설정하면 전역 음소거.
 * - urgent = true 이면 현재 재생 중인 사운드를 끊고 즉시 재생 (저혈당 긴급 알림용).
 * - urgent = false 이면 이미 재생 중일 때 무시 (중복 알림 방지).
 */
@Singleton
class TtsManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        @Volatile var isEnabled: Boolean = true

        private var mediaPlayer: MediaPlayer? = null
        private val lock = Any()

        fun playSound(
            @RawRes resId: Int,
            urgent: Boolean = false,
        ) {
            if (!isEnabled) return
            synchronized(lock) {
                if (urgent) {
                    mediaPlayer?.stop()
                    mediaPlayer?.release()
                    mediaPlayer = null
                } else if (mediaPlayer?.isPlaying == true) {
                    return
                }
                try {
                    mediaPlayer =
                        MediaPlayer.create(context, resId)?.apply {
                            setOnCompletionListener { mp ->
                                mp.release()
                                synchronized(lock) {
                                    if (mediaPlayer == mp) mediaPlayer = null
                                }
                            }
                            start()
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "MP3 재생 실패: resId=$resId", e)
                }
            }
        }

        fun stop() {
            synchronized(lock) {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
            }
        }

        private companion object {
            const val TAG = "TtsManager"
        }
    }
