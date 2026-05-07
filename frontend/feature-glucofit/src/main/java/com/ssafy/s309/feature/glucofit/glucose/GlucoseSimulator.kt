package com.ssafy.s309.feature.glucofit.glucose

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object GlucoseSimulator {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    private val _glucoseState = MutableStateFlow<Double?>(null)
    val glucoseState: StateFlow<Double?> = _glucoseState.asStateFlow()

    val isRunning: Boolean get() = job?.isActive == true

    private fun assetFile(diabetesType: String) =
        when (diabetesType) {
            "TYPE1" -> "glucose_sim/t1d.csv"
            "TYPE2" -> "glucose_sim/t2d.csv"
            else -> "glucose_sim/none.csv"
        }

    // CSV 로딩: assets에서 읽어 Double 리스트 반환. 실패 시 null.
    private fun loadValues(
        context: Context,
        diabetesType: String,
    ): List<Double>? =
        runCatching {
            context.assets.open(assetFile(diabetesType))
                .bufferedReader()
                .lineSequence()
                .mapNotNull { it.trim().toDoubleOrNull() }
                .toList()
                .takeIf { it.isNotEmpty() }
        }.getOrNull()

    fun start(
        context: Context,
        diabetesType: String = "NONE",
    ) {
        if (job?.isActive == true) return
        val values = loadValues(context, diabetesType)
        job =
            scope.launch {
                if (values != null) {
                    replayLoop(values)
                } else {
                    // assets 로드 실패 시 랜덤 fallback
                    randomLoop(diabetesType)
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
        _glucoseState.value = null
    }

    // CSV 값을 순서대로 1초마다 방출하고, 끝나면 처음부터 반복
    private suspend fun replayLoop(values: List<Double>) {
        var index = 0
        while (true) {
            _glucoseState.value = values[index]
            index = (index + 1) % values.size
            delay(1000L)
        }
    }

    // assets 없을 때 fallback용 랜덤 생성
    private data class Profile(val initial: Double, val min: Double, val max: Double, val swing: Double)

    private val PROFILES =
        mapOf(
            "NONE" to Profile(95.0, 65.0, 140.0, 2.0),
            "TYPE1" to Profile(120.0, 50.0, 300.0, 12.0),
            "TYPE2" to Profile(150.0, 100.0, 250.0, 4.0),
        )

    private suspend fun randomLoop(diabetesType: String) {
        val profile = PROFILES[diabetesType] ?: PROFILES.getValue("NONE")
        var current = profile.initial
        while (true) {
            current =
                (current + kotlin.random.Random.nextDouble(-profile.swing, profile.swing))
                    .coerceIn(profile.min, profile.max)
            _glucoseState.value = current
            delay(1000L)
        }
    }
}
