package com.example.acousticguard

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Environment
import java.io.File
import androidx.compose.ui.graphics.Brush
import com.example.acousticguard.ui.theme.*

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            NariShaktiSOSTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBg
                ) {
                    SettingsScreen()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SettingsScreen() {
        val prefs = remember { getSharedPreferences("NariShaktiSOSPrefs", Context.MODE_PRIVATE) }
        
        var emergencyFlashlight by remember { 
            mutableStateOf(prefs.getBoolean("emergency_flashlight", true)) 
        }
        var emergencyAlarm by remember { 
            mutableStateOf(prefs.getBoolean("emergency_alarm", true)) 
        }
        var emergencyVibration by remember { 
            mutableStateOf(prefs.getBoolean("emergency_vibration", true)) 
        }
        var silentSosMode by remember { 
            mutableStateOf(prefs.getBoolean("silent_sos_mode", false)) 
        }
        var voiceSos by remember { 
            mutableStateOf(prefs.getBoolean("voice_sos", false)) 
        }
        var lowBatteryAlert by remember { 
            mutableStateOf(prefs.getBoolean("low_battery_alert", true)) 
        }
        var emergencyCall by remember { 
            mutableStateOf(prefs.getBoolean("emergency_call", true)) 
        }
        var allowRemoteAlarm by remember { 
            mutableStateOf(prefs.getBoolean("allow_remote_alarm", true)) 
        }
        var safeWalkDuration by remember { 
            mutableIntStateOf(prefs.getInt("safe_walk_duration", 15)) 
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                    title = { Text("SETTINGS", fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = RoyalPurple)
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SettingSectionTitle("Emergency Features")
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceDark,
                    border = BorderStroke(1.dp, RoyalPurple.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        ToggleOption("Flashing Flashlight", emergencyFlashlight) {
                            emergencyFlashlight = it
                            prefs.edit().putBoolean("emergency_flashlight", it).apply()
                            if (!it) {
                                EmergencyManager(this@SettingsActivity).stopFlashlight()
                            }
                        }
                        ToggleOption("Loud Alarm Sound", emergencyAlarm) {
                            emergencyAlarm = it
                            prefs.edit().putBoolean("emergency_alarm", it).apply()
                            if (!it) {
                                EmergencyManager(this@SettingsActivity).stopAlarm()
                            }
                        }
                        ToggleOption("Vibration on Countdown", emergencyVibration) {
                            emergencyVibration = it
                            prefs.edit().putBoolean("emergency_vibration", it).apply()
                        }
                        Divider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = RoyalPurple.copy(alpha = 0.1f))
                        ToggleOption("Silent SOS Mode", silentSosMode) {
                            silentSosMode = it
                            prefs.edit().putBoolean("silent_sos_mode", it).apply()
                            if (it) {
                                EmergencyManager(this@SettingsActivity).stopFlashlight()
                                EmergencyManager(this@SettingsActivity).stopAlarm()
                            }
                        }
                        ToggleOption("Voice Activation", voiceSos) {
                            voiceSos = it
                            prefs.edit().putBoolean("voice_sos", it).apply()
                        }
                        ToggleOption("Low Battery Alert", lowBatteryAlert) {
                            lowBatteryAlert = it
                            prefs.edit().putBoolean("low_battery_alert", it).apply()
                        }
                        ToggleOption("Automatic Emergency Call", emergencyCall) {
                            emergencyCall = it
                            prefs.edit().putBoolean("emergency_call", it).apply()
                        }
                        ToggleOption("Allow Remote SOS Alarms", allowRemoteAlarm) {
                            allowRemoteAlarm = it
                            prefs.edit().putBoolean("allow_remote_alarm", it).apply()
                            if (!it) {
                                RemoteAlertService.stopAlert(this@SettingsActivity)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                SettingSectionTitle("Safety Tool Settings")

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceDark,
                    border = BorderStroke(1.dp, RoyalPurple.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Safe Walk Timer Duration", 
                            color = TextPrimary, 
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val durations = listOf(5, 15, 30, 60)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            durations.forEach { duration ->
                                FilterChip(
                                    selected = safeWalkDuration == duration,
                                    onClick = {
                                        safeWalkDuration = duration
                                        prefs.edit().putInt("safe_walk_duration", duration).apply()
                                    },
                                    label = { Text("$duration min") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = RoyalPurple,
                                        selectedLabelColor = Color.White,
                                        containerColor = SurfaceDark,
                                        labelColor = TextSecondary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = RoyalPurple.copy(alpha = 0.5f),
                                        selectedBorderColor = RoyalPurple
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                SettingSectionTitle("Updates & Info")

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceDark,
                    border = BorderStroke(1.dp, RoyalPurple.copy(alpha = 0.2f))
                ) {
                    val updateManager = remember { UpdateManager(this@SettingsActivity) }
                    val currentVersion = remember {
                        try {
                            packageManager.getPackageInfo(packageName, 0).versionName ?: "6.1.0"
                        } catch (e: Exception) {
                            "6.1.0"
                        }
                    }
                    var updateStatus by remember { mutableStateOf("App Version: v$currentVersion") }
                    var latestDownloadUrl by remember { mutableStateOf("") }
                    var latestFoundVersion by remember { mutableStateOf("") }
                    var isChecking by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Current Version", color = TextSecondary, fontSize = 14.sp)
                            Text("v$currentVersion", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Text(
                            updateStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                updateStatus.startsWith("Update available") -> GreenActive
                                updateStatus.startsWith("App is up to date") -> BlueProtection
                                updateStatus.startsWith("Error") || updateStatus.startsWith("Check failed") -> RedEmergency
                                else -> TextPrimary
                            },
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )

                        Button(
                            onClick = {
                                isChecking = true
                                updateStatus = "Checking GitHub for updates..."
                                updateManager.checkForUpdates(
                                    forceCheck = false,
                                    onUpdateAvailable = { version, url ->
                                        isChecking = false
                                        latestFoundVersion = version
                                        latestDownloadUrl = url
                                        updateStatus = "Update available: v$version (Downloading...)"
                                        updateManager.downloadAndInstall(url)
                                    },
                                    onNoUpdate = { version, url ->
                                        isChecking = false
                                        latestFoundVersion = version
                                        latestDownloadUrl = url
                                        updateStatus = "App is up to date (v$version)"
                                    },
                                    onError = { error ->
                                        isChecking = false
                                        updateStatus = "Check failed: $error"
                                    }
                                )
                            },
                            enabled = !isChecking,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple, contentColor = Color.White)
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Checking for Updates...")
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Check for Updates", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (latestDownloadUrl.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    updateStatus = "Downloading v${if (latestFoundVersion.isNotEmpty()) latestFoundVersion else currentVersion}..."
                                    updateManager.downloadAndInstall(latestDownloadUrl)
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, RoyalPurple)
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = RoyalPurple, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Re-download Latest APK", color = RoyalPurple, fontWeight = FontWeight.Bold)
                            }
                        }

                        val downloadedApk = remember(updateStatus) {
                            File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "NariShaktiSOS-Update.apk")
                        }
                        if (downloadedApk.exists() && downloadedApk.length() > 1000000) {
                            Button(
                                onClick = {
                                    updateManager.installApk()
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GreenActive, contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Install Downloaded APK", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SettingSectionTitle(title: String) {
        Text(
            text = title, 
            style = MaterialTheme.typography.titleSmall, 
            color = RoyalPurple,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            letterSpacing = 1.sp
        )
    }

    @Composable
    fun ToggleOption(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = TextPrimary)
            Switch(
                checked = checked, 
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = RoyalPurple,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = SurfaceDark,
                    uncheckedBorderColor = TextSecondary.copy(alpha = 0.3f)
                )
            )
        }
    }
}
