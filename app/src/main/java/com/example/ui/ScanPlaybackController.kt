package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ScanRecord
import com.example.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * ScanPlaybackController Card: Provides real-time playback controls for re-rendering 
 * completed 3D scan sequences point-by-point, illustrating exact field acquisition history.
 */
@Composable
fun ScanPlaybackControllerCard(
    viewModel: VisualizerViewModel,
    modifier: Modifier = Modifier
) {
    val viewedScan by viewModel.viewedScan.collectAsStateWithLifecycle()
    val isPlaybackActive by viewModel.isPlaybackActive.collectAsStateWithLifecycle()
    val isPlaybackPlaying by viewModel.isPlaybackPlaying.collectAsStateWithLifecycle()
    val playbackPointIndex by viewModel.playbackPointIndex.collectAsStateWithLifecycle()
    val playbackSpeedMultiplier by viewModel.playbackSpeedMultiplier.collectAsStateWithLifecycle()

    val measurementUnit by viewModel.measurementUnit.collectAsStateWithLifecycle()

    val scan = viewedScan ?: return
    val gridData = remember(scan) { scan.getGridData() }
    val totalPoints = gridData.size

    if (totalPoints == 0) return

    val currentPointIdx = (playbackPointIndex - 1).coerceIn(0, totalPoints - 1)
    val currentVal = gridData.getOrNull(currentPointIdx) ?: 0f

    // Estimated target depth calculation
    val estDepthMeters = (kotlin.math.abs(currentVal) / 180f).coerceIn(0.2f, 8.5f)
    val depthFormatted = if (measurementUnit == "Feet") {
        String.format("%.1f ft", estDepthMeters * 3.28084f)
    } else {
        String.format("%.2f m", estDepthMeters)
    }

    // Grid coordinates
    val width = scan.width
    val col = (currentPointIdx % width) + 1
    val row = (currentPointIdx / width) + 1

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.88f)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberGold.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Title, Playback Status Badge, Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isPlaybackPlaying) CyberCyan else CyberGold)
                    )
                    Column {
                        Text(
                            text = "بازپخش گام‌به‌گام اسکن (3D Scan Replay)",
                            color = CyberGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "بازسازی نقطه‌به‌نقطه مسیر اسکن فیزیکی زمین",
                            color = GrayText,
                            fontSize = 9.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Status Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isPlaybackPlaying) CyberCyan.copy(alpha = 0.2f) else CardBg)
                            .border(1.dp, if (isPlaybackPlaying) CyberCyan else Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isPlaybackPlaying) "در حال پخش..." else "متوقف شد",
                            color = if (isPlaybackPlaying) CyberCyan else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Exit Playback Button
                    IconButton(
                        onClick = { viewModel.exitPlayback() },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit Playback",
                            tint = Color.White
                        )
                    }
                }
            }

            // Current Step Info Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardBg)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Step & Grid Coords
                Column {
                    Text(
                        text = "گام $playbackPointIndex از $totalPoints",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "موقعیت: ستون $col، خط $row (${scan.scanPattern})",
                        color = GrayText,
                        fontSize = 9.5.sp
                    )
                }

                // Value and Tag
                val (tagName, tagColor) = when {
                    currentVal > 150f -> Pair("طلا / غیرآهنی", Color(0xFFFF1744))
                    currentVal > 30f -> Pair("معدنی / آهنی", Color(0xFFFFD700))
                    currentVal < -150f -> Pair("دالان عمیق", Color(0xFFD500F9))
                    currentVal < -30f -> Pair("حفره / تونل", Color(0xFF00E5FF))
                    else -> Pair("خاک خنثی", Color(0xFF2E7D32))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(tagColor.copy(alpha = 0.2f))
                            .border(1.dp, tagColor, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = tagName,
                            color = tagColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${if (currentVal >= 0) "+${currentVal.toInt()}" else "${currentVal.toInt()}"} LSB",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "عمق: $depthFormatted",
                            color = CyberGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Scrubbing Timeline Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = playbackPointIndex.toFloat(),
                    onValueChange = { viewModel.seekPlayback(it.toInt()) },
                    valueRange = 1f..totalPoints.toFloat(),
                    steps = if (totalPoints > 2) totalPoints - 2 else 0,
                    colors = SliderDefaults.colors(
                        thumbColor = CyberGold,
                        activeTrackColor = CyberGold,
                        inactiveTrackColor = CardBg
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )
            }

            // Transport Control Buttons & Speed Selectors Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Media Buttons Group
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Skip to Start
                    IconButton(
                        onClick = { viewModel.seekPlayback(1) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Start", tint = Color.White)
                    }

                    // Step Back (-1)
                    IconButton(
                        onClick = { viewModel.stepPlaybackBackward() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.NavigateBefore, contentDescription = "Prev", tint = CyberCyan)
                    }

                    // Play / Pause Button
                    FloatingActionButton(
                        onClick = {
                            if (isPlaybackPlaying) viewModel.pausePlayback() else viewModel.startPlayback()
                        },
                        containerColor = CyberGold,
                        contentColor = Color.Black,
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaybackPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Step Forward (+1)
                    IconButton(
                        onClick = { viewModel.stepPlaybackForward() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.NavigateNext, contentDescription = "Next", tint = CyberCyan)
                    }

                    // Skip to End
                    IconButton(
                        onClick = { viewModel.seekPlayback(totalPoints) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "End", tint = Color.White)
                    }
                }

                // Speed Selector Chips
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(0.5f, 1f, 2f, 4f, 8f).forEach { speed ->
                        val isSelected = playbackSpeedMultiplier == speed
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) CyberCyan.copy(alpha = 0.25f) else CardBg)
                                .border(1.dp, if (isSelected) CyberCyan else Color.Transparent, RoundedCornerShape(6.dp))
                                .clickable { viewModel.setPlaybackSpeed(speed) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${if (speed == 0.5f) "0.5" else speed.toInt()}x",
                                color = if (isSelected) CyberCyan else GrayText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
