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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        
        var themeMode by remember { 
            mutableIntStateOf(prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)) 
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
        var safeWalkDuration by remember { 
            mutableIntStateOf(prefs.getInt("safe_walk_duration", 15)) 
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
                SettingSectionTitle("App Theme")
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceDark,
                    border = BorderStroke(1.dp, RoyalPurple.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        ThemeOption("Follow System", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, themeMode) {
                            themeMode = it
                            prefs.edit().putInt("theme_mode", it).apply()
                            AppCompatDelegate.setDefaultNightMode(it)
                        }
                        ThemeOption("Light Mode", AppCompatDelegate.MODE_NIGHT_NO, themeMode) {
                            themeMode = it
                            prefs.edit().putInt("theme_mode", it).apply()
                            AppCompatDelegate.setDefaultNightMode(it)
                        }
                        ThemeOption("Dark Mode", AppCompatDelegate.MODE_NIGHT_YES, themeMode) {
                            themeMode = it
                            prefs.edit().putInt("theme_mode", it).apply()
                            AppCompatDelegate.setDefaultNightMode(it)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                
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
                        }
                        ToggleOption("Loud Alarm Sound", emergencyAlarm) {
                            emergencyAlarm = it
                            prefs.edit().putBoolean("emergency_alarm", it).apply()
                        }
                        ToggleOption("Vibration on Countdown", emergencyVibration) {
                            emergencyVibration = it
                            prefs.edit().putBoolean("emergency_vibration", it).apply()
                        }
                        Divider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = RoyalPurple.copy(alpha = 0.1f))
                        ToggleOption("Silent SOS Mode", silentSosMode) {
                            silentSosMode = it
                            prefs.edit().putBoolean("silent_sos_mode", it).apply()
                        }
                        ToggleOption("Voice Activation", voiceSos) {
                            voiceSos = it
                            prefs.edit().putBoolean("voice_sos", it).apply()
                        }
                        ToggleOption("Low Battery Alert", lowBatteryAlert) {
                            lowBatteryAlert = it
                            prefs.edit().putBoolean("low_battery_alert", it).apply()
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
                    var updateStatus by remember { mutableStateOf("Version: ${packageManager.getPackageInfo(packageName, 0).versionName}") }
                    var isChecking by remember { mutableStateOf(false) }

                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(updateStatus, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                isChecking = true
                                updateStatus = "Checking for updates..."
                                updateManager.checkForUpdates(
                                    onUpdateAvailable = { version, url ->
                                        isChecking = false
                                        updateStatus = "Update available: v$version"
                                        updateManager.downloadAndInstall(url)
                                    },
                                    onNoUpdate = {
                                        isChecking = false
                                        updateStatus = "App is up to date"
                                    },
                                    onError = { error ->
                                        isChecking = false
                                        updateStatus = "Error: $error"
                                    }
                                )
                            },
                            enabled = !isChecking,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple, contentColor = Color.White)
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Checking...")
                            } else {
                                Text("Check for Updates", fontWeight = FontWeight.Bold)
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
    fun ThemeOption(label: String, mode: Int, currentMode: Int, onSelect: (Int) -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(mode) }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = (mode == currentMode), 
                onClick = { onSelect(mode) },
                colors = RadioButtonDefaults.colors(selectedColor = RoyalPurple, unselectedColor = TextSecondary.copy(alpha = 0.5f))
            )
            Text(label, modifier = Modifier.padding(start = 12.dp), color = TextPrimary)
        }
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
