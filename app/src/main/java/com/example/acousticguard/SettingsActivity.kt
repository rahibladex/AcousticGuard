package com.example.acousticguard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
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
        val powerManager = remember { getSystemService(Context.POWER_SERVICE) as? PowerManager }
        var isBatteryIgnored by remember {
            mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(packageName) == true)
        }
        
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
                    title = { 
                        Text("Settings", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary) 
                    },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SettingSectionTitle("Emergency Actions")
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceDark,
                    border = BorderStroke(1.dp, RoyalPurple.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        ToggleOption("Strobe Flashlight", emergencyFlashlight) {
                            emergencyFlashlight = it
                            prefs.edit().putBoolean("emergency_flashlight", it).apply()
                            if (!it) {
                                EmergencyManager(this@SettingsActivity).stopFlashlight()
                            }
                        }
                        ToggleOption("Loud Siren Alarm", emergencyAlarm) {
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

                SettingSectionTitle("Background Protection")

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceDark,
                    border = BorderStroke(1.dp, if (isBatteryIgnored) GreenActive.copy(alpha = 0.4f) else RedEmergency.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.BatteryChargingFull,
                                    contentDescription = null,
                                    tint = if (isBatteryIgnored) GreenActive else RedEmergency
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Battery Optimization",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Text(
                                text = if (isBatteryIgnored) "Unrestricted" else "Optimized",
                                color = if (isBatteryIgnored) GreenActive else RedEmergency,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Text(
                            text = if (isBatteryIgnored) {
                                "App is protected against aggressive OEM battery killers (Samsung, Xiaomi, OnePlus). 24/7 background tracking is active."
                            } else {
                                "Recommended: Whitelist TEJASHWINI so Android does not kill audio tracking & SOS monitoring when screen is locked."
                            },
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )

                        if (!isBatteryIgnored) {
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:$packageName")
                                        }
                                        startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                        } catch (e2: Exception) {
                                            Toast.makeText(this@SettingsActivity, "Could not open battery settings", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple, contentColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Text("Allow Unrestricted Background", fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
