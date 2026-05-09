package com.ssafy.s309.feature.glucofit.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * IME가 메모리에 보유하는 음식 매칭 인덱스.
 *
 * filesDir/keyboard_foods.json (app 모듈이 동기화) → HashMap<String, KeyboardFoodItem>.
 * 19,600개 기준 메모리 ~3MB, 로드 50~100ms (IME onCreate 1회).
 *
 * 매칭 전략:
 *   - exact match: HashMap O(1)
 *   - partial match (포함): linear scan, 확인 버튼 누를 때만 1회 → 무관
 *
 * 파일 없거나 파싱 실패 시 빈 캐시 (배너 안 뜸, 폴백은 GlucoseKeyboard 측에서 결정).
 */
object KeyboardFoodCache {
    private const val TAG = "KeyboardFoodCache"
    private const val FILE_NAME = "keyboard_foods.json"

    private val byName: MutableMap<String, KeyboardFoodItem> = HashMap(20_000)
    private val nameList: MutableList<String> = ArrayList(20_000)
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) {
                Log.w(TAG, "no sync file — IME will not match foods until app syncs")
                loaded = true
                return
            }
            val start = System.currentTimeMillis()
            runCatching {
                val type = object : TypeToken<List<KeyboardFoodItem>>() {}.type
                val items: List<KeyboardFoodItem> = Gson().fromJson(file.readText(), type)
                items.forEach {
                    byName[it.name] = it
                    nameList.add(it.name)
                }
                Log.i(TAG, "loaded ${items.size} foods in ${System.currentTimeMillis() - start}ms")
            }.onFailure { Log.w(TAG, "parse failed", it) }
            loaded = true
        }
    }

    /** 정확 일치 (대소문자 무시는 한국어엔 의미 없음, 그대로). */
    fun findExact(text: String): KeyboardFoodItem? = byName[text.trim()]

    /**
     * 부분 일치 — 입력 텍스트에 포함된 음식명 중 가장 긴 것 반환. "치킨 먹고 싶다" → "치킨" 같은 단순 substring 매칭.
     *
     * <p>19,600개 linear scan이지만 확인 1회 누를 때만 1번 → ~5ms 무관.
     */
    fun findContained(text: String): KeyboardFoodItem? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        var best: KeyboardFoodItem? = null
        var bestLen = 0
        for (name in nameList) {
            if (name.length > bestLen && trimmed.contains(name)) {
                best = byName[name]
                bestLen = name.length
            }
        }
        return best
    }

    /** 사용자 등급이 있는 음식 Top N — 추천 칩으로 사용 (등급 좋은 순 / 동률이면 input order). */
    fun topGraded(limit: Int = 10): List<KeyboardFoodItem> {
        val gradeOrder = mapOf("S" to 0, "A" to 1, "B" to 2, "C" to 3, "D" to 4)
        return byName.values
            .filter { it.grade != null }
            .sortedBy { gradeOrder[it.grade] ?: 99 }
            .take(limit)
    }
}
