package com.ssafy.s309.data.repository

import android.util.Log
import com.ssafy.s309.data.api.WeeklyReportApi
import com.ssafy.s309.data.model.WeeklyReportResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeeklyReportRepository
    @Inject
    constructor(
        private val api: WeeklyReportApi,
    ) {
        suspend fun getWeeklyReports(): Result<List<WeeklyReportResponse>> =
            runCatching { api.getWeeklyReports() }
                .onFailure { Log.w(TAG, "getWeeklyReports failed", it) }

        suspend fun getPdfUrl(id: Int): Result<String> =
            runCatching {
                val response = api.getPdfRedirect(id)
                response.headers()["Location"] ?: error("PDF URL을 가져올 수 없어요")
            }.onFailure { Log.w(TAG, "getPdfUrl failed", it) }

        companion object {
            private const val TAG = "WeeklyReportRepository"
        }
    }
