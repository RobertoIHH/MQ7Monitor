package com.example.MQ7Monitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.util.Log
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SensorGasApp(
    viewModel: GasSensorViewModel,
    onScanClick: () -> Unit
) {
    val isConnected by viewModel.isConnected
    val connectionStatus by viewModel.connectionStatus
    val isScanning by viewModel.isScanning
    val selectedDevice by viewModel.selectedDevice

    // Importante: Usar DisposableEffect para efectos al montar y desmontar la composable
    DisposableEffect(Unit) {
        Log.d("SensorGasApp", "Componente SensorGasApp inicializado")
        onDispose {
            Log.d("SensorGasApp", "Componente SensorGasApp destruido")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Título
        Text(
            text = "Monitor de Sensor MQ7",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        // Botones de acción
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    Log.d("SensorGasApp", "Botón de escaneo presionado")
                    onScanClick() // Primero solicitar permisos si es necesario
                },
                modifier = Modifier.weight(1f),
                enabled = !isScanning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (isScanning) "Escaneando..." else "Buscar",
                    color = Color.White
                )
            }

            Button(
                onClick = { viewModel.connectToDevice() },
                modifier = Modifier.weight(1f),
                enabled = selectedDevice != null && !isConnected
            ) {
                Text("Conectar")
            }

            Button(
                onClick = { viewModel.disconnectDevice() },
                modifier = Modifier.weight(1f),
                enabled = isConnected
            ) {
                Text("Desconectar")
            }
        }

        // Estado de conexión
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Estado:",
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = connectionStatus,
                color = if (isConnected) Color.Green else Color.Red
            )
        }

        //lista Disp
        Text(
            text = "Dispositivos disponibles:",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp)
        )

        if (isScanning) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }

        // Imprimir log de dispositivos encontrados
        Log.d("SensorGasApp", "Renderizando ${viewModel.deviceList.size} dispositivos")

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                .background(Color(0xFFF0F0F0))
        ) {
            if (viewModel.deviceList.isEmpty()) {
                // Mostrar mensaje si no hay dispositivos
                Text(
                    text = if (isScanning) "Buscando dispositivos..." else "No se encontraron dispositivos",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    color = Color.Gray
                )
            } else {
                // Mostrar la lista de dispositivos encontrados
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(viewModel.deviceList) { device ->
                        DeviceItem(
                            deviceInfo = device,
                            isSelected = selectedDevice?.address == device.address,
                            onClick = {
                                Log.d("SensorGasApp", "Dispositivo seleccionado: ${device.name}")
                                viewModel.selectDevice(device)
                            }
                        )
                    }
                }
            }
        }

        // Datos del sensor
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Datos del sensor:",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val rawValue by viewModel.rawValue
                val voltage by viewModel.voltage
                val ppmValue by viewModel.ppmValue

                Text(text = "ADC: $rawValue")
                Text(text = "Voltaje: ${String.format("%.2f", voltage)} V")
                Text(text = "PPM: $ppmValue ppm")
            }
        }

        // Gráfico en tiempo real
        Text(
            text = "Gráfico en tiempo real:",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                .padding(8.dp)
        ) {
            if (viewModel.chartData.isNotEmpty()) {
                LineChart(dataPoints = viewModel.chartData)
            } else {
                Text(
                    text = "Esperando datos...",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun DeviceItem(
    deviceInfo: DeviceInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) Color(0xFFE3F2FD) else Color.Transparent)
            .padding(12.dp), // Aumentado para mejor visualización
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = deviceInfo.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = deviceInfo.address,
                fontSize = 12.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "RSSI: ${deviceInfo.rssi} dBm",
            fontSize = 12.sp,
            color = if (deviceInfo.rssi > -60) Color.Green else if (deviceInfo.rssi > -80) Color.Blue else Color.Red
        )
    }
}

@Composable
fun LineChart(dataPoints: List<DataPoint>) {
    if (dataPoints.isEmpty()) return

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val width = size.width
        val height = size.height
        val padding = 16.dp.toPx()

        // Calcular valores mínimos y máximos
        val minY = dataPoints.minByOrNull { it.y }?.y ?: 0f
        val maxY = dataPoints.maxByOrNull { it.y }?.y ?: 4095f
        val range = (maxY - minY).coerceAtLeast(1f)

        // Dibujar ejes
        drawLine(
            color = Color.Gray,
            start = Offset(padding, padding),
            end = Offset(padding, height - padding),
            strokeWidth = 1.dp.toPx()
        )

        drawLine(
            color = Color.Gray,
            start = Offset(padding, height - padding),
            end = Offset(width - padding, height - padding),
            strokeWidth = 1.dp.toPx()
        )

        // Dibujar línea de datos
        if (dataPoints.size > 1) {
            val path = Path()
            val points = dataPoints.mapIndexed { index, point ->
                // Escalar puntos al espacio del gráfico
                val x = padding + (index.toFloat() / (dataPoints.size - 1)) * (width - 2 * padding)
                val y = height - padding - ((point.y - minY) / range) * (height - 2 * padding)
                Offset(x, y)
            }

            // Mover a primer punto
            path.moveTo(points.first().x, points.first().y)

            // Conectar resto de puntos
            for (i in 1 until points.size) {
                path.lineTo(points[i].x, points[i].y)
            }

            drawPath(
                path = path,
                color = Color.Blue,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Dibujar puntos
            points.forEach { point ->
                drawCircle(
                    color = Color.Blue,
                    radius = 3.dp.toPx(),
                    center = point
                )
            }
        }
    }
}

@Preview
@Composable
fun SensorGasAppPreview() {
    // Esta es solo una vista previa y no funcional
}