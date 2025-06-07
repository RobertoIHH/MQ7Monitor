Integrantes del desarrollo 
Hernandez Hernandez Roberto Isaac
Gonzalez Llamosas Noe Ramses

# MQ7 Monitor - Android Application

## Descripción

MQ7 Monitor es una aplicación Android desarrollada en Kotlin con Jetpack Compose que permite monitorear en tiempo real múltiples tipos de gases utilizando sensores MQ conectados via Bluetooth Low Energy (BLE) a un dispositivo ESP32.

## Características Principales

### 🔍 Detección Multi-Gas
- **CO (Monóxido de Carbono)**: Detección de gases tóxicos
- **H2 (Hidrógeno)**: Monitoreo de gases inflamables
- **LPG (Gas Licuado de Petróleo)**: Detección de fugas de gas doméstico
- **CH4 (Metano)**: Monitoreo de gas natural
- **Alcohol**: Detección de vapores de alcohol

### 📱 Interfaz de Usuario Moderna
- Desarrollada con **Jetpack Compose**
- Diseño Material Design 3
- Gráficos en tiempo real para cada tipo de gas
- Cambio dinámico entre tipos de gases
- Indicadores visuales específicos por gas con códigos de color

### 🔗 Conectividad BLE
- Escaneo automático de dispositivos ESP32
- Conexión estable via Bluetooth Low Energy
- Intercambio de comandos para cambio de tipo de gas
- Recepción de datos en formato JSON

### 📊 Visualización de Datos
- **Valores en tiempo real**: ADC, voltaje y PPM
- **Gráficos duales**: PPM específico por gas y valores ADC
- **Escalas automáticas**: Adaptación dinámica de rangos min/max
- **Historial visual**: Hasta 60 puntos de datos por gráfico

## Arquitectura del Proyecto

### Estructura de Archivos Principales

```
app/src/main/java/com/example/MQ7Monitor/
├── MainActivity.kt              # Actividad principal y gestión de permisos
├── SensorGasApp.kt             # Interfaz de usuario principal (Compose)
├── GasSensorViewModel.kt       # Lógica de negocio y estado de la aplicación
├── BLEManager.kt               # Gestión de comunicación Bluetooth
├── SensorDataManager.kt        # Procesamiento de datos del sensor
└── ui/theme/                   # Temas y estilos de la aplicación
```

### Componentes Clave

#### 🎯 **GasSensorViewModel**
- Gestión del estado de la aplicación
- Coordinación entre UI y BLE
- Procesamiento de datos JSON del sensor
- Manejo de gráficos y datos históricos

#### 📡 **BLEManager**
- Escaneo de dispositivos BLE
- Gestión de conexiones
- Envío de comandos al ESP32
- Recepción de datos del sensor

#### 🎨 **SensorGasApp (Compose UI)**
- Interfaz de usuario reactiva
- Selector de gases con botones dinámicos
- Gráficos de líneas personalizados
- Lista de dispositivos disponibles

## Protocolo de Comunicación

### UUIDs de Servicios y Características

```kotlin
SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
CHARACTERISTIC_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8"  // Datos del sensor
COMMAND_CHAR_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a9"    // Comandos
STATUS_CHAR_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26aa"     // Estado
```

### Formato de Datos JSON

#### Datos del Sensor
```json
{
  "ADC": 1234,
  "V": 2.45,
  "ppm": 15.6,
  "gas": "CO"
}
```

#### Confirmación de Cambio de Gas
```json
{
  "command": "gas_changed",
  "to": "H2",
  "success": true,
  "timestamp": 1640995200000,
  "requested": "H2"
}
```

#### Estado del Sensor
```json
{
  "status": "ok",
  "current_gas": "CO",
  "gas_index": 0
}
```

## Requisitos del Sistema

### Versiones Android
- **Mínimo**: Android 7.0 (API 25)
- **Objetivo**: Android 14 (API 35)
- **Compilación**: SDK 35

### Permisos Necesarios
- `BLUETOOTH` / `BLUETOOTH_SCAN` (Android 12+)
- `BLUETOOTH_ADMIN` / `BLUETOOTH_CONNECT` (Android 12+)
- `ACCESS_FINE_LOCATION` - Requerido para escaneo BLE
- `ACCESS_COARSE_LOCATION`

### Dependencias Principales
```kotlin
// Compose y UI
implementation("androidx.activity:activity-compose:1.10.1")
implementation("androidx.compose.material3:material3")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

// Kotlin
kotlin = "2.0.0"
```

## Instalación y Configuración

### 1. Clonar el Repositorio
```bash
git clone [repository-url]
cd MQ7Monitor
```

### 2. Configurar Android Studio
- Abrir el proyecto en Android Studio
- Sincronizar dependencias de Gradle
- Configurar SDK de Android 35

### 3. Configurar Dispositivo ESP32
- Cargar firmware compatible con el protocolo de comunicación
- Configurar UUIDs de servicios BLE
- Implementar calibraciones para cada tipo de gas

### 4. Compilar y Ejecutar
```bash
./gradlew assembleDebug
```

## Uso de la Aplicación

### 1. **Buscar Dispositivos**
- Presionar "Buscar" para escanear dispositivos BLE
- Seleccionar el ESP32 de la lista

### 2. **Conectar**
- Presionar "Conectar" para establecer conexión BLE
- Esperar confirmación de conexión exitosa

### 3. **Seleccionar Gas**
- Usar los botones de gas para cambiar el tipo de medición
- Observar la confirmación del cambio en la interfaz

### 4. **Monitorear Datos**
- Ver valores en tiempo real: ADC, voltaje y PPM
- Observar gráficos actualizados automáticamente
- Analizar tendencias y patrones

## Calibraciones de Gases

La aplicación incluye calibraciones específicas para cada gas:

| Gas | Punto X0 (ppm) | Punto Y0 (Rs/R0) | Punto X1 (ppm) | Punto Y1 (Rs/R0) |
|-----|----------------|------------------|----------------|------------------|
| CO | 50.0 | 1.66 | 4000.0 | 0.052 |
| H2 | 50.0 | 1.36 | 4000.0 | 0.09 |
| LPG | 50.0 | 9.0 | 4000.0 | 5.0 |
| CH4 | 50.0 | 15.0 | 4000.0 | 9.0 |
| Alcohol | 50.0 | 17.0 | 4000.0 | 13.0 |

## Características Técnicas

### Gráficos
- **Escalas duales**: PPM (eje izquierdo) y ADC (eje derecho)
- **Colores específicos**: Cada gas tiene un color único
- **Actualización automática**: Datos en tiempo real
- **Buffer circular**: Máximo 60 puntos por gráfico

### Gestión de Estado
- **MVVM**: Arquitectura Model-View-ViewModel
- **Estado reactivo**: Uso de Compose State
- **Persistencia**: Datos en memoria durante la sesión

## Screenshots

### Pantalla Principal
![Pantalla Principal](screenshots/main_screen.png)

### Selección de Gas
![Selector de Gas](screenshots/gas_selector.png)

### Gráficos en Tiempo Real
![Gráficos](screenshots/real_time_charts.png)

## Solución de Problemas

### Problemas de Conexión BLE
1. Verificar permisos de Bluetooth y ubicación
2. Asegurar que Bluetooth esté habilitado
3. Verificar compatibilidad del dispositivo ESP32

### Datos Inconsistentes
1. Verificar calibraciones del sensor
2. Revisar estabilidad de la conexión BLE
3. Verificar formato JSON de los datos

### Problemas de Permisos
1. Conceder permisos manualmente en configuración
2. Verificar versión de Android para permisos correctos
3. Reiniciar aplicación después de conceder permisos

## API Reference

### BLEManager
```kotlin
class BLEManager(context: Context, callbacks: BLECallbacks) {
    fun scanForDevices()
    fun connectToDevice(device: BluetoothDevice)
    fun sendGasTypeCommand(gasType: GasType)
    fun readSensorStatus()
    fun disconnect()
}
```

### GasSensorViewModel
```kotlin
class GasSensorViewModel : ViewModel() {
    val connectionStatus: State<String>
    val currentGasType: State<GasType>
    val ppmValue: State<Double>
    
    fun changeGasType(gasType: GasType)
    fun startScan()
    fun connectToDevice()
}
```

## Desarrollo y Contribución

### Estructura de Clases
- `BLEManager`: Comunicación Bluetooth
- `GasSensorViewModel`: Lógica de negocio
- `SensorDataManager`: Procesamiento de datos
- `GasType`: Enumeración de tipos de gas

### Extensiones Futuras
- [ ] Almacenamiento persistente de datos
- [ ] Exportación de datos a CSV
- [ ] Alertas configurables por tipo de gas
- [ ] Calibración manual de sensores
- [ ] Soporte para múltiples sensores simultáneos
- [ ] Interfaz web complementaria
- [ ] Notificaciones push por límites de concentración

## Changelog

### v1.0.0 (Actual)
- ✅ Implementación inicial de monitoreo multi-gas
- ✅ Interfaz Jetpack Compose
- ✅ Comunicación BLE con ESP32
- ✅ Gráficos en tiempo real
- ✅ Selector dinámico de gases

### Roadmap v1.1.0
- 🔄 Persistencia de datos local
- 🔄 Configuración de alertas
- 🔄 Exportación de datos

## Licencias y Dependencias

### Licencia Principal
Este proyecto está bajo licencia MIT.

### Dependencias de Terceros
- **Android Jetpack Compose**: Apache 2.0
- **Kotlin**: Apache 2.0
- **Material Design**: Apache 2.0

## Hardware Requerido

### ESP32 Compatible
- ESP32 con Bluetooth LE habilitado
- Sensor MQ-7 (CO) o serie MQ compatible
- Circuito de acondicionamiento de señal
- Alimentación 3.3V/5V

### Esquema de Conexión
```
ESP32 Pin    |  Sensor MQ
-------------|------------
GPIO36 (A0)  |  A0 (Analog Out)
3.3V         |  VCC
GND          |  GND
GPIO2        |  D0 (Digital Out)
```


**Nota Importante**: Esta aplicación requiere un dispositivo ESP32 con firmware compatible y sensores MQ calibrados apropiadamente para funcionar correctamente. Asegúrese de seguir las medidas de seguridad apropiadas al trabajar con sensores de gas.

**⚠️ Advertencia de Seguridad**: Los sensores de gas son dispositivos de monitoreo y no deben ser la única medida de seguridad en ambientes potencialmente peligrosos. Siempre siga los protocolos de seguridad establecidos para el manejo de gases.
