package com.example.MQ7Monitor

import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
                val deviceName = device.name ?: "Dispositivo desconocido"
                val deviceAddress = device.address

                // Evitar duplicados
                if (_deviceList.none { it.address == deviceAddress }) {
                    _deviceList.add(DeviceInfo(deviceName, deviceAddress, rssi))
                }
            }

            override fun onDataReceived(data: String) {
                try {
                    val jsonData = JSONObject(data)
                    val rawValue = jsonData.getInt("gas")
                    val voltage = jsonData.getDouble("voltage")

                    dataManager.updateData(rawValue, voltage)

                    _rawValue.value = dataManager.rawValue
                    _voltage.value = dataManager.voltage
                    _ppmValue.value = dataManager.estimatedPpm

                    // Actualizar datos del gráfico
                    if (_chartData.size >= 60) {
                        _chartData.removeAt(0)
                    }
                    _chartData.add(DataPoint(_chartData.size.toFloat(), rawValue.toFloat()))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onConnectionStateChange(connected: Boolean) {
                _isConnected.value = connected
                _connectionStatus.value = if (connected) "Conectado" else "Desconectado"
            }
        })
    }

    fun startScan() {
        _deviceList.clear()
        _isScanning.value = true
        bleManager?.scanForDevices()

        // Simular detención de escaneo después de 30 segundos
        viewModelScope.launch {
            kotlinx.coroutines.delay(30000)
            _isScanning.value = false
        }
    }

    fun stopScan() {
        bleManager?.stopScan()
        _isScanning.value = false
    }

    fun selectDevice(device: DeviceInfo) {
        val btDevice = bleManager?.getDeviceByAddress(device.address)
        if (btDevice != null) {
            _selectedDevice.value = btDevice
        }
    }

    fun connectToDevice() {
        _selectedDevice.value?.let { device ->
            _connectionStatus.value = "Conectando..."
            bleManager?.connectToDevice(device)
        }
    }

    fun disconnectDevice() {
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