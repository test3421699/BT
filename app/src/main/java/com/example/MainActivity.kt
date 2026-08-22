package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ui.theme.DarkBg
import com.example.ui.theme.GrayMuted
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StopRed
import com.example.ui.theme.TechCard
import com.example.ui.theme.TechCyan
import com.example.ui.theme.White

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModelFactory(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = DarkBg
                ) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val connectionState by viewModel.bluetoothManager.connectionState.collectAsState()
    val connectedDeviceName by viewModel.bluetoothManager.connectedDeviceName.collectAsState()
    val commandLogs by viewModel.bluetoothManager.commandLogs.collectAsState()
    val driveMode by viewModel.driveMode.collectAsState()
    val activeMovement by viewModel.activeMovement.collectAsState()
    val currentSpeed by viewModel.currentSpeed.collectAsState()
    val steeringTrim by viewModel.steeringTrim.collectAsState()
    val robotMode by viewModel.robotMode.collectAsState()
    val isDevicePickerOpen by viewModel.isDevicePickerOpen.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val isConfigDialogOpen by viewModel.isConfigDialogOpen.collectAsState()
    val cmdForward by viewModel.cmdForward.collectAsState()
    val cmdBackward by viewModel.cmdBackward.collectAsState()
    val cmdLeft by viewModel.cmdLeft.collectAsState()
    val cmdRight by viewModel.cmdRight.collectAsState()
    val cmdStop by viewModel.cmdStop.collectAsState()

    val speedMin by viewModel.speedMin.collectAsState()
    val speedMax by viewModel.speedMax.collectAsState()
    val speedPrefix by viewModel.speedPrefix.collectAsState()
    val trimMin by viewModel.trimMin.collectAsState()
    val trimMax by viewModel.trimMax.collectAsState()
    val trimPrefix by viewModel.trimPrefix.collectAsState()
    val customButtons by viewModel.customButtons.collectAsState()
    val customSliders by viewModel.customSliders.collectAsState()
    val isControllerStarted by viewModel.isControllerStarted.collectAsState()

    // Permission launcher for Bluetooth permissions (Android 12+)
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val connectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false
        if (connectGranted) {
            viewModel.openDevicePicker()
        } else {
            Toast.makeText(context, "Bluetooth connection permission is required.", Toast.LENGTH_LONG).show()
        }
    }

    val onConnectClick = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            val hasScan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            
            if (hasConnect && hasScan) {
                viewModel.openDevicePicker()
            } else {
                permissionsLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
                )
            }
        } else {
            viewModel.openDevicePicker()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .drawBehind {
                val gridSize = 45.dp.toPx()
                val gridColor = Color(0x0600D2FC) // very subtle cyan grid lines
                val strokeWidth = 1.dp.toPx()

                // Vertical lines
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = strokeWidth
                    )
                    x += gridSize
                }

                // Horizontal lines
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = strokeWidth
                    )
                    y += gridSize
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {
            // 1. Top Panel / Header in Clean Minimalism style
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
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
                            Text(
                                text = "ESP32 RC CONTROLLER",
                                color = TechCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = 2.sp
                            )
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Configure Button Commands",
                                tint = TechCyan,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { viewModel.openConfigDialog() }
                                    .testTag("config_btn")
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            val dotColor = when (connectionState) {
                                ConnectionState.CONNECTED -> Color(0xFF10B981) // emerald
                                ConnectionState.CONNECTING -> Color(0xFFFBBF24) // amber
                                ConnectionState.DISCONNECTED -> StopRed
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(dotColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (connectionState) {
                                    ConnectionState.CONNECTED -> "CONNECTED: ${connectedDeviceName ?: "ESP32_CAR"}"
                                    ConnectionState.CONNECTING -> "CONNECTING..."
                                    ConnectionState.DISCONNECTED -> "DISCONNECTED"
                                },
                                color = when (connectionState) {
                                    ConnectionState.CONNECTED -> Color(0xFF34D399)
                                    ConnectionState.CONNECTING -> Color(0xFFFBBF24)
                                    ConnectionState.DISCONNECTED -> Color(0x66FFFFFF)
                                },
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (connectionState == ConnectionState.CONNECTED) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFF2A2A3A))
                                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(999.dp))
                                .clickable { viewModel.disconnectDevice() }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("disconnect_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "DISCONNECT",
                                color = White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (connectionState != ConnectionState.CONNECTED) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(TechCyan)
                            .clickable { onConnectClick() }
                            .padding(vertical = 12.dp)
                            .testTag("connect_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = "Scan",
                                tint = DarkBg,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "SCAN FOR DEVICES",
                                color = DarkBg,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            // 1.5. Start / Arm Controller Section
            StartControllerCard(
                isStarted = isControllerStarted,
                onToggleStart = { viewModel.toggleControllerStart() },
                currentSpeed = currentSpeed,
                speedPrefix = speedPrefix,
                steeringTrim = steeringTrim,
                trimPrefix = trimPrefix,
                customSlidersCount = customSliders.size
            )

            // 2. Control Grid Area - beautifully padded in the scroll area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                ControlGrid3x3(
                    activeMovement = activeMovement,
                    driveMode = driveMode,
                    isStarted = isControllerStarted,
                    onMovementAction = { cmd, isDown -> viewModel.handleMovementAction(cmd, isDown) },
                    onStopAction = { viewModel.triggerStop() }
                )
            }

            // 2.5. Custom Controls Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF181824))
                    .border(BorderStroke(1.dp, Color(0x0AFFFFFF)), RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🕹️ CUSTOM CONTROLS",
                        color = TechCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    if (!isControllerStarted) {
                        Text(
                            text = "(LOCKED - PRESS START)",
                            color = Color(0x66FFFFFF),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (customButtons.isNotEmpty()) {
                    Text(
                        text = "CUSTOM BUTTONS",
                        color = Color(0x66FFFFFF),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        customButtons.chunked(2).forEach { rowButtons ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowButtons.forEach { btn ->
                                    Button(
                                        onClick = {
                                            if (!isControllerStarted) {
                                                Toast.makeText(context, "Press START to activate controller!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                viewModel.sendCustomButtonCommand(btn.command)
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .alpha(if (isControllerStarted) 1.0f else 0.45f)
                                            .testTag("custom_button_${btn.id}"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF2A2A3A),
                                            contentColor = White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0x1FFFFFFF))
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = btn.label,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "(${btn.command})",
                                                fontSize = 9.sp,
                                                color = TechCyan,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                                if (rowButtons.size < 2) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (customSliders.isNotEmpty()) {
                    Text(
                        text = if (isControllerStarted) "CUSTOM SLIDERS (LIVE TRANSMIT)" else "CUSTOM SLIDERS (PRESET CONFIGURE)",
                        color = if (isControllerStarted) TechCyan else Color(0x66FFFFFF),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        customSliders.forEach { slider ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Text(
                                        text = slider.label.uppercase(),
                                        color = Color(0xBBFFFFFF),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${slider.prefix}${slider.current}",
                                        color = TechCyan,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Slider(
                                    value = slider.current.toFloat().coerceIn(slider.min.toFloat(), slider.max.toFloat()),
                                    onValueChange = { viewModel.updateCustomSliderValue(slider.id, it.toInt()) },
                                    valueRange = if (slider.min < slider.max) slider.min.toFloat()..slider.max.toFloat() else 0f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = White,
                                        activeTrackColor = TechCyan,
                                        inactiveTrackColor = DarkBg,
                                        activeTickColor = Color.Transparent,
                                        inactiveTickColor = Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("custom_slider_${slider.id}")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("MIN (${slider.min})", color = Color(0x33FFFFFF), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    Text("MAX (${slider.max})", color = Color(0x33FFFFFF), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }

                if (customButtons.isEmpty() && customSliders.isEmpty()) {
                    Text(
                        text = "No custom buttons or sliders added yet. Tap settings ⚙️ next to the title to create your custom dashboard!",
                        color = Color(0x55FFFFFF),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Bottom Panel: Speed, Mode & Status combined in beautiful slate container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(Color(0xFF1F1F2E))
                    .border(
                        BorderStroke(1.dp, Color(0x0DFFFFFF)),
                        RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                // Mode Toggle Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBg, RoundedCornerShape(999.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DriveMode.values().forEach { mode ->
                        val isSelected = driveMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (isSelected) Color(0xFF2A2A3A) else Color.Transparent)
                                .clickable { viewModel.setDriveMode(mode) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (mode == DriveMode.PRESS_HOLD) "HOLD MODE" else "CLICK TOGGLE",
                                color = if (isSelected) TechCyan else Color(0x66FFFFFF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Robot Firmware Mode Header
                Text(
                    text = "🤖 ROBOT OPERATING MODE",
                    color = Color(0x66FFFFFF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Robot Mode Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Obstacle Mode Button
                    Button(
                        onClick = {
                            if (!isControllerStarted) {
                                Toast.makeText(context, "Press START to activate controller!", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.sendObstacleMode()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .alpha(if (isControllerStarted) 1.0f else 0.45f)
                            .testTag("obstacle_mode_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (robotMode == "O") TechCyan else Color(0xFF2A2A3A),
                            contentColor = if (robotMode == "O") DarkBg else White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (robotMode == "O") TechCyan else Color(0x1FFFFFFF))
                    ) {
                        Text(
                            text = "OBSTACLE MODE (O)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Drive Mode Button
                    Button(
                        onClick = {
                            if (!isControllerStarted) {
                                Toast.makeText(context, "Press START to activate controller!", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.sendDriveMode()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .alpha(if (isControllerStarted) 1.0f else 0.45f)
                            .testTag("drive_mode_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (robotMode == "D") TechCyan else Color(0xFF2A2A3A),
                            contentColor = if (robotMode == "D") DarkBg else White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (robotMode == "D") TechCyan else Color(0x1FFFFFFF))
                    ) {
                        Text(
                            text = "DRIVE MODE (D)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Speed Slider Layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "ENGINE SPEED",
                        color = Color(0x66FFFFFF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "$speedPrefix$currentSpeed",
                        color = TechCyan,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Clean Modern Slider
                Slider(
                    value = currentSpeed.toFloat().coerceIn(speedMin.toFloat(), speedMax.toFloat()),
                    onValueChange = { viewModel.updateSpeed(it.toInt()) },
                    valueRange = if (speedMin < speedMax) speedMin.toFloat()..speedMax.toFloat() else 0f..255f,
                    colors = SliderDefaults.colors(
                        thumbColor = White,
                        activeTrackColor = TechCyan,
                        inactiveTrackColor = DarkBg,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("speed_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("MIN ($speedMin)", color = Color(0x33FFFFFF), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("MAX ($speedMax)", color = Color(0x33FFFFFF), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Steering Trim Slider Layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "⚖️ STEERING BALANCE / TRIM",
                        color = Color(0x66FFFFFF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "$trimPrefix$steeringTrim",
                        color = TechCyan,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Clean Modern Trim Slider
                Slider(
                    value = steeringTrim.toFloat().coerceIn(trimMin.toFloat(), trimMax.toFloat()),
                    onValueChange = { viewModel.updateSteeringTrim(it.toInt()) },
                    valueRange = if (trimMin < trimMax) trimMin.toFloat()..trimMax.toFloat() else -50f..50f,
                    colors = SliderDefaults.colors(
                        thumbColor = White,
                        activeTrackColor = TechCyan,
                        inactiveTrackColor = DarkBg,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trim_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("LEFT ($trimMin)", color = Color(0x33FFFFFF), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("CENTER (${(trimMin + trimMax) / 2})", color = Color(0x33FFFFFF), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("RIGHT ($trimMax)", color = Color(0x33FFFFFF), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Save Presets Button
                Button(
                    onClick = { viewModel.savePresets() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("save_presets_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2A2A3A),
                        contentColor = TechCyan
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, TechCyan.copy(alpha = 0.5f))
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save Presets",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SAVE PRESETS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Telemetry status preview or console expander
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            // Expand/collapse telemetry console or trigger clear
                            viewModel.bluetoothManager.clearLogs()
                        }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SENDING COMMAND: ${viewModel.resolveCommand(activeMovement)} | PWM: $currentSpeed | TRIM: $steeringTrim",
                        color = Color(0x26FFFFFF),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Error message popup
        errorMessage?.let { error ->
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                icon = { Icon(Icons.Default.Info, contentDescription = "Error", tint = StopRed) },
                title = { Text("Connection Error", color = White, fontWeight = FontWeight.Bold) },
                text = { Text(error, color = GrayMuted) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss", color = TechCyan)
                    }
                },
                containerColor = TechCard,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Device Picker Dialog
        if (isDevicePickerOpen) {
            DevicePickerDialog(
                pairedDevices = pairedDevices,
                onDeviceSelected = { viewModel.connectToDevice(it.address) },
                onDismiss = { viewModel.closeDevicePicker() },
                onRefresh = { viewModel.refreshPairedDevices() }
            )
        }

        // Button Configuration Dialog
        if (isConfigDialogOpen) {
            ButtonConfigDialog(
                currentForward = cmdForward,
                currentBackward = cmdBackward,
                currentLeft = cmdLeft,
                currentRight = cmdRight,
                currentStop = cmdStop,
                speedMin = speedMin,
                speedMax = speedMax,
                speedPrefix = speedPrefix,
                trimMin = trimMin,
                trimMax = trimMax,
                trimPrefix = trimPrefix,
                customButtons = customButtons,
                customSliders = customSliders,
                viewModel = viewModel,
                onDismiss = { viewModel.closeConfigDialog() }
            )
        }
    }
}

@Composable
fun StartControllerCard(
    isStarted: Boolean,
    onToggleStart: () -> Unit,
    currentSpeed: Int,
    speedPrefix: String,
    steeringTrim: Int,
    trimPrefix: String,
    customSlidersCount: Int,
    modifier: Modifier = Modifier
) {
    var isPressedState by remember { mutableStateOf(false) }
    val scaleFactor by animateFloatAsState(
        targetValue = if (isPressedState) 0.96f else 1.0f,
        label = "StartBtnScale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isStarted) Color(0xFF13221C) else Color(0xFF181826))
            .border(
                BorderStroke(
                    1.5.dp,
                    if (isStarted) Color(0xFF10B981).copy(alpha = 0.8f) else TechCyan.copy(alpha = 0.5f)
                ),
                RoundedCornerShape(20.dp)
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            if (isStarted) Color(0xFF10B981) else Color(0xFF6B7280),
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isStarted) "CONTROLLER ARMED & ACTIVE" else "CONTROLLER STANDBY (LOCKED)",
                    color = if (isStarted) Color(0xFF34D399) else GrayMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Icon(
                imageVector = if (isStarted) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = null,
                tint = if (isStarted) Color(0xFF34D399) else GrayMuted,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Big Prominent Start/Stop Action Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .scale(scaleFactor)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isStarted) StopRed else TechCyan
                )
                .pointerInput(isStarted) {
                    detectTapGestures(
                        onPress = {
                            isPressedState = true
                            tryAwaitRelease()
                            isPressedState = false
                        },
                        onTap = {
                            onToggleStart()
                        }
                    )
                }
                .testTag("start_button"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isStarted) Icons.Default.Stop else Icons.Default.PowerSettingsNew,
                    contentDescription = if (isStarted) "Stop Controller" else "Start Controller",
                    tint = if (isStarted) White else Color(0xFF14141E),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isStarted) "STOP CONTROLLER (STANDBY)" else "START CONTROLLER",
                    color = if (isStarted) White else Color(0xFF14141E),
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Explanatory badge about sending preset values
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                tint = if (isStarted) Color(0xFF34D399) else TechCyan.copy(alpha = 0.8f),
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isStarted) {
                    "Presets synchronized: Speed ($speedPrefix$currentSpeed), Trim ($trimPrefix$steeringTrim)" +
                            (if (customSlidersCount > 0) ", $customSlidersCount custom slider(s)" else "")
                } else {
                    "Tapping START activates buttons & transmits preset speed ($speedPrefix$currentSpeed) & trim ($trimPrefix$steeringTrim)" +
                            (if (customSlidersCount > 0) " + $customSlidersCount slider(s)" else "")
                },
                color = if (isStarted) Color(0xAA34D399) else Color(0x99FFFFFF),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ControlGrid3x3(
    activeMovement: String,
    driveMode: DriveMode,
    isStarted: Boolean,
    onMovementAction: (String, Boolean) -> Unit,
    onStopAction: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(300.dp)
    ) {
        // Row 1: [Empty] | [▲ (Forward)] | [Empty]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
            DriveButton(
                label = "▲",
                command = "F",
                activeMovement = activeMovement,
                driveMode = driveMode,
                isStarted = isStarted,
                onAction = onMovementAction,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .testTag("forward_btn")
            )
            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
        }

        // Row 2: [◀ (Left)] | [■ (STOP - Red)] | [▶ (Right)]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DriveButton(
                label = "◀",
                command = "L",
                activeMovement = activeMovement,
                driveMode = driveMode,
                isStarted = isStarted,
                onAction = onMovementAction,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .testTag("left_btn")
            )
            DriveStopButton(
                onClick = onStopAction,
                isActive = activeMovement == "S",
                isStarted = isStarted,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .testTag("stop_btn")
            )
            DriveButton(
                label = "▶",
                command = "R",
                activeMovement = activeMovement,
                driveMode = driveMode,
                isStarted = isStarted,
                onAction = onMovementAction,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .testTag("right_btn")
            )
        }

        // Row 3: [Empty] | [▼ (Backward)] | [Empty]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
            DriveButton(
                label = "▼",
                command = "B",
                activeMovement = activeMovement,
                driveMode = driveMode,
                isStarted = isStarted,
                onAction = onMovementAction,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .testTag("backward_btn")
            )
            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
        }
    }
}

@Composable
fun DriveButton(
    label: String,
    command: String,
    activeMovement: String,
    driveMode: DriveMode,
    isStarted: Boolean,
    onAction: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPressedState by remember { mutableStateOf(false) }
    val isActive = activeMovement == command && isStarted
    
    val scaleFactor by animateFloatAsState(
        targetValue = if (isPressedState && isStarted) 0.92f else 1.0f,
        label = "ButtonPressAnim"
    )

    val buttonBg = when {
        !isStarted -> Color(0xFF2A2A3A).copy(alpha = 0.5f)
        isActive -> TechCyan
        else -> Color(0xFF2A2A3A)
    }
    val contentColor = when {
        !isStarted -> TechCyan.copy(alpha = 0.4f)
        isActive -> Color(0xFF14141E)
        else -> TechCyan
    }
    val borderCol = when {
        !isStarted -> Color(0x1A00D2FC)
        isActive -> TechCyan
        else -> Color(0x3300D2FC)
    }

    Box(
        modifier = modifier
            .scale(scaleFactor)
            .alpha(if (isStarted) 1.0f else 0.45f)
            .clip(RoundedCornerShape(24.dp))
            .background(buttonBg)
            .border(1.dp, borderCol, RoundedCornerShape(24.dp))
            .pointerInput(driveMode, isStarted) {
                detectTapGestures(
                    onPress = {
                        if (!isStarted) {
                            Toast.makeText(context, "Press START to activate controller!", Toast.LENGTH_SHORT).show()
                        } else {
                            isPressedState = true
                            if (driveMode == DriveMode.PRESS_HOLD) {
                                onAction(command, true)
                            }
                            tryAwaitRelease()
                            isPressedState = false
                            if (driveMode == DriveMode.PRESS_HOLD) {
                                onAction(command, false)
                            }
                        }
                    },
                    onTap = {
                        if (!isStarted) {
                            Toast.makeText(context, "Press START to activate controller!", Toast.LENGTH_SHORT).show()
                        } else if (driveMode == DriveMode.CLICK_TOGGLE) {
                            onAction(command, true)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DriveStopButton(
    onClick: () -> Unit,
    isActive: Boolean,
    isStarted: Boolean,
    modifier: Modifier = Modifier
) {
    var isPressedState by remember { mutableStateOf(false) }
    val scaleFactor by animateFloatAsState(
        targetValue = if (isPressedState) 0.90f else 1.0f,
        label = "StopPressAnim"
    )

    val stopBg = StopRed
    val contentColor = White

    Box(
        modifier = modifier
            .scale(scaleFactor)
            .clip(RoundedCornerShape(24.dp))
            .background(stopBg)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressedState = true
                        tryAwaitRelease()
                        isPressedState = false
                    },
                    onTap = {
                        onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "■",
                color = contentColor,
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "STOP",
                color = contentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun DevicePickerDialog(
    pairedDevices: List<BluetoothDevice>,
    onDeviceSelected: (BluetoothDevice) -> Unit,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1F1F2E),
            border = BorderStroke(1.dp, Color(0x1F00D2FC))
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = TechCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PAIRED DEVICES",
                            color = White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Row {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = TechCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = GrayMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (pairedDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BluetoothConnected,
                                contentDescription = null,
                                tint = GrayMuted.copy(alpha = 0.3f),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No paired devices found",
                                color = White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Please pair your ESP32 inside your Android Bluetooth settings first.",
                                color = GrayMuted,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pairedDevices) { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(DarkBg)
                                    .border(BorderStroke(1.dp, Color(0x0DFFFFFF)), RoundedCornerShape(14.dp))
                                    .clickable { onDeviceSelected(device) }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = TechCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    @SuppressLint("MissingPermission")
                                    val name = device.name ?: "Unknown ESP32"
                                    Text(
                                        text = name,
                                        color = White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = device.address,
                                        color = GrayMuted,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonConfigDialog(
    currentForward: String,
    currentBackward: String,
    currentLeft: String,
    currentRight: String,
    currentStop: String,
    speedMin: Int,
    speedMax: Int,
    speedPrefix: String,
    trimMin: Int,
    trimMax: Int,
    trimPrefix: String,
    customButtons: List<CustomButton>,
    customSliders: List<CustomSlider>,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) } // 0: Directions, 1: Default Sliders, 2: Custom Buttons, 3: Custom Sliders

    // Direction states
    var forward by remember { mutableStateOf(currentForward) }
    var backward by remember { mutableStateOf(currentBackward) }
    var left by remember { mutableStateOf(currentLeft) }
    var right by remember { mutableStateOf(currentRight) }
    var stop by remember { mutableStateOf(currentStop) }

    // Default Sliders configurations
    var sMin by remember { mutableStateOf(speedMin.toString()) }
    var sMax by remember { mutableStateOf(speedMax.toString()) }
    var sPref by remember { mutableStateOf(speedPrefix) }

    var tMin by remember { mutableStateOf(trimMin.toString()) }
    var tMax by remember { mutableStateOf(trimMax.toString()) }
    var tPref by remember { mutableStateOf(trimPrefix) }

    // Add Custom Button form states
    var newBtnLabel by remember { mutableStateOf("") }
    var newBtnCmd by remember { mutableStateOf("") }

    // Add Custom Slider form states
    var newSldLabel by remember { mutableStateOf("") }
    var newSldPref by remember { mutableStateOf("") }
    var newSldMin by remember { mutableStateOf("") }
    var newSldMax by remember { mutableStateOf("") }
    var newSldDef by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1F1F2E),
            border = BorderStroke(1.dp, Color(0x1F00D2FC))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = TechCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CONTROLLER CONFIG",
                            color = White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = GrayMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom Tab Segment Selector (Scrollable Row)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBg, RoundedCornerShape(8.dp))
                        .padding(4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf("DIRECTIONS", "DEFAULT SLIDERS", "CUSTOM BUTTONS", "CUSTOM SLIDERS")
                    tabs.forEachIndexed { index, title ->
                        val isSelected = activeTab == index
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) Color(0xFF2A2A3A) else Color.Transparent)
                                .clickable { activeTab = index }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) TechCyan else Color(0x66FFFFFF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tab Contents inside a vertical scrollable column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (activeTab) {
                        0 -> {
                            // Directions Config Tab
                            Text(
                                text = "DIRECTIONS CONFIG",
                                color = TechCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Customize character/string commands sent to ESP32 for movement controls.",
                                color = GrayMuted,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                            ConfigInputField(label = "Forward (▲)", value = forward, onValueChange = { forward = it })
                            ConfigInputField(label = "Backward (▼)", value = backward, onValueChange = { backward = it })
                            ConfigInputField(label = "Left (◀)", value = left, onValueChange = { left = it })
                            ConfigInputField(label = "Right (▶)", value = right, onValueChange = { right = it })
                            ConfigInputField(label = "Stop (■)", value = stop, onValueChange = { stop = it })

                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (forward.isNotBlank() && backward.isNotBlank() && left.isNotBlank() && right.isNotBlank() && stop.isNotBlank()) {
                                        viewModel.updateButtonConfigurations(forward, backward, left, right, stop)
                                        Toast.makeText(context, "Direction commands updated!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = TechCyan),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("SAVE DIRECTIONS", color = DarkBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        1 -> {
                            // Default Sliders Config Tab
                            Text(
                                text = "DEFAULT SLIDERS CONFIG",
                                color = TechCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Configure limits and command prefixes for Engine Speed and Steering Trim.",
                                color = GrayMuted,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )

                            // Engine Speed Slider Parameters
                            Text(
                                text = "ENGINE SPEED CONFIG",
                                color = White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.weight(1f)) {
                                    ConfigInputField(label = "Min Value", value = sMin, onValueChange = { sMin = it })
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    ConfigInputField(label = "Max Value", value = sMax, onValueChange = { sMax = it })
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    ConfigInputField(label = "Prefix (e.g. none)", value = sPref, onValueChange = { sPref = it })
                                }
                            }

                            Button(
                                onClick = {
                                    val minVal = sMin.toIntOrNull() ?: 100
                                    val maxVal = sMax.toIntOrNull() ?: 255
                                    if (minVal < maxVal) {
                                        viewModel.updateSpeedConfig(minVal, maxVal, sPref)
                                        Toast.makeText(context, "Engine Speed limits updated!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Min must be less than Max!", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = TechCyan),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("SAVE SPEED CONFIG", color = DarkBg, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Steering Trim Slider Parameters
                            Text(
                                text = "STEERING BALANCE CONFIG",
                                color = White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.weight(1f)) {
                                    ConfigInputField(label = "Min Value", value = tMin, onValueChange = { tMin = it })
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    ConfigInputField(label = "Max Value", value = tMax, onValueChange = { tMax = it })
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    ConfigInputField(label = "Prefix (e.g. B:)", value = tPref, onValueChange = { tPref = it })
                                }
                            }

                            Button(
                                onClick = {
                                    val minVal = tMin.toIntOrNull() ?: -50
                                    val maxVal = tMax.toIntOrNull() ?: 50
                                    if (minVal < maxVal) {
                                        viewModel.updateTrimConfig(minVal, maxVal, tPref)
                                        Toast.makeText(context, "Steering balance limits updated!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Min must be less than Max!", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = TechCyan),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("SAVE STEERING CONFIG", color = DarkBg, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                        2 -> {
                            // Custom Buttons Tab
                            Text(
                                text = "MANAGE CUSTOM BUTTONS",
                                color = TechCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Add New Custom Button Form
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkBg, RoundedCornerShape(14.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("ADD NEW BUTTON", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                ConfigInputField(label = "Display Label (e.g. Horn)", value = newBtnLabel, onValueChange = { newBtnLabel = it })
                                ConfigInputField(label = "Command Character / String (e.g. H)", value = newBtnCmd, onValueChange = { newBtnCmd = it })
                                Button(
                                    onClick = {
                                        if (newBtnLabel.isNotBlank() && newBtnCmd.isNotBlank()) {
                                            viewModel.addCustomButton(newBtnLabel, newBtnCmd)
                                            newBtnLabel = ""
                                            newBtnCmd = ""
                                            Toast.makeText(context, "Custom button added!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = TechCyan),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("ADD BUTTON", color = DarkBg, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // List current custom buttons
                            Text("CURRENT BUTTONS (${customButtons.size})", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            if (customButtons.isEmpty()) {
                                Text("No custom buttons created yet.", color = GrayMuted, fontSize = 11.sp)
                            } else {
                                customButtons.forEach { btn ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF2A2A3A), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(btn.label, color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text("Command: ${btn.command}", color = TechCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        }
                                        IconButton(onClick = { viewModel.deleteCustomButton(btn.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StopRed, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                        3 -> {
                            // Custom Sliders Tab
                            Text(
                                text = "MANAGE CUSTOM SLIDERS",
                                color = TechCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Add New Custom Slider Form
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkBg, RoundedCornerShape(14.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("ADD NEW SLIDER", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                ConfigInputField(label = "Display Label (e.g. Servo Angle)", value = newSldLabel, onValueChange = { newSldLabel = it })
                                ConfigInputField(label = "Command Prefix (e.g. S:)", value = newSldPref, onValueChange = { newSldPref = it })
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        ConfigInputField(label = "Min Value", value = newSldMin, onValueChange = { newSldMin = it })
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        ConfigInputField(label = "Max Value", value = newSldMax, onValueChange = { newSldMax = it })
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        ConfigInputField(label = "Initial Value", value = newSldDef, onValueChange = { newSldDef = it })
                                    }
                                }
                                Button(
                                    onClick = {
                                        val minVal = newSldMin.toIntOrNull() ?: 0
                                        val maxVal = newSldMax.toIntOrNull() ?: 180
                                        val defVal = newSldDef.toIntOrNull() ?: minVal
                                        if (newSldLabel.isNotBlank() && minVal < maxVal) {
                                            viewModel.addCustomSlider(newSldLabel, newSldPref, minVal, maxVal, defVal)
                                            newSldLabel = ""
                                            newSldPref = ""
                                            newSldMin = ""
                                            newSldMax = ""
                                            newSldDef = ""
                                            Toast.makeText(context, "Custom slider added!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Invalid label or bounds!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = TechCyan),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("ADD SLIDER", color = DarkBg, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // List current custom sliders
                            Text("CURRENT SLIDERS (${customSliders.size})", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            if (customSliders.isEmpty()) {
                                Text("No custom sliders created yet.", color = GrayMuted, fontSize = 11.sp)
                            } else {
                                customSliders.forEach { slider ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF2A2A3A), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(slider.label, color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text("Prefix: '${slider.prefix}' | Range: ${slider.min} to ${slider.max}", color = TechCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        }
                                        IconButton(onClick = { viewModel.deleteCustomSlider(slider.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StopRed, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = TechCyan.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = White,
                unfocusedTextColor = White,
                focusedContainerColor = DarkBg,
                unfocusedContainerColor = DarkBg,
                focusedBorderColor = TechCyan,
                unfocusedBorderColor = Color(0x1AFFFFFF),
                cursorColor = TechCyan
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
