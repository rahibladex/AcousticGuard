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

            if (!isRemoteAlarmEnabled) {
                Log.i("RemoteSmsReceiver", "Remote alarm is disabled in settings.")
                return
            }

            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (message in messages) {
                val body = message.displayMessageBody ?: continue
                val sender = message.displayOriginatingAddress ?: "Unknown"

                if (body.contains(TRIGGER_KEYWORD)) {
                    Log.i("RemoteSmsReceiver", "SOS Trigger keyword detected from sender: $sender")

                    val trustedContacts = prefs.getStringSet("trusted_contacts", setOf()) ?: setOf()
                    val senderDigits = sender.filter { it.isDigit() }

                    val isTrusted = if (trustedContacts.isEmpty()) {
                        // If no specific contacts restricted, allow remote alarm to ring
                        true
                    } else {
                        trustedContacts.any { contact ->
                            val contactDigits = contact.filter { it.isDigit() }
                            contactDigits.isNotEmpty() && (
                                contactDigits == senderDigits ||
                                (contactDigits.length >= 7 && senderDigits.endsWith(contactDigits.takeLast(10))) ||
                                (senderDigits.length >= 7 && contactDigits.endsWith(senderDigits.takeLast(10)))
                            )
                        }
                    }

                    if (isTrusted) {
                        Log.i("RemoteSmsReceiver", "Sender $sender verified. Starting RemoteAlertService loud alarm!")
                        val mapsUrlRegex = Regex("""https://maps\.google\.com/\?[^\s()]+""")
                        val mapsUrl = mapsUrlRegex.find(body)?.value ?: ""
                        RemoteAlertService.startAlert(context, sender, mapsUrl, body)
                    } else {
                        Log.w("RemoteSmsReceiver", "Trigger received but sender $sender did not match configured trusted contacts: $trustedContacts")
                    }
                }
            }
        }
    }
}

