# AcousticGuard

AcousticGuard is an open-source Android application designed for personal safety. It uses AI-powered audio detection to monitor the environment for emergency sounds (like screams or loud shouting) and automatically triggers safety protocols.

## Features

- **AI Audio & Voice Detection**: Real-time monitoring of ambient sound levels with voice-activated SOS support.
- **Adjustable Sensitivity**: Users can set the detection threshold (dB) via a seeker bar.
- **Silent SOS Mode**: Trigger alerts (SMS + Location) discreetly without loud alarms or flashing lights.
- **Safety Tools**:
  - **Fake Call**: Schedule a simulated incoming call to escape uncomfortable situations.
  - **Safe Walk Timer**: A countdown timer that triggers SOS if not checked-in or cancelled.
- **Automated Alerts**:
  - **High-Intensity Alarm**: Forces system alarm volume to 100% for audible distress.
  - **Proof-of-Event**: Automatically records audio during an emergency for evidence.
  - **Low Battery SOS**: Notifies contacts when the battery drops below 5%.
  - **Location Sharing**: Sends fresh high-accuracy GPS coordinates via SMS.
- **Emergency Countdown**: 5-second countdown with **vibration feedback** before triggering actions.
- **Trusted Contacts Management**: Easily manage your circle of safety.
- **Material 3 UI**: Modern, smooth interface with full Dark Mode support.

## Download

You can download the latest APK from the [Releases](https://github.com/rahibladex/AcousticGuard/releases) page.

## Tech Stack

- **Kotlin**: Core application logic.
- **TensorFlow Lite**: AI model inference (Prototype uses mock logic; add `model.tflite` to assets for full functionality).
- **Material 3**: Modern Android UI components.
- **Foreground Services**: Ensuring reliable background monitoring.

## Version History

### v3.0.0 (The Update Update)
- **Automatic Updates**: Check for and install the latest app versions directly from the Settings menu.
- **Improved Reliability**: Refined background service stability for long-term monitoring.
- **Enhanced Security**: Integrated FileProvider for secure APK installations.

### v2.0.2 (Maintenance & UX)
- **Stop Alarm Option**: Added a dedicated button to silence the high-intensity alarm once safety is confirmed, without terminating the entire emergency session.
- **Improved UI States**: Clearer visual feedback when safety features are active.

### v2.0.0 (Major Feature Update)
- **Voice SOS**: Trigger emergencies using voice keywords.
- **Silent SOS**: Discreet alerting without alarm/flashlight.
- **Safe Walk Timer**: Scheduled SOS trigger for late-night commutes.
- **Fake Call**: Simulated incoming call tool.
- **Low Battery Alert**: Auto-notify contacts when battery is < 5%.
- **Audio Recording**: Automated "Proof-of-Event" recording during emergencies.

### v1.7.0 (Redesign)
- **Jetpack Compose Migration**: Redesigned UI with Material 3.
- **Pulsing Safety Toggle**: Modernized active state indicators.
- **Improved Settings & Themes**: Functional dark/light mode support.

### v1.6.0 (Motion Trigger)
- **Shake-to-SOS**: Trigger an emergency instantly by vigorously shaking the device.
- Uses advanced accelerometer filtering to prevent accidental triggers while walking.

### v1.5.0 (Bug Fixes)
- Fixed a bug where multiple location SMS messages were sent for a single emergency event.

### v1.4.0 (UX & Control)
- **App Themes**: Full support for Dark Mode and Light Mode with a manual toggle in Settings.
- **Contact Management**: Improved Trusted Contacts dialog with the ability to remove existing contacts.
- **Dedicated Settings Screen**: New activity to toggle specific emergency features (Alarm, Flashlight, Vibration).

### v1.3.0 (Reliability & Intensity)
- **Improved GPS Accuracy**: Prioritizes fresh GPS fixes for precise high-accuracy location sharing.
- **High-Intensity Alarm**: Automatically forces system alarm volume to 100% when triggered.

### v1.2.0 (Customization & Feedback)
- **Adjustable Sensitivity**: New dB threshold slider to calibrate audio detection for different environments.
- **Countdown Vibration**: Continuous haptic feedback during the 5-second safety countdown.

### v1.0.0 (Initial Release)
- Core safety monitoring service with AI audio classification.
- Automated Emergency Protocol: Loud alarm, flashing flashlight, and SMS alerts.
- Basic Trusted Contacts management.
- Resolved initial Gradle and Environment compatibility issues.

## Permissions Required

- `RECORD_AUDIO`: For real-time sound monitoring.
- `ACCESS_FINE_LOCATION`: To send accurate coordinates during an emergency.
- `SEND_SMS`: To notify your trusted contacts.
- `CAMERA`: To use the flashlight for distress signaling.

## License

This project is open-source and available under the [MIT License](LICENSE).
