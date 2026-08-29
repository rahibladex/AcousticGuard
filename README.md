# TEJASHWINI (NariShakti SOS / AcousticGuard)

**TEJASHWINI** is an open-source Android personal safety and distress defense application. It uses **On-Device Edge AI audio inference**, **Shake-to-SOS motion sensors**, **real-time satellite GPS beacons**, and **Remote SOS Alarm Sync** to protect women and individuals in distress.

---

## 🌟 Key Features

- **🧠 Edge AI Audio & Scream Detection**: Real-time monitoring of ambient sound buffers via TensorFlow Lite to classify distress screams and shout decibel spikes locally without cloud latency or privacy exposure.
- **⚡ Shake-to-SOS Motion Trigger**: Discreetly activates emergency protocols by vigorously shaking the phone inside a pocket or purse without unlocking the screen.
- **🚨 Remote SOS Alarm Sync (Guardian Ringing)**:
  - When an emergency SOS is triggered, your trusted contacts' phones will **automatically ring loud sirens at 100% max volume** (overriding Silent and Do-Not-Disturb modes).
  - Wakes up the guardian's screen with `WakeLock`, posts high-priority Heads-Up Notifications, and displays a pulsing alert dialog with a **"VIEW LIVE LOCATION"** one-tap button.
- **📍 Google Fused Live Satellite GPS Tracking**: Automatically generates universal Google Maps links (`https://maps.google.com/?q=lat,lng`) and continuously transmits fresh location beacons every 2 minutes.
- **🚶 Safe Walk Transit Timer**: Configurable countdown timer (5m, 15m, 30m, 60m) that automatically dispatches emergency alerts if you do not check in safely before arrival.
- **📞 Decoy Fake Call**: Simulates an authentic full-screen incoming phone call to gracefully exit threatening or uncomfortable situations.
- **🔕 Stealth / Silent SOS Mode**: Disables local sirens and camera strobes while silently broadcasting GPS coordinates to guardians.
- **🔋 Low Battery Emergency Beacon**: Automatically broadcasts an urgent alert with your last known GPS coordinates when battery drops below 5%.
- **⏱️ 5-Second False-Alarm Buffer**: Prominent countdown with continuous vibration to abort accidental triggers safely.
- **🔄 In-App GitHub Auto-Updater**: One-tap version checking, background APK downloading, and seamless system package installation.
- **🎨 Pure Dark Theme**: High-contrast, battery-efficient dark theme interface built with Jetpack Compose.

---

## 📥 Download

Download the latest APK release from the [GitHub Releases](https://github.com/rahibladex/AcousticGuard/releases) page.

---

## 🛠️ Tech Stack

- **Kotlin & Jetpack Compose**: Modern declarative Android UI.
- **TensorFlow Lite**: On-device audio classification.
- **Google Play Services Location**: Fused Location Provider.
- **Android Camera2 API**: Multi-camera hardware strobe.
- **Android Telephony & SMS**: Automated emergency dispatch and Remote SOS trigger broadcasting.
- **Foreground Services & WakeLocks**: Reliable 24/7 background protection.

---

## 📜 Version History

### v8.0.0 (The Ironclad Concurrency & OEM Protection Release) — *Latest*
- **🎙️ Instant Hardware Microphone Release**:
  - Rewrote `AudioDetectionService` with `@Volatile` recording states, thread-safe synchronized locks, and immediate `AudioRecord.stop()` and `release()` on STOP TRACKING.
  - Resolved UI race conditions so `AI Detection: OFF` resets instantaneously.
- **⚡ Background Battery Optimization Exemption**:
  - Added dedicated **Background Protection** card under Settings with 1-tap whitelisting from aggressive OEM battery killers (**Samsung OneUI, Xiaomi HyperOS, OnePlus OxygenOS, Vivo/Oppo**).
- **🛡️ Full Android 14 (API 34) Foreground Service Compliance**:
  - Declared `FOREGROUND_SERVICE_MICROPHONE`, `FOREGROUND_SERVICE_LOCATION`, and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
  - Service initialization strictly passes bitwise-OR matching types into `ServiceCompat.startForeground`.
- **📍 Verified GPS Link Validation & Dual-Phase SMS Dispatch**:
  - Added strict coordinate validation preventing invalid `(0, 0)` or null Google Maps links.
  - Implemented automatic fast 3.5s retry for instant indoors fix with immediate follow-up SMS.
- **🔒 Thread-Safe Hardware Strobes & AudioFocus Contention Handling**:
  - Added explicit thread `.join()` on Camera2 strobe thread and ToneGenerator shutoff to eliminate asynchronous HAL race conditions.
  - Integrated `AudioManager.OnAudioFocusChangeListener` and `AudioFocusRequest` to gracefully pause and resume during phone calls.
- **⚡ Zero-ANR Async TFLite Inference**:
  - Converted model loading in `AudioClassifier` to single-thread background executors preventing main-thread blocking.

### v7.0.0 (The Ultimate Guardian & Stability Release)
- **🚨 Remote SOS Siren Sync**:
  - Implemented `RemoteAlertService` foreground service with `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `WAKE_LOCK`.
  - Automatically forces `AudioManager.STREAM_ALARM` to 100% max volume with `FLAG_AUDIBILITY_ENFORCED`.
  - Added continuous emergency vibration and high-priority heads-up notifications with direct map links.
  - Added real-time SMS trigger receiver (`[TEJASHWINI_SOS_TRIGGER]`) with phone number normalization and safety verification.
- **📍 Google Fused GPS & Live Location Links**:
  - Fixed location sharing to always provide valid, live Google Maps links (`https://maps.google.com/?q=lat,lng`).
  - Added recurring 2-minute live location beacon updates.
- **🌙 Permanent Dark Theme**:
  - Removed theme switching and locked the app to a high-contrast dark theme matching AMOLED displays.
- **⚡ Immediate Hardware Shut-Off**:
  - Rewrote `EmergencyManager` with `@Volatile` companion state and thread-safe interruption.
  - Tapping "STOP EMERGENCY", "I AM SAFE", or toggling off features in Settings immediately shuts off the camera torch and terminates tone generators.
- **🔄 In-App Updater Fixes**:
  - Fixed background thread Looper/Toast crash (`Can't toast on a thread that has not called Looper.prepare()`).
  - Added required GitHub REST API headers and Android 8.0+ `ACTION_MANAGE_UNKNOWN_APP_SOURCES` permission handling.
- **📽️ Presentation Website**:
  - Created an interactive showcase and slide deck presentation website inside `website/`.

### v6.1.0 (The Stability & UI Fix)
- **Theme Fixes**: Migrated theme switching to Compose-native state. Fixed black screen/ANR issues on startup.
- **Gradle Recovery**: Restored missing Gradle wrapper files.

### v6.0.0 (The Sync & Call Update)
- **Remote Alarm Sync**: Initial prototype for remote SOS triggering via SMS.
- **Automatic Emergency Call**: Automatically initiates a call to the first trusted contact.

### v3.0.0 (The Update Update)
- **Automatic Updates**: In-app APK updates via GitHub Releases.
- **FileProvider Integration**: Secure package installation.

### v2.0.0 (Major Feature Update)
- **Safe Walk Timer**, **Fake Call**, **Low Battery Alert**, and **Proof-of-Event Audio Recording**.

### v1.0.0 (Initial Release)
- Core AI audio distress monitoring and automated emergency protocols.

---

## 🔒 Permissions Required

- `RECORD_AUDIO`: For real-time on-device sound monitoring.
- `ACCESS_FINE_LOCATION`: To send live GPS coordinates.
- `SEND_SMS`: To broadcast emergency alerts to trusted contacts.
- `RECEIVE_SMS` & `READ_SMS`: For Remote SOS Alarm synchronization.
- `CALL_PHONE`: To trigger emergency calls to primary guardians.
- `CAMERA`: For emergency flashing flashlight strobe.
- `WAKE_LOCK`: To wake screen during remote emergency alerts.
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: For loud remote alarm siren playback.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
