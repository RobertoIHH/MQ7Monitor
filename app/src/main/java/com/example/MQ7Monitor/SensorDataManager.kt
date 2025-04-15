package com.example.MQ7Monitor

import android.util.Log

class SensorDataManager {
    var rawValue: Int = 0
        private set

    var voltage: Double = 0.0
        private set

    var estimatedPpm: Int = 0
        private set

    fun updateData(rawValue: Int, voltage: Double, ppm: Int) {
        this.rawValue = rawValue
        this.voltage = voltage
        this.estimatedPpm = ppm

        Log.d("SensorDataManager", "Datos actualizados: ADC=$rawValue, V=$voltage, ppm=$ppm")
    }
}