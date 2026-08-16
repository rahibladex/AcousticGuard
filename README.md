# AcousticGuard

AcousticGuard is an open-source Android application designed for personal safety. It uses AI-powered audio detection to monitor the environment for emergency sounds (like screams or loud shouting) and automatically triggers safety protocols.

## Features

- **AI Audio Detection**: Real-time monitoring of ambient sound levels and event classification.
- **Adjustable Sensitivity**: Users can now set the detection threshold (dB) via a seeker bar to work in quieter or noisier environments.
- **Safety Mode**: Activate monitoring with a single tap.
- **Emergency Countdown**: A 5-second countdown with **tactile vibration feedback** allows users to feel the trigger even if the phone is in their pocket.
- **Automated Alerts**:
  - **High-Intensity Alarm**: Automatically forces system alarm volume to maximum for audible distress signaling.
  - **Accurate Location Tracking**: Prioritizes fresh GPS fixes for high-accuracy location sharing.
  - Flashing flashlight for visual distress signaling.
  - Automatic SMS with real-time Google Maps location to trusted contacts.
- **Trusted Contacts Management**: Easily add and manage phone numbers for emergency alerts.

## Download

You can download the latest APK from the [Releases](https://github.com/rahibladex/AcousticGuard/releases) page.

## Tech Stack

- **Kotlin**: Core application logic.
- **TensorFlow Lite**: AI model inference (Prototype uses mock logic; add `model.tflite` to assets for full functionality).
- **Material 3**: Modern Android UI components.
- **Foreground Services**: Ensuring reliable background monitoring.

## Version History

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
