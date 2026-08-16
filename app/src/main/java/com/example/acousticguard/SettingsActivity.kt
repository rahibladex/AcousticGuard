package com.example.acousticguard

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("AcousticGuardPrefs", Context.MODE_PRIVATE)

        val rgTheme = findViewById<RadioGroup>(R.id.rgTheme)
        val cbFlashlight = findViewById<CheckBox>(R.id.cbFlashlight)
        val cbAlarm = findViewById<CheckBox>(R.id.cbAlarm)
        val cbVibration = findViewById<CheckBox>(R.id.cbVibration)
        val btnBack = findViewById<Button>(R.id.btnBack)

        // Load saved settings
        val themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        when (themeMode) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> rgTheme.check(R.id.rbSystem)
            AppCompatDelegate.MODE_NIGHT_NO -> rgTheme.check(R.id.rbLight)
            AppCompatDelegate.MODE_NIGHT_YES -> rgTheme.check(R.id.rbDark)
        }

        cbFlashlight.isChecked = prefs.getBoolean("emergency_flashlight", true)
        cbAlarm.isChecked = prefs.getBoolean("emergency_alarm", true)
        cbVibration.isChecked = prefs.getBoolean("emergency_vibration", true)

        rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.rbDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            prefs.edit().putInt("theme_mode", mode).apply()
            AppCompatDelegate.setDefaultNightMode(mode)
        }

        cbFlashlight.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("emergency_flashlight", isChecked).apply()
        }

        cbAlarm.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("emergency_alarm", isChecked).apply()
        }

        cbVibration.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("emergency_vibration", isChecked).apply()
        }

        btnBack.setOnClickListener {
            finish()
        }
    }
}
