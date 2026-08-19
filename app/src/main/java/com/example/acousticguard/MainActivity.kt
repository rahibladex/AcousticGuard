package com.example.acousticguard

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.acousticguard.ui.theme.AcousticGuardTheme
import com.example.acousticguard.ui.theme.BlueProtection
import com.example.acousticguard.ui.theme.GreenActive
import com.example.acousticguard.ui.theme.RedEmergency

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_EMERGENCY_STARTED = "com.example.acousticguard.EMERGENCY_STARTED"
    }

    private lateinit var emergencyManager: EmergencyManager
    private var countdownTimer: CountDownTimer? = null

    // State variables
    private var isSafetyModeActive by mutableStateOf(false)
    private var isEmergencyMode by mutableStateOf(false)
    private var aiStatus by mutableStateOf("AI Detection: OFF")
    private var gpsStatus by mutableStateOf("GPS: OFF")
    private var motionStatus by mutableStateOf("Motion SOS: Inactive")
    private var showEmergencyDialog by mutableStateOf(false)
    private var countdownValue by mutableStateOf(5)
    private var trustedContacts by mutableStateOf(setOf<String>())
    private var isAddingContact by mutableStateOf(false)
    
    private var isSafeWalkActive by mutableStateOf(false)
    private var safeWalkRemainingTime by mutableStateOf("15:00")
    private var safeWalkTimer: CountDownTimer? = null

    private val audioUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioDetectionService.ACTION_AUDIO_UPDATE -> {
                    val db = intent.getDoubleExtra(AudioDetectionService.EXTRA_LOUDNESS, 0.0)
                    val event = intent.getStringExtra(AudioDetectionService.EXTRA_EVENT)
                    aiStatus = if (event.isNullOrEmpty()) {
                        "AI Detection: ON (Vol: ${String.format("%.1f", db)} dB)"
                    } else {
                        "AI Detection: ON (${String.format("%.1f", db)} dB) - $event"
                    }
                }
                AudioDetectionService.ACTION_EMERGENCY_CONFIRM -> {
                    if (!isEmergencyMode) {
                        showEmergencyDialog = true
                        startCountdown()
                    }
                }
                ACTION_EMERGENCY_STARTED -> {
                    activateEmergencyActions()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        emergencyManager = EmergencyManager(this)
        loadSettings()

        setContent {
            AcousticGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreenContent()
                }
            }
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("AcousticGuardPrefs", Context.MODE_PRIVATE)
        trustedContacts = prefs.getStringSet("trusted_contacts", setOf()) ?: setOf()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreenContent() {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("ACOUSTIC GUARD", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { 
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                MainToggleButton()

                StatusDashboard()

                QuickActions()

                SafetyToolsSection()

                ContactsSection()
            }
        }

        if (showEmergencyDialog) {
            EmergencyModal()
        }
        
        if (isAddingContact) {
            AddContactDialog()
        }
    }

    @Composable
    fun MainToggleButton() {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = if (isSafetyModeActive || isEmergencyMode) 0.6f else 0.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )

        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (isSafetyModeActive || isEmergencyMode) 1.15f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )

        val buttonColor = when {
            isEmergencyMode -> RedEmergency
            isSafetyModeActive -> BlueProtection
            else -> GreenActive
        }

        val buttonText = when {
            isEmergencyMode -> "STOP\nEMERGENCY"
            isSafetyModeActive -> "PROTECTION\nACTIVE"
            else -> "START\nPROTECTION"
        }

        Box(
            modifier = Modifier
                .size(220.dp),
            contentAlignment = Alignment.Center
        ) {
            // Pulsing Glow
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .then(if (isSafetyModeActive || isEmergencyMode) Modifier.size((200 * scale).dp) else Modifier)
                    .clip(CircleShape)
                    .background(buttonColor.copy(alpha = if (isSafetyModeActive || isEmergencyMode) alpha else 0.1f))
            )
            
            Button(
                onClick = { handleToggleClick() },
                modifier = Modifier
                    .size(160.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 12.dp)
            ) {
                Text(
                    text = buttonText,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }

    @Composable
    fun StatusDashboard() {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusCard(modifier = Modifier.weight(1f), title = "AI Audio", status = aiStatus, active = isSafetyModeActive)
                StatusCard(modifier = Modifier.weight(1f), title = "Motion SOS", status = motionStatus, active = isSafetyModeActive)
            }
            StatusCard(modifier = Modifier.fillMaxWidth(), title = "GPS Status", status = gpsStatus, active = isSafetyModeActive)
        }
    }

    @Composable
    fun StatusCard(modifier: Modifier, title: String, status: String, active: Boolean) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = title, style = MaterialTheme.typography.labelMedium)
                Text(text = status, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
        }
    }

    @Composable
    fun QuickActions() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val isAlarmActive = emergencyManager.isAlarmActive
            Button(
                onClick = { 
                    emergencyManager.toggleAlarm()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAlarmActive) Color.DarkGray else RedEmergency
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isAlarmActive) {
                    Text("STOP ALARM")
                } else {
                    Icon(Icons.Default.Warning, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Alarm")
                }
            }
            Button(
                onClick = { 
                    emergencyManager.toggleFlashlight()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Toggle Light")
            }
        }
    }

    @Composable
    fun SafetyToolsSection() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Safety Tools", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { 
                            if (isSafeWalkActive) stopSafeWalkTimer() else startSafeWalkTimer()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSafeWalkActive) Color.Red else MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text(if (isSafeWalkActive) "Stop Timer ($safeWalkRemainingTime)" else "Safe Walk Timer")
                    }
                    Button(
                        onClick = { triggerFakeCall() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Fake Call")
                    }
                }
            }
        }
    }

    @Composable
    fun ContactsSection() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Trusted Contacts", fontWeight = FontWeight.Bold)
                    IconButton(onClick = { isAddingContact = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Contact")
                    }
                }
                
                if (trustedContacts.isEmpty()) {
                    Text("No contacts added", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                        items(trustedContacts.toList()) { contact ->
                            ListItem(
                                headlineContent = { Text(contact) },
                                trailingContent = {
                                    TextButton(onClick = { removeContact(contact) }) {
                                        Text("Remove", color = Color.Red)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun EmergencyModal() {
        Dialog(onDismissRequest = { /* Cannot dismiss */ }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = RedEmergency)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "EMERGENCY TRIGGERED",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                    Text(
                        "Are you safe?",
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    Text(
                        "$countdownValue",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 64.sp
                    )
                    Button(
                        onClick = { 
                            cancelEmergency()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("I AM SAFE (STOP)")
                    }
                }
            }
        }
    }

    @Composable
    fun AddContactDialog() {
        var phoneNumber by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { isAddingContact = false },
            title = { Text("Add Trusted Contact") },
            text = {
                TextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (phoneNumber.isNotEmpty()) {
                        val prefs = getSharedPreferences("AcousticGuardPrefs", Context.MODE_PRIVATE)
                        val current = trustedContacts.toMutableSet()
                        current.add(phoneNumber)
                        trustedContacts = current
                        prefs.edit().putStringSet("trusted_contacts", trustedContacts).apply()
                        isAddingContact = false
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { isAddingContact = false }) { Text("Cancel") }
            }
        )
    }

    private fun handleToggleClick() {
        if (isEmergencyMode) {
            stopEmergency()
            return
        }

        if (!isSafetyModeActive) {
            if (checkPermissions()) {
                startSafetyMode()
            } else {
                requestPermissions()
            }
        } else {
            stopSafetyMode()
        }
    }

    private fun startSafetyMode() {
        isSafetyModeActive = true
        aiStatus = "AI Detection: ON"
        gpsStatus = "GPS: ON"
        motionStatus = "Motion SOS: Active"
        
        val serviceIntent = Intent(this, AudioDetectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "Safety Mode Started", Toast.LENGTH_SHORT).show()
    }

    private fun stopSafetyMode() {
        isSafetyModeActive = false
        aiStatus = "AI Detection: OFF"
        gpsStatus = "GPS: OFF"
        motionStatus = "Motion SOS: Inactive"
        
        val serviceIntent = Intent(this, AudioDetectionService::class.java)
        stopService(serviceIntent)
        Toast.makeText(this, "Safety Mode Stopped", Toast.LENGTH_SHORT).show()
    }

    private fun activateEmergencyActions() {
        isEmergencyMode = true
        showEmergencyDialog = false
        emergencyManager.activateEmergencyMode()
        Toast.makeText(this, "EMERGENCY ACTIVATED", Toast.LENGTH_LONG).show()
    }

    private fun stopEmergency() {
        isEmergencyMode = false
        emergencyManager.stopEmergencyMode()
        stopSafetyMode()
    }

    private fun cancelEmergency() {
        countdownTimer?.cancel()
        stopVibration()
        showEmergencyDialog = false
        Toast.makeText(this, "Emergency Cancelled", Toast.LENGTH_SHORT).show()
    }

    private fun startCountdown() {
        countdownValue = 5
        startVibration()
        countdownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                countdownValue = (millisUntilFinished / 1000).toInt()
            }
            override fun onFinish() {
                stopVibration()
                activateEmergencyActions()
            }
        }.start()
    }

    private fun startVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200), 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 500, 200), 0)
        }
    }

    private fun stopVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.cancel()
    }

    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
    }

    private fun removeContact(contact: String) {
        val prefs = getSharedPreferences("AcousticGuardPrefs", Context.MODE_PRIVATE)
        val current = trustedContacts.toMutableSet()
        current.remove(contact)
        trustedContacts = current
        prefs.edit().putStringSet("trusted_contacts", trustedContacts).apply()
    }

    private fun startSafeWalkTimer() {
        isSafeWalkActive = true
        safeWalkTimer = object : CountDownTimer(15 * 60 * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                safeWalkRemainingTime = String.format("%02d:%02d", minutes, seconds)
            }
            override fun onFinish() {
                isSafeWalkActive = false
                emergencyManager.triggerEmergency()
            }
        }.start()
        Toast.makeText(this, "Safe Walk Timer Started (15m)", Toast.LENGTH_SHORT).show()
    }

    private fun stopSafeWalkTimer() {
        safeWalkTimer?.cancel()
        isSafeWalkActive = false
        Toast.makeText(this, "Safe Walk Timer Stopped", Toast.LENGTH_SHORT).show()
    }

    private fun triggerFakeCall() {
        Toast.makeText(this, "Fake call scheduled in 30s", Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, FakeCallActivity::class.java))
        }, 30000)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(AudioDetectionService.ACTION_AUDIO_UPDATE)
            addAction(AudioDetectionService.ACTION_EMERGENCY_CONFIRM)
            addAction(ACTION_EMERGENCY_STARTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(audioUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(audioUpdateReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(audioUpdateReceiver)
    }
}
