# Carlo 🚗

A friendly Android app (Kotlin, Jetpack Compose, Material 3) for tracking maintenance, costs and reminders across all the vehicles in your garage. Everything is stored locally on the device with Room, so it works fully offline.

## Features
- **Garage:** vehicle switcher, `+500 mi` odometer button, interactive 3D car (SceneView/Filament), health pill (green / amber / coral) and maintenance cards.
- **Expenses:** lifetime cost, cost per mile, MPG, category donut chart and history.
- **Reminders:** tap the bell on any maintenance card to pre-fill one, or schedule a notification for any date and time (oil change, insurance renewal…). They survive reboots.
- **Backup:** menu → *Back up (share JSON)* opens the Android share sheet, so you can save the file to Google Drive, Dropbox, email, etc. *Import backup* reads one back in. Importing merges: rows with the same ID are overwritten, everything else is kept.

## Setup
1. Open in Android Studio (JDK 17) and let it sync.
2. Put a `.glb` car model at `app/src/main/assets/vw_golf_mk7.glb` (a placeholder is shown until then).

Reminders use an inexact alarm, so no special exact-alarm permission is needed; a notification can arrive a few minutes late on some devices. On Android 13+ the app asks for notification permission when you add your first reminder.

Status: written without an Android SDK available, so it has not been compiled yet.
