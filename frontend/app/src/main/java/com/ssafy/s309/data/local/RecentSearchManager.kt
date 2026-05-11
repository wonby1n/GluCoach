package com.ssafy.s309.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentSearchManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val prefs = context.getSharedPreferences("recent_search_prefs", Context.MODE_PRIVATE)

        fun getKeywords(userId: String): List<String> {
            val json = prefs.getString(keyFor(userId), null) ?: return emptyList()
            return try {
                val array = JSONArray(json)
                List(array.length()) { array.getString(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun addKeyword(
            userId: String,
            keyword: String,
        ) {
            val keywords = getKeywords(userId).toMutableList()
            keywords.remove(keyword)
            keywords.add(0, keyword)
            if (keywords.size > MAX_KEYWORDS) {
                keywords.subList(MAX_KEYWORDS, keywords.size).clear()
            }
            save(userId, keywords)
        }

        fun removeKeyword(
            userId: String,
            keyword: String,
        ) {
            val keywords = getKeywords(userId).toMutableList()
            keywords.remove(keyword)
            save(userId, keywords)
        }

        private fun save(
            userId: String,
            keywords: List<String>,
        ) {
            val array = JSONArray(keywords)
            prefs.edit().putString(keyFor(userId), array.toString()).apply()
        }

        private fun keyFor(userId: String) = "recent_keywords_$userId"

        private companion object {
            const val MAX_KEYWORDS = 20
        }
    }
