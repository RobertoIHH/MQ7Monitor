package com.example.sensorgas

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.nio.charset.StandardCharsets
import java.util.UUID

class BLEManager(private val context: Context, callbacks: BLECallbacks) {
    companion object {
        private const val TAG = "BLEManager"

        // UUID del servicio y característica de la ESP32
        private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        private val CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        private val DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val SCAN_PERIOD = 30000L // 30 segundos
    }

    private var callbacks: BLECallbacks = callbacks
    private var scanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var dataCharacteristic: BluetoothGattCharacteristic? = null
    private val scanHandler = Handler(Looper.getMainLooper())
    private var isScanning = false

    // Mantener un mapa de dispositivos
    private val deviceMap = mutableMapOf<String, BluetoothDevice>()

    interface BLECallbacks {
        fun onDeviceFound(device: BluetoothDevice, rssi: Int)
        fun onDataReceived(data: String)
        fun onConnectionStateChange(connected: Boolean)
    }

    fun setCallbacks(callbacks: BLECallbacks) {
        this.callbacks = callbacks
    }

    fun scanForDevices() {
        val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        scanner = bluetoothAdapter?.bluetoothLeScanner

        if (scanner == null) {
            Log.e(TAG, "BluetoothLE scanner no disponible")
            return
        }

        if (isScanning) {
            stopScan()
        }

        try {
            // Detener el escaneo después de un período definido
            scanHandler.postDelayed({ stopScan() }, SCAN_PERIOD)

            isScanning = true
            scanner?.startScan(scanCallback)
            Log.d(TAG, "Escaneo BLE iniciado")
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de seguridad al escanear: ${e.message}")
        }
    }

    fun stopScan() {
        if (isScanning && scanner != null) {
            try {
                isScanning = false
                scanner?.stopScan(scanCallback)
                Log.d(TAG, "Escaneo BLE detenido")
            } catch (e: SecurityException) {
                Log.e(TAG, "Error de seguridad al detener escaneo: ${e.message}")
            }
        }
    }

    fun getDeviceByAddress(address: String): BluetoothDevice? {
        return deviceMap[address]
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            result.device?.let { device ->
                deviceMap[device.address] = device
                callbacks.onDeviceFound(device, result.rssi)
            }
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            Log.d(TAG, "Intentando conectar a ${device.name ?: device.address}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de seguridad al conectar: ${e.message}")
        }
    }

    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
            Log.d(TAG, "Desconectado")
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de seguridad al desconectar: ${e.message}")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Conectado al dispositivo GATT")
                try {
                    gatt.discoverServices()
                } catch (e: SecurityException) {
                    Log.e(TAG, "Error al descubrir servicios: ${e.message}")
                }
                callbacks.onConnectionStateChange(true)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Desconectado del dispositivo GATT")
                callbacks.onConnectionStateChange(false)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    dataCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID)

                    if (dataCharacteristic != null) {
                        try {
                            // Habilitar notificaciones
                            gatt.setCharacteristicNotification(dataCharacteristic, true)

                            // Escribir el descriptor para habilitar notificaciones
                            val descriptor = dataCharacteristic?.getDescriptor(DESCRIPTOR_UUID)
                            if (descriptor != null) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                                } else {
                                    @Suppress("DEPRECATION")
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    @Suppress("DEPRECATION")
                                    gatt.writeDescriptor(descriptor)
                                }
                                Log.d(TAG, "Notificaciones habilitadas")
                            }
                        } catch (e: SecurityException) {
                            Log.e(TAG, "Error al configurar notificaciones: ${e.message}")
                        }
                    } else {
                        Log.e(TAG, "No se encontró la característica")
                    }
                } else {
                    Log.e(TAG, "No se encontró el servicio")
                }
            } else {
                Log.e(TAG, "Error al descubrir servicios: $status")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            handleCharacteristicChanged(characteristic)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Obtenemos los datos directamente del valor proporcionado
                val data = String(value, StandardCharsets.UTF_8)
                Log.d(TAG, "Datos recibidos: $data")
                callbacks.onDataReceived(data)
            }
        }
    }

    private fun handleCharacteristicChanged(characteristic: BluetoothGattCharacteristic) {
        if (CHARACTERISTIC_UUID == characteristic.uuid) {
            try {
                @Suppress("DEPRECATION")
                val data = characteristic.value
                if (data != null && data.isNotEmpty()) {
                    val dataString = String(data, StandardCharsets.UTF_8)
                    Log.d(TAG, "Datos recibidos: $dataString")
                    callbacks.onDataReceived(dataString)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al procesar datos: ${e.message}")
            }
        }
    }
}