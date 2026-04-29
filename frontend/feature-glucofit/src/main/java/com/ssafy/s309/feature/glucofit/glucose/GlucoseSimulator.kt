package com.ssafy.s309.feature.glucofit.glucose

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

object GlucoseSimulator {
    private var current = 95.0
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null

    private val _glucoseState = MutableStateFlow<Double?>(null)
    val glucoseState: StateFlow<Double?> = _glucoseState.asStateFlow()

    val isRunning: Boolean get() = job?.isActive == true

    fun start() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                while (true) {
                    current = (current + Random.nextDouble(-2.0, 2.0)).coerceIn(60.0, 300.0)
                    _glucoseState.value = current
                    delay(1000L)
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
        _glucoseState.value = null
        current = 95.0
    }
}
