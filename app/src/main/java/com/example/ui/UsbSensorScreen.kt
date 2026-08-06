package com.example.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OutlinedFlag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hardware.ParsedSensorPacket
import com.example.hardware.UsbSerialDataService
import com.example.hardware.UsbSerialDataViewModel
import com.example.hardware.UsbServiceConnectionState
import com.example.ui.theme.CardBg
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGold
import com.example.ui.theme.DarkBg
import com.example.ui.theme.GrayText
import com.example.ui.theme.SurfaceBg

/**
 * Jetpack Compose screen that manages USB hardware connections, provides a button to request
 * USB permissions and connect to the sensor, and displays dynamic status indicators showing
 * the sensor's real-time connection state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbSensorScreen(
    modifier: Modifier = Modifier,
    viewModel: UsbSerialDataViewModel = viewModel()
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val deviceDetails by viewModel.deviceDetails.collectAsStateWithLifecycle()
    val baudRate by viewModel.baudRate.collectAsStateWithLifecycle()
    val latestPacket by viewModel.latestSensorPacket.collectAsStateWithLifecycle()
    val logEntries by viewModel.logEntries.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
    val totalBytesReceived by viewModel.totalBytesReceived.collectAsStateWithLifecycle()
    val totalPacketsReceived by viewModel.totalPacketsReceived.collectAsStateWithLifecycle()

    var commandInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll logs when new entries arrive if not paused
    LaunchedEffect(logEntries.size) {
        if (logEntries.isNotEmpty() && !isPaused) {
            listState.animateScrollToItem(logEntries.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Title
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "مدیریت اتصال سنسور USB",
                            color = CyberGold,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "دریافت مستقیم داده‌های سخت‌افزاری mGPR / Magnetometer",
                            color = GrayText,
                            fontSize = 11.sp
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.DeveloperBoard,
                        contentDescription = "USB Board",
                        tint = CyberCyan,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // 1. Connection Status Card & Status Indicator
            item {
                UsbConnectionStatusCard(
                    connectionState = connectionState,
                    statusMessage = statusMessage,
                    deviceDetails = deviceDetails,
                    rxBytes = totalBytesReceived,
                    rxPackets = totalPacketsReceived
                )
            }

            // 2. Action Controls: Request USB Permission & Connect / Disconnect
            item {
                UsbActionControlsCard(
                    connectionState = connectionState,
                    currentBaudRate = baudRate,
                    onRequestPermission = {
                        requestUsbPermission(context)
                    },
                    onToggleConnect = {
                        viewModel.toggleConnect()
                    },
                    onBaudRateSelected = { newBaud ->
                        viewModel.setBaudRate(newBaud)
                    }
                )
            }

            // 3. Live Readout Card (if connected or packet received)
            item {
                LiveSensorPacketCard(packet = latestPacket)
            }

            // 4. Live Log Console & Command Sender
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SettingsInputComponent,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "کنسول داده‌های زنده (Live Console)",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { viewModel.togglePauseListening() },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("usb_pause_log_button")
                                ) {
                                    Icon(
                                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        contentDescription = "Pause Log",
                                        tint = if (isPaused) CyberGold else Color.White
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.clearLogs() },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("usb_clear_log_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear Logs",
                                        tint = Color.Red
                                    )
                                }
                            }
                        }

                        // Terminal view
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CardBg)
                                .border(
                                    BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(8.dp)
                        ) {
                            if (logEntries.isEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = GrayText,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "هنوز پکت داده‌ای دریافت نشده است",
                                        color = GrayText,
                                        fontSize = 11.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(logEntries) { entry ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "[${entry.timestamp}] ${entry.text}",
                                                color = if (entry.isOutgoing) CyberCyan else CyberGold,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Command Sender Box
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = commandInput,
                                onValueChange = { commandInput = it },
                                placeholder = { Text("ارسال فرمان مانند PING یا READ", fontSize = 11.sp, color = GrayText) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("usb_command_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberCyan,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Button(
                                onClick = {
                                    if (commandInput.isNotBlank()) {
                                        viewModel.sendCommand(commandInput)
                                        commandInput = ""
                                    }
                                },
                                enabled = connectionState == UsbServiceConnectionState.CONNECTED && commandInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberCyan,
                                    disabledContainerColor = CardBg
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("usb_send_command_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send Command",
                                    tint = if (connectionState == UsbServiceConnectionState.CONNECTED) Color.Black else GrayText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom USB status component displaying a green/red/yellow indicator icon and detailed state.
 */
@Composable
fun UsbConnectionStatusCard(
    connectionState: UsbServiceConnectionState,
    statusMessage: String,
    deviceDetails: com.example.hardware.UsbDeviceDetails?,
    rxBytes: Long,
    rxPackets: Long
) {
    val transition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlphaPulse"
    )

    val (statusColor, statusIcon, statusTitle) = when (connectionState) {
        UsbServiceConnectionState.CONNECTED -> Triple(
            Color(0xFF00E676),
            Icons.Default.CheckCircle,
            "سنسور متصل و فعال است (CONNECTED)"
        )
        UsbServiceConnectionState.CONNECTING -> Triple(
            Color(0xFFFFD600),
            Icons.Default.Refresh,
            "در حال برقراری ارتباط (CONNECTING)"
        )
        UsbServiceConnectionState.ERROR -> Triple(
            Color(0xFFFF1744),
            Icons.Default.Error,
            "خطا در ارتباط USB (ERROR)"
        )
        UsbServiceConnectionState.DISCONNECTED -> Triple(
            Color(0xFF757575),
            Icons.Default.UsbOff,
            "دستگاه غیرفعال است (DISCONNECTED)"
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, statusColor.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("usb_status_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Status Indicator Icon with dynamic glow/pulse
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.15f))
                            .border(BorderStroke(2.dp, statusColor.copy(alpha = if (connectionState == UsbServiceConnectionState.CONNECTING) pulseAlpha else 1f)), CircleShape)
                            .testTag("usb_status_indicator")
                    ) {
                        if (connectionState == UsbServiceConnectionState.CONNECTING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = statusColor,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = "Connection Status",
                                tint = statusColor,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = statusTitle,
                            color = statusColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = statusMessage,
                            color = GrayText,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (deviceDetails != null) {
                Divider(color = Color.White.copy(alpha = 0.1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("نام سخت‌افزار:", color = GrayText, fontSize = 10.sp)
                        Text(deviceDetails.driverName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("شناسه VID / PID:", color = GrayText, fontSize = 10.sp)
                        Text(
                            String.format("0x%04X : 0x%04X", deviceDetails.vendorId, deviceDetails.productId),
                            color = CyberCyan,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text("بایت/پکت دریافتی:", color = GrayText, fontSize = 10.sp)
                        Text(
                            "$rxBytes B ($rxPackets pkt)",
                            color = CyberGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Action card providing USB Permission Request and Connect/Disconnect triggers.
 */
@Composable
fun UsbActionControlsCard(
    connectionState: UsbServiceConnectionState,
    currentBaudRate: Int,
    onRequestPermission: () -> Unit,
    onToggleConnect: () -> Unit,
    onBaudRateSelected: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "کنترل اتصال و مجوزهای USB",
                color = CyberGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            // Primary Request USB Permission & Connect Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Button 1: Request Permission & Scan
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("usb_permission_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = "Request Permission",
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "درخواست مجوز USB",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Button 2: Toggle Connect / Disconnect
                Button(
                    onClick = onToggleConnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (connectionState == UsbServiceConnectionState.CONNECTED) Color(0xFFFF1744) else CyberGold
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("usb_connect_button")
                ) {
                    Icon(
                        imageVector = if (connectionState == UsbServiceConnectionState.CONNECTED) Icons.Default.PowerOff else Icons.Default.Power,
                        contentDescription = "Toggle Connection",
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (connectionState == UsbServiceConnectionState.CONNECTED) "قطع اتصال" else "اتصال به پورت",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Baud rate selection
            Column {
                Text(
                    text = "سرعت ارتباطی Baud Rate:",
                    color = GrayText,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(9600, 19200, 38400, 57600, 115200).forEach { baud ->
                        val isSelected = currentBaudRate == baud
                        FilterChip(
                            selected = isSelected,
                            onClick = { onBaudRateSelected(baud) },
                            label = { Text("$baud", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberGold.copy(alpha = 0.25f),
                                selectedLabelColor = CyberGold,
                                containerColor = CardBg,
                                labelColor = Color.White
                            ),
                            modifier = Modifier.testTag("baud_chip_$baud")
                        )
                    }
                }
            }
        }
    }
}

/**
 * Display card for current sensor metrics parsed from the incoming USB serial stream.
 */
@Composable
fun LiveSensorPacketCard(packet: ParsedSensorPacket?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "آخرین مقادیر دریافت شده از سنسور",
                color = CyberGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            if (packet == null) {
                Text(
                    text = "در انتظار اولین پکت از سخت‌افزار...",
                    color = GrayText,
                    fontSize = 12.sp
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    MetricBox(title = "ADC Value", value = "${packet.adcValue ?: "---"}", color = CyberCyan)
                    MetricBox(title = "Phase Shift", value = "${packet.phaseShift ?: "---"}°", color = CyberGold)
                    MetricBox(title = "Signal", value = "${packet.signalStrength ?: "---"}%", color = Color(0xFF00E676))
                    MetricBox(title = "Compass", value = "${packet.compassHeading ?: "---"}°", color = Color(0xFFFF9100))
                }
            }
        }
    }
}

@Composable
fun MetricBox(title: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(CardBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(title, color = GrayText, fontSize = 9.sp)
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Triggers standard Android UsbManager permissions dialog for plugged-in USB hardware devices.
 */
private fun requestUsbPermission(context: Context) {
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val deviceList = usbManager.deviceList

    if (deviceList.isEmpty()) {
        android.widget.Toast.makeText(
            context,
            "هیچ دستگاه USB فیزیکی وصل نشده است. در صورت نیاز از شبیه‌ساز نرم‌افزاری استفاده می‌شود.",
            android.widget.Toast.LENGTH_LONG
        ).show()
        return
    }

    val device = deviceList.values.first()
    if (!usbManager.hasPermission(device)) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(UsbSerialDataService.ACTION_USB_PERMISSION),
            flags
        )
        usbManager.requestPermission(device, permissionIntent)
        android.widget.Toast.makeText(
            context,
            "درخواست مجوز ارسال شد برای: ${device.deviceName}",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    } else {
        android.widget.Toast.makeText(
            context,
            "مجوز USB قبلاً داده شده است.",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}
