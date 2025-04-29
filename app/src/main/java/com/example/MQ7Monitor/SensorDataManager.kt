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

    // Calibraciones para cada gas (corresponden a las del firmware ESP32)
    private val gasCalibrations = mapOf(
        GasType.CO to GasCalibration("CO", 50.0, 1.66, 4000.0, 0.052),
        GasType.H2 to GasCalibration("H2", 50.0, 1.36, 4000.0, 0.09),
        GasType.LPG to GasCalibration("LPG", 50.0, 9.0, 4000.0, 5.0),
        GasType.CH4 to GasCalibration("CH4", 50.0, 15.0, 4000.0, 9.0),
        GasType.ALCOHOL to GasCalibration("Alcohol", 50.0, 17.0, 4000.0, 13.0)
    )

    fun updateData(rawValue: Int, voltage: Double, ppm: Double, gasType: GasType = GasType.CO) {
        this.rawValue = rawValue
        this.voltage = voltage
        this.estimatedPpm = ppm
        this.gasType = gasType

        Log.d("SensorDataManager", "Datos actualizados: ADC=$rawValue, V=$voltage, ppm=$ppm, gas=$gasType")
    }

    // Recalcular PPM basado en la relación Rs/R0 para cualquier gas (para verificación local)
    fun calculatePpm(rsRoRatio: Double, gasType: GasType): Double {
        val calibration = gasCalibrations[gasType] ?: return 0.0

        // Calcular en escala logarítmica como lo hace el firmware
        val logX0 = Math.log10(calibration.x0)
        val logY0 = Math.log10(calibration.y0)
        val logX1 = Math.log10(calibration.x1)
        val logY1 = Math.log10(calibration.y1)

        // Calcular pendiente y coordenada
        val scope = (logY1 - logY0) / (logX1 - logX0)
        val coord = logY0 - logX0 * scope

        // Aplicar la fórmula de conversión
        return Math.pow(10.0, coord + scope * Math.log10(rsRoRatio))
    }
}

// Clase que representa la calibración para un tipo de gas
data class GasCalibration(
    val name: String,   // Nombre del gas
    val x0: Double,     // Punto X0 (ppm)
    val y0: Double,     // Punto Y0 (Rs/R0)
    val x1: Double,     // Punto X1 (ppm)
    val y1: Double      // Punto Y1 (Rs/R0)
)