package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.AudioSourceType
import com.example.data.SpeakerMode
import com.example.ui.components.ConnectionStatusIndicator
import com.example.ui.components.NetworkQualityIndicator
import com.example.ui.screens.BluetoothScreen
import com.example.ui.screens.DesktopAudioScreen
import com.example.ui.screens.GuideScreen
import com.example.ui.screens.NetworkDevicesScreen
import com.example.ui.screens.WifiReceiverScreen
import com.example.ui.screens.WifiSenderScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: SpeakerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // 0 = "Be a Speaker" (Primary), 1 = "Send Audio"
    var selectedModeIndex by remember { mutableIntStateOf(0) }
    var showDevicesSheet by remember { mutableStateOf(false) }
    var showGuideSheet by remember { mutableStateOf(false) }

    // Permission launchers
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setSelectedSource(AudioSourceType.MICROPHONE)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun switchMode(index: Int) {
        selectedModeIndex = index
        if (index == 0) {
            viewModel.setMode(SpeakerMode.WIFI_RECEIVER)
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            viewModel.setMode(SpeakerMode.WIFI_SENDER)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SoundLink",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                },
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 12.dp)) {
                        NetworkQualityIndicator(metrics = uiState.networkQuality)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDevicesSheet = true },
                        modifier = Modifier.testTag("devices_tools_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = "Devices & Tools",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = { showGuideSheet = true },
                        modifier = Modifier.testTag("guide_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Help Guide",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Clean, Modern Segmented Control at the top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedModeIndex == 0,
                        onClick = { switchMode(0) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Be a Speaker", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    )

                    SegmentedButton(
                        selected = selectedModeIndex == 1,
                        onClick = { switchMode(1) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.Default.Radio, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Send Audio", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    )
                }
            }

            // Visual Connection Status Indicator: Bitrate, Protocol (Wi-Fi/Bluetooth), PC Ping
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp)
            ) {
                ConnectionStatusIndicator(
                    uiState = uiState,
                    onRefreshPing = { viewModel.refreshPingToPc() }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Content Body
            Box(modifier = Modifier.fillMaxSize()) {
                Crossfade(targetState = selectedModeIndex, label = "main_mode_fade") { mode ->
                    when (mode) {
                        0 -> WifiReceiverScreen(
                            uiState = uiState,
                            onStartReceiver = { viewModel.startReceiver() },
                            onStopReceiver = { viewModel.stopReceiver() },
                            onVolumeChange = { viewModel.setVolume(it) },
                            onToggleMute = { viewModel.toggleMute() },
                            onOpenDevicesSheet = { showDevicesSheet = true }
                        )
                        1 -> WifiSenderScreen(
                            uiState = uiState,
                            onTargetIpChange = { viewModel.setTargetIp(it) },
                            onTargetPinChange = { viewModel.setTargetPin(it) },
                            onSelectDiscoveredSpeaker = { viewModel.selectDiscoveredSpeaker(it) },
                            onSelectSource = { source ->
                                if (source == AudioSourceType.MICROPHONE) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                        != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        viewModel.setSelectedSource(source)
                                    }
                                } else {
                                    viewModel.setSelectedSource(source)
                                }
                            },
                            onStartSender = { uri -> viewModel.startSender(uri) },
                            onStopSender = { viewModel.stopSender() },
                            onToggleMicMute = { viewModel.setMicMuted(!uiState.isMicMuted) },
                            onMicGainChange = { viewModel.setMicGain(it) },
                            onAudioFileSelected = { _, name -> viewModel.setAudioFileName(name) },
                            onRefreshDiscovery = { viewModel.startSpeakerDiscovery() }
                        )
                    }
                }
            }
        }

        // Help Guide BottomSheet
        if (showGuideSheet) {
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { showGuideSheet = false },
                sheetState = sheetState
            ) {
                GuideScreen()
            }
        }

        // Secondary Tools & Devices BottomSheet
        if (showDevicesSheet) {
            val sheetState = rememberModalBottomSheetState()
            var subToolTab by remember { mutableIntStateOf(0) }

            ModalBottomSheet(
                onDismissRequest = { showDevicesSheet = false },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Devices & Advanced Tools",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = subToolTab == 0,
                            onClick = { subToolTab = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            label = { Text("Wi-Fi Devices", fontSize = 11.sp) }
                        )
                        SegmentedButton(
                            selected = subToolTab == 1,
                            onClick = { subToolTab = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            label = { Text("Desktop NSD", fontSize = 11.sp) }
                        )
                        SegmentedButton(
                            selected = subToolTab == 2,
                            onClick = { subToolTab = 2 },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            label = { Text("Bluetooth", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(480.dp)
                    ) {
                        when (subToolTab) {
                            0 -> NetworkDevicesScreen(
                                uiState = uiState,
                                onStartScan = { viewModel.startSubnetScan() },
                                onToggleWebStreamer = { viewModel.toggleDesktopWebServer() },
                                onConnectDevice = {
                                    viewModel.connectToNetworkDevice(it)
                                    showDevicesSheet = false
                                }
                            )
                            1 -> DesktopAudioScreen(
                                uiState = uiState,
                                onConnectDesktop = {
                                    viewModel.connectToDesktopService(it)
                                    showDevicesSheet = false
                                },
                                onDisconnectDesktop = { viewModel.disconnectFromDesktop() },
                                onRefreshDiscovery = { viewModel.startDesktopDiscovery() },
                                onManualIpChange = { viewModel.setDesktopManualIp(it) },
                                onManualPortChange = { viewModel.setDesktopManualPort(it) },
                                onConnectManual = {
                                    viewModel.connectToManualDesktop()
                                    showDevicesSheet = false
                                },
                                onVolumeChange = { viewModel.setVolume(it) },
                                onToggleMute = { viewModel.toggleMute() },
                                onRetryNow = { viewModel.retryDesktopConnectionNow() }
                            )
                            2 -> BluetoothScreen(
                                uiState = uiState,
                                onCheckAgain = { viewModel.checkBluetoothCapability() },
                                onSwitchToWifiMode = {
                                    switchMode(0)
                                    showDevicesSheet = false
                                },
                                onVolumeChange = { viewModel.setVolume(it) },
                                onToggleMute = { viewModel.toggleMute() }
                            )
                        }
                    }
                }
            }
        }
    }
}
