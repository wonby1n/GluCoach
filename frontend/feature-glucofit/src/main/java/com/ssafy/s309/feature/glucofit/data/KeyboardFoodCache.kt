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
    private val GRADE_ORDER = mapOf("S" to 0, "A" to 1, "B" to 2, "C" to 3, "D" to 4)

    private val byName: MutableMap<String, KeyboardFoodItem> = HashMap(20_000)
    private val byDisplayName: MutableMap<String, KeyboardFoodItem> = HashMap(20_000)
    private val allItems: MutableList<KeyboardFoodItem> = ArrayList(20_000)

    private var loadedMtime: Long = -1L

    fun load(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            if (loadedMtime != -1L) {
                synchronized(this) {
                    byName.clear()
                    byDisplayName.clear()
                    allItems.clear()
                    loadedMtime = -1L
                }
            }
            Log.w(TAG, "no sync file — IME will not match foods until app syncs")
            return
        }
        val mtime = file.lastModified()
        if (mtime == loadedMtime) return
        synchronized(this) {
            if (mtime == loadedMtime) return
            val start = System.currentTimeMillis()
            runCatching {
                val type = object : TypeToken<List<KeyboardFoodItem>>() {}.type
                val items: List<KeyboardFoodItem> = Gson().fromJson(file.readText(), type)
                byName.clear()
                byDisplayName.clear()
                allItems.clear()
                allItems.addAll(items)
                items.forEach { item ->
                    indexToMap(byName, item.name, item)
                    item.displayName?.takeIf { it.isNotBlank() }
                        ?.let { indexToMap(byDisplayName, it, item) }
                }
                Log.i(TAG, "loaded ${items.size} foods in ${System.currentTimeMillis() - start}ms (mtime=$mtime)")
            }.onFailure { Log.w(TAG, "parse failed", it) }
            loadedMtime = mtime
        }
    }

    private fun indexToMap(
        map: MutableMap<String, KeyboardFoodItem>,
        key: String,
        item: KeyboardFoodItem,
    ) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return
        val existing = map[trimmed]
        if (existing == null || isBetter(item, existing)) {
            map[trimmed] = item
        }
    }

    private fun isBetter(
        candidate: KeyboardFoodItem,
        existing: KeyboardFoodItem,
    ): Boolean {
        if (existing.grade == null && candidate.grade != null) return true
        if (existing.grade != null && candidate.grade == null) return false
        if (existing.grade != null && candidate.grade != null) {
            return (GRADE_ORDER[candidate.grade] ?: 99) < (GRADE_ORDER[existing.grade] ?: 99)
        }
        return false
    }

    /**
     * 6단계 검색:
     *   1. name 정확 일치
     *   2. displayName 정확 일치
     *   3. input.contains(name)   — 문장 안에 음식명 포함 (가장 긴 매칭 우선)
     *   4. input.contains(displayName)
     *   5. name.contains(input)   — 타이핑 중 prefix 매칭 (fallback, 짧은 쪽 우선)
     *   6. displayName.contains(input)
     */
    fun findBest(text: String): KeyboardFoodItem? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        byName[trimmed]?.let { return it }
        byDisplayName[trimmed]?.let { return it }

        if (trimmed.length < 2) return null

        findFoodInInput(trimmed, useDisplayName = false)?.let { return it }
        findFoodInInput(trimmed, useDisplayName = true)?.let { return it }

        findInputInFood(trimmed, useDisplayName = false)?.let { return it }
        return findInputInFood(trimmed, useDisplayName = true)
    }

    /** 입력 문장이 음식명을 포함 — 가장 긴 매칭 우선. 단일 글자 음식명은 오탐 방지로 제외. */
    private fun findFoodInInput(
        input: String,
        useDisplayName: Boolean,
    ): KeyboardFoodItem? {
        var best: KeyboardFoodItem? = null
        var bestLen = 0
        for (item in allItems) {
            val field = (if (useDisplayName) item.displayName else item.name) ?: continue
            if (field.length < 2) continue
            if (!input.contains(field)) continue
            if (field.length > bestLen ||
                (field.length == bestLen && (best == null || isBetter(item, best)))
            ) {
                best = item
                bestLen = field.length
            }
        }
        return best
    }

    /** 음식명이 입력을 포함 — 타이핑 중 prefix 매칭. 짧은 이름 우선(완성도 높은 후보). */
    private fun findInputInFood(
        input: String,
        useDisplayName: Boolean,
    ): KeyboardFoodItem? {
        var best: KeyboardFoodItem? = null
        var bestLen = Int.MAX_VALUE
        for (item in allItems) {
            val field = (if (useDisplayName) item.displayName else item.name) ?: continue
            if (!field.contains(input)) continue
            if (field.length < bestLen || (field.length == bestLen && (best == null || isBetter(item, best)))) {
                best = item
                bestLen = field.length
            }
        }
        return best
    }

    fun topGraded(limit: Int = 10): List<KeyboardFoodItem> {
        return allItems
            .filter { it.grade != null }
            .sortedBy { GRADE_ORDER[it.grade] ?: 99 }
            .take(limit)
    }
}
