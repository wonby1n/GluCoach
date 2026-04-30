package com.ssafy.s309.feature.glucofit.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.BloodGlucose
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import androidx.health.connect.client.records.metadata.Metadata as HcMetadata

class HealthConnectManager(private val context: Context) {
    companion object {
        val PERMISSIONS =
            setOf(
                HealthPermission.getReadPermission(BloodGlucoseRecord::class),
                HealthPermission.getWritePermission(BloodGlucoseRecord::class),
                HealthPermission.getReadPermission(NutritionRecord::class),
            )
    }

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasPermissions(): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(PERMISSIONS)
    }

    suspend fun readBloodGlucose(days: Long = 7): List<BloodGlucoseSummary> {
        val end = Instant.now()
        val start = end.minus(days, ChronoUnit.DAYS)

        val response =
            client.readRecords(
                ReadRecordsRequest(
                    recordType = BloodGlucoseRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )

        val formatter = DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault())

        return response.records
            .sortedByDescending { it.time }
            .map { record ->
                BloodGlucoseSummary(
                    timeLabel = formatter.format(record.time),
                    mmolPerL = record.level.inMillimolesPerLiter,
                    mgPerDl = record.level.inMilligramsPerDeciliter,
                    relationToMeal =
                        when (record.relationToMeal) {
                            BloodGlucoseRecord.RELATION_TO_MEAL_FASTING -> "공복"
                            BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL -> "식전"
                            BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL -> "식후"
                            BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL -> "일반"
                            else -> "알 수 없음"
                        },
                    specimenSource =
                        when (record.specimenSource) {
                            BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD -> "모세혈관"
                            BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID -> "간질액(CGM)"
                            BloodGlucoseRecord.SPECIMEN_SOURCE_WHOLE_BLOOD -> "전혈"
                            else -> "혈액"
                        },
                )
            }
    }

    suspend fun readNutrition(days: Long = 7): List<NutritionSummary> {
        val end = Instant.now()
        val start = end.minus(days, ChronoUnit.DAYS)

        val response =
            client.readRecords(
                ReadRecordsRequest(
                    recordType = NutritionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )

        val formatter = DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault())

        return response.records
            .sortedByDescending { it.startTime }
            .map { record ->
                NutritionSummary(
                    timeLabel = formatter.format(record.startTime),
                    name = record.name ?: "알 수 없음",
                    energyKcal = record.energy?.inKilocalories,
                    totalCarbs = record.totalCarbohydrate?.inGrams,
                    sugar = record.sugar?.inGrams,
                )
            }
    }

    suspend fun insertSampleBloodGlucose() {
        val now = Instant.now()
        val kst = ZoneOffset.of("+09:00")

        val records =
            listOf(
                BloodGlucoseRecord(
                    time = now.minus(2, ChronoUnit.HOURS),
                    zoneOffset = kst,
                    level = BloodGlucose.milligramsPerDeciliter(95.0),
                    specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD,
                    relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_FASTING,
                    mealType = 0,
                    metadata = HcMetadata.unknownRecordingMethod(),
                ),
                BloodGlucoseRecord(
                    time = now.minus(30, ChronoUnit.MINUTES),
                    zoneOffset = kst,
                    level = BloodGlucose.milligramsPerDeciliter(140.0),
                    specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD,
                    relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL,
                    mealType = 0,
                    metadata = HcMetadata.unknownRecordingMethod(),
                ),
            )
        client.insertRecords(records)
    }
}

data class BloodGlucoseSummary(
    val timeLabel: String,
    val mmolPerL: Double,
    val mgPerDl: Double,
    val relationToMeal: String,
    val specimenSource: String,
)

data class NutritionSummary(
    val timeLabel: String,
    val name: String,
    val energyKcal: Double?,
    val totalCarbs: Double?,
    val sugar: Double?,
)
