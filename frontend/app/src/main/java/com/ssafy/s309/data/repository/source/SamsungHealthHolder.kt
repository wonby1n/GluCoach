package com.ssafy.s309.data.repository.source

import android.app.Activity
import android.os.Build
import android.util.Log
import com.ssafy.s309.feature.glucofit.health.SamsungHealthManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Samsung Health SDK 의 [SamsungHealthManager] 는 생성 시 Activity 가 필요하므로 @Singleton 이
 * 될 수 없다. MainActivity 가 onCreate 에서 [attach] 로 자기 자신을 등록하고, onDestroy 에서
 * [detach] 한다. Activity 미부착 / SDK 미지원 / 매니저 생성 실패 시 [manager] 는 null 로 유지되며,
 * SamsungHealthDataSource 는 이 상태를 사용 불가로 보고 Repository 의 mock fallback 으로 떨어진다.
 *
 * Holder 자체는 Singleton 이므로 어디서든 안전하게 주입받아 manager 가용성을 조회할 수 있다.
 */
@Singleton
class SamsungHealthHolder
    @Inject
    constructor() {
        @Volatile
        private var current: SamsungHealthManager? = null

        val manager: SamsungHealthManager? get() = current

        fun attach(activity: Activity) {
            // Samsung Health Data SDK 는 API 29+ 요구. 그 이하 기기에서는 SDK 자체가 깨질 수 있음.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Log.w(TAG, "API ${Build.VERSION.SDK_INT} — Samsung Health 미지원, manager 비활성")
                current = null
                return
            }
            current =
                try {
                    SamsungHealthManager(activity)
                } catch (t: Throwable) {
                    Log.w(TAG, "SamsungHealthManager 생성 실패 — SDK 미설치 가능성", t)
                    null
                }
        }

        fun detach() {
            current = null
        }

        private companion object {
            const val TAG = "SamsungHealthHolder"
        }
    }
