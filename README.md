# Garage Tracker

Android (Kotlin + Jetpack Compose, Material 3) app for tracking maintenance schedules and costs across multiple vehicles.

- **Dashboard:** vehicle switcher, `+500 mi` odometer button, interactive 3D car (SceneView/Filament), health pill, maintenance cards.
- **Expenses:** lifetime cost, cost per mile, MPG, category donut chart, history.
- **Storage:** Room (offline-first), synced with Google Sheets (tabs `Vehicles`, `Maintenance_Schedules`, `Expense_Logs`).

## Setup
1. Open in Android Studio (JDK 17). Let it generate the Gradle wrapper / sync.
2. Drop a `.glb` car model at `app/src/main/assets/vw_golf_mk7.glb` (a placeholder shows until then).
3. Google Sheets sync: create an OAuth Android client in Google Cloud Console (package `com.garage.tracker` + your debug SHA-1), enable the Sheets API, then tap the sync icon, sign in, and paste your spreadsheet ID.

Status: scaffolded without an Android SDK available, so it has not been compiled yet.
