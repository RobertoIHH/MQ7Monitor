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
import java.util.regex.Pattern

class BLEManager(private val context: Context, private var callbacks: BLECallbacks) {
    companion object {
        private const val TAG = "BLEManager"

        // UUID del servicio y característica de la ESP32
        private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        private val CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        private val DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val SCAN_PERIOD = 30000L // 30 segundos

        // Tiempo máximo para esperar a que se complete un mensaje JSON (ms)
        private const val JSON_COMPLETION_TIMEOUT = 1000L
    }

    private var bluetoothAdapter: BluetoothAdapter
    private var scanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var dataCharacteristic: BluetoothGattCharacteristic? = null
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

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Conectado al dispositivo GATT")
                try {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED) {
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
                    processReceivedData(data)
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
                    processReceivedData(dataString)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al procesar datos: ${e.message}")
            }
        }
    }

    // Método para procesar datos potencialmente fragmentados
    private fun processReceivedData(dataFragment: String) {
        try {
            val currentTime = System.currentTimeMillis()
            Log.d(TAG, "Datos recibidos: $dataFragment (${dataFragment.length} chars)")

            // Resetear el buffer si ha pasado mucho tiempo desde el último fragmento
            if (lastFragmentTime > 0 && currentTime - lastFragmentTime > JSON_COMPLETION_TIMEOUT) {
                Log.d(TAG, "Timeout: Descartando buffer incompleto: $dataBuffer")
                dataBuffer.clear()
            }

            // Actualizar tiempo del último fragmento
            lastFragmentTime = currentTime

            // Añadir el fragmento al buffer
            dataBuffer.append(dataFragment)
            val bufferStr = dataBuffer.toString()

            // Log del buffer completo para depuración
            Log.d(TAG, "Buffer actual: $bufferStr (${bufferStr.length} chars)")

            // Verificar si tenemos un JSON completo o múltiples JSON
            if (bufferStr.startsWith("{") && bufferStr.endsWith("}")) {
                // Parece un JSON completo, pero verificamos estructura
                if (isValidJson(bufferStr)) {
                    Log.d(TAG, "JSON completo encontrado: $bufferStr")
                    mainHandler.post {
                        callbacks.onDataReceived(bufferStr)
                    }
                    dataBuffer.clear()
                }
            } else if (bufferStr.contains("}{")) {
                // Fragmentos múltiples juntos - procesamos el primero completo
                val firstClosingBrace = bufferStr.indexOf("}")
                if (firstClosingBrace > 0) {
                    val firstJson = bufferStr.substring(0, firstClosingBrace + 1)
                    if (isValidJson(firstJson)) {
                        Log.d(TAG, "Primer JSON extraído: $firstJson")
                        mainHandler.post {
                            callbacks.onDataReceived(firstJson)
                        }
                        // Guardar el resto en el buffer
                        val remaining = bufferStr.substring(firstClosingBrace + 1)
                        dataBuffer.clear()
                        dataBuffer.append(remaining)
                    }
                }
            } else {
                // Extraer cualquier JSON completo usando regex
                val pattern = Pattern.compile("\\{[^\\{\\}]*\\}")
                val matcher = pattern.matcher(bufferStr)
                while (matcher.find()) {
                    val json = matcher.group()
                    if (isValidJson(json)) {
                        Log.d(TAG, "JSON extraído por regex: $json")
                        mainHandler.post {
                            callbacks.onDataReceived(json)
                        }
                    }
                }

                // Si hemos extraído todos los JSON completos, limpiar el buffer
                if (bufferStr.endsWith("}") && bufferStr.indexOf("{") <= bufferStr.lastIndexOf("}")) {
                    dataBuffer.clear()
                }
            }

            // Análisis alternativo: Si tenemos las etiquetas esperadas en el fragmento, extraer datos directamente
            tryExtractValues(dataFragment)

        } catch (e: Exception) {
            Log.e(TAG, "Error al procesar fragmento de datos: ${e.message}")
            e.printStackTrace()
            // No limpiamos el buffer automáticamente para intentar recuperar más datos
        }
    }

    // Método para validar JSON mínimamente sin parsearlo
    private fun isValidJson(str: String): Boolean {
        return str.startsWith("{") && str.endsWith("}")
    }

    // Método para extraer valores directamente cuando el JSON está fragmentado
    private fun tryExtractValues(fragment: String) {
        try {
            // Buscar patrones de ADC, V y ppm
            val adcPattern = "\\\"ADC\\\"\\s*:\\s*(\\d+)".toRegex()
            val voltagePattern = "\\\"V\\\"\\s*:\\s*([0-9.]+)".toRegex()
            val ppmPattern = "\\\"ppm\\\"\\s*:\\s*([0-9.]+)".toRegex()

            // Map para almacenar valores encontrados
            val values = mutableMapOf<String, Any>()

            // Intentar encontrar valores
            adcPattern.find(fragment)?.let {
                values["ADC"] = it.groupValues[1].toInt()
            }

            voltagePattern.find(fragment)?.let {
                values["V"] = it.groupValues[1].toDouble()
            }

            ppmPattern.find(fragment)?.let {
                values["ppm"] = it.groupValues[1].toDouble()
            }

            // Si tenemos al menos 2 de los 3 valores, reconstruimos el JSON
            if (values.size >= 2) {
                val reconstructed = StringBuilder("{")
                values.entries.forEachIndexed { index, entry ->
                    val key = entry.key
                    val value = entry.value

                    reconstructed.append("\"$key\":")
                    when (value) {
                        is Int -> reconstructed.append(value)
                        is Double -> reconstructed.append(value)
                        else -> reconstructed.append("\"$value\"")
                    }

                    if (index < values.size - 1) {
                        reconstructed.append(",")
                    }
                }
                reconstructed.append("}")

                val jsonStr = reconstructed.toString()
                Log.d(TAG, "JSON reconstruido de fragmentos: $jsonStr")

                // Enviar a ViewModel
                mainHandler.post {
                    callbacks.onDataReceived(jsonStr)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al extraer valores de fragmento: ${e.message}")
        }
    }
}