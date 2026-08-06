package com.example.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hardware.ConnectionMode
import com.example.hardware.SensorType
import com.example.hardware.FeedbackManager
import com.example.ui.theme.*
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun SettingsScreen(
    viewModel: VisualizerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // State flows from ViewModel
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val soundEnabled by viewModel.soundEnabled.collectAsStateWithLifecycle()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsStateWithLifecycle()
    val sensorBeepEnabled by viewModel.sensorBeepEnabled.collectAsStateWithLifecycle()
    val measurementUnit by viewModel.measurementUnit.collectAsStateWithLifecycle()
    val isOutdoorSunlightMode by viewModel.isOutdoorSunlightMode.collectAsStateWithLifecycle()

    val connectionState by viewModel.sensorManager.connectionState.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.sensorManager.tvStatus.collectAsStateWithLifecycle()
    val activeSensorType by viewModel.sensorManager.sensorType.collectAsStateWithLifecycle()
    val activeBaudRate by viewModel.sensorManager.baudRate.collectAsStateWithLifecycle()

    // File Picker for importing V3D
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val content = reader.readText()
                    
                    val success = viewModel.importV3DData(content)
                    if (success) {
                        FeedbackManager.vibrate(context, 120)
                        Toast.makeText(context, "✅ اسکن V3D با موفقیت وارد و بارگذاری شد!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "❌ خطا در بارگذاری: فرمت فایل غیرمجاز است", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "❌ خطا در خواندن فایل: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header & Professional Custom Logo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppLogo(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(CardBg.copy(alpha = 0.5f))
                    .border(BorderStroke(1.dp, CyberGold.copy(alpha = 0.3f)), CircleShape)
            )

            Column {
                Text(
                    text = AppTranslations.getString("app_title", appLanguage),
                    color = CyberGold,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = AppTranslations.getString("settings_header_subtitle", appLanguage),
                    color = GrayText,
                    fontSize = 11.sp
                )
            }
        }

        // Section 1: Localization
        SettingsSectionCard(
            title = AppTranslations.getString("settings_lang", appLanguage),
            icon = Icons.Default.Language,
            iconColor = CyberCyan
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        FeedbackManager.playClick(context)
                        viewModel.setAppLanguage("fa")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (appLanguage == "fa") CyberGold.copy(alpha = 0.2f) else CardBg,
                        contentColor = if (appLanguage == "fa") CyberGold else Color.White
                    ),
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, if (appLanguage == "fa") CyberGold else Color.Transparent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(AppTranslations.getString("settings_lang_fa", appLanguage), fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        FeedbackManager.playClick(context)
                        viewModel.setAppLanguage("en")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (appLanguage == "en") CyberGold.copy(alpha = 0.2f) else CardBg,
                        contentColor = if (appLanguage == "en") CyberGold else Color.White
                    ),
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, if (appLanguage == "en") CyberGold else Color.Transparent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(AppTranslations.getString("settings_lang_en", appLanguage), fontWeight = FontWeight.Bold)
                }
            }
        }

        // Section 2: Audio & Haptic Feedback Settings
        SettingsSectionCard(
            title = AppTranslations.getString("settings_feedback", appLanguage),
            icon = Icons.Default.VolumeUp,
            iconColor = CyberGold
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Key Sound
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(AppTranslations.getString("settings_sound", appLanguage), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = {
                            FeedbackManager.playClick(context)
                            viewModel.setSoundEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyberGold,
                            checkedTrackColor = CyberGold.copy(alpha = 0.4f)
                        )
                    )
                }

                HorizontalDivider(color = CardBg.copy(alpha = 0.3f))

                // Beeper Anomaly Alarm
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(AppTranslations.getString("settings_beep", appLanguage), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Switch(
                        checked = sensorBeepEnabled,
                        onCheckedChange = {
                            FeedbackManager.playClick(context)
                            viewModel.setSensorBeepEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyberGold,
                            checkedTrackColor = CyberGold.copy(alpha = 0.4f)
                        )
                    )
                }

                if (sensorBeepEnabled) {
                    Button(
                        onClick = {
                            FeedbackManager.playBuzzer(context, 800)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = CyberGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تست بوق فلزات (Buzzer Test)", color = Color.White, fontSize = 11.sp)
                    }
                }

                HorizontalDivider(color = CardBg.copy(alpha = 0.3f))

                // Vibration feedback
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(AppTranslations.getString("settings_vibration", appLanguage), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Switch(
                        checked = vibrationEnabled,
                        onCheckedChange = {
                            FeedbackManager.playClick(context)
                            viewModel.setVibrationEnabled(it)
                            if (it) {
                                FeedbackManager.vibrate(context, 100)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyberGold,
                            checkedTrackColor = CyberGold.copy(alpha = 0.4f)
                        )
                    )
                }
            }
        }

        // Section 3: Display & Outdoor Sunlight High-Contrast Theme
        SettingsSectionCard(
            title = "تم و وضوح تصویر در تابش مستقیم آفتاب (Outdoor Display)",
            icon = Icons.Default.WbSunny,
            iconColor = CyberGold
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "حالت کنتراست بالا برای محیط بیرونی (Sunlight High Contrast)",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "بهینه‌سازی طیف رنگی، فونت‌ها و خطوط شبکه اسکن برای خوانایی کامل در آفتاب شدید",
                        color = GrayText,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isOutdoorSunlightMode,
                    onCheckedChange = {
                        FeedbackManager.playClick(context)
                        viewModel.setOutdoorSunlightMode(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyberGold,
                        checkedTrackColor = CyberGold.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.testTag("switch_outdoor_sunlight_mode")
                )
            }
        }

        // Section: Interactive Help Tutorial
        SettingsSectionCard(
            title = if (appLanguage == "fa") "راهنما و آموزش تعاملی (Interactive Help)" else "Interactive Help & Tutorial",
            icon = Icons.Default.HelpOutline,
            iconColor = CyberCyan
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (appLanguage == "fa") "آموزش گام به گام کار با رادار سه‌بعدی" else "Step-by-step 3D radar guide",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (appLanguage == "fa") "آشنایی با ژست‌های حرکتی، زاویه دید، بازپخش و حالت آفتاب شدید" else "Learn 3D gestures, viewports, playback, and outdoor sunlight mode",
                        color = GrayText,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        FeedbackManager.playClick(context)
                        viewModel.openHelpTutorial()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("settings_open_help_tutorial")
                ) {
                    Text(
                        text = if (appLanguage == "fa") "شروع آموزش" else "Start Tutorial",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Section 4: Depth Unit
        SettingsSectionCard(
            title = AppTranslations.getString("settings_unit", appLanguage),
            icon = Icons.Default.Straighten,
            iconColor = CyberCyan
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        FeedbackManager.playClick(context)
                        viewModel.setMeasurementUnit("m")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (measurementUnit == "m") CyberCyan.copy(alpha = 0.2f) else CardBg,
                        contentColor = if (measurementUnit == "m") CyberCyan else Color.White
                    ),
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, if (measurementUnit == "m") CyberCyan else Color.Transparent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(AppTranslations.getString("settings_unit_m", appLanguage), fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        FeedbackManager.playClick(context)
                        viewModel.setMeasurementUnit("ft")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (measurementUnit == "ft") CyberCyan.copy(alpha = 0.2f) else CardBg,
                        contentColor = if (measurementUnit == "ft") CyberCyan else Color.White
                    ),
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, if (measurementUnit == "ft") CyberCyan else Color.Transparent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(AppTranslations.getString("settings_unit_ft", appLanguage), fontWeight = FontWeight.Bold)
                }
            }
        }

        // Section 4: Bluetooth & Serial Device Config
        SettingsSectionCard(
            title = AppTranslations.getString("settings_device_conf", appLanguage),
            icon = Icons.Default.Bluetooth,
            iconColor = CyberCyan
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Connection widget
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val indicatorColor = when (connectionState) {
                            ConnectionMode.DISCONNECTED -> Color.Red
                            ConnectionMode.CONNECTING_USB, ConnectionMode.CONNECTING_BT -> Color.Yellow
                            ConnectionMode.USB -> Color.Green
                            ConnectionMode.BLUETOOTH -> CyberCyan
                            ConnectionMode.SIMULATOR -> CyberGold
                        }
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(indicatorColor)
                        )
                        Text(AppTranslations.getString("settings_bt_connected", appLanguage), color = Color.White, fontSize = 13.sp)
                    }

                    Text(connectionStatus, color = CyberGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        FeedbackManager.playClick(context)
                        viewModel.sensorManager.autoConnect()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (connectionState != ConnectionMode.DISCONNECTED) CyberRed.copy(alpha = 0.2f) else CyberCyan.copy(alpha = 0.2f),
                        contentColor = if (connectionState != ConnectionMode.DISCONNECTED) CyberRed else CyberCyan
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, if (connectionState != ConnectionMode.DISCONNECTED) CyberRed else CyberCyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (connectionState != ConnectionMode.DISCONNECTED) Icons.Default.BluetoothDisabled else Icons.Default.BluetoothSearching,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (connectionState != ConnectionMode.DISCONNECTED) "قطع اتصال سخت‌افزار" else AppTranslations.getString("settings_bt_scan", appLanguage),
                        fontWeight = FontWeight.Bold
                    )
                }

                // Launch Calibration Wizard
                OutlinedButton(
                    onClick = {
                        FeedbackManager.playClick(context)
                        viewModel.openCalibrationWizard()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberGold),
                    border = BorderStroke(1.dp, CyberGold),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_launch_calibration_wizard_button")
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = CyberGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "شروع راهنمای کالیبراسیون ۵ مرحله‌ای (Calibration Wizard)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                HorizontalDivider(color = CardBg.copy(alpha = 0.3f))

                // Sensor type selection
                Text(AppTranslations.getString("settings_sensor_type", appLanguage), color = GrayText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SensorType.values().forEach { sensorType ->
                        val isSelected = activeSensorType == sensorType
                        Button(
                            onClick = {
                                FeedbackManager.playClick(context)
                                viewModel.sensorManager.setSensorType(sensorType)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) CyberCyan.copy(alpha = 0.15f) else CardBg,
                                contentColor = if (isSelected) CyberCyan else Color.White
                            ),
                            border = BorderStroke(1.dp, if (isSelected) CyberCyan else Color.Transparent),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = sensorType.name.replace("_", " "),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Section 5: V3D Import File Reader (MANDATORY REQUEST)
        SettingsSectionCard(
            title = AppTranslations.getString("settings_import_title", appLanguage),
            icon = Icons.Default.FolderOpen,
            iconColor = CyberGold
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = AppTranslations.getString("settings_import_desc", appLanguage),
                    color = GrayText,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        FeedbackManager.playClick(context)
                        filePickerLauncher.launch("*/*")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppTranslations.getString("settings_import_btn", appLanguage),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Footer version info
        Text(
            text = "GOLD RADAR X20 v2.5.0 • Geophysics Systems Pro\nDesigned with Jetpack Compose & WebGL Rendering",
            color = GrayText.copy(alpha = 0.5f),
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
        )
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBg.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(color = CardBg.copy(alpha = 0.4f))

            content()
        }
    }
}
