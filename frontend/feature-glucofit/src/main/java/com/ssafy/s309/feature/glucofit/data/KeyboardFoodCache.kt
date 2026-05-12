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

    /** 마지막으로 로딩한 파일의 mtime. 파일이 새로 동기화되면 mtime이 바뀌어 재로딩 트리거. */
    private var loadedMtime: Long = -1L

    /**
     * 캐시 로드. 파일 mtime이 마지막 로드 시점과 다르면 재로딩.
     *
     * IME는 app 모듈과 같은 프로세스에서 돌지만 라이프사이클이 별개라, KeyboardFoodSyncManager가
     * 로그인 후 파일을 새로 쓰는 시점에 IME 서비스가 이미 살아있을 수 있다. 그 경우 옛 파일을 캐싱한
     * 상태로 남아 grade=null로 응답하는 트랩이 발생 (S14P31S309-1285 진단). mtime 비교로 자동 복구.
     */
    fun load(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            if (loadedMtime != -1L) {
                // 파일이 사라졌으면 캐시도 비움
                synchronized(this) {
                    byName.clear()
                    nameList.clear()
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
                nameList.clear()
                items.forEach { item ->
                    indexKey(item.name, item)
                    item.displayName?.takeIf { it.isNotBlank() && it != item.name }
                        ?.let { indexKey(it, item) }
                }
                Log.i(TAG, "loaded ${items.size} foods (${byName.size} keys) in ${System.currentTimeMillis() - start}ms (mtime=$mtime)")
            }.onFailure { Log.w(TAG, "parse failed", it) }
            loadedMtime = mtime
        }
    }

    /**
     * 같은 키에 여러 아이템이 매핑될 수 있음 (예: displayName="삼겹살"이 여러 풀네임에 존재).
     * grade 있는 아이템이 우선. 이미 grade 있는 게 들어가 있으면 덮어쓰지 않음.
     */
    private fun indexKey(
        key: String,
        item: KeyboardFoodItem,
    ) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return
        val existing = byName[trimmed]
        if (existing == null) {
            byName[trimmed] = item
            nameList.add(trimmed)
        } else if (existing.grade == null && item.grade != null) {
            byName[trimmed] = item
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
        if (best != null) return best
        // Reverse fallback — DB name이 input을 포함 (예: input "삼겹살" → "삼겹살구이").
        // 가장 짧은 매칭 우선 (입력에 가까운 것). 동률이면 grade 있는 쪽 우선.
        if (trimmed.length < 2) return null
        var rev: KeyboardFoodItem? = null
        var revLen = Int.MAX_VALUE
        for (name in nameList) {
            if (name.length >= revLen || !name.contains(trimmed)) continue
            val item = byName[name] ?: continue
            if (name.length < revLen || (rev?.grade == null && item.grade != null)) {
                rev = item
                revLen = name.length
            }
        }
        return rev
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
