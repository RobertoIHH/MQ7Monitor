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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
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
                        color = when (gasType) {
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
        // Reemplaza el Box del gráfico en SensorGasApp.kt con este código:

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
            LineChart(adcDataPoints = viewModel.adcChartData)
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
fun LineChart(adcDataPoints: List<DataPoint>) {
    // Acceder al ViewModel para obtener los valores y el tipo de gas actual
    val viewModel: GasSensorViewModel = viewModel()
    val currentGasType by viewModel.gasType

    // Seleccionar la lista de datos según el gas actual
    val ppmDataPoints = if (currentGasType == "CO") {
        viewModel.coPpmData.toList()
    } else {
        viewModel.h2PpmData.toList()
    }

    if (ppmDataPoints.isEmpty() && adcDataPoints.isEmpty()) {
        // Si no hay datos, no dibujamos nada
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Esperando datos...",
                modifier = Modifier.align(Alignment.Center),
                color = Color.Gray
            )
        }
        return
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val padding = 16.dp.toPx()
        val leftPadding = 30.dp.toPx()
        val rightPadding = 30.dp.toPx()
        val bottomPadding = 20.dp.toPx()
        val topPadding = 30.dp.toPx()

        // Calcular valores mínimos y máximos para el eje Y (PPM)
        var minPPM = 0f
        var maxPPM = if (currentGasType == "CO") 100f else 1000f

        if (ppmDataPoints.isNotEmpty()) {
            minPPM = ppmDataPoints.minOf { it.y }.coerceAtMost(0f)
            maxPPM = ppmDataPoints.maxOf { it.y }.coerceAtLeast(
                if (currentGasType == "CO") 100f else 1000f
            )
        }

        // Calcular valores mínimos y máximos para el eje Y (ADC)
        var minADC = 0f
        var maxADC = 4095f

        if (adcDataPoints.isNotEmpty()) {
            minADC = adcDataPoints.minOf { it.y }.coerceAtMost(0f)
            maxADC = adcDataPoints.maxOf { it.y }.coerceAtLeast(4095f)
        }

        // Asegurar que los rangos nunca sean cero
        val ppmRange = (maxPPM - minPPM).coerceAtLeast(1f)
        val adcRange = (maxADC - minADC).coerceAtLeast(1f)

        // Dibujar ejes
        drawLine(
            color = Color.Gray,
            start = Offset(leftPadding, topPadding),
            end = Offset(leftPadding, height - bottomPadding),
            strokeWidth = 1.dp.toPx()
        )

        drawLine(
            color = Color.Gray,
            start = Offset(leftPadding, height - bottomPadding),
            end = Offset(width - rightPadding, height - bottomPadding),
            strokeWidth = 1.dp.toPx()
        )

        // Eje derecho para ADC
        drawLine(
            color = Color.Gray,
            start = Offset(width - rightPadding, topPadding),
            end = Offset(width - rightPadding, height - bottomPadding),
            strokeWidth = 1.dp.toPx()
        )

        // Dibujar línea de datos de PPM
        if (ppmDataPoints.size > 1) {
            val path = Path()
            val points = mutableListOf<Offset>()

            for (i in ppmDataPoints.indices) {
                val point = ppmDataPoints[i]
                val x = leftPadding + (i.toFloat() / (ppmDataPoints.size - 1).coerceAtLeast(1)) * (width - leftPadding - rightPadding)
                val y = height - bottomPadding - ((point.y - minPPM) / ppmRange) * (height - topPadding - bottomPadding)
                points.add(Offset(x, y))
            }

            // Solo dibujar la línea si hay puntos
            if (points.isNotEmpty()) {
                path.moveTo(points.first().x, points.first().y)

                for (i in 1 until points.size) {
                    path.lineTo(points[i].x, points[i].y)
                }

                drawPath(
                    path = path,
                    color = if (currentGasType == "CO") Color.Blue else Color.Green,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )

                // Dibujar puntos
                for (point in points) {
                    drawCircle(
                        color = if (currentGasType == "CO") Color.Blue else Color.Green,
                        radius = 3.dp.toPx(),
                        center = point
                    )
                }
            }
        }

        // Dibujar línea de datos de ADC
        if (adcDataPoints.size > 1) {
            val path = Path()
            val points = mutableListOf<Offset>()

            for (i in adcDataPoints.indices) {
                val point = adcDataPoints[i]
                val x = leftPadding + (i.toFloat() / (adcDataPoints.size - 1).coerceAtLeast(1)) * (width - leftPadding - rightPadding)
                val y = height - bottomPadding - ((point.y - minADC) / adcRange) * (height - topPadding - bottomPadding)
                points.add(Offset(x, y))
            }

            // Solo dibujar la línea si hay puntos
            if (points.isNotEmpty()) {
                path.moveTo(points.first().x, points.first().y)

                for (i in 1 until points.size) {
                    path.lineTo(points[i].x, points[i].y)
                }

                drawPath(
                    path = path,
                    color = Color.Red,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )

                // Dibujar puntos
                for (point in points) {
                    drawCircle(
                        color = Color.Red,
                        radius = 3.dp.toPx(),
                        center = point
                    )
                }
            }
        }

        // Dibujar etiquetas en el eje Y (izquierdo - PPM)
        val yLabelCount = 5
        for (i in 0..yLabelCount) {
            val yPos = height - bottomPadding - (i.toFloat() / yLabelCount) * (height - topPadding - bottomPadding)
            val ppmValue = minPPM + (i.toFloat() / yLabelCount) * ppmRange

            // Marca en el eje
            drawLine(
                color = Color.LightGray,
                start = Offset(leftPadding - 3.dp.toPx(), yPos),
                end = Offset(leftPadding, yPos),
                strokeWidth = 1.dp.toPx()
            )

            // Texto del valor
            drawContext.canvas.nativeCanvas.drawText(
                String.format("%.0f", ppmValue),
                leftPadding - 5.dp.toPx(),
                yPos + 4.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 8.dp.toPx()
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
            )
        }

        // Dibujar etiquetas en el eje Y (derecho - ADC)
        for (i in 0..yLabelCount) {
            val yPos = height - bottomPadding - (i.toFloat() / yLabelCount) * (height - topPadding - bottomPadding)
            val adcValue = minADC + (i.toFloat() / yLabelCount) * adcRange

            // Marca en el eje
            drawLine(
                color = Color.LightGray,
                start = Offset(width - rightPadding, yPos),
                end = Offset(width - rightPadding + 3.dp.toPx(), yPos),
                strokeWidth = 1.dp.toPx()
            )

            // Texto del valor
            drawContext.canvas.nativeCanvas.drawText(
                String.format("%.0f", adcValue),
                width - rightPadding + 5.dp.toPx(),
                yPos + 4.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 8.dp.toPx()
                    textAlign = android.graphics.Paint.Align.LEFT
                }
            )
        }

        // Dibujar etiquetas de los ejes
        drawContext.canvas.nativeCanvas.drawText(
            currentGasType + " PPM",
            leftPadding,
            topPadding - 10.dp.toPx(),
            android.graphics.Paint().apply {
                color = if (currentGasType == "CO")
                    android.graphics.Color.BLUE
                else
                    android.graphics.Color.GREEN
                textSize = 10.dp.toPx()
                textAlign = android.graphics.Paint.Align.LEFT
                isFakeBoldText = true
            }
        )

        drawContext.canvas.nativeCanvas.drawText(
            "ADC",
            width - rightPadding - 20.dp.toPx(),
            topPadding - 10.dp.toPx(),
            android.graphics.Paint().apply {
                color = android.graphics.Color.RED
                textSize = 10.dp.toPx()
                textAlign = android.graphics.Paint.Align.LEFT
                isFakeBoldText = true
            }
        )

        // Dibujar el fondo para el indicador de gas actual
        val gasInfoBackgroundColor = when (currentGasType) {
            "CO" -> Color.Blue.copy(alpha = 0.1f)
            "H2" -> Color.Green.copy(alpha = 0.1f)
            else -> Color.Gray.copy(alpha = 0.1f)
        }

        // Dibujar un rectángulo redondeado
        drawRoundRect(
            color = gasInfoBackgroundColor,
            topLeft = Offset(leftPadding + 20.dp.toPx(), topPadding + 10.dp.toPx()),
            size = Size(170.dp.toPx(), 20.dp.toPx()),
            cornerRadius = CornerRadius(5.dp.toPx()),
            alpha = 0.8f
        )

        // Texto del gas actual
        drawContext.canvas.nativeCanvas.drawText(
            "Midiendo: $currentGasType",
            leftPadding + 25.dp.toPx(),
            topPadding + 25.dp.toPx(),
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
    }
}
@Preview
@Composable
fun SensorGasAppPreview() {
    // Esta es solo una vista previa y no funcional
}