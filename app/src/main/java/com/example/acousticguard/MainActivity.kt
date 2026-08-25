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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.acousticguard.ui.theme.*

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
            NariShaktiSOSTheme {
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
        val prefs = getSharedPreferences("NariShaktiSOSPrefs", Context.MODE_PRIVATE)
        trustedContacts = prefs.getStringSet("trusted_contacts", setOf()) ?: setOf()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreenContent() {
        val backgroundColor by animateColorAsState(
            targetValue = when {
                isEmergencyMode -> RedEmergency.copy(alpha = 0.8f)
                isSafetyModeActive -> BlueProtection.copy(alpha = 0.8f)
                else -> DarkBg
            },
            animationSpec = tween(1000), label = "bgColor"
        )

        Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
            // Subtle animated background pattern
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f))
                        )
                    )
            )

            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    CenterAlignedTopAppBar(
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                        title = { Text("NARISHAKTI SOS", fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = Color.White) },
                        actions = {
                            IconButton(onClick = { 
                                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                            }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
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
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    MainToggleButton()

                    StatusDashboard()

                    QuickActions()

                    SafetyToolsSection()

                    ContactsSection()
                }
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
        val haptic = LocalHapticFeedback.current
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.9f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "scale"
        )

        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (isSafetyModeActive || isEmergencyMode) 1.3f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulse"
        )
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "alpha"
        )

        val buttonColor = when {
            isEmergencyMode -> RedEmergency
            isSafetyModeActive -> BlueProtection
            else -> GreenActive
        }

        Box(contentAlignment = Alignment.Center) {
            // Animated Pulse Halo
            if (isSafetyModeActive || isEmergencyMode) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                        .clip(CircleShape)
                        .background(buttonColor.copy(alpha = pulseAlpha))
                )
            }

            Surface(
                onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    handleToggleClick() 
                },
                modifier = Modifier
                    .size(160.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale),
                shape = CircleShape,
                color = buttonColor,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                interactionSource = interactionSource
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = if (isEmergencyMode) R.drawable.ic_warning else R.drawable.ic_shield),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when {
                            isEmergencyMode -> "STOP"
                            isSafetyModeActive -> "ACTIVE"
                            else -> "START"
                        },
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                }
            }
        }
    }

    @Composable
    fun StatusDashboard() {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusCard(modifier = Modifier.weight(1f), title = "AI Audio", status = aiStatus, icon = Icons.Default.Mic)
                StatusCard(modifier = Modifier.weight(1f), title = "Motion SOS", status = motionStatus, icon = Icons.Default.DirectionsRun)
            }
            StatusCard(modifier = Modifier.fillMaxWidth(), title = "Location Accuracy", status = gpsStatus, icon = Icons.Default.LocationOn)
        }
    }

    @Composable
    fun StatusCard(modifier: Modifier, title: String, status: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(20.dp),
            color = GlassWhite,
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(text = title, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    Text(text = status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White)
                }
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
            ActionBtn(
                modifier = Modifier.weight(1f),
                text = if (isAlarmActive) "STOP ALARM" else "ALARM",
                icon = rememberVectorPainter(Icons.Default.NotificationsActive),
                color = if (isAlarmActive) Color.DarkGray else RedEmergency,
                onClick = { emergencyManager.toggleAlarm() }
            )
            ActionBtn(
                modifier = Modifier.weight(1f),
                text = "LIGHT",
                icon = painterResource(id = R.drawable.ic_flashlight),
                color = Color(0xFF5856D6),
                onClick = { emergencyManager.toggleFlashlight() }
            )
        }
    }

    @Composable
    fun ActionBtn(modifier: Modifier, text: String, icon: androidx.compose.ui.graphics.painter.Painter, color: Color, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            modifier = modifier.height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = color),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }

    @Composable
    fun SafetyToolsSection() {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = GlassWhite,
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Handyman, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Safety Tools", fontWeight = FontWeight.Bold, color = Color.White)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ToolBtn(
                        modifier = Modifier.weight(1.2f),
                        text = if (isSafeWalkActive) "Stop $safeWalkRemainingTime" else "Safe Walk",
                        icon = Icons.Default.Timer,
                        active = isSafeWalkActive,
                        onClick = { if (isSafeWalkActive) stopSafeWalkTimer() else startSafeWalkTimer() }
                    )
                    ToolBtn(
                        modifier = Modifier.weight(1f),
                        text = "Fake Call",
                        icon = Icons.Default.AddIcCall,
                        active = false,
                        onClick = { triggerFakeCall() }
                    )
                }
            }
        }
    }

    @Composable
    fun ToolBtn(modifier: Modifier, text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onClick: () -> Unit) {
        val haptic = LocalHapticFeedback.current
        Button(
            onClick = { 
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick() 
            },
            modifier = modifier.height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (active) RedEmergency else Color.White.copy(alpha = 0.15f),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }

    @Composable
    fun ContactsSection() {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = GlassWhite,
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ShieldMoon, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Trusted Contacts", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    IconButton(onClick = { isAddingContact = true }) {
                        Icon(androidx.compose.material.icons.Icons.Default.AddCircle, contentDescription = "Add", tint = GreenActive)
                    }
                }
                
                if (trustedContacts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                        Text("No contacts secured yet", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.4f))
                    }
                } else {
                    Spacer(Modifier.height(10.dp))
                    trustedContacts.toList().forEach { contact ->
                        ContactItem(contact) { removeContact(contact) }
                    }
                }
            }
        }
    }

    @Composable
    fun ContactItem(contact: String, onRemove: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(BlueProtection.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Text(contact.take(1), color = BlueProtection, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text(contact, color = Color.White, fontWeight = FontWeight.Medium)
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, tint = RedEmergency.copy(alpha = 0.7f))
            }
        }
    }

    @Composable
    fun EmergencyModal() {
        Dialog(onDismissRequest = { /* Cannot dismiss */ }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                color = RedEmergency,
                shadowElevation = 24.dp
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "modalPulse")
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "scale"
                    )

                    Icon(
                        Icons.Default.ReportProblem, 
                        contentDescription = null, 
                        modifier = Modifier.size(80.dp).graphicsLayer(scaleX = scale, scaleY = scale),
                        tint = Color.White
                    )
                    
                    Text(
                        "EMERGENCY ACTIVE",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 26.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        "Protocol starts in",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 16.sp
                    )
                    
                    Text(
                        "$countdownValue",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 80.sp
                    )
                    
                    Button(
                        onClick = { cancelEmergency() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("I AM SAFE", fontWeight = FontWeight.Bold, fontSize = 18.sp)
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

