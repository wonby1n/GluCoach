package com.ssafy.s309.ui.screen.ble

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.data.ble.BleConnectionState
import com.ssafy.s309.data.ble.ScannedDevice
import com.ssafy.s309.data.model.GlucoseReading
import com.ssafy.s309.data.repository.HealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BleViewModel
    @Inject
    constructor(
        private val healthRepository: HealthRepository,
    ) : ViewModel() {
        val connectionState: StateFlow<BleConnectionState> =
            healthRepository.bleConnectionState
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BleConnectionState.Idle)

        val scannedDevices: StateFlow<List<ScannedDevice>> =
            healthRepository.scannedDevices
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        private val _glucoseReadings = MutableStateFlow<List<GlucoseReading>>(emptyList())
        val glucoseReadings: StateFlow<List<GlucoseReading>> = _glucoseReadings

        init {
            viewModelScope.launch {
                healthRepository.glucoseStream.collect { reading ->
                    _glucoseReadings.update { list -> (list + reading).takeLast(100) }
                }
            }
        }

        fun startScan() = healthRepository.startBleScan()

        fun stopScan() = healthRepository.stopBleScan()

        fun connect(device: ScannedDevice) {
            healthRepository.stopBleScan()
            healthRepository.connectBleDevice(device)
        }

        fun disconnect() = healthRepository.disconnectBleDevice()

        override fun onCleared() {
            super.onCleared()
            healthRepository.stopBleScan()
        }
    }
