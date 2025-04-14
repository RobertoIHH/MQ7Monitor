package com.example.MQ7Monitor

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
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
    private var bluetoothAdapter: BluetoothAdapter
    private var scanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var dataCharacteristic: BluetoothGattCharacteristic? = null
    private val scanHandler = Handler(Looper.getMainLooper())
    private var isScanning = false

    // Mantener un mapa de dispositivos
    private val deviceMap = mutableMapOf<String, BluetoothDevice>()

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
    }

    interface BLECallbacks {
        fun onDeviceFound(device: BluetoothDevice, rssi: Int)
        fun onDataReceived(data: String)
        fun onConnectionStateChange(connected: Boolean)
    }

    fun setCallbacks(callbacks: BLECallbacks) {
        this.callbacks = callbacks
    }

    fun scanForDevices() {
        scanner = bluetoothAdapter.bluetoothLeScanner

        if (scanner == null) {
            Log.e(TAG, "BluetoothLE scanner no disponible")
            return
        }

        if (isScanning) {
            stopScan()
        }

        try {
            // Configuración del escaneo
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            // Filtros opcionales - puedes filtrar por servicio UUID específico
            val filters = mutableListOf<ScanFilter>()
            /*
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()
            filters.add(filter)
            */

            // Detener el escaneo después de un período definido
            scanHandler.postDelayed({ stopScan() }, SCAN_PERIOD)

            isScanning = true
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED) {
                scanner?.startScan(filters, settings, scanCallback)
                Log.d(TAG, "Escaneo BLE iniciado")
            } else {
                Log.e(TAG, "Permiso BLUETOOTH_SCAN no concedido")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de seguridad al escanear: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar escaneo: ${e.message}")
        }
    }

    fun stopScan() {
        if (isScanning && scanner != null) {
            try {
                isScanning = false
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED) {
                    scanner?.stopScan(scanCallback)
                    Log.d(TAG, "Escaneo BLE detenido")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Error de seguridad al detener escaneo: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error al detener escaneo: ${e.message}")
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

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Escaneo BLE fallido con código de error: $errorCode")
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt = device.connectGatt(context, false, gattCallback)
                Log.d(TAG, "Intentando conectar a ${device.name ?: device.address}")
            } else {
                Log.e(TAG, "Permiso BLUETOOTH_CONNECT no concedido")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de seguridad al conectar: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error al conectar: ${e.message}")
        }
    }

    fun disconnect() {
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt?.disconnect()
                bluetoothGatt?.close()
                bluetoothGatt = null
                Log.d(TAG, "Desconectado")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de seguridad al desconectar: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error al desconectar: ${e.message}")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Conectado al dispositivo GATT")
                try {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED) {
                        gatt.discoverServices()
                    }
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
                            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                                == PackageManager.PERMISSION_GRANTED) {
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

        // Para Android < 13
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                handleCharacteristicChanged(characteristic)
            }
        }

        // Para Android 13+
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (CHARACTERISTIC_UUID == characteristic.uuid) {
                    val data = String(value, StandardCharsets.UTF_8)
                    Log.d(TAG, "Datos recibidos: $data")
                    callbacks.onDataReceived(data)
                }
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