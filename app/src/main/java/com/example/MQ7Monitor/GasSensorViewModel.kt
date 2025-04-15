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

    private val _ppmValue = mutableStateOf(0)
    val ppmValue: State<Int> = _ppmValue

    // Datos para el gráfico
    val chartData = mutableStateListOf<DataPoint>()

    private var bleManager: BLEManager? = null
    private val dataManager = SensorDataManager()

    // Conjunto para asegurar que no hay duplicados
    private val addedDeviceAddresses = mutableSetOf<String>()

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
                        val ppm = jsonData.getDouble("ppm").toInt()

                        Log.d("GasSensorViewModel", "Datos procesados: ADC=$rawValue, V=$voltage, ppm=$ppm")

                        dataManager.updateData(rawValue, voltage, ppm)

                        // Actualizar en el hilo principal
                        viewModelScope.launch(Dispatchers.Main) {
                            _rawValue.value = rawValue
                            _voltage.value = voltage
                            _ppmValue.value = ppm

                            // Actualizar datos del gráfico
                            if (chartData.size >= 60) {
                                chartData.removeAt(0)
                            }
                            chartData.add(DataPoint(chartData.size.toFloat(), rawValue.toFloat()))
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