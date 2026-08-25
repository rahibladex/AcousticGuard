package com.example.acousticguard

import android.os.Bundle
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.appcompat.app.AppCompatActivity
import com.example.acousticguard.databinding.ActivityFakeCallXmlBinding

class FakeCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFakeCallXmlBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFakeCallXmlBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Pulse animation for status text
        val pulse = AlphaAnimation(0.2f, 1.0f).apply {
            duration = 1000
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        binding.callStatus.startAnimation(pulse)

        binding.btnDecline.setOnClickListener {
            finish()
        }

        binding.btnAccept.setOnClickListener {
            binding.callStatus.clearAnimation()
            binding.callStatus.text = "Connected"
            // In a real app, we'd play a sound or show a call timer
        }
    }
}
