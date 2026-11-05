# Carlo 🚗

A friendly Android app for keeping track of your cars: service schedules, expenses and reminders, all in one tidy place. Carlo works fully offline. Your data lives on your phone, and you can back it up to Google Drive (or anywhere else) whenever you like.

Built with Kotlin, Jetpack Compose (Material 3) and Room.

## Download

Every push to `main` builds a fresh APK on GitHub Actions.

1. Open the repo's **Releases** page and pick **Carlo (latest build)**.
2. Download `carlo-debug.apk` on your phone.
3. Open it and allow "install unknown apps" for your browser or file manager when Android asks.

It is a debug-signed build for personal use. It is not a Play Store release.

## Features

Everything is metric (km, litres, L/100 km) and money is in Canadian dollars. Carlo starts empty: you add your own vehicles and details.

- **Welcome screen:** shown until you add your first vehicle (or restore a backup).
- **Garage:** switch between vehicles, tap the odometer to type in the new reading, and add a photo of your car. Tap the photo to see it full-screen.
- **Tap any card to enlarge it.** Maintenance cards open to show the interval, last and next due km, and actions (done now, remind me, edit, delete). Expense and reminder cards and the summary charts expand too.
- **Health at a glance:** a status pill (green / amber / coral) and a progress bar for every maintenance item.
- **Expenses:** lifetime cost, cost per km, fuel use (L/100 km), a category donut chart and a full history.
- **Tires:** a top-down car whose wheels are coloured by season (sun for summer, snow for winter). Keep several tire sets, see which one is on the car and how many km each has done, and swap them in a tap. Log rotation, balancing, alignment and punctures. Tap a wheel to see its punctures. Care tiles show how long ago each service was done.
- **Reminders:** schedule a notification for any date and time, or tap *Remind me* on a maintenance card to pre-fill one. Reminders survive reboots.
- **Settings:**
  - *App:* how close to due a service turns amber, the default reminder time, and backup/import.
  - *Vehicle:* photo, edit details, set odometer, add another vehicle, delete a vehicle.
- **Backup and restore:** Settings → *Back up* opens the Android share sheet, so you can send the JSON file to Drive, Dropbox, email and so on. *Import* loads one back in. Importing merges: rows with the same ID are overwritten and everything else is kept. Tire sets and history are included; photos aren't.

## Build it yourself

You need Android Studio (or JDK 17 and the Android SDK).

```bash
./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/app-debug.apk`.

## How it fits together

| Area | Where |
| :--- | :--- |
| Room entities, DAOs, stats and health rules | `app/src/main/java/com/garage/tracker/data/` |
| Backup/restore JSON, app settings | `data/Backup.kt`, `data/SettingsStore.kt` |
| Screens and dialogs | `ui/screens/` |
| Vehicle photo and donut chart | `ui/components/` |
| Reminder alarms and notifications | `notifications/` |
| CI build and release | `.github/workflows/build-apk.yml` |

Reminders use an inexact alarm, so no special exact-alarm permission is needed. A notification can arrive a few minutes late on some phones. On Android 13 and later, Carlo asks for notification permission when you add your first reminder.
