package com.example.MQ7Monitor

import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

class GasSensorViewModel : ViewModel() {
    // Estados para la UI
    private val _connectionStatus = mutableStateOf("Desconectado")
    val connectionStatus: State<String> = _connectionStatus

    private val _isConnected = mutableStateOf(false)
    val isConnected: State<Boolean> = _isConnected

    private val _isScanning = mutableStateOf(false)
    val isScanning: State<Boolean> = _isScanning

    // Lista de dispositivos
    val deviceList = mutableStateListOf<DeviceInfo>()

    private val _selectedDevice = mutableStateOf<BluetoothDevice?>(null)
    val selectedDevice: State<BluetoothDevice?> = _selectedDevice

    // Datos del sensor
    private val _rawValue = mutableStateOf(0)
    val rawValue: State<Int> = _rawValue

    private val _voltage = mutableStateOf(0.0)
    val voltage: State<Double> = _voltage

    private val _ppmValue = mutableStateOf(0.0)
    val ppmValue: State<Double> = _ppmValue

    // Tipo de gas actual
    private val _currentGasType = mutableStateOf(GasType.CO)
    val currentGasType: State<GasType> = _currentGasType

    // Estado para mostrar el proceso de cambio de gas
    private val _isChangingGas = mutableStateOf(false)
    val isChangingGas: State<Boolean> = _isChangingGas

    // Mensaje de estado para mostrar información al usuario
    private val _statusMessage = mutableStateOf("")
    val statusMessage: State<String> = _statusMessage

    // Datos para los gráficos por tipo de gas
    val gasChartData = mutableMapOf(
        GasType.CO to mutableStateListOf<DataPoint>(),
        GasType.H2 to mutableStateListOf<DataPoint>(),
        GasType.LPG to mutableStateListOf<DataPoint>(),
        GasType.CH4 to mutableStateListOf<DataPoint>(),
        GasType.ALCOHOL to mutableStateListOf<DataPoint>()
    )

    // Datos para el gráfico de ADC (común para todos los gases)
    val adcChartData = mutableStateListOf<DataPoint>()

    // Último timestamp para cada gas
    private val lastGasTimestamps = mutableMapOf<GasType, Long>()
    private var lastUpdateTime = 0L

    // Gas objetivo que hemos solicitado
    private var targetGasType: GasType? = null
    private var changeGasRequestTime: Long = 0
    private var lastChangeGasTimestamp: String = ""

    private var bleManager: BLEManager? = null
    private val dataManager = SensorDataManager()

    // Conjunto para asegurar que no hay duplicados
    private val addedDeviceAddresses = mutableSetOf<String>()

    // Mínimos y máximos para cada gas
    private val minPPMValues = mutableMapOf<GasType, Double>()
    private val maxPPMValues = mutableMapOf<GasType, Double>()

    // Valores mínimos y máximos para ADC
    private val _minADCValue = mutableStateOf(4095)
    val minADCValue: State<Int> = _minADCValue

    private val _maxADCValue = mutableStateOf(0)
    val maxADCValue: State<Int> = _maxADCValue

    init {
        // Inicializar valores mínimos y máximos para cada gas
        GasType.values().forEach { gasType ->
            if (gasType != GasType.UNKNOWN) {
                minPPMValues[gasType] = Double.MAX_VALUE
                maxPPMValues[gasType] = 0.0
                lastGasTimestamps[gasType] = 0L

                // Inicializar gráficos con puntos vacíos
                if (gasChartData[gasType] == null) {
                    gasChartData[gasType] = mutableStateListOf()
                }

                // Añadir algunos puntos iniciales (esto hace que los gráficos se muestren incluso sin datos)
                val initialPoints = gasChartData[gasType] ?: mutableStateListOf()
                if (initialPoints.isEmpty()) {
                    for (i in 0 until 5) {
                        initialPoints.add(DataPoint(i.toFloat(), 0.0f))
                    }
                }
            }
        }

        // Inicializar gráfico ADC con datos iniciales
        if (adcChartData.isEmpty()) {
            for (i in 0 until 5) {
                adcChartData.add(DataPoint(i.toFloat(), 0.0f))
            }
        }
    }

    fun setBleManager(manager: BLEManager) {
        bleManager = manager

        // Configurar callbacks
        bleManager?.setCallbacks(object : BLEManager.BLECallbacks {
            override fun onDeviceFound(device: BluetoothDevice, rssi: Int) {
                try {
                    val deviceName = device.name ?: "Dispositivo desconocido"
                    val deviceAddress = device.address

                    Log.d("GasSensorViewModel", "Dispositivo encontrado en ViewModel: $deviceName, $deviceAddress, RSSI: $rssi")

                    // Verificar si ya existe el dispositivo para evitar duplicados
                    if (!addedDeviceAddresses.contains(deviceAddress)) {
                        Log.d("GasSensorViewModel", "Añadiendo dispositivo a la lista: $deviceName")

                        // Añadir al conjunto para control de duplicados
                        addedDeviceAddresses.add(deviceAddress)

                        // Importante: Actualizar la UI en el hilo principal
                        viewModelScope.launch(Dispatchers.Main) {
                            deviceList.add(DeviceInfo(deviceName, deviceAddress, rssi))
                            Log.d("GasSensorViewModel", "Lista actual en ViewModel: ${deviceList.size} dispositivos")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GasSensorViewModel", "Error al procesar dispositivo: ${e.message}")
                    e.printStackTrace()
                }
            }

            override fun onDataReceived(data: String) {
                try {
                    Log.d("GasSensorViewModel", "Datos JSON recibidos: $data")
                    lastUpdateTime = System.currentTimeMillis()

                    // Validar que el JSON esté completo
                    if (!data.startsWith("{") || !data.endsWith("}")) {
                        Log.e("GasSensorViewModel", "JSON incompleto: $data")
                        return
                    }

                    val jsonData = JSONObject(data)

                    // Verificar si es una respuesta de confirmación de cambio de gas
                    if (jsonData.has("command") && jsonData.getString("command") == "gas_changed") {
                        val toGas = jsonData.getString("to")
                        val success = jsonData.getBoolean("success")
                        val timestamp = if (jsonData.has("timestamp")) jsonData.getLong("timestamp").toString() else ""
                        val requestedGas = if (jsonData.has("requested")) jsonData.getString("requested") else ""

                        Log.d("GasSensorViewModel", "Recibida confirmación de cambio de gas a $toGas (solicitado: $requestedGas), éxito: $success")

                        if (success) {
                            val gasType = GasType.fromString(toGas)

                            // Actualizar en el hilo principal
                            viewModelScope.launch(Dispatchers.Main) {
                                _currentGasType.value = gasType
                                targetGasType = null
                                _isChangingGas.value = false
                                _statusMessage.value = "Cambiado a gas ${gasType.name}"

                                // Forzar actualización de la UI
                                _ppmValue.value = _ppmValue.value
                            }
                        } else {
                            // Error al cambiar el gas
                            viewModelScope.launch(Dispatchers.Main) {
                                _statusMessage.value = "Error al cambiar a gas $requestedGas"
                                _isChangingGas.value = false
                                targetGasType = null
                            }
                        }
                        return

                        // Procesar el mensaje de estado
                    } else if (jsonData.has("status") && jsonData.getString("status") == "ok" && jsonData.has("current_gas")) {
                        val currentGas = jsonData.getString("current_gas")
                        val gasIndex = if (jsonData.has("gas_index")) jsonData.getInt("gas_index") else -1

                        Log.d("GasSensorViewModel", "Recibido estado: gas actual=$currentGas, índice=$gasIndex")

                        // Actualizar el tipo de gas actual si no estamos en proceso de cambio
                        val gasType = GasType.fromString(currentGas)

                        viewModelScope.launch(Dispatchers.Main) {
                            if (!_isChangingGas.value) {
                                _currentGasType.value = gasType
                                _statusMessage.value = "Gas detectado: ${gasType.name}"
                            }
                        }

                        return
                    }

                    // Procesar datos normales del sensor
                    if (jsonData.has("ADC") && jsonData.has("V") && jsonData.has("ppm")) {
                        val rawValue = jsonData.getInt("ADC")
                        val voltage = jsonData.getDouble("V")
                        val ppm = jsonData.getDouble("ppm")

                        // Obtener el tipo de gas
                        val gasString = if (jsonData.has("gas")) jsonData.getString("gas") else "CO"
                        val gasType = GasType.fromString(gasString)

                        // Verificar si recibimos datos del gas objetivo
                        if (targetGasType != null && gasType == targetGasType) {
                            Log.d("GasSensorViewModel", "¡Recibidos datos del gas objetivo ${gasType.name}!")

                            viewModelScope.launch(Dispatchers.Main) {
                                _currentGasType.value = gasType
                                targetGasType = null
                                _isChangingGas.value = false
                            }
                        }

                        Log.d("GasSensorViewModel", "Datos procesados: ADC=$rawValue, V=$voltage, ppm=$ppm, gas=$gasType")

                        dataManager.updateData(rawValue, voltage, ppm, gasType)

                        // Registrar timestamp para este gas
                        lastGasTimestamps[gasType] = System.currentTimeMillis()

                        // Actualizar en el hilo principal
                        viewModelScope.launch(Dispatchers.Main) {
                            _rawValue.value = rawValue
                            _voltage.value = voltage
                            _ppmValue.value = ppm

                            // IMPORTANTE: Solo actualizar el gas actual si no estamos en proceso de cambio
                            if (!_isChangingGas.value) {
                                _currentGasType.value = gasType
                            }

                            // Actualizar los valores mínimos y máximos de ADC
                            _minADCValue.value = minOf(_minADCValue.value, rawValue)
                            _maxADCValue.value = maxOf(_maxADCValue.value, rawValue)

                            // Actualizar min/max para el gas actual
                            if (minPPMValues.containsKey(gasType)) {
                                minPPMValues[gasType] = minOf(minPPMValues[gasType] ?: Double.MAX_VALUE, ppm)
                                maxPPMValues[gasType] = maxOf(maxPPMValues[gasType] ?: 0.0, ppm)
                            }

                            // Actualizar datos del gráfico para el gas específico
                            val gasDataPoints = gasChartData[gasType] ?: run {
                                val newList = mutableStateListOf<DataPoint>()
                                gasChartData[gasType] = newList
                                newList
                            }

                            if (gasDataPoints.size >= 60) {
                                gasDataPoints.removeAt(0)
                            }
                            gasDataPoints.add(DataPoint(gasDataPoints.size.toFloat(), ppm.toFloat()))

                            // Actualizar datos del gráfico para ADC (común)
                            if (adcChartData.size >= 60) {
                                adcChartData.removeAt(0)
                            }
                            adcChartData.add(DataPoint(adcChartData.size.toFloat(), rawValue.toFloat()))
                        }
                    } else {
                        Log.e("GasSensorViewModel", "JSON no contiene los campos esperados: $data")
                    }
                } catch (e: JSONException) {
                    Log.e("GasSensorViewModel", "Error al procesar JSON: ${e.message}\nJSON: $data")
                } catch (e: Exception) {
                    Log.e("GasSensorViewModel", "Error al procesar los datos: ${e.message}")
                }
            }

            override fun onConnectionStateChange(connected: Boolean) {
                viewModelScope.launch(Dispatchers.Main) {
                    Log.d("GasSensorViewModel", "Estado de conexión cambiado a: $connected")
                    _isConnected.value = connected
                    _connectionStatus.value = if (connected) "Conectado" else "Desconectado"

                    // Actualizar mensaje de estado
                    if (connected) {
                        _statusMessage.value = "Conectado. Solicitando datos del sensor..."
                        delay(1000)
                        // Leer el estado actual después de conectar
                        bleManager?.readSensorStatus()
                    } else {
                        _statusMessage.value = "Desconectado"
                    }

                    // Resetear el estado de cambio de gas si nos desconectamos
                    if (!connected) {
                        _isChangingGas.value = false
                        targetGasType = null
                    }
                }
            }

            override fun onStatusUpdate(statusJson: String) {
                try {
                    Log.d("GasSensorViewModel", "Actualización de estado recibida: $statusJson")

                    // Validar que el JSON esté completo
                    if (!statusJson.startsWith("{") || !statusJson.endsWith("}")) {
                        Log.e("GasSensorViewModel", "JSON de estado incompleto: $statusJson")
                        return
                    }

                    val jsonData = JSONObject(statusJson)

                    if (jsonData.has("status") && jsonData.getString("status") == "ok") {
                        if (jsonData.has("current_gas")) {
                            val currentGas = jsonData.getString("current_gas")
                            val gasType = GasType.fromString(currentGas)

                            viewModelScope.launch(Dispatchers.Main) {
                                // Solo actualizar si no estamos cambiando de gas activamente
                                if (!_isChangingGas.value) {
                                    _currentGasType.value = gasType
                                    _statusMessage.value = "Estado: midiendo ${gasType.name}"
                                }
                            }
                        }
                    }
                } catch (e: JSONException) {
                    Log.e("GasSensorViewModel", "Error al procesar JSON de estado: ${e.message}")
                }
            }
        })
    }

    fun getMinMaxPpmForGas(gasType: GasType): Pair<Double, Double> {
        val min = minPPMValues[gasType] ?: 0.0
        val max = maxPPMValues[gasType] ?: 100.0
        return if (min != Double.MAX_VALUE && max > min) {
            Pair(min, max)
        } else {
            Pair(0.0, 100.0) // Valores por defecto
        }
    }

    // Función para solicitar cambio de gas con mejoras
    fun changeGasType(gasType: GasType) {
        if (!_isConnected.value) {
            Log.e("GasSensorViewModel", "No se puede cambiar el gas: dispositivo no conectado")
            _statusMessage.value = "Error: No conectado"
            return
        }

        if (_isChangingGas.value) {
            Log.e("GasSensorViewModel", "Ya se está cambiando el gas, espera un momento")
            _statusMessage.value = "Espera, ya se está cambiando el gas..."
            return
        }

        if (gasType == _currentGasType.value) {
            Log.d("GasSensorViewModel", "Ya estamos midiendo este gas: ${gasType.name}")
            _statusMessage.value = "Ya estamos midiendo ${gasType.name}"
            return
        }

        Log.d("GasSensorViewModel", "Solicitando cambio a gas: ${gasType.name}")
        _statusMessage.value = "Solicitando cambio a ${gasType.name}..."

        // Marcar que estamos en proceso de cambio
        _isChangingGas.value = true
        targetGasType = gasType
        changeGasRequestTime = System.currentTimeMillis()
        lastChangeGasTimestamp = System.currentTimeMillis().toString()

        // Enviar comando al dispositivo ESP32
        bleManager?.sendGasTypeCommand(gasType)

        // Programar un timeout para resetear el estado de cambio después de 5 segundos
        // en caso de que no recibamos confirmación
        viewModelScope.launch {
            delay(5000)
            if (_isChangingGas.value && targetGasType == gasType) {
                // Si todavía estamos esperando cambiar a este gas específico después de 5 segundos
                targetGasType = null
                _isChangingGas.value = false

                // MEJORA: Asumimos que el cambio se produjo pero no recibimos la confirmación
                // Actualizamos el gas actual de todas formas para mejorar la experiencia de usuario
                _currentGasType.value = gasType
                _statusMessage.value = "Cambiado a ${gasType.name} (sin confirmación)"

                Log.w("GasSensorViewModel", "Timeout al cambiar el gas")

                // Volver a leer el estado para verificar
                bleManager?.readSensorStatus()

                // Actualizar manualmente los datos si no hemos recibido datos en los últimos 5 segundos
                if (System.currentTimeMillis() - lastUpdateTime > 5000) {
                    Log.d("GasSensorViewModel", "Actualizando datos manualmente después del timeout")

                    // Solo actualizar los datos si hay datos antiguos para este gas
                    val gasDataPoints = gasChartData[gasType]
                    if (gasDataPoints != null && gasDataPoints.isNotEmpty()) {
                        val lastPoint = gasDataPoints.last()

                        // Añadir un nuevo punto similar para mantener la continuidad
                        if (gasDataPoints.size >= 60) {
                            gasDataPoints.removeAt(0)
                        }
                        gasDataPoints.add(DataPoint(gasDataPoints.size.toFloat(), lastPoint.y))

                        // Actualizar los valores mostrados
                        _ppmValue.value = lastPoint.y.toDouble()
                    }
                }
            }
        }
    }

    fun startScan() {
        Log.d("GasSensorViewModel", "Iniciando escaneo...")

        // Ejecutar en el hilo principal
        viewModelScope.launch(Dispatchers.Main) {
            // Limpiar lista y conjunto antes de comenzar nuevo escaneo
            deviceList.clear()
            addedDeviceAddresses.clear()

            // Actualizar estado de escaneo
            _isScanning.value = true
            _statusMessage.value = "Buscando dispositivos..."

            // Log después de limpiar
            Log.d("GasSensorViewModel", "Lista limpiada, ahora tiene ${deviceList.size} dispositivos")
        }

        // Asegurarse de que el bleManager no sea nulo
        if (bleManager == null) {
            Log.e("GasSensorViewModel", "Error: bleManager es nulo")
            _isScanning.value = false
            _statusMessage.value = "Error: no se pudo iniciar el escaneo"
            return
        }

        // Iniciar escaneo BLE
        bleManager?.scanForDevices()
        Log.d("GasSensorViewModel", "Escaneo iniciado, lista limpiada")

        // Simular detención de escaneo después de 30 segundos
        viewModelScope.launch {
            delay(30000)
            if (_isScanning.value) {
                stopScan()
                Log.d("GasSensorViewModel", "Escaneo detenido después de 30 segundos")
            }
        }
    }

    fun stopScan() {
        viewModelScope.launch(Dispatchers.Main) {
            bleManager?.stopScan()
            _isScanning.value = false

            if (deviceList.isEmpty()) {
                _statusMessage.value = "No se encontraron dispositivos"
            } else {
                _statusMessage.value = "Selecciona un dispositivo para conectar"
            }

            Log.d("GasSensorViewModel", "Escaneo detenido. Lista tiene ${deviceList.size} dispositivos.")
        }
    }

    fun selectDevice(device: DeviceInfo) {
        val btDevice = bleManager?.getDeviceByAddress(device.address)
        if (btDevice != null) {
            _selectedDevice.value = btDevice
            _statusMessage.value = "Dispositivo seleccionado: ${device.name}"
            Log.d("GasSensorViewModel", "Dispositivo seleccionado: ${device.name}")
        } else {
            _statusMessage.value = "Error al seleccionar dispositivo"
            Log.e("GasSensorViewModel", "No se pudo encontrar el dispositivo BLE para la dirección: ${device.address}")
        }
    }

    fun connectToDevice() {
        _selectedDevice.value?.let { device ->
            _connectionStatus.value = "Conectando..."
            _statusMessage.value = "Conectando a ${device.name ?: device.address}..."
            Log.d("GasSensorViewModel", "Intentando conectar a: ${device.address}")
            bleManager?.connectToDevice(device)
        }
    }

    fun disconnectDevice() {
        Log.d("GasSensorViewModel", "Desconectando...")
        _statusMessage.value = "Desconectando..."
        bleManager?.disconnect()
    }

    // Determinar si un gas tiene datos
    fun hasGasData(gasType: GasType): Boolean {
        val dataPoints = gasChartData[gasType]
        return dataPoints != null && dataPoints.isNotEmpty() && dataPoints.any { it.y > 0 }
    }

    override fun onCleared() {
        super.onCleared()
        bleManager?.disconnect()
        bleManager?.stopScan()
    }
}

data class DeviceInfo(
    val name: String,
    val address: String,
    val rssi: Int
)

data class DataPoint(
    val x: Float,
    val y: Float
)