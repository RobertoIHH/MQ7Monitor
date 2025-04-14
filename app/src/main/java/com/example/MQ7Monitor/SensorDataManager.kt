package com.example.MQ7Monitor

class SensorDataManager {
    var rawValue: Int = 0
        private set

    var voltage: Double = 0.0
        private set

    var estimatedPpm: Int = 0
        private set

    private val PPM_CONVERSION_FACTOR = 100.0

    fun updateData(rawValue: Int, voltage: Double) {
        this.rawValue = rawValue
        this.voltage = voltage
        this.estimatedPpm = calculatePpm(rawValue, voltage)
    }

    private fun calculatePpm(rawValue: Int, voltage: Double): Int {
        // Cálculo aproximado - debe ser calibrado para cada sensor específico
        return (rawValue * PPM_CONVERSION_FACTOR / 4095.0).toInt()
    }
}