package com.example.MQ7Monitor

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.MQ7Monitor.ui.theme.MQ7MonitorTheme

class MainActivity : ComponentActivity() {
    // Constante para solicitud de activación de Bluetooth
    private val REQUEST_ENABLE_BT = 1

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bleManager: BLEManager
    private var viewModel: GasSensorViewModel? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.d("MainActivity", "Todos los permisos concedidos, iniciando escaneo")
            startBleScan()
        } else {
            Log.e("MainActivity", "No se concedieron los permisos necesarios")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar Bluetooth
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            // El dispositivo no soporta Bluetooth
            Log.e("MainActivity", "Este dispositivo no soporta Bluetooth")
            finish()
            return
        }

        // Inicializar el gestor BLE con callbacks
        bleManager = BLEManager(this, object : BLEManager.BLECallbacks {
            override fun onDeviceFound(device: BluetoothDevice, rssi: Int) {
                // Se manejará en el ViewModel
                Log.d("MainActivity", "Dispositivo encontrado: ${device.name ?: "Desconocido"}, pasando a ViewModel")
            }

            override fun onDataReceived(data: String) {
                // Se manejará en el ViewModel
            }

            override fun onConnectionStateChange(connected: Boolean) {
                // Se manejará en el ViewModel
            }
        })

        setContent {
            MQ7MonitorTheme {
                val viewModelInstance: GasSensorViewModel = viewModel()
                this.viewModel = viewModelInstance

                // Pasar el BLEManager al ViewModel
                LaunchedEffect(Unit) {
                    viewModelInstance.setBleManager(bleManager)
                    Log.d("MainActivity", "BLEManager configurado en el ViewModel")
                }

                SensorGasApp(
                    viewModel = viewModelInstance,
                    onScanClick = {
                        Log.d("MainActivity", "Botón de escaneo tocado en la UI")
                        checkPermissionsAndScan()
                    }
                )
            }
        }
    }

    private fun checkPermissionsAndScan() {
        Log.d("MainActivity", "Verificando permisos para escaneo BLE")
        val requiredPermissions = mutableListOf<String>()

        // Verificar permisos de Bluetooth
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
                != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN)
                != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }

        // Verificar permiso de ubicación (necesario para escaneo BLE)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (requiredPermissions.isNotEmpty()) {
            Log.d("MainActivity", "Solicitando permisos: ${requiredPermissions.joinToString()}")
            requestPermissionLauncher.launch(requiredPermissions.toTypedArray())
        } else {
            // Verificar si Bluetooth está habilitado antes de iniciar el escaneo
            if (!bluetoothAdapter.isEnabled) {
                // Solicitar al usuario que active Bluetooth
                Log.d("MainActivity", "Bluetooth no está habilitado, solicitando activación")
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED) {
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                    return
                }
            } else {
                Log.d("MainActivity", "Todos los permisos concedidos y Bluetooth activo, iniciando escaneo")
                startBleScan()
            }
        }
    }

    private fun startBleScan() {
        // Verificar si Bluetooth está activado
        if (!bluetoothAdapter.isEnabled) {
            Log.w("MainActivity", "Bluetooth no está activado")
            // Podríamos solicitar al usuario que lo active
            return
        }

        Log.d("MainActivity", "Iniciando escaneo desde MainActivity")
        bleManager.scanForDevices()
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.disconnect()
        bleManager.stopScan()
        Log.d("MainActivity", "Actividad destruida, escáner y conexiones detenidas")
    }
}