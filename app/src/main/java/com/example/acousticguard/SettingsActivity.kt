package com.example.acousticguard

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.example.acousticguard.ui.theme.AcousticGuardTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            AcousticGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SettingsScreen() {
        val prefs = remember { getSharedPreferences("AcousticGuardPrefs", Context.MODE_PRIVATE) }
        
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

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("App Theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
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

                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Emergency Features", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
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
                        Divider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
                        ToggleOption("Silent SOS Mode", silentSosMode) {
                            silentSosMode = it
                            prefs.edit().putBoolean("silent_sos_mode", it).apply()
                        }
                        ToggleOption("Voice Activation (Help Me)", voiceSos) {
                            voiceSos = it
                            prefs.edit().putBoolean("voice_sos", it).apply()
                        }
                        ToggleOption("Low Battery Alert", lowBatteryAlert) {
                            lowBatteryAlert = it
                            prefs.edit().putBoolean("low_battery_alert", it).apply()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Updates & Info", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val updateManager = remember { UpdateManager(this@SettingsActivity) }
                    var updateStatus by remember { mutableStateOf("Version: ${packageManager.getPackageInfo(packageName, 0).versionName}") }
                    var isChecking by remember { mutableStateOf(false) }

                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(updateStatus, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
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
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Checking...")
                            } else {
                                Text("Check for Updates")
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ThemeOption(label: String, mode: Int, currentMode: Int, onSelect: (Int) -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = (mode == currentMode), onClick = { onSelect(mode) })
            Text(label, modifier = Modifier.padding(start = 8.dp))
        }
    }

    @Composable
    fun ToggleOption(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label)
            Switch(checked = checked, onCheckedChange = onToggle)
        }
    }
}
