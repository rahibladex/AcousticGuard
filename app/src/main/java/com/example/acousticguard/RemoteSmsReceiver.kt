package com.example.acousticguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class RemoteSmsReceiver : BroadcastReceiver() {

    companion object {
        const val TRIGGER_KEYWORD = "[TEJASHWINI_SOS_TRIGGER]"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val prefs = context.getSharedPreferences("NariShaktiSOSPrefs", Context.MODE_PRIVATE)
            val isRemoteAlarmEnabled = prefs.getBoolean("allow_remote_alarm", true)

            if (!isRemoteAlarmEnabled) return

            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (message in messages) {
                val body = message.displayMessageBody
                val sender = message.displayOriginatingAddress

                if (body != null && body.contains(TRIGGER_KEYWORD)) {
                    Log.i("RemoteSmsReceiver", "SOS Trigger detected from $sender")
                    
                    // Verify if sender is a trusted contact (highly recommended)
                    val trustedContacts = prefs.getStringSet("trusted_contacts", setOf()) ?: setOf()
                    
                    // Normalize number for comparison (strip + and spaces if necessary)
                    val isTrusted = trustedContacts.any { it.replace(" ", "").contains(sender.replace(" ", "").takeLast(10)) }

                    if (isTrusted) {
                        val emergencyManager = EmergencyManager(context)
                        emergencyManager.startRemoteAlarm()
                    } else {
                        Log.w("RemoteSmsReceiver", "Trigger received but sender $sender is not in trusted contacts.")
                    }
                }
            }
        }
    }
}
