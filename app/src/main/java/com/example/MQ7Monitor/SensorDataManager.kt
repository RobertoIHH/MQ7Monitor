package com.example.MQ7Monitor

import android.util.Log

// Enumeración para los tipos de gas soportados
enum class GasType {
    CO, H2, LPG, CH4, ALCOHOL, UNKNOWN;

    companion object {
        fun fromString(gasName: String): GasType {
            return when (gasName.trim().uppercase()) {
                "CO" -> CO
                "H2" -> H2
                "LPG" -> LPG
                "CH4" -> CH4
                "ALCOHOL" -> ALCOHOL
                else -> {
                    Log.w("GasType", "Gas desconocido: $gasName, usando UNKNOWN")
                    UNKNOWN
                }
            }
        }
    }
}

class SensorDataManager {
    var rawValue: Int = 0
        private set

    var voltage: Double = 0.0
        private set

    var estimatedPpm: Double = 0.0
        private set

    var gasType: GasType = GasType.CO
        private set

    fun updateData(rawValue: Int, voltage: Double, ppm: Double, gasType: GasType = GasType.CO) {
        this.rawValue = rawValue
        this.voltage = voltage
        this.estimatedPpm = ppm
        this.gasType = gasType

        Log.d("SensorDataManager", "Datos actualizados: ADC=$rawValue, V=$voltage, ppm=$ppm, gas=$gasType")
    }
}