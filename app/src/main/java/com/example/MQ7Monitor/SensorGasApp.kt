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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

// Colores para cada tipo de gas
val gasColors = mapOf(
    GasType.CO to Color(0xFF3366CC),       // Azul
    GasType.H2 to Color(0xFFDC3912),       // Rojo
    GasType.LPG to Color(0xFFFF9900),      // Naranja
    GasType.CH4 to Color(0xFF109618),      // Verde
    GasType.ALCOHOL to Color(0xFF990099),  // Morado
    GasType.UNKNOWN to Color.Gray         // Gris para gases desconocidos
)

@Composable
fun SensorGasApp(
    viewModel: GasSensorViewModel,
    onScanClick: () -> Unit
) {
    val isConnected by viewModel.isConnected
    val connectionStatus by viewModel.connectionStatus
    val isScanning by viewModel.isScanning
    val selectedDevice by viewModel.selectedDevice
    val currentGasType by viewModel.currentGasType

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
            text = "Monitor Multisensor de Gases",
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
                .height(120.dp)
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
                .padding(vertical = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Mostrar el tipo de gas actual
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Gas actual: ",
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = gasColors[currentGasType] ?: Color.Gray,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = currentGasType.name,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Datos del sensor
                val rawValue by viewModel.rawValue
                val voltage by viewModel.voltage
                val ppmValue by viewModel.ppmValue

                Text(text = "ADC: $rawValue")
                Text(text = "Voltaje: ${String.format("%.2f", voltage)} V")
                Text(text = "PPM: ${String.format("%.2f", ppmValue)} ppm")
            }
        }

        // Panel de selección de gases para ver gráficos
        GasTabSelector(
            viewModel = viewModel,
            currentGasType = currentGasType,
            modifier = Modifier.fillMaxWidth()
        )

        // Gráfico en tiempo real
        Text(
            text = "Gráfico en tiempo real:",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                .padding(8.dp)
        ) {
            // Mostrar gráfico del gas actual
            val gasDataPoints = viewModel.gasChartData[currentGasType] ?: mutableStateListOf()

            if (gasDataPoints.isNotEmpty() || viewModel.adcChartData.isNotEmpty()) {
                MultiGasLineChart(
                    ppmDataPoints = gasDataPoints,
                    adcDataPoints = viewModel.adcChartData,
                    gasType = currentGasType,
                    viewModel = viewModel
                )
            } else {
                Text(
                    text = "Esperando datos de ${currentGasType.name}...",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun GasTabSelector(
    viewModel: GasSensorViewModel,
    currentGasType: GasType,
    modifier: Modifier = Modifier
) {
    val scrollableTabRowState = rememberScrollState()

    Row(
        modifier = modifier
            .horizontalScroll(scrollableTabRowState)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GasType.values().forEach { gasType ->
            if (gasType != GasType.UNKNOWN) {
                val hasRecentData = viewModel.hasRecentData(gasType)
                val isSelected = gasType == currentGasType

                TabChip(
                    gasType = gasType,
                    isSelected = isSelected,
                    hasData = hasRecentData,
                    onClick = {
                        // Solo permitir cambiar a pestañas que tienen datos
                        if (hasRecentData || gasType == currentGasType) {
                            viewModel.gasChartData[gasType]?.let {
                                // Cambiar el estado del gas actual temporalmente para ver sus datos
                                // Esto es solo para la UI, no afecta los datos que se reciben
                                val field = viewModel::class.java.getDeclaredField("_currentGasType")
                                field.isAccessible = true
                                val mutableState = field.get(viewModel) as MutableState<GasType>
                                mutableState.value = gasType
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun TabChip(
    gasType: GasType,
    isSelected: Boolean,
    hasData: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        gasColors[gasType] ?: Color.Gray
    } else {
        Color.LightGray.copy(alpha = 0.5f)
    }

    val textColor = if (isSelected) {
        Color.White
    } else {
        if (hasData) Color.Black else Color.Gray
    }

    Surface(
        modifier = Modifier
            .clickable(enabled = hasData || isSelected) { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        shadowElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Text(
            text = gasType.name,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = textColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
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
fun MultiGasLineChart(
    ppmDataPoints: List<DataPoint>,
    adcDataPoints: List<DataPoint>,
    gasType: GasType,
    viewModel: GasSensorViewModel
) {
    if (ppmDataPoints.isEmpty() && adcDataPoints.isEmpty()) return

    // Obtener color específico para el gas actual
    val gasColor = gasColors[gasType] ?: Color.Blue

    // Obtener los valores min/max para el gas actual
    val (minPPM, maxPPM) = viewModel.getMinMaxPpmForGas(gasType)

    // Acceder al ViewModel para obtener los valores min/max de ADC
    val minADC = viewModel.minADCValue.value.toFloat()
    val maxADC = viewModel.maxADCValue.value.toFloat().coerceAtLeast(minADC + 1f)

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val width = size.width
        val height = size.height
        val padding = 16.dp.toPx()
        val leftPadding = 40.dp.toPx() // Padding para etiquetas
        val rightPadding = 40.dp.toPx() // Padding adicional para la escala derecha

        // Calcular rango de PPM
        val ppmMin = minPPM.toFloat()
        val ppmMax = maxPPM.toFloat()
        val ppmRange = (ppmMax - ppmMin).coerceAtLeast(1f)

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

        // Dibujar grid horizontal
        val yLabelCount = 5
        for (i in 0..yLabelCount) {
            val yPos = height - padding - (i.toFloat() / yLabelCount) * (height - 2 * padding)

            // Línea horizontal de grid
            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(leftPadding, yPos),
                end = Offset(width - rightPadding, yPos),
                strokeWidth = 0.5.dp.toPx()
            )
        }

        // Dibujar línea de datos PPM (específica para el gas)
        if (ppmDataPoints.size > 1) {
            val path = Path()
            val points = ppmDataPoints.mapIndexed { index, point ->
                // Escalar puntos al espacio del gráfico para PPM
                val x = leftPadding + (index.toFloat() / (ppmDataPoints.size - 1)) * (width - leftPadding - rightPadding)
                val normalizedValue = ((point.y - ppmMin) / ppmRange).coerceIn(0f, 1f)
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
                color = gasColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Dibujar puntos
            points.forEach { point ->
                drawCircle(
                    color = gasColor,
                    radius = 3.dp.toPx(),
                    center = point
                )
            }
        }

        // Dibujar línea de datos ADC
        if (adcDataPoints.size > 1) {
            val path = Path()
            val points = adcDataPoints.mapIndexed { index, point ->
                // Escalar puntos al espacio del gráfico para ADC
                val x = leftPadding + (index.toFloat() / (adcDataPoints.size - 1)) * (width - leftPadding - rightPadding)
                // Usar el rango de ADC para calcular Y
                val normalizedValue = ((point.y - minADC) / adcRange).coerceIn(0f, 1f)
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

        // Dibujar etiquetas en el eje Y (izquierdo - PPM)
        for (i in 0..yLabelCount) {
            val yPos = height - padding - (i.toFloat() / yLabelCount) * (height - 2 * padding)
            val ppmValue = ppmMin + (i.toFloat() / yLabelCount) * ppmRange

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
                leftPadding - 8.dp.toPx(),
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
            "${gasType.name} (PPM)",
            padding,
            padding - 10.dp.toPx(),
            android.graphics.Paint().apply {
                color = gasColor.toArgb()
                textSize = 10.dp.toPx()
                textAlign = android.graphics.Paint.Align.LEFT
                isFakeBoldText = true
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