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
import androidx.compose.ui.graphics.vector.ImageVector
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
    private var gpsStatus by mutableStateOf("GPS : OFF")
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
                        "AI Detection:\nON"
                    } else {
                        "AI Detection:\nON ($event)"
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
                    color = DarkBg
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
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                    title = { Text("NariShakti SOS", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary) },
                    actions = {
                        IconButton(onClick = { 
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = LuxuryGold)
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.Start
            ) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    HeroSection()
                }

                item {
                    StartTrackingButton()
                }

                item {
                    StatusRow()
                }

                item {
                    SectionHeader("TRUSTED CONTACTS")
                    AddContactButton()
                    
                    if (trustedContacts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        trustedContacts.forEach { contact ->
                            ContactItem(contact) { removeContact(contact) }
                        }
                    }
                }

                item {
                    SectionHeader("LOCATION")
                    LocationCard()
                }

                item {
                    SectionHeader("SAFETY TOOLS")
                    SafetyToolsRow()
                    Spacer(modifier = Modifier.height(20.dp))
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
    fun HeroSection() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Apki Suraksha,\nHar kadam,\nHar pal....",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 32.sp,
                modifier = Modifier.weight(1f)
            )
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(SurfaceDark),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_shield),
                        contentDescription = null,
                        modifier = Modifier.size(50.dp),
                        tint = LuxuryGold
                    )
                }
                Text(
                    text = "Nari Shakti",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    @Composable
    fun StartTrackingButton() {
        val haptic = LocalHapticFeedback.current
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "scale")

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(LuxuryGold, Color(0xFFB8860B))))
                .clickable(interactionSource = interactionSource, indication = null) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    handleToggleClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isEmergencyMode) "STOP EMERGENCY" else if (isSafetyModeActive) "STOP TRACKING" else "START TRACKING",
                color = DarkBg,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }
    }

    @Composable
    fun StatusRow() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusCard(
                modifier = Modifier.weight(1f),
                title = "AI AUDIO",
                status = aiStatus,
                icon = Icons.Default.Mic
            )
            StatusCard(
                modifier = Modifier.weight(1f),
                title = "MOTION SOS",
                status = motionStatus,
                icon = Icons.Default.DirectionsRun
            )
        }
    }

    @Composable
    fun StatusCard(modifier: Modifier, title: String, status: String, icon: ImageVector) {
        Box(
            modifier = modifier
                .height(85.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .border(1.dp, LuxuryGold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = LuxuryGold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(title, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(status, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
            }
        }
    }

    @Composable
    fun SectionHeader(title: String) {
        Text(
            text = title,
            color = LuxuryGold,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
            letterSpacing = 1.sp
        )
    }

    @Composable
    fun AddContactButton() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceDark)
                .border(1.dp, LuxuryGold.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .clickable { isAddingContact = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add", tint = LuxuryGold, modifier = Modifier.size(30.dp))
        }
    }

    @Composable
    fun LocationCard() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .border(1.dp, LuxuryGold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = LuxuryGold, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Location Accuracy", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(gpsStatus, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    fun SafetyToolsRow() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ToolButton(
                modifier = Modifier.weight(1f),
                text = if (isSafeWalkActive) safeWalkRemainingTime else "Safe Walk",
                icon = Icons.Default.Timer,
                onClick = { if (isSafeWalkActive) stopSafeWalkTimer() else startSafeWalkTimer() }
            )
            ToolButton(
                modifier = Modifier.weight(1f),
                text = "Fake Call",
                icon = Icons.Default.AddIcCall,
                onClick = { triggerFakeCall() }
            )
        }
    }

    @Composable
    fun ToolButton(modifier: Modifier, text: String, icon: ImageVector, onClick: () -> Unit) {
        Box(
            modifier = modifier
                .height(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .border(1.dp, LuxuryGold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .clickable { onClick() }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = LuxuryGold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(text, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    fun ContactItem(contact: String, onRemove: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark.copy(alpha = 0.7f))
                .border(0.5.dp, TextSecondary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(contact, color = TextPrimary, fontWeight = FontWeight.Medium)
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, tint = RedEmergency.copy(alpha = 0.8f))
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
            containerColor = SurfaceDark,
            titleContentColor = LuxuryGold,
            textContentColor = TextPrimary,
            title = { Text("Add Trusted Contact") },
            text = {
                TextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkBg,
                        unfocusedContainerColor = DarkBg,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = LuxuryGold,
                        focusedIndicatorColor = LuxuryGold
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (phoneNumber.isNotEmpty()) {
                            val prefs = getSharedPreferences("NariShaktiSOSPrefs", Context.MODE_PRIVATE)
                            val current = trustedContacts.toMutableSet()
                            current.add(phoneNumber)
                            trustedContacts = current
                            prefs.edit().putStringSet("trusted_contacts", trustedContacts).apply()
                            isAddingContact = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryGold, contentColor = DarkBg)
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { isAddingContact = false }) { Text("Cancel", color = TextSecondary) }
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
        aiStatus = "AI Detection:\nON"
        gpsStatus = "GPS : ON"
        motionStatus = "Motion SOS:\nActive"
        
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
        aiStatus = "AI Detection:\nOFF"
        gpsStatus = "GPS : OFF"
        motionStatus = "Motion SOS:\nInactive"
        
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
        val prefs = getSharedPreferences("NariShaktiSOSPrefs", Context.MODE_PRIVATE)
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
