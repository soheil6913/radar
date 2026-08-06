package com.example.ui
 
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ScanRecord
import com.example.ui.theme.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.cos
import kotlin.math.sin

data class Point3D(val x: Float, val y: Float, val z: Float)
data class Point2D(val x: Float, val y: Float)

data class QuadPolygon(
    val i1: Int, val i2: Int, val i3: Int, val i4: Int,
    val avgZ: Float, // camera space depth for sorting
    val cx: Int, val cy: Int
)

fun calculateNodeDepth(value: Float, maxVal: Float): Float {
    if (maxVal <= 0f) return 0f
    val ratio = (abs(value) / maxVal).coerceIn(0f, 1f)
    return if (value > 0f) {
        (18.0f * (1.0f - ratio)).coerceIn(0.65f, 12.0f)
    } else {
        (8.0f + (1.0f - ratio) * 12.0f).coerceIn(1.8f, 20.0f)
    }
}

@Composable
fun ThreeDGridVisualizer(
    scan: ScanRecord,
    yaw: Float,
    pitch: Float,
    zoom: Float,
    panX: Float,
    panY: Float,
    zScale: Float,
    colorThreshold: Float,
    renderStyle: String,
    isRgbAnalysis: Boolean = false,
    selectedNodeIndex: Int?,
    onNodeSelected: (Int?) -> Unit,
    onRotate: (Float, Float) -> Unit,
    onZoom: (Float) -> Unit,
    onPan: (Float, Float) -> Unit,
    selectedDepthLayer: String = "All",
    colorAutoScale: Boolean = false,
    colorPalette: String = "Thermal",
    modifier: Modifier = Modifier
) {
    val width = scan.width
    val length = scan.length
    val gridData = remember(scan) { scan.getGridData() }

    if (gridData.isEmpty()) {
        return
    }

    // Find min and max values to normalize heights
    val maxVal = remember(gridData) {
        val maxAbs = gridData.maxOfOrNull { abs(it) } ?: 1.0f
        if (maxAbs == 0f) 1.0f else maxAbs
    }

    val minDataVal = remember(gridData) { gridData.minOrNull() ?: 0f }
    val maxDataVal = remember(gridData) { gridData.maxOrNull() ?: 1f }

    // Geometry calculations
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures(panZoomLock = false) { centroid, pan, zoomChange, rotation ->
                    if (zoomChange != 1.0f) {
                        onZoom(zoomChange)
                        onPan(pan.x, pan.y)
                    } else if (rotation != 0f && kotlin.math.abs(rotation) > 0.6f) {
                        onRotate(rotation, 0f)
                    } else {
                        onRotate(-pan.x * 0.4f, -pan.y * 0.4f)
                    }
                }
            }
            .pointerInput(scan, yaw, pitch, zoom, panX, panY, zScale) {
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

                    for (cy in 0 until length) {
                        for (cx in 0 until width) {
                            val ox = if (width > 1) (cx - (width - 1) / 2f) / ((width - 1) / 2f) else 0f
                            val oy = if (length > 1) (cy - (length - 1) / 2f) / ((length - 1) / 2f) else 0f
                            
                            val idx = cy * width + cx
                            val rawVal = gridData.getOrNull(idx) ?: 0f
                            val normVal = rawVal / maxVal
                            
                            val oz = if (renderStyle == "Heatmap") 0f else (normVal * 0.45f * zScale)

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
                            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                            if (dist < minDistance) {
                                minDistance = dist
                                closestIdx = idx
                            }
                        }
                    }

                    // Touch tolerance of 45 pixels to find target node
                    if (minDistance < 45f) {
                        onNodeSelected(closestIdx)
                    } else {
                        onNodeSelected(null)
                    }
                }
            }
            .transformable(state = rememberTransformableState { zoomChange, panChange, _ ->
                onZoom(zoomChange)
                onPan(panChange.x, panChange.y)
            })
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // 1. Transform all vertices into Camera Space
            val cameraPoints = ArrayList<Point3D>(width * length)
            val projectedPoints = ArrayList<Point2D>(width * length)

            val radYaw = Math.toRadians(yaw.toDouble())
            val radPitch = Math.toRadians(pitch.toDouble())

            val cosY = cos(radYaw).toFloat()
            val sinY = sin(radYaw).toFloat()
            val cosP = cos(radPitch).toFloat()
            val sinP = sin(radPitch).toFloat()

            for (cy in 0 until length) {
                for (cx in 0 until width) {
                    // Map grid (0..width-1, 0..length-1) to object space [-1.0..1.0]
                    val ox = if (width > 1) (cx - (width - 1) / 2f) / ((width - 1) / 2f) else 0f
                    val oy = if (length > 1) (cy - (length - 1) / 2f) / ((length - 1) / 2f) else 0f
                    
                    val idx = cy * width + cx
                    val rawVal = gridData.getOrNull(idx) ?: 0f
                    val normVal = rawVal / maxVal
                    
                    // Z height scaled by normalized value and height multiplier
                    val oz = if (renderStyle == "Heatmap") 0f else (normVal * 0.45f * zScale)

                    // Rotate around Z/Y (Yaw)
                    // We rotate X and Z around Y axis
                    val x1 = ox * cosY - oz * sinY
                    val z1 = ox * sinY + oz * cosY

                    // Rotate around X (Pitch)
                    // We rotate Y and the rotated Z
                    val y2 = oy * cosP - z1 * sinP
                    val z2 = oy * sinP + z1 * cosP

                    cameraPoints.add(Point3D(x1, y2, z2))

                    // 2. Project into 2D Screen Space (Perspective Projection)
                    val cameraDistance = 3.2f
                    val projectionFactor = zoom * minOf(canvasWidth, canvasHeight) * 0.8f / (cameraDistance + z2)

                    val px = canvasWidth / 2f + x1 * projectionFactor + panX
                    val py = canvasHeight / 2f - y2 * projectionFactor + panY

                    projectedPoints.add(Point2D(px, py))
                }
            }

            // Draw Bounding Wire Box / Reference Cage (Professional Geophysics Outline) (Skip in Heatmap mode)
            if (renderStyle != "Heatmap") {
                val cageX = listOf(-1.1f, 1.1f)
                val cageY = listOf(-1.1f, 1.1f)
                val cageZ = listOf(-0.2f * zScale, 0.5f * zScale)
                
                val cagePoints = ArrayList<Point2D>()
                val cameraDistCage = 3.2f
                
                for (cx in cageX) {
                    for (cy in cageY) {
                        for (cz in cageZ) {
                            val x1 = cx * cosY - cz * sinY
                            val z1 = cx * sinY + cz * cosY
                            val y2 = cy * cosP - z1 * sinP
                            val z2 = cy * sinP + z1 * cosP
                            
                            val projectionFactor = zoom * minOf(canvasWidth, canvasHeight) * 0.8f / (cameraDistCage + z2)
                            val px = canvasWidth / 2f + x1 * projectionFactor + panX
                            val py = canvasHeight / 2f - y2 * projectionFactor + panY
                            cagePoints.add(Point2D(px, py))
                        }
                    }
                }
                
                val connections = listOf(
                    Pair(0, 1), Pair(2, 3), Pair(4, 5), Pair(6, 7), // vertical Segments
                    Pair(0, 2), Pair(2, 6), Pair(6, 4), Pair(4, 0), // bottom Loop
                    Pair(1, 3), Pair(3, 7), Pair(7, 5), Pair(5, 1)  // top Loop
                )
                
                for (conn in connections) {
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
            }

            // 3. Assemble and sort polygons using Painter's Algorithm (Back-to-Front)
            val quads = ArrayList<QuadPolygon>()
            for (cy in 0 until length - 1) {
                for (cx in 0 until width - 1) {
                    val i1 = cy * width + cx           // Top Left
                    val i2 = cy * width + (cx + 1)     // Top Right
                    val i3 = (cy + 1) * width + (cx + 1) // Bottom Right
                    val i4 = (cy + 1) * width + cx     // Bottom Left

                    // Get average camera Z coordinate
                    val avgZ = (cameraPoints[i1].z + cameraPoints[i2].z + cameraPoints[i3].z + cameraPoints[i4].z) / 4f
                    quads.add(QuadPolygon(i1, i2, i3, i4, avgZ, cx, cy))
                }
            }

            // Sort by average depth descending (largest depth = farthest first)
            quads.sortByDescending { it.avgZ }

            // 4. Render Polygons
            quads.forEach { quad ->
                val p1 = projectedPoints[quad.i1]
                val p2 = projectedPoints[quad.i2]
                val p3 = projectedPoints[quad.i3]
                val p4 = projectedPoints[quad.i4]

                // Determine height values
                val h1 = gridData.getOrNull(quad.i1) ?: 0f
                val h2 = gridData.getOrNull(quad.i2) ?: 0f
                val h3 = gridData.getOrNull(quad.i3) ?: 0f
                val h4 = gridData.getOrNull(quad.i4) ?: 0f
                val avgHeight = (h1 + h2 + h3 + h4) / 4f
                val normHeight = if (colorAutoScale) {
                    val range = maxDataVal - minDataVal
                    val fraction = if (range > 0f) (avgHeight - minDataVal) / range else 0.5f
                    fraction * 2f - 1f
                } else {
                    avgHeight / maxVal
                }

                // Light shading
                // Simple diffuse shader: slope in X & Y directions
                val slopeX = (cameraPoints[quad.i2].z - cameraPoints[quad.i1].z) +
                        (cameraPoints[quad.i3].z - cameraPoints[quad.i4].z)
                val slopeY = (cameraPoints[quad.i4].z - cameraPoints[quad.i1].z) +
                        (cameraPoints[quad.i3].z - cameraPoints[quad.i2].z)
                val lightFactor = (1.0f + (slopeX * 0.3f + slopeY * 0.3f)).coerceIn(0.6f, 1.4f)

                // Get base color of polygon based on height and threshold
                val baseColor = getTargetColor(normHeight, colorThreshold, isRgbAnalysis, colorPalette)

                // Check depth layer
                val nodeDepth = calculateNodeDepth(avgHeight, maxVal)
                val isInActiveLayer = when (selectedDepthLayer) {
                    "Surface" -> nodeDepth in 0f..3.5f
                    "Subsurface" -> nodeDepth in 3.5f..8.0f
                    "Deep" -> nodeDepth in 8.0f..14.0f
                    "Bedrock" -> nodeDepth in 14.0f..20.0f
                    else -> true
                }

                // Apply shading
                val layerAlphaFactor = if (isInActiveLayer) 1.0f else 0.12f
                val finalColor = Color(
                    red = (baseColor.red * lightFactor).coerceIn(0f, 1f),
                    green = (baseColor.green * lightFactor).coerceIn(0f, 1f),
                    blue = (baseColor.blue * lightFactor).coerceIn(0f, 1f),
                    alpha = (if (renderStyle == "Heatmap") 0.95f else baseColor.alpha) * layerAlphaFactor
                )

                val path = Path().apply {
                    moveTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                    lineTo(p4.x, p4.y)
                    close()
                }

                when (renderStyle) {
                    "Solid", "Heatmap" -> {
                        // Draw filled polygon
                        drawPath(path = path, color = finalColor)
                        
                        // Draw mesh outline for structural depth
                        val outlineAlpha = if (isInActiveLayer) 0.2f else 0.05f
                        drawPath(
                            path = path,
                            color = if (renderStyle == "Heatmap") Color.Black.copy(alpha = 0.15f * layerAlphaFactor) else CyberCyan.copy(alpha = outlineAlpha),
                            style = Stroke(width = 1f)
                        )
                    }
                    "Wireframe" -> {
                        // Only draw the mesh wireframe with height-based coloring
                        val lineCol = if (abs(normHeight) >= colorThreshold) {
                            baseColor.copy(alpha = 0.8f * layerAlphaFactor)
                        } else {
                            CyberCyan.copy(alpha = 0.15f * layerAlphaFactor)
                        }
                        drawPath(
                            path = path,
                            color = lineCol,
                            style = Stroke(width = 1.5f)
                        )
                    }
                    "Points" -> {
                        // Handled separately below to draw points on top
                    }
                }
            }

            // Draw individual point nodes if "Points" style selected
            if (renderStyle == "Points") {
                projectedPoints.forEachIndexed { idx, pt ->
                    val rawH = gridData.getOrNull(idx) ?: 0f
                    val normH = if (colorAutoScale) {
                        val range = maxDataVal - minDataVal
                        val fraction = if (range > 0f) (rawH - minDataVal) / range else 0.5f
                        fraction * 2f - 1f
                    } else {
                        rawH / maxVal
                    }
                    val dotColor = getTargetColor(normH, colorThreshold, isRgbAnalysis, colorPalette)
                    
                    val nodeDepth = calculateNodeDepth(rawH, maxVal)
                    val isInActiveLayer = when (selectedDepthLayer) {
                        "Surface" -> nodeDepth in 0f..3.5f
                        "Subsurface" -> nodeDepth in 3.5f..8.0f
                        "Deep" -> nodeDepth in 8.0f..14.0f
                        "Bedrock" -> nodeDepth in 14.0f..20.0f
                        else -> true
                    }
                    
                    val layerAlphaFactor = if (isInActiveLayer) 1.0f else 0.12f
                    
                    // Highlight selected point
                    val isSelected = selectedNodeIndex == idx
                    val radius = if (isSelected) 8f else 4.5f
                    val glowCol = (if (isSelected) Color.White else dotColor).copy(alpha = layerAlphaFactor)
                    
                    drawCircle(
                        color = glowCol,
                        radius = radius,
                        center = Offset(pt.x, pt.y)
                    )
                    
                    if (isSelected && isInActiveLayer) {
                        drawCircle(
                            color = CyberGold,
                            radius = 14f,
                            center = Offset(pt.x, pt.y),
                            style = Stroke(width = 1.5f)
                        )
                    }
                }
            } else if (selectedNodeIndex != null) {
                // Highlight the selected node on top of Solid or Wireframe mesh
                val pt = projectedPoints.getOrNull(selectedNodeIndex)
                if (pt != null) {
                    val rawH = gridData.getOrNull(selectedNodeIndex) ?: 0f
                    val nodeDepth = calculateNodeDepth(rawH, maxVal)
                    val isInActiveLayer = when (selectedDepthLayer) {
                        "Surface" -> nodeDepth in 0f..3.5f
                        "Subsurface" -> nodeDepth in 3.5f..8.0f
                        "Deep" -> nodeDepth in 8.0f..14.0f
                        "Bedrock" -> nodeDepth in 14.0f..20.0f
                        else -> true
                    }
                    val layerAlphaFactor = if (isInActiveLayer) 1.0f else 0.25f
                    
                    drawCircle(
                        color = Color.White.copy(alpha = layerAlphaFactor),
                        radius = 7f,
                        center = Offset(pt.x, pt.y)
                    )
                    drawCircle(
                        color = CyberGold.copy(alpha = layerAlphaFactor),
                        radius = 13f,
                        center = Offset(pt.x, pt.y),
                        style = Stroke(width = 1.8f)
                    )
                }
            }

            // Draw coordinate axis guides in the corner (3D Compass)
            draw3DCompass(canvasWidth, canvasHeight, cosY, sinY, cosP, sinP)
        }
    }
}

/**
 * Maps a normalized height value into a beautiful high-contrast copper-red (metals)
 * and deep electric blue (cavities) color ramp.
 */

fun interpolateColor(color1: Color, color2: Color, factor: Float): Color {
    val f = factor.coerceIn(0f, 1f)
    return Color(
        red = color1.red + (color2.red - color1.red) * f,
        green = color1.green + (color2.green - color1.green) * f,
        blue = color1.blue + (color2.blue - color1.blue) * f,
        alpha = color1.alpha + (color2.alpha - color1.alpha) * f
    )
}

/**
 * Maps a normalized value into the user's custom color palette.
 */
fun getTargetColor(value: Float, threshold: Float = 0.05f, palette: String = "Thermal"): Color {
    val normalized = value.coerceIn(0f, 1f)
    return when (palette) {
        "MetalCavity", "Metal / Cavity", "Geophysical" -> {
            when {
                // High Noble Metal Anomaly (Gold / Silver / High Conductivity) -> Red / Magenta / White-Gold
                normalized > 0.88f -> interpolateColor(Color(0xFFFF0055), Color(0xFFFFFFFF), (normalized - 0.88f) / 0.12f)
                normalized > 0.70f -> interpolateColor(Color(0xFFFF9100), Color(0xFFFF0055), (normalized - 0.70f) / 0.18f)
                normalized > 0.55f -> interpolateColor(Color(0xFFFFD700), Color(0xFFFF9100), (normalized - 0.55f) / 0.15f)
                
                // Neutral Soil Matrix -> Dark Emerald Green
                normalized in 0.45f..0.55f -> {
                    val t = (normalized - 0.45f) / 0.10f
                    interpolateColor(Color(0xFF1B5E20), Color(0xFF2E7D32), t)
                }
                
                // Cavity / Void / Tunnel Dip -> Electric Cyan / Royal Blue / Deep Violet
                normalized > 0.30f -> interpolateColor(Color(0xFF00E5FF), Color(0xFF2E7D32), (normalized - 0.30f) / 0.15f)
                normalized > 0.12f -> interpolateColor(Color(0xFF2979FF), Color(0xFF00E5FF), (normalized - 0.12f) / 0.18f)
                else -> interpolateColor(Color(0xFFD500F9), Color(0xFF2979FF), normalized / 0.12f)
            }
        }
        "Thermal" -> {
            when {
                normalized > 0.88f -> interpolateColor(Color(0xFFFF0000), Color(0xFFFFFFFF), (normalized - 0.88f) / 0.12f)
                normalized > 0.70f -> interpolateColor(Color(0xFFFFFF00), Color(0xFFFF0000), (normalized - 0.70f) / 0.18f)
                normalized > 0.45f -> interpolateColor(Color(0xFF00FF00), Color(0xFFFFFF00), (normalized - 0.45f) / 0.25f)
                normalized > 0.20f -> interpolateColor(Color(0xFF00FFFF), Color(0xFF00FF00), (normalized - 0.20f) / 0.25f)
                else -> interpolateColor(Color(0xFF00008B), Color(0xFF00FFFF), normalized / 0.20f)
            }
        }
        "Grayscale" -> {
            Color(red = normalized, green = normalized, blue = normalized)
        }
        "OutdoorSunlight", "OutdoorHighContrast", "Contrast", "HighContrast" -> {
            // Remap 0..1 back to -1..1 value internally for Contrast formula
            val valReal = normalized * 2f - 1f
            val valScaled = valReal * 350f // approx max height
            when {
                valScaled > 150f -> {
                    val t = ((valScaled - 150f) / 200f).coerceIn(0f, 1f)
                    interpolateColor(Color(0xFFFFD700), Color(0xFFFFFFFF), t)
                }
                valScaled > 30f -> {
                    val t = ((valScaled - 30f) / 120f).coerceIn(0f, 1f)
                    interpolateColor(Color(0xFFFF9100), Color(0xFFFFD700), t)
                }
                valScaled < -100f -> {
                    val t = ((abs(valScaled) - 100f) / 150f).coerceIn(0f, 1f)
                    interpolateColor(Color(0xFF00E5FF), Color(0xFFE040FB), t)
                }
                valScaled < -30f -> {
                    val t = ((abs(valScaled) - 30f) / 70f).coerceIn(0f, 1f)
                    interpolateColor(Color(0xFF2979FF), Color(0xFF00E5FF), t)
                }
                else -> {
                    Color(0xFF0A0D14) // Pure pitch dark background for maximum sunlight contrast
                }
            }
        }
        "IronOxide", "Copper", "Mineralized" -> {
            when {
                normalized > 0.85f -> interpolateColor(Color(0xFFFFD700), Color(0xFFFFFFFF), (normalized - 0.85f) / 0.15f)
                normalized > 0.65f -> interpolateColor(Color(0xFFE65100), Color(0xFFFFD700), (normalized - 0.65f) / 0.20f)
                normalized > 0.40f -> interpolateColor(Color(0xFF8D6E63), Color(0xFFE65100), (normalized - 0.40f) / 0.25f)
                normalized > 0.20f -> interpolateColor(Color(0xFF3E2723), Color(0xFF8D6E63), (normalized - 0.20f) / 0.20f)
                else -> interpolateColor(Color(0xFF1A0C08), Color(0xFF3E2723), normalized / 0.20f)
            }
        }
        else -> { // Classic
            when {
                normalized > 0.85f -> Color(0xFFFF4500) // red - High Metal
                normalized > 0.70f -> Color(0xFFFF8C00) // orange
                normalized > 0.55f -> Color(0xFFFFD700) // gold - Mid Metal
                normalized > 0.40f -> Color(0xFF2E7D32) // green - Soil
                normalized > 0.25f -> Color(0xFF00E5FF) // cyan - Mild Cavity
                else -> Color(0xFF2979FF)               // deep blue - Severe Cavity
            }
        }
    }
}

/**
 * Performs bilinear interpolation between nodes of the raw scan grid to provide
 * smooth, high-fidelity heatmaps matching professional geophysics software.
 */
fun interpolateGridValue(
    data: List<Float>,
    cols: Int,
    rows: Int,
    xFrac: Float,
    yFrac: Float
): Float {
    if (data.isEmpty() || cols <= 0 || rows <= 0) return 0f
    
    val x0 = xFrac.toInt().coerceIn(0, cols - 1)
    val x1 = (x0 + 1).coerceIn(0, cols - 1)
    val y0 = yFrac.toInt().coerceIn(0, rows - 1)
    val y1 = (y0 + 1).coerceIn(0, rows - 1)
    
    val tx = (xFrac - x0).coerceIn(0f, 1f)
    val ty = (yFrac - y0).coerceIn(0f, 1f)
    
    val v00 = data.getOrNull(y0 * cols + x0) ?: 0f
    val v10 = data.getOrNull(y0 * cols + x1) ?: 0f
    val v01 = data.getOrNull(y1 * cols + x0) ?: 0f
    val v11 = data.getOrNull(y1 * cols + x1) ?: 0f
    
    val v0 = v00 * (1f - tx) + v10 * tx
    val v1 = v01 * (1f - tx) + v11 * tx
    return v0 * (1f - ty) + v1 * ty
}

/**
 * Overloaded getTargetColor for the 3D grid, mapping normalized height values using the new custom color palette.
 */
fun getTargetColor(normalizedVal: Float, threshold: Float, isRgbAnalysis: Boolean = false, palette: String = "Thermal"): Color {
    if (isRgbAnalysis) {
        // Red = Metal (> 0.15), Blue = Cavity (< -0.15), Green = Virgin Soil (otherwise)
        val v = normalizedVal.coerceIn(-1f, 1f)
        return if (v > 0.15f) {
            // RED: Metal
            val intensity = 0.5f + (v - 0.15f) * (0.5f / 0.85f)
            Color(red = intensity.coerceIn(0.5f, 1f), green = 0.1f, blue = 0.1f, alpha = 0.95f)
        } else if (v < -0.15f) {
            // BLUE: Cavity
            val intensity = 0.5f + (abs(v) - 0.15f) * (0.5f / 0.85f)
            Color(red = 0.1f, green = 0.1f, blue = intensity.coerceIn(0.5f, 1f), alpha = 0.95f)
        } else {
            // GREEN: Pristine ground/soil
            val dev = abs(v) / 0.15f
            val greenIntensity = 0.85f - (dev * 0.35f)
            Color(red = 0.1f, green = greenIntensity.coerceIn(0.4f, 0.9f), blue = 0.1f, alpha = 0.85f)
        }
    }

    val absVal = abs(normalizedVal)
    
    // Under the filter threshold, color it with dark obsidian ground color
    if (absVal < threshold) {
        return Color(0xFF22262B).copy(alpha = 0.45f)
    }

    // Map -1..1 to 0..1 for the palette
    val value = (normalizedVal + 1f) / 2f
    return getTargetColor(value, threshold, palette)
}

/**
 * Draws a gorgeous little 3D coordinate widget in the bottom-left corner
 */
fun DrawScope.draw3DCompass(
    width: Float,
    height: Float,
    cosY: Float,
    sinY: Float,
    cosP: Float,
    sinP: Float
) {
    val cx = 60f
    val cy = height - 60f
    val len = 40f

    // Transform coordinate directions
    // X axis (1, 0, 0)
    val xx = cosY
    val xy = -sinY * sinP
    
    // Y axis (0, 1, 0)
    val yx = 0f
    val yy = cosP

    // Z axis (0, 0, 1)
    val zx = sinY
    val zy = cosY * sinP

    // Draw X-Axis (Red)
    drawLine(
        color = Color(0xFFFF3D00),
        start = Offset(cx, cy),
        end = Offset(cx + xx * len, cy - xy * len),
        strokeWidth = 3f
    )

    // Draw Y-Axis (Green)
    drawLine(
        color = Color(0xFF4CAF50),
        start = Offset(cx, cy),
        end = Offset(cx + yx * len, cy - yy * len),
        strokeWidth = 3f
    )

    // Draw Z-Axis (Blue)
    drawLine(
        color = Color(0xFF2979FF),
        start = Offset(cx, cy),
        end = Offset(cx + zx * len, cy - zy * len),
        strokeWidth = 3f
    )
}

data class DetectedTarget(
    val col: Int,
    val row: Int,
    val value: Float,
    val depth: Double,
    val type: String,
    val id: Int
)

fun detectTargetsInGrid(scanData: List<Float>, cols: Int, rows: Int, maxVal: Float): List<DetectedTarget> {
    if (scanData.isEmpty() || cols <= 0 || rows <= 0) return emptyList()
    val targets = mutableListOf<DetectedTarget>()
    
    val threshold = maxVal * 0.35f
    if (threshold <= 0f) return emptyList()
    
    var targetId = 1
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val idx = r * cols + c
            val value = scanData.getOrNull(idx) ?: 0f
            if (value < threshold) continue
            
            var isLocalMax = true
            for (dr in -1..1) {
                for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    val nc = c + dc
                    val nr = r + dr
                    if (nc in 0 until cols && nr in 0 until rows) {
                        val nIdx = nr * cols + nc
                        val nVal = scanData.getOrNull(nIdx) ?: 0f
                        if (nVal > value) {
                            isLocalMax = false
                            break
                        }
                    }
                }
                if (!isLocalMax) break
            }
            
            if (isLocalMax) {
                val ratio = (abs(value) / if (maxVal == 0f) 1f else maxVal).coerceIn(0f, 1f)
                val depth = (18.0 * (1.0 - ratio)).coerceIn(0.5, 15.0)
                val type = when {
                    value > maxVal * 0.75f -> "طلا ✨"
                    value > maxVal * 0.50f -> "فلز ارزشمند 💎"
                    else -> "فلز ناشناخته ❓"
                }
                targets.add(DetectedTarget(c, r, value, depth, type, targetId++))
            }
        }
    }
    return targets.sortedByDescending { it.value }.take(5)
}

@Composable
fun PlanView(
    scanData: List<Float>,
    gridCols: Int,
    gridRows: Int,
    maxVal: Float,
    selectedCol: Int,
    selectedRow: Int,
    onSelectPoint: (Int, Int) -> Unit,
    smoothInterpolation: Boolean,
    selectedDepthLayer: String = "All",
    colorAutoScale: Boolean = false,
    colorPalette: String = "Thermal",
    showTargetMarkers: Boolean = true,
    onTargetsDetected: (List<DetectedTarget>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var containerWidth by remember { mutableStateOf(1f) }
    var containerHeight by remember { mutableStateOf(1f) }

    val minDataVal = remember(scanData) { scanData.minOrNull() ?: 0f }
    val maxDataVal = remember(scanData) { scanData.maxOfOrNull { it } ?: 1f }

    val detectedTargets = remember(scanData, gridCols, gridRows, maxVal) {
        val list = detectTargetsInGrid(scanData, gridCols, gridRows, maxVal)
        onTargetsDetected(list)
        list
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(8.dp)
            .onSizeChanged { size ->
                containerWidth = size.width.toFloat()
                containerHeight = size.height.toFloat()
            }
            .pointerInput(gridCols, gridRows) {
                detectTapGestures { offset ->
                    val cellWidth = containerWidth / gridCols
                    val cellHeight = containerHeight / gridRows
                    val col = (offset.x / cellWidth).toInt().coerceIn(0, gridCols - 1)
                    val row = (offset.y / cellHeight).toInt().coerceIn(0, gridRows - 1)
                    onSelectPoint(col, row)
                }
            }
            .pointerInput(gridCols, gridRows) {
                detectDragGestures { change, _ ->
                    val cellWidth = containerWidth / gridCols
                    val cellHeight = containerHeight / gridRows
                    val col = (change.position.x / cellWidth).toInt().coerceIn(0, gridCols - 1)
                    val row = (change.position.y / cellHeight).toInt().coerceIn(0, gridRows - 1)
                    onSelectPoint(col, row)
                }
            }
    ) {
        if (gridCols == 0 || gridRows == 0 || scanData.isEmpty()) {
            Text(
                "داده‌ای برای نمایش وجود ندارد",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
            return@Box
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellWidth = size.width / gridCols
            val cellHeight = size.height / gridRows

            if (smoothInterpolation) {
                // High density smooth rendering (bilinear interpolation)
                val denseCols = 100
                val denseRows = 100
                val denseCellWidth = size.width / denseCols
                val denseCellHeight = size.height / denseRows

                for (y in 0 until denseRows) {
                    for (x in 0 until denseCols) {
                        val xFrac = (x.toFloat() / (denseCols - 1)).coerceIn(0f, 1f) * (gridCols - 1)
                        val yFrac = (y.toFloat() / (denseRows - 1)).coerceIn(0f, 1f) * (gridRows - 1)

                        val value = interpolateGridValue(scanData, gridCols, gridRows, xFrac, yFrac)
                        val normalized = if (colorAutoScale) {
                            val range = maxDataVal - minDataVal
                            if (range > 0f) ((value - minDataVal) / range).coerceIn(0f, 1f) else 0.5f
                        } else {
                            val max = if (maxVal == 0f) 1f else maxVal
                            (value / max).coerceIn(0f, 1f)
                        }
                        val color = getTargetColor(normalized, threshold = 0.05f, palette = colorPalette)

                        // Check layer
                        val nodeDepth = calculateNodeDepth(value, maxVal)
                        val isInActiveLayer = when (selectedDepthLayer) {
                            "Surface" -> nodeDepth in 0f..3.5f
                            "Subsurface" -> nodeDepth in 3.5f..8.0f
                            "Deep" -> nodeDepth in 8.0f..14.0f
                            "Bedrock" -> nodeDepth in 14.0f..20.0f
                            else -> true
                        }
                        val finalColor = if (isInActiveLayer) color else color.copy(alpha = 0.12f)

                        drawRect(
                            color = finalColor,
                            topLeft = Offset(x * denseCellWidth, y * denseCellHeight),
                            size = Size(denseCellWidth + 0.5f, denseCellHeight + 0.5f)
                        )
                    }
                }
            } else {
                // Raw pixelated grid blocks
                for (y in 0 until gridRows) {
                    for (x in 0 until gridCols) {
                        val idx = y * gridCols + x
                        val value = scanData.getOrNull(idx) ?: 0f
                        val normalized = if (colorAutoScale) {
                            val range = maxDataVal - minDataVal
                            if (range > 0f) ((value - minDataVal) / range).coerceIn(0f, 1f) else 0.5f
                        } else {
                            val max = if (maxVal == 0f) 1f else maxVal
                            (value / max).coerceIn(0f, 1f)
                        }
                        val color = getTargetColor(normalized, threshold = 0.05f, palette = colorPalette)

                        // Check layer
                        val nodeDepth = calculateNodeDepth(value, maxVal)
                        val isInActiveLayer = when (selectedDepthLayer) {
                            "Surface" -> nodeDepth in 0f..3.5f
                            "Subsurface" -> nodeDepth in 3.5f..8.0f
                            "Deep" -> nodeDepth in 8.0f..14.0f
                            "Bedrock" -> nodeDepth in 14.0f..20.0f
                            else -> true
                        }
                        val finalColor = if (isInActiveLayer) color else color.copy(alpha = 0.12f)

                        drawRect(
                            color = finalColor,
                            topLeft = Offset(x * cellWidth, y * cellHeight),
                            size = Size(cellWidth - 1f, cellHeight - 1f)
                        )
                    }
                }
            }

            // Draw guidelines for calibration and references
            for (i in 0..gridCols) {
                val xPos = i * cellWidth
                drawLine(
                    color = Color.White.copy(alpha = 0.15f),
                    start = Offset(xPos, 0f),
                    end = Offset(xPos, size.height),
                    strokeWidth = 1f
                )
            }
            for (i in 0..gridRows) {
                val yPos = i * cellHeight
                drawLine(
                    color = Color.White.copy(alpha = 0.15f),
                    start = Offset(0f, yPos),
                    end = Offset(size.width, yPos),
                    strokeWidth = 1f
                )
            }

            // Draw Professional Dashed Crosshair Reticle for Interactive Analysis
            if (selectedCol in 0 until gridCols && selectedRow in 0 until gridRows) {
                val crossX = (selectedCol + 0.5f) * cellWidth
                val crossY = (selectedRow + 0.5f) * cellHeight

                // Horizontal dashed targeting line
                drawLine(
                    color = Color.White.copy(alpha = 0.8f),
                    start = Offset(0f, crossY),
                    end = Offset(size.width, crossY),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)
                )

                // Vertical dashed targeting line
                drawLine(
                    color = Color.White.copy(alpha = 0.8f),
                    start = Offset(crossX, 0f),
                    end = Offset(crossX, size.height),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)
                )

                // Precise target circle and golden ring
                drawCircle(
                    color = Color.White,
                    radius = 6f,
                    center = Offset(crossX, crossY)
                )
                drawCircle(
                    color = Color.Black,
                    radius = 3f,
                    center = Offset(crossX, crossY)
                )
                drawCircle(
                    color = CyberGold,
                    radius = 14f,
                    center = Offset(crossX, crossY),
                    style = Stroke(width = 1.8f)
                )
            }

            // Draw Detected Peak Target Indicators
            if (showTargetMarkers) {
                detectedTargets.forEach { target ->
                    val tx = (target.col + 0.5f) * cellWidth
                    val ty = (target.row + 0.5f) * cellHeight
                    
                    // Outer target ring
                    drawCircle(
                        color = Color(0xFFFF3D00).copy(alpha = 0.4f),
                        radius = 28f,
                        center = Offset(tx, ty),
                        style = Stroke(width = 1.5f)
                    )
                    // Inner dashed gold ring
                    drawCircle(
                        color = Color(0xFFFFD700),
                        radius = 18f,
                        center = Offset(tx, ty),
                        style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                    )
                    // Center dot
                    drawCircle(
                        color = Color(0xFFFF3D00),
                        radius = 4f,
                        center = Offset(tx, ty)
                    )
                    // Crosshair lines
                    drawLine(
                        color = Color(0xFFFFD700),
                        start = Offset(tx - 12f, ty),
                        end = Offset(tx + 12f, ty),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = Color(0xFFFFD700),
                        start = Offset(tx, ty - 12f),
                        end = Offset(tx, ty + 12f),
                        strokeWidth = 2f
                    )
                }
            }
        }

        // Bottom status overlay inside the canvas container for coordinates
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(6.dp)
        ) {
            Text(
                text = "Max: ${String.format("%.2f", maxVal)}",
                color = Color.White,
                fontSize = 9.sp
            )
            Text(
                text = "Grid: ${gridCols}x${gridRows}",
                color = Color.White,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
fun SignalProfileChart(
    scanData: List<Float>,
    gridCols: Int,
    gridRows: Int,
    selectedCol: Int,
    selectedRow: Int,
    maxVal: Float,
    modifier: Modifier = Modifier
) {
    if (scanData.isEmpty() || gridCols <= 0 || gridRows <= 0) return

    // Mode: 0 = Row Profile (افقی), 1 = Column Profile (عمودی), 2 = Entire Scan (کل شبکه)
    var chartMode by remember { mutableStateOf(0) }

    // Prepare active data subset and selected index within that subset
    val dataSubset = remember(scanData, gridCols, gridRows, selectedCol, selectedRow, chartMode) {
        when (chartMode) {
            0 -> { // Row Profile: values across selected row
                val list = mutableListOf<Float>()
                for (x in 0 until gridCols) {
                    val idx = selectedRow * gridCols + x
                    list.add(scanData.getOrNull(idx) ?: 0f)
                }
                list
            }
            1 -> { // Column Profile: values across selected column
                val list = mutableListOf<Float>()
                for (y in 0 until gridRows) {
                    val idx = y * gridCols + selectedCol
                    list.add(scanData.getOrNull(idx) ?: 0f)
                }
                list
            }
            else -> scanData // Entire scan data points
        }
    }

    val selectedIdxInSubset = remember(selectedCol, selectedRow, gridCols, gridRows, chartMode) {
        when (chartMode) {
            0 -> selectedCol
            1 -> selectedRow
            else -> selectedRow * gridCols + selectedCol
        }
    }

    val subsetMaxVal = remember(dataSubset) {
        val maxAbs = dataSubset.maxOfOrNull { abs(it) } ?: 1f
        if (maxAbs == 0f) 1f else maxAbs
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBg),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header with title and mode selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "📈 نمودار فرکانس سنسور (Signal Waves)",
                        color = CyberGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (chartMode) {
                            0 -> "پروفایل مقطع افقی (ردیف سنسور ${selectedRow + 1})"
                            1 -> "پروفایل مقطع عمودی (ستون سنسور ${selectedCol + 1})"
                            else -> "نمودار شدت کل نقاط به ترتیب اسکن"
                        },
                        color = GrayText,
                        fontSize = 10.sp
                    )
                }

                // Modes Tab Selection
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeBg = CyberCyan.copy(alpha = 0.15f)
                    val inactiveBg = CardBg
                    val activeContent = CyberCyan
                    val inactiveContent = Color.White

                    // Row Mode
                    Box(
                        modifier = Modifier
                            .background(if (chartMode == 0) activeBg else inactiveBg, RoundedCornerShape(6.dp))
                            .border(1.dp, if (chartMode == 0) CyberCyan else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { chartMode = 0 }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("مقطع X", color = if (chartMode == 0) activeContent else inactiveContent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    // Col Mode
                    Box(
                        modifier = Modifier
                            .background(if (chartMode == 1) activeBg else inactiveBg, RoundedCornerShape(6.dp))
                            .border(1.dp, if (chartMode == 1) CyberCyan else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { chartMode = 1 }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("مقطع Y", color = if (chartMode == 1) activeContent else inactiveContent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    // Grid Mode
                    Box(
                        modifier = Modifier
                            .background(if (chartMode == 2) activeBg else inactiveBg, RoundedCornerShape(6.dp))
                            .border(1.dp, if (chartMode == 2) CyberCyan else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { chartMode = 2 }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("کل اسکن", color = if (chartMode == 2) activeContent else inactiveContent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Canvas Line Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .border(BorderStroke(1.dp, CardBg.copy(alpha = 0.4f)), RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val centerY = h / 2f // Neutral 0 Hz baseline

                    // Draw baseline (0 Hz ground level)
                    drawLine(
                        color = Color.White.copy(alpha = 0.35f),
                        start = Offset(0f, centerY),
                        end = Offset(w, centerY),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                    )

                    // Draw reference grid lines (Top +Bottom)
                    val gridYTop = h * 0.15f
                    val gridYBottom = h * 0.85f
                    drawLine(color = Color(0x1100E5FF), start = Offset(0f, gridYTop), end = Offset(w, gridYTop), strokeWidth = 1f)
                    drawLine(color = Color(0x1100E5FF), start = Offset(0f, gridYBottom), end = Offset(w, gridYBottom), strokeWidth = 1f)

                    if (dataSubset.isNotEmpty()) {
                        val pointsCount = dataSubset.size
                        val xSpacing = if (pointsCount > 1) w / (pointsCount - 1) else w
                        
                        val path = Path()
                        val fillPath = Path()

                        // Initialize paths
                        val firstVal = dataSubset[0]
                        val firstY = centerY - (firstVal / subsetMaxVal) * (h / 2f) * 0.85f
                        path.moveTo(0f, firstY)
                        fillPath.moveTo(0f, centerY)
                        fillPath.lineTo(0f, firstY)

                        dataSubset.forEachIndexed { index, valAtPoint ->
                            val xCoord = index * xSpacing
                            val yCoord = centerY - (valAtPoint / subsetMaxVal) * (h / 2f) * 0.85f

                            if (index > 0) {
                                path.lineTo(xCoord, yCoord)
                            }
                            fillPath.lineTo(xCoord, yCoord)
                        }

                        fillPath.lineTo((pointsCount - 1) * xSpacing, centerY)
                        fillPath.close()

                        // Draw dual-gradient fill based on polarity
                        drawPath(
                            path = fillPath,
                            color = if ((dataSubset.getOrNull(selectedIdxInSubset) ?: 0f) >= 0f) CyberGold.copy(alpha = 0.08f) else Color(0x1200E5FF)
                        )

                        // Draw waveform line
                        drawPath(
                            path = path,
                            color = if (chartMode == 2) CyberCyan else CyberGold,
                            style = Stroke(width = 2.5f)
                        )

                        // Draw vertical vertical axis markers
                        for (i in 0 until pointsCount step max(1, pointsCount / 10)) {
                            val xc = i * xSpacing
                            drawLine(
                                color = Color.White.copy(alpha = 0.08f),
                                start = Offset(xc, 0f),
                                end = Offset(xc, h),
                                strokeWidth = 1f
                            )
                        }

                        // Draw glowing marker for Selected Node
                        if (selectedIdxInSubset in 0 until pointsCount) {
                            val selX = selectedIdxInSubset * xSpacing
                            val selY = centerY - (dataSubset[selectedIdxInSubset] / subsetMaxVal) * (h / 2f) * 0.85f

                            // Vertical highlight bar
                            drawLine(
                                color = CyberCyan.copy(alpha = 0.4f),
                                start = Offset(selX, 0f),
                                end = Offset(selX, h),
                                strokeWidth = 1.2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                            )

                            // Glowing ring
                            drawCircle(
                                color = CyberCyan.copy(alpha = 0.3f),
                                radius = 10f,
                                center = Offset(selX, selY)
                            )
                            drawCircle(
                                color = CyberCyan,
                                radius = 4f,
                                center = Offset(selX, selY)
                            )
                        }
                    }
                }
            }
        }
    }
}
