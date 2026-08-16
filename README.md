# AcousticGuard

AcousticGuard is an open-source Android application designed for personal safety. It uses AI-powered audio detection to monitor the environment for emergency sounds (like screams or loud shouting) and automatically triggers safety protocols.

## Features

- **AI Audio Detection**: Real-time monitoring of ambient sound levels and event classification.
- **Adjustable Sensitivity**: Users can now set the detection threshold (dB) via a seeker bar to work in quieter or noisier environments.
- **Safety Mode**: Activate monitoring with a single tap.
- **Emergency Countdown**: A 5-second countdown with **tactile vibration feedback** allows users to feel the trigger even if the phone is in their pocket.
- **Automated Alerts**:
  - CDMA-style emergency alarm.
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

## Permissions Required

- `RECORD_AUDIO`: For real-time sound monitoring.
- `ACCESS_FINE_LOCATION`: To send accurate coordinates during an emergency.
- `SEND_SMS`: To notify your trusted contacts.
- `CAMERA`: To use the flashlight for distress signaling.

## License

This project is open-source and available under the [MIT License](LICENSE).
