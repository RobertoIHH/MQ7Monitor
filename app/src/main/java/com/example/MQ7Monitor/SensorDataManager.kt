package com.example.MQ7Monitor

import android.util.Log

class SensorDataManager {
    var rawValue: Int = 0
        private set

    var voltage: Double = 0.0
        private set

    var estimatedPpm: Double = 0.0
        private set

    var gasType: String = "CO"
        private set

    fun updateData(rawValue: Int, voltage: Double, ppm: Double, gasType: String = "CO") {
        this.rawValue = rawValue
        this.voltage = voltage
        this.estimatedPpm = ppm
        this.gasType = gasType

        Log.d("SensorDataManager", "Datos actualizados: ADC=$rawValue, V=$voltage, ppm=$ppm, gas=$gasType")
    }
}