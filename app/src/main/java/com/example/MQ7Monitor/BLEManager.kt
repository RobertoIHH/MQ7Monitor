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
import android.util.Log
import androidx.core.app.ActivityCompat
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

class BLEManager(private val context: Context, private var callbacks: BLECallbacks) {
    companion object {
        private const val TAG = "BLEManager"

        // UUID del servicio y características de la ESP32
        private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        private val CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        private val COMMAND_CHAR_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a9")
        private val STATUS_CHAR_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26aa")
        private val DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val SCAN_PERIOD = 30000L // 30 segundos

        // Tiempo máximo para esperar a que se complete un mensaje JSON (ms)
        private const val JSON_COMPLETION_TIMEOUT = 1000L
    }

    private var bluetoothAdapter: BluetoothAdapter
    private var scanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var dataCharacteristic: BluetoothGattCharacteristic? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var statusCharacteristic: BluetoothGattCharacteristic? = null
    private val scanHandler = Handler(Looper.getMainLooper())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isScanning = false
    private var dataBuffer = StringBuilder(100)  // Buffer más grande para acumular datos (100 chars)
    private var lastFragmentTime = 0L  // Para seguimiento de fragmentos y timeouts

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

        // Nuevo método para recibir actualizaciones de estado
        fun onStatusUpdate(statusJson: String)
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

            // No usar filtros para encontrar todos los dispositivos
            val filters = mutableListOf<ScanFilter>()

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
                // Incluir dispositivos sin nombre
                val deviceName = device.name ?: "Desconocido"
                val deviceAddress = device.address

                // Guardar el dispositivo en el mapa
                deviceMap[deviceAddress] = device

                // Informar del dispositivo encontrado desde el hilo principal
                mainHandler.post {
                    Log.d(TAG, "Dispositivo encontrado: $deviceName, dirección: $deviceAddress, RSSI: ${result.rssi}")
                    callbacks.onDeviceFound(device, result.rssi)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Escaneo BLE fallido con código de error: $errorCode")
            // Proporcionar más información sobre el error
            val reason = when (errorCode) {
                ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Escaneo ya iniciado"
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Fallo registro aplicación"
                ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "Característica no soportada"
                ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Error interno"
                else -> "Error desconocido"
            }
            Log.e(TAG, "Razón: $reason")
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        try {
            // Limpiar buffer al iniciar una nueva conexión
            dataBuffer.clear()
            lastFragmentTime = 0L

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
                // Abrir conexión con mayor MTU para paquetes más grandes
                bluetoothGatt = device.connectGatt(
                    context,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )
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

                // Notificar desconexión explícitamente
                mainHandler.post {
                    callbacks.onConnectionStateChange(false)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de seguridad al desconectar: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error al desconectar: ${e.message}")
        }
    }

    // Enviar comando para cambiar el tipo de gas
    fun sendGasTypeCommand(gasType: GasType) {
        try {
            if (commandCharacteristic == null) {
                Log.e(TAG, "No se puede enviar comando: característica de comando no disponible")
                return
            }

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
                // Crear comando basado en el tipo de gas con timestamp para confirmación
                val timestamp = System.currentTimeMillis()
                val command = "${gasType.name}:$timestamp"

                // Convertir a bytes y enviar
                commandCharacteristic?.setValue(command.toByteArray(StandardCharsets.UTF_8))
                val success = bluetoothGatt?.writeCharacteristic(commandCharacteristic)

                Log.d(TAG, "Enviando comando para cambiar a gas ${gasType.name} (timestamp: $timestamp): $success")
            } else {
                Log.e(TAG, "Permiso BLUETOOTH_CONNECT no concedido")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de seguridad al enviar comando: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar comando: ${e.message}")
        }
    }

    // Leer el estado actual del sensor
    fun readSensorStatus() {
        try {
            if (statusCharacteristic == null) {
                Log.e(TAG, "No se puede leer estado: característica de estado no disponible")
                return
            }

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
                val success = bluetoothGatt?.readCharacteristic(statusCharacteristic)
                Log.d(TAG, "Solicitando estado del sensor: $success")
            } else {
                Log.e(TAG, "Permiso BLUETOOTH_CONNECT no concedido")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de seguridad al leer estado: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error al leer estado: ${e.message}")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Conectado al dispositivo GATT")
                try {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        )
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        // Solicitar un MTU más grande para paquetes más grandes
                        bluetoothGatt?.requestMtu(512)
                        gatt.discoverServices()
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Error al descubrir servicios: ${e.message}")
                }
                // Notificar en el hilo principal
                mainHandler.post {
                    callbacks.onConnectionStateChange(true)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Desconectado del dispositivo GATT")
                // Notificar en el hilo principal
                mainHandler.post {
                    callbacks.onConnectionStateChange(false)
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "MTU cambiado a $mtu bytes")
            } else {
                Log.e(TAG, "No se pudo cambiar el MTU, status: $status")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    // Obtener características
                    dataCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
                    commandCharacteristic = service.getCharacteristic(COMMAND_CHAR_UUID)
                    statusCharacteristic = service.getCharacteristic(STATUS_CHAR_UUID)

                    if (dataCharacteristic != null) {
                        try {
                            if (ActivityCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                )
                                == PackageManager.PERMISSION_GRANTED
                            ) {
                                // Habilitar notificaciones para la característica de datos
                                gatt.setCharacteristicNotification(dataCharacteristic, true)

                                // Escribir el descriptor para habilitar notificaciones
                                val descriptor = dataCharacteristic?.getDescriptor(DESCRIPTOR_UUID)
                                if (descriptor != null) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        gatt.writeDescriptor(
                                            descriptor,
                                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                        )
                                    } else {
                                        @Suppress("DEPRECATION")
                                        descriptor.value =
                                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                        @Suppress("DEPRECATION")
                                        gatt.writeDescriptor(descriptor)
                                    }
                                    Log.d(TAG, "Notificaciones habilitadas para datos")
                                }

                                // Leer el estado inicial del sensor
                                if (statusCharacteristic != null) {
                                    mainHandler.postDelayed({
                                        readSensorStatus()
                                    }, 500) // Pequeña espera para estabilizar la conexión
                                }

                                Log.d(
                                    TAG,
                                    "Características encontradas: Data=${dataCharacteristic != null}, " +
                                            "Command=${commandCharacteristic != null}, " +
                                            "Status=${statusCharacteristic != null}"
                                )
                            }
                        } catch (e: SecurityException) {
                            Log.e(TAG, "Error al configurar notificaciones: ${e.message}")
                        }
                    } else {
                        Log.e(TAG, "No se encontró la característica de datos")
                    }

                    if (commandCharacteristic == null) {
                        Log.e(TAG, "No se encontró la característica de comandos")
                    }

                    if (statusCharacteristic == null) {
                        Log.e(TAG, "No se encontró la característica de estado")
                    }
                } else {
                    Log.e(TAG, "No se encontró el servicio")
                }
            } else {
                Log.e(TAG, "Error al descubrir servicios: $status")
            }
        }

        // Este método es para Android 12 (API 31) y superior
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, value, status)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (characteristic.uuid) {
                    STATUS_CHAR_UUID -> {
                        val statusJson = String(value, StandardCharsets.UTF_8)
                        Log.d(TAG, "Leído estado: $statusJson")

                        mainHandler.post {
                            callbacks.onStatusUpdate(statusJson)
                        }
                    }
                    else -> {
                        Log.d(TAG, "Leída característica desconocida: ${characteristic.uuid}")
                    }
                }
            } else {
                Log.e(TAG, "Error al leer característica: $status")
            }
        }

        // Este método es para Android 11 (API 30) y anteriores
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    when (characteristic.uuid) {
                        STATUS_CHAR_UUID -> {
                            val value = characteristic.value
                            if (value != null) {
                                val statusJson = String(value, StandardCharsets.UTF_8)
                                Log.d(TAG, "Leído estado (compat): $statusJson")

                                mainHandler.post {
                                    callbacks.onStatusUpdate(statusJson)
                                }
                            }
                        }
                        else -> {
                            Log.d(TAG, "Leída característica desconocida: ${characteristic.uuid}")
                        }
                    }
                } else {
                    Log.e(TAG, "Error al leer característica: $status")
                }
            }
        }

        // Este método es para Android 12 (API 31) y superior
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)

            when (characteristic.uuid) {
                CHARACTERISTIC_UUID -> {
                    // Datos del sensor recibidos
                    val dataString = String(value, StandardCharsets.UTF_8)
                    Log.d(TAG, "Datos recibidos: $dataString")

                    // Verificar que sea un JSON completo (empieza con { y termina con })
                    if (dataString.trim().startsWith("{") && dataString.trim().endsWith("}")) {
                        mainHandler.post {
                            callbacks.onDataReceived(dataString)
                        }
                    } else {
                        Log.e(TAG, "Datos recibidos no son un JSON válido: $dataString")
                    }
                }
                else -> {
                    Log.d(TAG, "Notificación de característica desconocida: ${characteristic.uuid}")
                }
            }
        }

        // Este método es para Android 11 (API 30) y anteriores
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                when (characteristic.uuid) {
                    CHARACTERISTIC_UUID -> {
                        // Datos del sensor recibidos
                        val value = characteristic.value
                        if (value != null) {
                            val dataString = String(value, StandardCharsets.UTF_8)
                            Log.d(TAG, "Datos recibidos (compat): $dataString")

                            // Verificar que sea un JSON completo (empieza con { y termina con })
                            if (dataString.trim().startsWith("{") && dataString.trim().endsWith("}")) {
                                mainHandler.post {
                                    callbacks.onDataReceived(dataString)
                                }
                            } else {
                                Log.e(TAG, "Datos recibidos no son un JSON válido: $dataString")
                            }
                        }
                    }
                    else -> {
                        Log.d(TAG, "Notificación de característica desconocida: ${characteristic.uuid}")
                    }
                }
            }
        }
    }
}