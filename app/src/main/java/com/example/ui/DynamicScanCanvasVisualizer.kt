package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Maps signal strength (positive for metals/peaks, negative for cavities/dips)
 * to a smooth color gradient based on the selected geophysical palette.
 */
fun getSignalGradientColor(
    rawSignal: Float,
    maxAbsSignal: Float = 500f,
    palette: String = "MetalCavity"
): Color {
    val norm = (rawSignal / max(maxAbsSignal, 100f)).coerceIn(-1f, 1f)
    
    val norm01 = (norm + 1f) / 2f

    return when (palette) {
        "MetalCavity", "Classic" -> {
            when {
                // High Noble Metal Peak (Gold / Silver / Bronze Anomaly)
                norm > 0.75f -> {
                    val t = (norm - 0.75f) / 0.25f
                    interpolateColor(Color(0xFFFF9100), Color(0xFFFF1744), t)
                }
                norm > 0.40f -> {
                    val t = (norm - 0.40f) / 0.35f
                    interpolateColor(Color(0xFFFFD700), Color(0xFFFF9100), t)
                }
                norm > 0.15f -> {
                    val t = (norm - 0.15f) / 0.25f
                    interpolateColor(Color(0xFF76FF03), Color(0xFFFFD700), t)
                }
                // Neutral Soil Baseline
                norm in -0.15f..0.15f -> {
                    val t = (norm + 0.15f) / 0.30f
                    interpolateColor(Color(0xFF1B5E20), Color(0xFF2E7D32), t)
                }
                // Cavity / Void / Tunnel Dip
                norm > -0.40f -> {
                    val t = (-norm - 0.15f) / 0.25f
                    interpolateColor(Color(0xFF2E7D32), Color(0xFF00E5FF), t)
                }
                norm > -0.75f -> {
                    val t = (-norm - 0.40f) / 0.35f
                    interpolateColor(Color(0xFF00E5FF), Color(0xFF2979FF), t)
                }
                else -> { // Severe Void / Deep Cavity
                    val t = (-norm - 0.75f) / 0.25f
                    interpolateColor(Color(0xFF2979FF), Color(0xFFD500F9), t)
                }
            }
        }
        "Thermal" -> {
            // Standard Geophysical Infrared Rainbow
            when {
                norm01 > 0.85f -> interpolateColor(Color(0xFFFF0000), Color(0xFFFFFFFF), (norm01 - 0.85f) / 0.15f)
                norm01 > 0.65f -> interpolateColor(Color(0xFFFFFF00), Color(0xFFFF0000), (norm01 - 0.65f) / 0.20f)
                norm01 > 0.45f -> interpolateColor(Color(0xFF00FF00), Color(0xFFFFFF00), (norm01 - 0.45f) / 0.20f)
                norm01 > 0.25f -> interpolateColor(Color(0xFF00FFFF), Color(0xFF00FF00), (norm01 - 0.25f) / 0.20f)
                else -> interpolateColor(Color(0xFF00008B), Color(0xFF00FFFF), norm01 / 0.25f)
            }
        }
        "Grayscale" -> {
            Color(red = norm01, green = norm01, blue = norm01)
        }
        "OutdoorSunlight", "OutdoorHighContrast", "HighContrast", "Contrast" -> {
            when {
                norm > 0.70f -> interpolateColor(Color(0xFFFFD700), Color(0xFFFFFFFF), (norm - 0.70f) / 0.30f)
                norm > 0.20f -> interpolateColor(Color(0xFFFF9100), Color(0xFFFFD700), (norm - 0.20f) / 0.50f)
                norm < -0.70f -> interpolateColor(Color(0xFF00E5FF), Color(0xFFE040FB), (-norm - 0.70f) / 0.30f)
                norm < -0.20f -> interpolateColor(Color(0xFF2979FF), Color(0xFF00E5FF), (-norm - 0.20f) / 0.50f)
                else -> Color(0xFF0A0D14) // Pure ultra-dark pitch background for maximum sunlight contrast
            }
        }
        "IronOxide", "Copper", "Mineralized" -> {
            when {
                norm01 > 0.85f -> interpolateColor(Color(0xFFFFD700), Color(0xFFFFFFFF), (norm01 - 0.85f) / 0.15f)
                norm01 > 0.65f -> interpolateColor(Color(0xFFE65100), Color(0xFFFFD700), (norm01 - 0.65f) / 0.20f)
                norm01 > 0.40f -> interpolateColor(Color(0xFF8D6E63), Color(0xFFE65100), (norm01 - 0.40f) / 0.25f)
                norm01 > 0.20f -> interpolateColor(Color(0xFF3E2723), Color(0xFF8D6E63), (norm01 - 0.20f) / 0.20f)
                else -> interpolateColor(Color(0xFF1A0C08), Color(0xFF3E2723), norm01 / 0.20f)
            }
        }
        else -> { // Spectral
            when {
                norm01 > 0.80f -> interpolateColor(Color(0xFFFF1744), Color(0xFFFFD700), (norm01 - 0.80f) / 0.20f)
                norm01 > 0.60f -> interpolateColor(Color(0xFFFFD700), Color(0xFF00E676), (norm01 - 0.60f) / 0.20f)
                norm01 > 0.40f -> interpolateColor(Color(0xFF00E676), Color(0xFF00B0FF), (norm01 - 0.40f) / 0.20f)
                norm01 > 0.20f -> interpolateColor(Color(0xFF00B0FF), Color(0xFF651FFF), (norm01 - 0.20f) / 0.20f)
                else -> interpolateColor(Color(0xFF303F9F), Color(0xFF000000), 1f - norm01 / 0.20f)
            }
        }
    }
}

/**
 * Bilinear interpolation for smooth continuous sub-mesh rendering.
 */
fun interpolateScanValue(
    gridData: List<Float>,
    cols: Int,
    rows: Int,
    xFrac: Float,
    yFrac: Float
): Float {
    if (gridData.isEmpty() || cols <= 0 || rows <= 0) return 0f
    val x0 = xFrac.toInt().coerceIn(0, cols - 1)
    val x1 = (x0 + 1).coerceIn(0, cols - 1)
    val y0 = yFrac.toInt().coerceIn(0, rows - 1)
    val y1 = (y0 + 1).coerceIn(0, rows - 1)

    val tx = (xFrac - x0).coerceIn(0f, 1f)
    val ty = (yFrac - y0).coerceIn(0f, 1f)

    val v00 = gridData.getOrNull(y0 * cols + x0) ?: 0f
    val v10 = gridData.getOrNull(y0 * cols + x1) ?: 0f
    val v01 = gridData.getOrNull(y1 * cols + x0) ?: 0f
    val v11 = gridData.getOrNull(y1 * cols + x1) ?: 0f

    val top = v00 * (1f - tx) + v10 * tx
    val bottom = v01 * (1f - tx) + v11 * tx
    return top * (1f - ty) + bottom * ty
}

data class MeshQuad3D(
    val p1: Offset,
    val p2: Offset,
    val p3: Offset,
    val p4: Offset,
    val avgZ2: Float,
    val avgSignal: Float
)

/**
 * A Jetpack Compose Canvas visualization that renders incoming scan points dynamically with smooth
 * color gradient transitions for different metal anomalies and cavity signal strengths.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicScanCanvasVisualizer(
    activeScanData: List<Float>,
    gridWidth: Int,
    gridLength: Int,
    currentCol: Int,
    currentRow: Int,
    isScanActive: Boolean,
    scanPattern: String = "زیگزاگ (Zig-Zag)",
    viewModel: VisualizerViewModel? = null,
    modifier: Modifier = Modifier
) {
    var viewMode by remember { mutableStateOf("3D") } // "3D" or "2D"
    var selectedPalette by remember { mutableStateOf("MetalCavity") }
    var interpolationSubdivisions by remember { mutableStateOf(4) } // 4x smooth sub-mesh
    var showContourGrid by remember { mutableStateOf(true) }
    var showScanTrajectory by remember { mutableStateOf(true) }
    var touchedPointInfo by remember { mutableStateOf<String?>(null) }
    var touchedCoords by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    var localFlattenBaseEnabled by remember { mutableStateOf(true) }
    val vmFlattenEnabled = viewModel?.flattenBaseEnabled?.collectAsStateWithLifecycle()?.value
    val flattenBaseEnabled = vmFlattenEnabled ?: localFlattenBaseEnabled

    val vmGroundStats = viewModel?.groundPlaneStats?.collectAsStateWithLifecycle()?.value
    val groundPlaneStats = vmGroundStats ?: GroundPlaneStats()

    val (processedScanData, currentGroundStats) = remember(activeScanData, flattenBaseEnabled, gridWidth, gridLength) {
        if (flattenBaseEnabled && activeScanData.isNotEmpty()) {
            VisualizerViewModel.calculateAndApplyGroundPlaneFlattening(activeScanData, gridWidth, gridLength)
        } else {
            Pair(activeScanData, groundPlaneStats)
        }
    }

    // 3D Rendering Camera Controls
    var yaw by remember { mutableFloatStateOf(-30f) }
    var pitch by remember { mutableFloatStateOf(45f) }
    var zoom by remember { mutableFloatStateOf(1.0f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var zScale by remember { mutableFloatStateOf(1.2f) }
    var autoRotate by remember { mutableStateOf(false) }

    // Auto Rotation Loop for 3D Surface Mode
    LaunchedEffect(autoRotate) {
        if (autoRotate) {
            while (true) {
                delay(30)
                yaw = (yaw + 0.8f) % 360f
            }
        }
    }

    // Dynamic animation transitions for incoming scan pulse & radar laser sweep
    val infiniteTransition = rememberInfiniteTransition(label = "DynamicScanCanvas")
    
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 38f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseRadius"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseAlpha"
    )

    val radarSweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarSweep"
    )

    // Calculate maximum absolute signal strength in current scan for dynamic auto-scaling
    val maxAbsSignal = remember(activeScanData) {
        val maxVal = activeScanData.maxOfOrNull { abs(it) } ?: 300f
        if (maxVal < 100f) 300f else maxVal
    }

    // Scanned point count
    val totalPoints = gridWidth * gridLength
    val scannedPoints = remember(activeScanData, gridWidth, gridLength, currentCol, currentRow) {
        val isZigZag = scanPattern.contains("Zig-Zag") || scanPattern.contains("زیگزاگ")
        var count = 0
        for (y in 0 until gridLength) {
            val goingRight = !isZigZag || (y % 2 == 0)
            for (x in 0 until gridWidth) {
                val isPast = when {
                    y < currentRow -> true
                    y == currentRow -> if (goingRight) x <= currentCol else x >= currentCol
                    else -> false
                }
                if (isPast) count++
            }
        }
        min(count, totalPoints)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceBg)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        // TOP CONTROLS & MODE SELECTOR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isScanActive) CyberGold else CyberCyan)
                    )
                    Text(
                        text = if (viewMode == "3D") "رسم سه‌بعدی زنده سنسور (3D Coordinate Space)" else "نقشه گرادینت هموار زمین (2D Map)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (viewMode == "3D") "رسم لحظه‌ای پالس‌ها در فضای 3D با چرخش و تفکیک عمق" else "تفکیک فلزات گرانبها / حفره با تفکیک طیفی Canvas",
                    color = GrayText,
                    fontSize = 10.sp
                )
            }

            // Mode Selector Toggle (2D / 3D)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = viewMode == "3D",
                    onClick = { viewMode = "3D" },
                    label = { Text("سه‌بعدی (3D)", fontSize = 9.sp) },
                    leadingIcon = { Icon(Icons.Default.ViewInAr, contentDescription = null, modifier = Modifier.size(12.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberGold.copy(alpha = 0.25f),
                        selectedLabelColor = CyberGold,
                        containerColor = CardBg,
                        labelColor = Color.White
                    )
                )

                FilterChip(
                    selected = viewMode == "2D",
                    onClick = { viewMode = "2D" },
                    label = { Text("دوبعدی (2D)", fontSize = 9.sp) },
                    leadingIcon = { Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(12.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberCyan.copy(alpha = 0.25f),
                        selectedLabelColor = CyberCyan,
                        containerColor = CardBg,
                        labelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // PALETTE CHIPS & CAMERA CONTROLS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val palettes = listOf(
                "Thermal" to "حرارتی 🔥",
                "MetalCavity" to "فلز / حفره 🏆",
                "HighContrast" to "کنتراست بالا ⚡",
                "Grayscale" to "خاکستری 🔳",
                "IronOxide" to "معدنی 🧱"
            )
            palettes.forEach { (key, label) ->
                val isSelected = selectedPalette == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) CyberGold.copy(alpha = 0.25f) else CardBg)
                        .border(
                            BorderStroke(
                                1.dp,
                                if (isSelected) CyberGold else Color.White.copy(alpha = 0.08f)
                            ),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            selectedPalette = key
                            viewModel?.setColorPalette(key)
                        }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) CyberGold else Color.White,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            // Ground Base Flattening Toggle
            IconButton(
                onClick = {
                    val nextState = !flattenBaseEnabled
                    if (viewModel != null) {
                        viewModel.setFlattenBaseEnabled(nextState)
                    } else {
                        localFlattenBaseEnabled = nextState
                    }
                },
                modifier = Modifier
                    .size(32.dp)
                    .background(if (flattenBaseEnabled) CyberGold.copy(alpha = 0.3f) else CardBg, RoundedCornerShape(8.dp))
            ) {
                Icon(
                    Icons.Default.Compress,
                    contentDescription = "Flatten Base",
                    tint = if (flattenBaseEnabled) CyberGold else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (viewMode == "3D") {
                // Auto rotate toggle chip
                IconButton(
                    onClick = { autoRotate = !autoRotate },
                    modifier = Modifier
                        .size(32.dp)
                        .background(if (autoRotate) CyberCyan.copy(alpha = 0.3f) else CardBg, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        Icons.Default.RotateRight,
                        contentDescription = "Auto Rotate",
                        tint = if (autoRotate) CyberCyan else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Reset camera angle
                IconButton(
                    onClick = {
                        yaw = -30f
                        pitch = 45f
                        zoom = 1.0f
                        panX = 0f
                        panY = 0f
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .background(CardBg, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        Icons.Default.RestartAlt,
                        contentDescription = "Reset Angle",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // MAIN CANVAS SURFACE CONTAINER
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.2f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F1115))
                .border(BorderStroke(1.dp, CyberGold.copy(alpha = 0.25f)), RoundedCornerShape(12.dp))
                .pointerInput(viewMode) {
                    detectTransformGestures(panZoomLock = false) { centroid, pan, zoomChange, rotation ->
                        if (viewMode == "3D") {
                            if (zoomChange != 1.0f) {
                                zoom = (zoom * zoomChange).coerceIn(0.4f, 4.0f)
                                panX += pan.x
                                panY += pan.y
                            } else if (rotation != 0f && kotlin.math.abs(rotation) > 0.6f) {
                                yaw = (yaw + rotation) % 360f
                            } else {
                                yaw = (yaw - pan.x * 0.4f) % 360f
                                pitch = (pitch - pan.y * 0.4f).coerceIn(10f, 85f)
                            }
                        } else {
                            if (zoomChange != 1.0f) {
                                zoom = (zoom * zoomChange).coerceIn(0.5f, 4.0f)
                            }
                            panX += pan.x
                            panY += pan.y
                        }
                    }
                }
                .pointerInput(viewMode, gridWidth, gridLength, activeScanData, panX, panY, zoom) {
                    detectTapGestures { offset ->
                        val w = size.width
                        val h = size.height
                        val cellW = w / gridWidth
                        val cellH = h / gridLength
                        val tappedX = (((offset.x - panX) / cellW)).toInt().coerceIn(0, gridWidth - 1)
                        val tappedY = (((offset.y - panY) / cellH)).toInt().coerceIn(0, gridLength - 1)

                        val idx = tappedY * gridWidth + tappedX
                        val signal = activeScanData.getOrNull(idx) ?: 0f

                        val targetType = when {
                            signal > 150f -> "🏆 فلز باارزش / طلا (Peak: +${signal.toInt()} LSB)"
                            signal > 30f -> "🪙 فلز گرانبها / نقره (Peak: +${signal.toInt()} LSB)"
                            signal < -150f -> "🕳 حفره عمیق / دالان (Dip: ${signal.toInt()} LSB)"
                            signal < -30f -> "📦 حفره / فلز آهنی (Dip: ${signal.toInt()} LSB)"
                            else -> "🌱 خاک معمولی / خنثی (Val: ${signal.toInt()} LSB)"
                        }

                        touchedCoords = Pair(tappedX + 1, tappedY + 1)
                        touchedPointInfo = targetType
                    }
                }
        ) {
            val reusableQuadPath = remember { Path() }
            val meshQuads = remember { mutableListOf<MeshQuad3D>() }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val isZigZag = scanPattern.contains("Zig-Zag") || scanPattern.contains("زیگزاگ")

                if (viewMode == "3D") {
                    // =========================================================
                    // 3D COORDINATE SPACE SURFACE RENDERING ENGINE
                    // =========================================================
                    val radYaw = Math.toRadians(yaw.toDouble())
                    val radPitch = Math.toRadians(pitch.toDouble())
                    val cosY = cos(radYaw).toFloat()
                    val sinY = sin(radYaw).toFloat()
                    val cosP = cos(radPitch).toFloat()
                    val sinP = sin(radPitch).toFloat()

                    // Projection helper function mapping 3D world (X, Y, Z) to 2D Canvas Offset
                    fun projectPoint(gx: Float, gy: Float, gz: Float): Pair<Offset, Float> {
                        // Center origin (0,0) in grid unit coordinates [-1..1]
                        val ox = if (gridWidth > 1) (gx - (gridWidth - 1) / 2f) / ((gridWidth - 1) / 2f) else 0f
                        val oy = if (gridLength > 1) (gy - (gridLength - 1) / 2f) / ((gridLength - 1) / 2f) else 0f
                        val oz = (gz / maxAbsSignal).coerceIn(-1.2f, 1.2f) * 0.45f * zScale

                        // Rotate Yaw around Y
                        val x1 = ox * cosY - oz * sinY
                        val z1 = ox * sinY + oz * cosY

                        // Rotate Pitch around X
                        val y2 = oy * cosP - z1 * sinP
                        val z2 = oy * sinP + z1 * cosP

                        val cameraDistance = 3.2f
                        val projectionFactor = zoom * minOf(canvasWidth, canvasHeight) * 0.75f / (cameraDistance + z2)

                        val px = canvasWidth / 2f + x1 * projectionFactor + panX
                        val py = canvasHeight / 2f - y2 * projectionFactor + panY
                        return Pair(Offset(px, py), z2)
                    }

                    // 1. DRAW GROUND REFERENCE GRID PLANE (Z = 0)
                    val groundGridColor = Color.White.copy(alpha = 0.08f)
                    for (gy in 0 until gridLength) {
                        for (gx in 0 until gridWidth) {
                            val (p1, _) = projectPoint(gx.toFloat(), gy.toFloat(), 0f)
                            if (gx < gridWidth - 1) {
                                val (p2, _) = projectPoint((gx + 1).toFloat(), gy.toFloat(), 0f)
                                drawLine(groundGridColor, p1, p2, strokeWidth = 1f)
                            }
                            if (gy < gridLength - 1) {
                                val (p2, _) = projectPoint(gx.toFloat(), (gy + 1).toFloat(), 0f)
                                drawLine(groundGridColor, p1, p2, strokeWidth = 1f)
                            }
                        }
                    }

                    // 2. DRAW 3D COORDINATE AXES (X, Y, Z)
                    val origin3D = projectPoint(0f, 0f, 0f).first
                    val xAxisPoint = projectPoint((gridWidth - 1).toFloat(), 0f, 0f).first
                    val yAxisPoint = projectPoint(0f, (gridLength - 1).toFloat(), 0f).first
                    val zAxisPeak = projectPoint(0f, 0f, maxAbsSignal * 0.8f).first
                    val zAxisDip = projectPoint(0f, 0f, -maxAbsSignal * 0.8f).first

                    // X Axis (Columns - Cyan)
                    drawLine(CyberCyan, origin3D, xAxisPoint, strokeWidth = 2.5f)
                    // Y Axis (Rows - Gold)
                    drawLine(CyberGold, origin3D, yAxisPoint, strokeWidth = 2.5f)
                    // Z Axis (Signal Height / Depth - Lime Peak / Purple Dip)
                    drawLine(Color(0xFF00E676), origin3D, zAxisPeak, strokeWidth = 2.5f)
                    drawLine(Color(0xFFD500F9), origin3D, zAxisDip, strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f))

                    // 3. DRAW SHADED 3D SURFACE MESH QUADS FOR SCANNED DATA
                    meshQuads.clear()

                    for (gy in 0 until gridLength - 1) {
                        for (gx in 0 until gridWidth - 1) {
                            val idx1 = gy * gridWidth + gx
                            val idx2 = gy * gridWidth + (gx + 1)
                            val idx3 = (gy + 1) * gridWidth + (gx + 1)
                            val idx4 = (gy + 1) * gridWidth + gx

                            // Verify if all 4 corners have been scanned
                            fun isPointScanned(px: Int, py: Int): Boolean {
                                val goingRight = !isZigZag || (py % 2 == 0)
                                return when {
                                    py < currentRow -> true
                                    py == currentRow -> if (goingRight) px <= currentCol else px >= currentCol
                                    else -> false
                                }
                            }

                            if (isPointScanned(gx, gy) && isPointScanned(gx + 1, gy) &&
                                isPointScanned(gx + 1, gy + 1) && isPointScanned(gx, gy + 1)) {

                                val v1 = processedScanData.getOrNull(idx1) ?: 0f
                                val v2 = processedScanData.getOrNull(idx2) ?: 0f
                                val v3 = processedScanData.getOrNull(idx3) ?: 0f
                                val v4 = processedScanData.getOrNull(idx4) ?: 0f

                                val (proj1, z21) = projectPoint(gx.toFloat(), gy.toFloat(), v1)
                                val (proj2, z22) = projectPoint((gx + 1).toFloat(), gy.toFloat(), v2)
                                val (proj3, z23) = projectPoint((gx + 1).toFloat(), (gy + 1).toFloat(), v3)
                                val (proj4, z24) = projectPoint(gx.toFloat(), (gy + 1).toFloat(), v4)

                                val avgZ2 = (z21 + z22 + z23 + z24) / 4f
                                val avgSig = (v1 + v2 + v3 + v4) / 4f

                                meshQuads.add(MeshQuad3D(proj1, proj2, proj3, proj4, avgZ2, avgSig))
                            }
                        }
                    }

                    // Painter's algorithm depth sort (draw furthest quads first)
                    meshQuads.sortByDescending { it.avgZ2 }

                    meshQuads.forEach { quad ->
                        reusableQuadPath.reset()
                        reusableQuadPath.moveTo(quad.p1.x, quad.p1.y)
                        reusableQuadPath.lineTo(quad.p2.x, quad.p2.y)
                        reusableQuadPath.lineTo(quad.p3.x, quad.p3.y)
                        reusableQuadPath.lineTo(quad.p4.x, quad.p4.y)
                        reusableQuadPath.close()

                        val baseColor = getSignalGradientColor(quad.avgSignal, maxAbsSignal, selectedPalette)
                        drawPath(path = reusableQuadPath, color = baseColor.copy(alpha = 0.85f))
                        drawPath(path = reusableQuadPath, color = Color.White.copy(alpha = 0.18f), style = Stroke(width = 1f))
                    }

                    // 4. DRAW 3D VERTEX NODES
                    for (gy in 0 until gridLength) {
                        val goingRight = !isZigZag || (gy % 2 == 0)
                        for (gx in 0 until gridWidth) {
                            val isScanned = when {
                                gy < currentRow -> true
                                gy == currentRow -> if (goingRight) gx <= currentCol else gx >= currentCol
                                else -> false
                            }

                            if (isScanned) {
                                val idx = gy * gridWidth + gx
                                val signalVal = processedScanData.getOrNull(idx) ?: 0f
                                val (nodePos, _) = projectPoint(gx.toFloat(), gy.toFloat(), signalVal)

                                val dotColor = when {
                                    signalVal > 120f -> Color(0xFFFF1744)
                                    signalVal < -120f -> Color(0xFF00E5FF)
                                    else -> CyberGold
                                }

                                drawCircle(
                                    color = dotColor,
                                    radius = if (abs(signalVal) > 150f) 3.5f else 2f,
                                    center = nodePos
                                )
                            }
                        }
                    }

                    // 5. ANIMATED ACTIVE 3D SENSOR SCANNER PULSE BEACON
                    if (isScanActive && currentCol in 0 until gridWidth && currentRow in 0 until gridLength) {
                        val activeIdx = currentRow * gridWidth + currentCol
                        val curSignal = processedScanData.getOrNull(activeIdx) ?: 0f

                        val (beaconPos3D, _) = projectPoint(currentCol.toFloat(), currentRow.toFloat(), curSignal)
                        val (beaconBase3D, _) = projectPoint(currentCol.toFloat(), currentRow.toFloat(), 0f)

                        // Vertical Drop line from 3D peak/dip down to ground plane
                        drawLine(
                            color = CyberGold,
                            start = beaconBase3D,
                            end = beaconPos3D,
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                        )

                        // Pulsing target beacon ring in 3D
                        drawCircle(
                            color = CyberGold.copy(alpha = pulseAlpha),
                            radius = pulseRadius * 0.8f,
                            center = beaconPos3D,
                            style = Stroke(width = 2.5f)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 5f,
                            center = beaconPos3D
                        )
                    }

                } else {
                    // =========================================================
                    // 2D CONTINUOUS BILINEAR GRADIENT MATRIX
                    // =========================================================
                    val cellWidth = canvasWidth / gridWidth
                    val cellHeight = canvasHeight / gridLength

                    val subCols = (gridWidth - 1) * interpolationSubdivisions
                    val subRows = (gridLength - 1) * interpolationSubdivisions

                    val subWidth = canvasWidth / max(subCols, 1)
                    val subHeight = canvasHeight / max(subRows, 1)

                    // Render fine interpolated quads across the grid
                    for (sy in 0 until subRows) {
                        val yFrac = sy.toFloat() / interpolationSubdivisions
                        for (sx in 0 until subCols) {
                            val xFrac = sx.toFloat() / interpolationSubdivisions

                            val gridCellY = yFrac.toInt().coerceIn(0, gridLength - 1)
                            val gridCellX = xFrac.toInt().coerceIn(0, gridWidth - 1)

                            val goingRight = !isZigZag || (gridCellY % 2 == 0)
                            val isScanned = when {
                                gridCellY < currentRow -> true
                                gridCellY == currentRow -> if (goingRight) gridCellX <= currentCol else gridCellX >= currentCol
                                else -> false
                            }

                            val left = sx * subWidth
                            val top = sy * subHeight

                            if (isScanned) {
                                val interpolatedSignal = interpolateScanValue(
                                    processedScanData,
                                    gridWidth,
                                    gridLength,
                                    xFrac,
                                    yFrac
                                )
                                val quadColor = getSignalGradientColor(
                                    interpolatedSignal,
                                    maxAbsSignal,
                                    selectedPalette
                                )

                                drawRect(
                                    color = quadColor,
                                    topLeft = Offset(left, top),
                                    size = Size(subWidth + 0.5f, subHeight + 0.5f)
                                )
                            } else {
                                drawRect(
                                    color = Color(0xFF1B1E22),
                                    topLeft = Offset(left, top),
                                    size = Size(subWidth + 0.5f, subHeight + 0.5f)
                                )
                            }
                        }
                    }

                    // 2. DRAW CONTOUR GRID LINES & NODE DOTS
                    if (showContourGrid) {
                        val gridColor = Color.White.copy(alpha = 0.12f)
                        val strokeWidth = 1f

                        for (x in 0..gridWidth) {
                            val xPos = x * cellWidth
                            drawLine(color = gridColor, start = Offset(xPos, 0f), end = Offset(xPos, canvasHeight), strokeWidth = strokeWidth)
                        }

                        for (y in 0..gridLength) {
                            val yPos = y * cellHeight
                            drawLine(color = gridColor, start = Offset(0f, yPos), end = Offset(canvasWidth, yPos), strokeWidth = strokeWidth)
                        }
                    }

                    // 3. ANIMATED INCOMING SCAN POINT PULSE & TARGET CROSSHAIR
                    if (isScanActive && currentCol in 0 until gridWidth && currentRow in 0 until gridLength) {
                        val curX = (currentCol + 0.5f) * cellWidth
                        val curY = (currentRow + 0.5f) * cellHeight

                        drawCircle(color = CyberGold.copy(alpha = pulseAlpha), radius = pulseRadius, center = Offset(curX, curY), style = Stroke(width = 3f))
                        drawCircle(color = CyberGold.copy(alpha = 0.8f), radius = 8f, center = Offset(curX, curY))
                    }
                }
            }

            // OVERLAY: SIGNAL GRADIENT SCALE BAR (RIGHT SIDE)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .width(18.dp)
                    .height(130.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)), RoundedCornerShape(6.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    getSignalGradientColor(maxAbsSignal, maxAbsSignal, selectedPalette),
                                    getSignalGradientColor(maxAbsSignal * 0.5f, maxAbsSignal, selectedPalette),
                                    getSignalGradientColor(0f, maxAbsSignal, selectedPalette),
                                    getSignalGradientColor(-maxAbsSignal * 0.5f, maxAbsSignal, selectedPalette),
                                    getSignalGradientColor(-maxAbsSignal, maxAbsSignal, selectedPalette)
                                )
                            )
                        )
                )
            }

            // OVERLAY: 3D ANGLE HUD BADGE
            if (viewMode == "3D") {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "زاویه Yaw: ${yaw.toInt()}° | Pitch: ${pitch.toInt()}°",
                        color = CyberGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // OVERLAY: TOUCH INSPECTION POPUP
            if (touchedPointInfo != null && touchedCoords != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg.copy(alpha = 0.92f)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CyberGold),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column {
                            Text(
                                text = "موقعیت: ستون ${touchedCoords!!.first} ، سطر ${touchedCoords!!.second}",
                                color = CyberGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = touchedPointInfo!!,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(
                            onClick = { touchedPointInfo = null },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // BOTTOM HUD: VISUAL SIGNAL GRADIENT LEGEND
        val touchedSignalVal = touchedCoords?.let { (cx, cy) ->
            val idx = (cy - 1) * gridWidth + (cx - 1)
            activeScanData.getOrNull(idx)
        }
        SignalGradientLegendCard(
            maxAbsSignal = maxAbsSignal,
            selectedSignalValue = touchedSignalVal,
            colorPalette = selectedPalette,
            isExpandedDefault = false,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
