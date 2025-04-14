package com.example.MQ7Monitor

import android.util.Log

class SensorDataManager {
    var rawValue: Int = 0
        private set

    var voltage: Double = 0.0
        private set

    var estimatedPpm: Int = 0
        private set

    private val PPM_CONVERSION_FACTOR = 10.0

    fun updateData(rawValue: Int, voltage: Double) {
        this.rawValue = rawValue
        this.voltage = voltage
        this.estimatedPpm = calculatePpm(rawValue, voltage)

        Log.d("SensorDataManager", "Datos actualizados: raw=$rawValue, voltage=$voltage, ppm=$estimatedPpm")
    }

    private fun calculatePpm(rawValue: Int, voltage: Double): Int {
        // Para el sensor MQ7, la relación entre voltaje y concentración de CO
        // no es lineal. Esta es una aproximación simple.
        //
        // Un cálculo más preciso requeriría conocer:
        // - La resistencia de carga (RL) del circuito
        // - El valor R0 (resistencia del sensor en aire limpio)
        // - La curva de sensibilidad específica del sensor

        // Asumimos que el valor ADC está en el rango 0-4095 (12 bits)
        // y el voltaje de referencia es 3.3V

        // Calculamos un valor de ppm proporcional al valor ADC
        // Esto es una aproximación muy simple
        val ppm = (rawValue * PPM_CONVERSION_FACTOR / 4095.0).toInt()

        return ppm.coerceIn(0, 1000) // Limitar a un rango razonable para CO
    }
}