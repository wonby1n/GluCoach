package com.ssafy.s309.data.api

import com.ssafy.s309.data.model.AlertListResponse
import com.ssafy.s309.data.model.CgmRecordResponse
import com.ssafy.s309.data.model.DailyHealthSummaryResponse
import com.ssafy.s309.data.model.DailyHealthSummaryUpsertRequest
import com.ssafy.s309.data.model.HealthSnapshotBatchRequest
import com.ssafy.s309.data.model.HealthSnapshotBatchResponse
import com.ssafy.s309.data.model.MealCreateResponse
import com.ssafy.s309.data.model.MealRecordResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface HealthApi {
    /** 혈당 기록 기간 조회. from/to = ISO-8601 datetime (e.g. "2026-05-06T00:00:00") */
    @GET("api/glucose-records")
    suspend fun getGlucoseRecords(
        @Query("from") from: String,
        @Query("to") to: String,
    ): List<CgmRecordResponse>

    /** 일별 헬스 요약 기간 조회. from/to = ISO-8601 date (e.g. "2026-05-06") */
    @GET("api/health/daily-summary")
    suspend fun getDailyHealthSummary(
        @Query("from") from: String,
        @Query("to") to: String,
    ): List<DailyHealthSummaryResponse>

    /** 날짜별 식사 기록 조회. date = ISO-8601 date (e.g. "2026-05-06") */
    @GET("api/meals")
    suspend fun getMeals(
        @Query("date") date: String,
    ): List<MealRecordResponse>

    /** 식사 기록 생성. request = JSON, image = 선택적 사진 */
    @Multipart
    @POST("api/meals")
    suspend fun createMeal(
        @Part("request") request: RequestBody,
        @Part image: MultipartBody.Part?,
    ): MealCreateResponse

    /** 알림 목록 조회 */
    @GET("api/v1/alerts")
    suspend fun getAlerts(
        @Query("is_read") isRead: Boolean? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): AlertListResponse

    /** 알림 읽음 처리 */
    @PATCH("api/v1/alerts/{id}/read")
    suspend fun markAlertRead(
        @Path("id") id: Int,
    )

    /** 1분 폴 시계열 5분 배치 INSERT */
    @POST("api/health/snapshots")
    suspend fun saveSnapshotBatch(
        @Body request: HealthSnapshotBatchRequest,
    ): HealthSnapshotBatchResponse

    /** 일별 헬스 요약 upsert (대시보드/AI Report 원천) */
    @POST("api/health/daily-summary")
    suspend fun upsertDailySummary(
        @Body request: DailyHealthSummaryUpsertRequest,
    ): DailyHealthSummaryResponse
}
