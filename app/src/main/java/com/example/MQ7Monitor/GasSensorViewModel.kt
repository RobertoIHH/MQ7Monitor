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
import org.json.JSONObject

class GasSensorViewModel : ViewModel() {
    // Estados para la UI
    private val _connectionStatus = mutableStateOf("Desconectado")
    val connectionStatus: State<String> = _connectionStatus

    private val _isConnected = mutableStateOf(false)
    val isConnected: State<Boolean> = _isConnected

    private val _isScanning = mutableStateOf(false)
    val isScanning: State<Boolean> = _isScanning

    private val _deviceList = mutableStateListOf<DeviceInfo>()
    val deviceList: List<DeviceInfo> = _deviceList

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
    private val _chartData = mutableStateListOf<DataPoint>()
    val chartData: List<DataPoint> = _chartData

    private var bleManager: BLEManager? = null
    private val dataManager = SensorDataManager()

    fun setBleManager(manager: BLEManager) {
        bleManager = manager

        // Configurar callbacks
        bleManager?.setCallbacks(object : BLEManager.BLECallbacks {
            override fun onDeviceFound(device: BluetoothDevice, rssi: Int) {
                try {
                    val deviceName = device.name ?: "Dispositivo desconocido"
                    val deviceAddress = device.address

                    Log.d("GasSensorViewModel", "Dispositivo encontrado en ViewModel: $deviceName, $deviceAddress, RSSI: $rssi")

                    // Importante: Actualizar la UI en el hilo principal
                    viewModelScope.launch(Dispatchers.Main) {
                        // Verificar si ya existe el dispositivo
                        val exists = _deviceList.any { it.address == deviceAddress }
                        if (!exists) {
                            Log.d("GasSensorViewModel", "Añadiendo dispositivo a la lista: $deviceName")
                            _deviceList.add(DeviceInfo(deviceName, deviceAddress, rssi))
                            Log.d("GasSensorViewModel", "Lista actual: ${_deviceList.size} dispositivos")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GasSensorViewModel", "Error al procesar dispositivo: ${e.message}")
                    e.printStackTrace()
                }
            }

            override fun onDataReceived(data: String) {
                try {
                    val jsonData = JSONObject(data)
                    val rawValue = jsonData.getInt("gas")
                    val voltage = jsonData.getDouble("voltage")

                    dataManager.updateData(rawValue, voltage)

                    // Actualizar en el hilo principal
                    viewModelScope.launch {
                        _rawValue.value = dataManager.rawValue
                        _voltage.value = dataManager.voltage
                        _ppmValue.value = dataManager.estimatedPpm

                        // Actualizar datos del gráfico
                        if (_chartData.size >= 60) {
                            _chartData.removeAt(0)
                        }
                        _chartData.add(DataPoint(_chartData.size.toFloat(), rawValue.toFloat()))
                    }
                } catch (e: Exception) {
                    Log.e("GasSensorViewModel", "Error al procesar los datos: ${e.message}")
                }
            }

            override fun onConnectionStateChange(connected: Boolean) {
                viewModelScope.launch {
                    _isConnected.value = connected
                    _connectionStatus.value = if (connected) "Conectado" else "Desconectado"
                }
            }
        })
    }

    fun startScan() {
        Log.d("GasSensorViewModel", "Iniciando escaneo...")
        _deviceList.clear()
        _isScanning.value = true

        // Asegurarse de que el bleManager no sea nulo
        if (bleManager == null) {
            Log.e("GasSensorViewModel", "Error: bleManager es nulo")
            _isScanning.value = false
            return
        }

        bleManager?.scanForDevices()
        Log.d("GasSensorViewModel", "Escaneo iniciado, lista limpiada")

        // Simular detención de escaneo después de 30 segundos
        viewModelScope.launch {
            delay(30000)
            if (_isScanning.value) {
                _isScanning.value = false
                Log.d("GasSensorViewModel", "Escaneo detenido después de 30 segundos")
            }
        }
    }

    fun stopScan() {
        bleManager?.stopScan()
        _isScanning.value = false
        Log.d("GasSensorViewModel", "Escaneo detenido manualmente")
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