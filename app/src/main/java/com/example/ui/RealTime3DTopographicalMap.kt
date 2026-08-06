package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hardware.ConnectionMode
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real-Time 3D Topographical Surface Map Component.
 *
 * Renders real-time incoming sensor stream data (ADC, Phase, Magnetometer) as an interactive
 * 3D topographical surface map using custom perspective projection math, dynamic lighting,
 * contour elevation lines, and touch gesture camera rotation.
 */
@Composable
fun RealTime3DTopographicalMap(
    viewModel: VisualizerViewModel,
    modifier: Modifier = Modifier
) {
    val sensorManager = viewModel.sensorManager
    val adcValue by sensorManager.adcValue.collectAsStateWithLifecycle()
    val phaseShift by sensorManager.phaseShift.collectAsStateWithLifecycle()
    val signalStrength by sensorManager.signalStrength.collectAsStateWithLifecycle()
    val depthMeters by sensorManager.depthMeters.collectAsStateWithLifecycle()
    val compassHeading by sensorManager.compassHeading.collectAsStateWithLifecycle()
    val connectionState by sensorManager.connectionState.collectAsStateWithLifecycle()
    val connectionStatus by sensorManager.tvStatus.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Topography Grid Configuration (16x16 streaming mesh)
    val gridCols = 16
    val gridRows = 16
    val totalPoints = gridCols * gridRows

    // 2D Matrix buffer for real-time sensor stream
    val streamBuffer = remember { mutableStateListOf<Float>().apply { repeat(totalPoints) { add(0f) } } }
    var currentInsertIdx by remember { mutableIntStateOf(0) }

    // Display / Rendering settings
    var yaw by remember { mutableFloatStateOf(-35f) }
    var pitch by remember { mutableFloatStateOf(45f) }
    var zoom by remember { mutableFloatStateOf(1.0f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var zScale by remember { mutableFloatStateOf(1.2f) }
    var renderStyle by remember { mutableStateOf("Solid") } // Solid, Wireframe, Contour, Heatmap
    var colorPalette by remember { mutableStateOf("Thermal") } // Thermal, Copper, Grayscale
    var autoRotate by remember { mutableStateOf(false) }
    var showContourLines by remember { mutableStateOf(true) }
    var isPaused by remember { mutableStateOf(false) }
    var selectedNodeIndex by remember { mutableStateOf<Int?>(null) }
    var showExportMenu by remember { mutableStateOf(false) }

    // Auto-rotation loop
    LaunchedEffect(autoRotate) {
        if (autoRotate) {
            while (true) {
                delay(30)
                yaw = (yaw + 0.6f) % 360f
            }
        }
    }

    // Stream ingestion: Update rolling grid buffer as live sensor values arrive
    LaunchedEffect(adcValue, isPaused) {
        if (!isPaused && adcValue != 0) {
            // Map ADC value to signed anomaly amplitude (-500 to +800 LSB centered around baseline)
            val signedVal = if (phaseShift > 20) {
                adcValue.toFloat() * 1.1f // Metal peak
            } else if (phaseShift < -15) {
                -adcValue.toFloat() * 0.9f // Cavity dip
            } else {
                (adcValue - 400).toFloat()
            }

            streamBuffer[currentInsertIdx] = signedVal
            currentInsertIdx = (currentInsertIdx + 1) % totalPoints
        }
    }

    val flattenBaseEnabled by viewModel.flattenBaseEnabled.collectAsStateWithLifecycle()

    val (displayBuffer, groundStats) = remember(streamBuffer.toList(), flattenBaseEnabled) {
        if (flattenBaseEnabled) {
            val rawList = streamBuffer.toList()
            viewModel.calculateAndApplyGroundPlaneFlattening(rawList, gridCols, gridRows)
        } else {
            Pair(streamBuffer.toList(), GroundPlaneStats())
        }
    }

    // Max absolute elevation in buffer for normalization
    val maxElevation = remember(displayBuffer) {
        val maxAbs = displayBuffer.maxOfOrNull { abs(it) } ?: 100f
        if (maxAbs < 10f) 100f else maxAbs
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceBg),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBg),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Status & Live Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isPaused) Color.Yellow else CyberCyan)
                        )
                        Text(
                            text = "نقشه توپوگرافی سه‌بعدی زنده",
                            color = CyberGold,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Real-Time Topographical Sensor Stream (16x16 Grid)",
                        color = GrayText,
                        fontSize = 10.sp
                    )
                }

                // Action Controls
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Flatten Base Toggle
                    IconButton(
                        onClick = { viewModel.setFlattenBaseEnabled(!flattenBaseEnabled) },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (flattenBaseEnabled) CyberGold.copy(alpha = 0.2f) else CardBg
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compress,
                            contentDescription = "Flatten Base",
                            tint = if (flattenBaseEnabled) CyberGold else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Pause/Resume
                    IconButton(
                        onClick = { isPaused = !isPaused },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = CardBg),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = "Pause/Resume",
                            tint = if (isPaused) Color.Yellow else CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Auto Rotate Toggle
                    IconButton(
                        onClick = { autoRotate = !autoRotate },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (autoRotate) CyberCyan.copy(alpha = 0.2f) else CardBg
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Autorenew,
                            contentDescription = "Auto Rotate",
                            tint = if (autoRotate) CyberCyan else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Clear Buffer
                    IconButton(
                        onClick = {
                            for (i in streamBuffer.indices) streamBuffer[i] = 0f
                            currentInsertIdx = 0
                            selectedNodeIndex = null
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = CardBg),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Buffer",
                            tint = Color.Red,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Export Menu (CSV / JSON)
                    Box {
                        IconButton(
                            onClick = { showExportMenu = true },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = CardBg),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Export Data",
                                tint = CyberGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false },
                            modifier = Modifier.background(CardBg)
                        ) {
                            DropdownMenuItem(
                                text = { Text("خروجی CSV (جدول و ماتریس)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    showExportMenu = false
                                    val streamRecord = com.example.data.ScanRecord(
                                        name = "Live Topo Map Stream",
                                        timestamp = System.currentTimeMillis(),
                                        width = gridCols,
                                        length = gridRows,
                                        soilType = "Real-Time Sensor Stream",
                                        scanPattern = "RealTime",
                                        notes = "Live 3D topographical surface map snapshot",
                                        gridDataJson = "[${streamBuffer.joinToString(",")}]"
                                    )
                                    exportScanRecord(context, streamRecord, "csv")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("خروجی JSON (دیتا بیس دسکتاپ)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    showExportMenu = false
                                    val streamRecord = com.example.data.ScanRecord(
                                        name = "Live Topo Map Stream",
                                        timestamp = System.currentTimeMillis(),
                                        width = gridCols,
                                        length = gridRows,
                                        soilType = "Real-Time Sensor Stream",
                                        scanPattern = "RealTime",
                                        notes = "Live 3D topographical surface map snapshot",
                                        gridDataJson = "[${streamBuffer.joinToString(",")}]"
                                    )
                                    exportScanRecord(context, streamRecord, "json")
                                }
                            )
                        }
                    }
                }
            }

            // Real-Time 3D Topographical Canvas Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(14.dp))
                    .pointerInput(Unit) {
                        detectTransformGestures(panZoomLock = false) { centroid, pan, zoomChange, rotation ->
                            if (zoomChange != 1.0f) {
                                zoom = (zoom * zoomChange).coerceIn(0.5f, 3.5f)
                                panX += pan.x
                                panY += pan.y
                            } else if (rotation != 0f && kotlin.math.abs(rotation) > 0.6f) {
                                yaw = (yaw + rotation) % 360f
                            } else {
                                yaw = (yaw - pan.x * 0.4f) % 360f
                                pitch = (pitch - pan.y * 0.4f).coerceIn(10f, 85f)
                            }
                        }
                    }
                    .pointerInput(displayBuffer, yaw, pitch, zoom, panX, panY, zScale) {
                        detectTapGestures { pressOffset ->
                            val canvasWidth = size.width.toFloat()
                            val canvasHeight = size.height.toFloat()

                            val radYaw = Math.toRadians(yaw.toDouble())
                            val radPitch = Math.toRadians(pitch.toDouble())
                            val cosY = cos(radYaw).toFloat()
                            val sinY = sin(radYaw).toFloat()
                            val cosP = cos(radPitch).toFloat()
                            val sinP = sin(radPitch).toFloat()

                            var closestIdx = -1
                            var minDistance = Float.MAX_VALUE

                            for (cy in 0 until gridRows) {
                                for (cx in 0 until gridCols) {
                                    val ox = (cx - (gridCols - 1) / 2f) / ((gridCols - 1) / 2f)
                                    val oy = (cy - (gridRows - 1) / 2f) / ((gridRows - 1) / 2f)

                                    val idx = cy * gridCols + cx
                                    val rawVal = displayBuffer.getOrElse(idx) { 0f }
                                    val normVal = rawVal / maxElevation
                                    val oz = normVal * 0.45f * zScale

                                    val x1 = ox * cosY - oz * sinY
                                    val z1 = ox * sinY + oz * cosY
                                    val y2 = oy * cosP - z1 * sinP
                                    val z2 = oy * sinP + z1 * cosP

                                    val cameraDistance = 3.2f
                                    val projectionFactor = zoom * minOf(canvasWidth, canvasHeight) * 0.8f / (cameraDistance + z2)

                                    val px = canvasWidth / 2f + x1 * projectionFactor + panX
                                    val py = canvasHeight / 2f - y2 * projectionFactor + panY

                                    val dx = px - pressOffset.x
                                    val dy = py - pressOffset.y
                                    val dist = sqrt(dx * dx + dy * dy)
                                    if (dist < minDistance) {
                                        minDistance = dist
                                        closestIdx = idx
                                    }
                                }
                            }

                            if (minDistance < 40f) {
                                selectedNodeIndex = closestIdx
                            } else {
                                selectedNodeIndex = null
                            }
                        }
                    }
            ) {
                val reusablePath = remember { Path() }
                val cameraPoints = remember { ArrayList<Point3D>(totalPoints) }
                val projectedPoints = remember { ArrayList<Point2D>(totalPoints) }
                val cagePoints = remember { ArrayList<Point2D>() }
                val quads = remember { ArrayList<QuadPolygon>() }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    // 1. Convert 3D Grid points to Camera & Screen Space
                    cameraPoints.clear()
                    projectedPoints.clear()

                    val radYaw = Math.toRadians(yaw.toDouble())
                    val radPitch = Math.toRadians(pitch.toDouble())
                    val cosY = cos(radYaw).toFloat()
                    val sinY = sin(radYaw).toFloat()
                    val cosP = cos(radPitch).toFloat()
                    val sinP = sin(radPitch).toFloat()

                    for (cy in 0 until gridRows) {
                        for (cx in 0 until gridCols) {
                            val ox = (cx - (gridCols - 1) / 2f) / ((gridCols - 1) / 2f)
                            val oy = (cy - (gridRows - 1) / 2f) / ((gridRows - 1) / 2f)

                            val idx = cy * gridCols + cx
                            val rawVal = displayBuffer.getOrElse(idx) { 0f }
                            val normVal = rawVal / maxElevation

                            val oz = if (renderStyle == "Heatmap") 0f else (normVal * 0.45f * zScale)

                            // Rotate Yaw (Y axis)
                            val x1 = ox * cosY - oz * sinY
                            val z1 = ox * sinY + oz * cosY

                            // Rotate Pitch (X axis)
                            val y2 = oy * cosP - z1 * sinP
                            val z2 = oy * sinP + z1 * cosP

                            cameraPoints.add(Point3D(x1, y2, z2))

                            // Perspective projection
                            val cameraDistance = 3.2f
                            val projectionFactor = zoom * minOf(canvasWidth, canvasHeight) * 0.8f / (cameraDistance + z2)

                            val px = canvasWidth / 2f + x1 * projectionFactor + panX
                            val py = canvasHeight / 2f - y2 * projectionFactor + panY

                            projectedPoints.add(Point2D(px, py))
                        }
                    }

                    // 2. Build Wire Cage Base (Reference Frame)
                    val cageX = listOf(-1.1f, 1.1f)
                    val cageY = listOf(-1.1f, 1.1f)
                    val cageZ = listOf(-0.25f * zScale, 0.5f * zScale)
                    cagePoints.clear()

                    for (cx in cageX) {
                        for (cy in cageY) {
                            for (cz in cageZ) {
                                val x1 = cx * cosY - cz * sinY
                                val z1 = cx * sinY + cz * cosY
                                val y2 = cy * cosP - z1 * sinP
                                val z2 = cy * sinP + z1 * cosP
                                val projectionFactor = zoom * minOf(canvasWidth, canvasHeight) * 0.8f / (3.2f + z2)
                                val px = canvasWidth / 2f + x1 * projectionFactor + panX
                                val py = canvasHeight / 2f - y2 * projectionFactor + panY
                                cagePoints.add(Point2D(px, py))
                            }
                        }
                    }

                    val cageConnections = listOf(
                        Pair(0, 1), Pair(2, 3), Pair(4, 5), Pair(6, 7),
                        Pair(0, 2), Pair(2, 6), Pair(6, 4), Pair(4, 0),
                        Pair(1, 3), Pair(3, 7), Pair(7, 5), Pair(5, 1)
                    )

                    for (conn in cageConnections) {
                        val cp1 = cagePoints.getOrNull(conn.first)
                        val cp2 = cagePoints.getOrNull(conn.second)
                        if (cp1 != null && cp2 != null) {
                            drawLine(
                                color = Color(0x2200E5FF),
                                start = Offset(cp1.x, cp1.y),
                                end = Offset(cp2.x, cp2.y),
                                strokeWidth = 1f
                            )
                        }
                    }

                    // 3. Assemble and depth-sort quad polygons (Back-to-Front Painter's Algorithm)
                    quads.clear()
                    for (cy in 0 until gridRows - 1) {
                        for (cx in 0 until gridCols - 1) {
                            val i1 = cy * gridCols + cx
                            val i2 = cy * gridCols + (cx + 1)
                            val i3 = (cy + 1) * gridCols + (cx + 1)
                            val i4 = (cy + 1) * gridCols + cx

                            val avgZ = (cameraPoints[i1].z + cameraPoints[i2].z + cameraPoints[i3].z + cameraPoints[i4].z) / 4f
                            quads.add(QuadPolygon(i1, i2, i3, i4, avgZ, cx, cy))
                        }
                    }

                    quads.sortByDescending { it.avgZ }

                    // 4. Render Quad Topographical Surface
                    quads.forEach { quad ->
                        val p1 = projectedPoints[quad.i1]
                        val p2 = projectedPoints[quad.i2]
                        val p3 = projectedPoints[quad.i3]
                        val p4 = projectedPoints[quad.i4]

                        val h1 = streamBuffer.getOrElse(quad.i1) { 0f }
                        val h2 = streamBuffer.getOrElse(quad.i2) { 0f }
                        val h3 = streamBuffer.getOrElse(quad.i3) { 0f }
                        val h4 = streamBuffer.getOrElse(quad.i4) { 0f }
                        val avgHeight = (h1 + h2 + h3 + h4) / 4f
                        val normHeight = (avgHeight / maxElevation).coerceIn(-1f, 1f)

                        // Hill Shading Light Calculation (Slope vectors)
                        val slopeX = (cameraPoints[quad.i2].z - cameraPoints[quad.i1].z) + (cameraPoints[quad.i3].z - cameraPoints[quad.i4].z)
                        val slopeY = (cameraPoints[quad.i4].z - cameraPoints[quad.i1].z) + (cameraPoints[quad.i3].z - cameraPoints[quad.i2].z)
                        val lightFactor = (1.0f + (slopeX * 0.35f + slopeY * 0.35f)).coerceIn(0.6f, 1.4f)

                        val baseColor = getTargetColor(normHeight, threshold = 0.05f, palette = colorPalette)
                        val finalColor = Color(
                            red = (baseColor.red * lightFactor).coerceIn(0f, 1f),
                            green = (baseColor.green * lightFactor).coerceIn(0f, 1f),
                            blue = (baseColor.blue * lightFactor).coerceIn(0f, 1f),
                            alpha = if (renderStyle == "Wireframe") 0.3f else 0.9f
                        )

                        reusablePath.reset()
                        reusablePath.moveTo(p1.x, p1.y)
                        reusablePath.lineTo(p2.x, p2.y)
                        reusablePath.lineTo(p3.x, p3.y)
                        reusablePath.lineTo(p4.x, p4.y)
                        reusablePath.close()

                        if (renderStyle != "Wireframe") {
                            drawPath(path = reusablePath, color = finalColor)
                        }

                        // Surface Wireframe overlay for topographical grid definition
                        val gridLineColor = if (renderStyle == "Wireframe") {
                            getTargetColor(normHeight, threshold = 0.05f, palette = colorPalette)
                        } else {
                            Color.Black.copy(alpha = 0.25f)
                        }
                        drawPath(
                            path = reusablePath,
                            color = gridLineColor,
                            style = Stroke(width = if (renderStyle == "Wireframe") 1.8f else 1f)
                        )
                    }

                    // 5. Draw Active Pulse Sweep Marker (Current Ingestion Cell)
                    val activePt = projectedPoints.getOrNull(currentInsertIdx)
                    if (activePt != null) {
                        drawCircle(
                            color = CyberCyan,
                            radius = 6f,
                            center = Offset(activePt.x, activePt.y)
                        )
                        drawCircle(
                            color = CyberCyan.copy(alpha = 0.4f),
                            radius = 14f,
                            center = Offset(activePt.x, activePt.y),
                            style = Stroke(width = 2f)
                        )
                    }

                    // 6. Highlight Selected Node
                    if (selectedNodeIndex != null) {
                        val pt = projectedPoints.getOrNull(selectedNodeIndex!!)
                        if (pt != null) {
                            drawCircle(
                                color = Color.White,
                                radius = 7f,
                                center = Offset(pt.x, pt.y)
                            )
                            drawCircle(
                                color = CyberGold,
                                radius = 15f,
                                center = Offset(pt.x, pt.y),
                                style = Stroke(width = 2f)
                            )
                        }
                    }

                    // 7. Draw 3D Orientation Axis Compass Widget
                    draw3DCompass(canvasWidth, canvasHeight, cosY, sinY, cosP, sinP)
                }

                // Overlay Info HUD on Canvas Top-Left
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "جریان زنده: ADC $adcValue | PHASE $phaseShift",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "جهت قطب‌نما: ${String.format("%.1f°", compassHeading)} | عمق: ${String.format("%.2f m", depthMeters)}",
                        color = CyberCyan,
                        fontSize = 9.sp
                    )
                    selectedNodeIndex?.let { idx ->
                        val valAtNode = streamBuffer.getOrElse(idx) { 0f }
                        val col = idx % gridCols
                        val row = idx / gridCols
                        Text(
                            text = "نقطه انتخاب شده: [$col, $row] = ${valAtNode.toInt()} LSB",
                            color = CyberGold,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Live Elevation Scale Legend on Bottom Right
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text("ارتفاع بیشینه: ${maxElevation.toInt()}", color = CyberGold, fontSize = 9.sp)
                    Text("زاویه دید: ${yaw.toInt()}° / ${pitch.toInt()}°", color = GrayText, fontSize = 9.sp)
                }
            }

            // Mode Selection Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Style Selector (Solid, Wireframe, Heatmap)
                listOf("Solid" to "سه‌بعدی پر", "Wireframe" to "مشبک (Grid)", "Heatmap" to "حرارتی").forEach { (styleKey, styleLabel) ->
                    val isSelected = renderStyle == styleKey
                    FilterChip(
                        selected = isSelected,
                        onClick = { renderStyle = styleKey },
                        label = { Text(styleLabel, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                            selectedLabelColor = CyberCyan,
                            containerColor = CardBg,
                            labelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Palette Selector Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("پالت رنگی توپوگرافی:", color = GrayText, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Thermal" to "حرارتی 🔥", "Copper" to "مس/طلا ⚡", "Contrast" to "کنتراست 🌗").forEach { (palKey, palLabel) ->
                        val isSelected = colorPalette == palKey
                        Card(
                            onClick = { colorPalette = palKey },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) CyberGold.copy(alpha = 0.2f) else CardBg
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) CyberGold else Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(palLabel, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Visual Signal Gradient Legend Card
            val selNodeVal = selectedNodeIndex?.let { streamBuffer.getOrNull(it) }
            SignalGradientLegendCard(
                maxAbsSignal = maxElevation,
                selectedSignalValue = selNodeVal,
                colorPalette = colorPalette,
                isExpandedDefault = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
