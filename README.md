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
- **Home hub:** the first screen shows your car's photo, an attention banner ("Everything looks good" or the most urgent thing to fix, such as an overdue service, low tread or thin brake pads), and a tile for every section with a live status: Maintenance, Expenses, Tires, Brakes, Car wash and Reminders. Tiles ease in one after another and dip slightly when pressed. Tap the odometer on the home screen to type in a new reading, or use the switcher to change vehicle. The bottom bar is just Home, Reminders and Settings.
- **Tap any card to enlarge it.** Maintenance cards open to show the interval, last and next due km, and actions (done now, remind me, edit, delete). Expense and reminder cards and the summary charts expand too.
- **Car washes:** the Car wash section shows when you last washed the car. Tap *Log a wash* to add a date and price. Washes count in your expenses.
- **Health at a glance:** a status pill (green / amber / coral) and a progress bar for every maintenance item.
- **Expenses:** pick a category (or "Other" and say what it was for, like a remote starter). Lifetime cost, cost per km, fuel use (L/100 km), a category donut chart and a full history.
- **Tires:** a top-down car whose wheels are coloured by season (sun for summer, snow for winter), with the latest tread depth printed on each wheel. Keep several tire sets, see which one is on the car and how many km each has done, and use **Swap tires** to switch (for example summer to winter) with the date, odometer and what it cost. Log rotation, balancing, alignment, punctures and tread depth in mm; Carlo shows wear per 1,000 km and an estimate of how far you can go before replacement. A swap counts as a rotation, so the Rotation tile stays green until it's time to swap again (winter tires mid-October to mid-April).
- **Brakes** (its own section): log brake pad thickness in mm for the front and rear, and when new pads go on. Gauges turn green, amber and coral, with an estimate of the km left before ~3 mm.
- **Monthly insurance:** the *Insurance premium* card at the top of Expenses (also under Settings). Set the amount and the payment date and Carlo adds it to your expenses every month by itself, including any months that passed while the app was closed.
- **Edit anything you logged:** expenses, car washes, tire events, brake entries and services all have an Edit button when you open their card.
- **Reminders:** schedule a notification for any date and time, or tap *Remind me* on a maintenance card to pre-fill one. Reminders survive reboots.
- **Dark mode:** Settings → Appearance: System, Light or Dark.
- **Your own colours:** in the vehicle form, the last colour swatch (the rainbow one) opens a colour picker. Save a colour to "my colours" and it appears as a swatch for every vehicle. Long-press a saved colour to remove it.
- **Settings:**
  - *App:* how close to due a service turns amber, the default reminder time, and backup/import.
  - *Vehicle:* photo, edit details, set odometer, add another vehicle, delete a vehicle.
- **Automatic backup (survives uninstalling):** Settings → *Automatic backup*. Choose a folder (for example Documents) and Carlo saves your vehicles, history, photos and settings there after every change. After reinstalling, tap *Restore from my backup folder* on the welcome screen and pick the same folder. Carlo never overwrites a backup while the garage is empty.
- **Backup and restore:** Settings → *Back up* opens the Android share sheet, so you can send the JSON file to Drive, Dropbox, email and so on. *Import* loads one back in. Importing merges: rows with the same ID are overwritten and everything else is kept. Tire sets, tread and brake history and the insurance plan are included; photos aren't.

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
| Screens, dialogs and the home hub | `ui/screens/` (`HomeScreen.kt`, `HomeStatus.kt`) |
| Vehicle photo and donut chart | `ui/components/` |
| Reminder alarms and notifications | `notifications/` |
| CI build and release | `.github/workflows/build-apk.yml` |

Reminders use an inexact alarm, so no special exact-alarm permission is needed. A notification can arrive a few minutes late on some phones. On Android 13 and later, Carlo asks for notification permission when you add your first reminder.
