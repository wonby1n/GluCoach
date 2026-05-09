package com.ssafy.s309.data.api

import com.ssafy.s309.data.model.WeeklyReportResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface WeeklyReportApi {
    @GET("api/weekly-reports")
    suspend fun getWeeklyReports(): List<WeeklyReportResponse>

    @GET("api/weekly-reports/{id}/pdf")
    suspend fun getPdfRedirect(
        @Path("id") id: Int,
    ): Response<Unit>
}
