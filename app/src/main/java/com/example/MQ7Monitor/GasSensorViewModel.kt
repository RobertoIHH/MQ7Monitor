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

    // Esta es la clave: usar mutableStateListOf y exponerlo directamente
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

    // Último timestamp para cada gas (para mostrar datos históricos)
    private val lastGasTimestamps = mutableMapOf<GasType, Long>()

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

                    // Validar que el JSON esté completo
                    if (!data.startsWith("{") || !data.endsWith("}")) {
                        Log.e("GasSensorViewModel", "JSON incompleto: $data")
                        return
                    }

                    val jsonData = JSONObject(data)

                    if (jsonData.has("ADC") && jsonData.has("V") && jsonData.has("ppm")) {
                        val rawValue = jsonData.getInt("ADC")
                        val voltage = jsonData.getDouble("V")
                        val ppm = jsonData.getDouble("ppm")

                        // Obtener el tipo de gas
                        val gasString = if (jsonData.has("gas")) jsonData.getString("gas") else "CO"
                        val gasType = GasType.fromString(gasString)

                        Log.d("GasSensorViewModel", "Datos procesados: ADC=$rawValue, V=$voltage, ppm=$ppm, gas=$gasType")

                        dataManager.updateData(rawValue, voltage, ppm, gasType)

                        // Registrar timestamp para este gas
                        lastGasTimestamps[gasType] = System.currentTimeMillis()

                        // Actualizar en el hilo principal
                        viewModelScope.launch(Dispatchers.Main) {
                            _rawValue.value = rawValue
                            _voltage.value = voltage
                            _ppmValue.value = ppm
                            _currentGasType.value = gasType

                            // Actualizar los valores mínimos y máximos de ADC
                            _minADCValue.value = minOf(_minADCValue.value, rawValue)
                            _maxADCValue.value = maxOf(_maxADCValue.value, rawValue)

                            // Actualizar min/max para el gas actual
                            minPPMValues[gasType] = minOf(minPPMValues[gasType] ?: Double.MAX_VALUE, ppm)
                            maxPPMValues[gasType] = maxOf(maxPPMValues[gasType] ?: 0.0, ppm)

                            // Actualizar datos del gráfico para el gas específico
                            val gasDataPoints = gasChartData[gasType] ?: return@launch

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

    fun startScan() {
        Log.d("GasSensorViewModel", "Iniciando escaneo...")

        // Ejecutar en el hilo principal
        viewModelScope.launch(Dispatchers.Main) {
            // Limpiar lista y conjunto antes de comenzar nuevo escaneo
            deviceList.clear()
            addedDeviceAddresses.clear()

            // Actualizar estado de escaneo
            _isScanning.value = true

            // Log después de limpiar
            Log.d("GasSensorViewModel", "Lista limpiada, ahora tiene ${deviceList.size} dispositivos")
        }

        // Asegurarse de que el bleManager no sea nulo
        if (bleManager == null) {
            Log.e("GasSensorViewModel", "Error: bleManager es nulo")
            _isScanning.value = false
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
            Log.d("GasSensorViewModel", "Escaneo detenido. Lista tiene ${deviceList.size} dispositivos.")
        }
    }

    fun selectDevice(device: DeviceInfo) {
        val btDevice = bleManager?.getDeviceByAddress(device.address)
        if (btDevice != null) {
            _selectedDevice.value = btDevice
            Log.d("GasSensorViewModel", "Dispositivo seleccionado: ${device.name}")
        } else {
            Log.e("GasSensorViewModel", "No se pudo encontrar el dispositivo BLE para la dirección: ${device.address}")
        }
    }

    fun connectToDevice() {
        _selectedDevice.value?.let { device ->
            _connectionStatus.value = "Conectando..."
            Log.d("GasSensorViewModel", "Intentando conectar a: ${device.address}")
            bleManager?.connectToDevice(device)
        }
    }

    fun disconnectDevice() {
        Log.d("GasSensorViewModel", "Desconectando...")
        bleManager?.disconnect()
    }

    // Determinar si un gas tiene datos recientes (últimos 65 segundos)
    fun hasRecentData(gasType: GasType): Boolean {
        val lastTimestamp = lastGasTimestamps[gasType] ?: 0L
        val currentTime = System.currentTimeMillis()
        // Consideramos 65 segundos (un poco más que el intervalo de 60s) para asegurar que mostramos datos actuales
        return (currentTime - lastTimestamp) < 65000
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