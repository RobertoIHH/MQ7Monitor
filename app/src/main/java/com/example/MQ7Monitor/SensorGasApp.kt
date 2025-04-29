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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

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
                val gasType by viewModel.gasType

                // Gas actual - Destacado con color
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Gas:",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(
                        text = gasType,
                        fontWeight = FontWeight.Bold,
                        color = when(gasType) {
                            "CO" -> Color.Blue
                            "H2" -> Color.Green
                            else -> Color.Black
                        }
                    )
                }

                // Valores del ADC
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ADC:",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(text = "$rawValue")
                }

                // Voltaje
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Voltaje:",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(text = "${String.format("%.2f", voltage)} V")
                }

                // PPM con colores indicativos del nivel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PPM:",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(
                        text = "${String.format("%.2f", ppmValue)} ppm",
                        fontWeight = FontWeight.Bold,
                        color = when {
                            gasType == "CO" && ppmValue > 50 -> Color.Red
                            gasType == "CO" && ppmValue > 30 -> Color(0xFFFF8C00) // Naranja
                            gasType == "H2" && ppmValue > 1000 -> Color.Red
                            gasType == "H2" && ppmValue > 500 -> Color(0xFFFF8C00) // Naranja
                            else -> Color.Green
                        }
                    )
                }
            }
        }

        // Gráfico en tiempo real
        Text(
            text = "Gráfico en tiempo real:",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp)
        )

        // Aquí estaba faltando el Box con el gráfico
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                .padding(8.dp)
        ) {
            if (viewModel.ppmChartData.isNotEmpty() || viewModel.adcChartData.isNotEmpty()) {
                LineChart(
                    ppmDataPoints = viewModel.ppmChartData,
                    adcDataPoints = viewModel.adcChartData
                )
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
fun LineChart(ppmDataPoints: List<DataPoint>,
              adcDataPoints: List<DataPoint>) {
    if (ppmDataPoints.isEmpty() && adcDataPoints.isEmpty()) return

    // Acceder al ViewModel para obtener los valores min/max de ADC y el tipo de gas
    val viewModel: GasSensorViewModel = viewModel()
    val minADC = viewModel.minADCValue.value.toFloat()
    val maxADC = viewModel.maxADCValue.value.toFloat().coerceAtLeast(minADC + 1f)
    val currentGasType by viewModel.gasType  // Obtener el tipo de gas actual

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val width = size.width
        val height = size.height
        val padding = 16.dp.toPx()
        val leftPadding = 16.dp.toPx()
        val rightPadding = 40.dp.toPx() // Padding adicional para la escala derecha

        // Calcular valores mínimos y máximos para el eje Y (PPM)
        val minPPM = ppmDataPoints.minByOrNull { it.y }?.y ?: 0f
        val maxPPM = ppmDataPoints.maxByOrNull { it.y }?.y ?: 100f
        val ppmRange = (maxPPM - minPPM).coerceAtLeast(1f)

        // Usar los valores dinámicos para el eje ADC
        val adcRange = (maxADC - minADC).coerceAtLeast(1f)

        // Dibujar ejes
        drawLine(
            color = Color.Gray,
            start = Offset(leftPadding, padding),
            end = Offset(leftPadding, height - padding),
            strokeWidth = 1.dp.toPx()
        )

        drawLine(
            color = Color.Gray,
            start = Offset(leftPadding, height - padding),
            end = Offset(width - rightPadding, height - padding),
            strokeWidth = 1.dp.toPx()
        )

        // Dibujar eje Y secundario (derecho)
        drawLine(
            color = Color.Gray,
            start = Offset(width - rightPadding, padding),
            end = Offset(width - rightPadding, height - padding),
            strokeWidth = 1.dp.toPx()
        )

        // Filtrar los puntos para mostrar solo los del gas actual
        val filteredPpmPoints = ppmDataPoints.filter { it.gasType == currentGasType }

        // Dibujar línea de datos solo para el gas actual
        if (filteredPpmPoints.isNotEmpty()) {
            val path = Path()
            val points = filteredPpmPoints.mapIndexed { index, point ->
                // Escalar puntos al espacio del gráfico para PPM
                val xPosition = leftPadding + (index.toFloat() / (filteredPpmPoints.size - 1).coerceAtLeast(1)) * (width - leftPadding - rightPadding)
                val y = height - padding - ((point.y - minPPM) / ppmRange) * (height - 2 * padding)
                Offset(xPosition, y)
            }

            // Color según tipo de gas
            val lineColor = when (currentGasType) {
                "CO" -> Color.Blue
                "H2" -> Color.Green
                else -> Color.Gray
            }

            // Mover a primer punto
            if (points.isNotEmpty()) {
                path.moveTo(points.first().x, points.first().y)

                // Conectar resto de puntos
                for (i in 1 until points.size) {
                    path.lineTo(points[i].x, points[i].y)
                }

                // Dibujar la línea
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Dibujar puntos
                points.forEach { point ->
                    drawCircle(
                        color = lineColor,
                        radius = 3.dp.toPx(),
                        center = point
                    )
                }
            }
        }

        // Dibujar línea para ADC
        if (adcDataPoints.size > 1) {
            val path = Path()
            val points = adcDataPoints.mapIndexed { index, point ->
                // Escalar puntos al espacio del gráfico para ADC
                val x = leftPadding + (index.toFloat() / (adcDataPoints.size - 1)) * (width - leftPadding - rightPadding)
                // Usar el rango de ADC para calcular Y
                val normalizedValue = (point.y - minADC) / adcRange
                val y = height - padding - normalizedValue * (height - 2 * padding)
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
                color = Color.Red,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Dibujar puntos
            points.forEach { point ->
                drawCircle(
                    color = Color.Red,
                    radius = 3.dp.toPx(),
                    center = point
                )
            }
        }

        // Dibujar indicador de gas actual
        drawRect(
            color = when (currentGasType) {
                "CO" -> Color.Blue.copy(alpha = 0.1f)
                "H2" -> Color.Green.copy(alpha = 0.1f)
                else -> Color.Gray.copy(alpha = 0.1f)
            },
            topLeft = Offset(leftPadding + 10.dp.toPx(), padding + 10.dp.toPx()),
            size = Size(130.dp.toPx(), 25.dp.toPx())
        )

        drawContext.canvas.nativeCanvas.drawText(
            "Gas actual: $currentGasType",
            leftPadding + 15.dp.toPx(),
            padding + 25.dp.toPx(),
            android.graphics.Paint().apply {
                color = when (currentGasType) {
                    "CO" -> android.graphics.Color.BLUE
                    "H2" -> android.graphics.Color.GREEN
                    else -> android.graphics.Color.BLACK
                }
                textSize = 12.dp.toPx()
                isFakeBoldText = true
            }
        )

        // Dibujar etiquetas en el eje Y (izquierdo - PPM)
        val yLabelCount = 5
        for (i in 0..yLabelCount) {
            val yPos = height - padding - (i.toFloat() / yLabelCount) * (height - 2 * padding)
            val ppmValue = minPPM + (i.toFloat() / yLabelCount) * ppmRange

            // Línea de marca
            drawLine(
                color = Color.LightGray,
                start = Offset(leftPadding - 5.dp.toPx(), yPos),
                end = Offset(leftPadding, yPos),
                strokeWidth = 1.dp.toPx()
            )

            // Texto del valor PPM
            drawContext.canvas.nativeCanvas.drawText(
                String.format("%.1f", ppmValue),
                leftPadding - 10.dp.toPx(),
                yPos + 5.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 8.dp.toPx()
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
            )
        }

        // Dibujar etiquetas en el eje Y secundario (derecho - ADC)
        for (i in 0..yLabelCount) {
            val yPos = height - padding - (i.toFloat() / yLabelCount) * (height - 2 * padding)
            val adcValue = minADC + (i.toFloat() / yLabelCount) * adcRange

            // Línea de marca
            drawLine(
                color = Color.LightGray,
                start = Offset(width - rightPadding, yPos),
                end = Offset(width - rightPadding + 5.dp.toPx(), yPos),
                strokeWidth = 1.dp.toPx()
            )

            // Texto del valor ADC
            drawContext.canvas.nativeCanvas.drawText(
                adcValue.toInt().toString(),
                width - rightPadding + 7.dp.toPx(),
                yPos + 5.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 8.dp.toPx()
                    textAlign = android.graphics.Paint.Align.LEFT
                }
            )
        }

        // Etiquetas para los ejes
        drawContext.canvas.nativeCanvas.drawText(
            currentGasType + " (PPM)",
            leftPadding - 35.dp.toPx(),
            padding - 10.dp.toPx(),
            android.graphics.Paint().apply {
                color = when (currentGasType) {
                    "CO" -> android.graphics.Color.BLUE
                    "H2" -> android.graphics.Color.GREEN
                    else -> android.graphics.Color.DKGRAY
                }
                textSize = 10.dp.toPx()
                textAlign = android.graphics.Paint.Align.LEFT
            }
        )

        drawContext.canvas.nativeCanvas.drawText(
            "ADC",
            width - rightPadding + 5.dp.toPx(),
            padding - 10.dp.toPx(),
            android.graphics.Paint().apply {
                color = android.graphics.Color.RED
                textSize = 10.dp.toPx()
                textAlign = android.graphics.Paint.Align.LEFT
            }
        )
    }
}
@Preview
@Composable
fun SensorGasAppPreview() {
    // Esta es solo una vista previa y no funcional
}