# Carlo 🚗

A friendly Android app for keeping track of your cars: service schedules, expenses and reminders, all in one tidy place. Carlo works fully offline. Your data lives on your phone, and you can back it up to Google Drive (or anywhere else) whenever you like.

Built with Kotlin, Jetpack Compose (Material 3), Room and SceneView (Google Filament).

## Download

Every push to `main` builds a fresh APK on GitHub Actions.

1. Open the repo's **Releases** page and pick **Carlo (latest build)**.
2. Download `carlo-debug.apk` on your phone.
3. Open it and allow "install unknown apps" for your browser or file manager when Android asks.

It is a debug-signed build for personal use. It is not a Play Store release.

## Features

- **Garage:** switch between vehicles, bump the odometer with `+500 mi`, and see an interactive 3D model of your car.
- **Health at a glance:** a status pill (green / amber / coral) and a progress card for every maintenance item.
- **Expenses:** lifetime cost, cost per mile, MPG, a category donut chart and a full history.
- **Reminders:** schedule a notification for any date and time, or tap the bell on a maintenance card to pre-fill one. Reminders survive reboots.
- **Backup and restore:** menu → *Back up (share JSON)* opens the Android share sheet, so you can send the file to Drive, Dropbox, email and so on. *Import backup* loads one back in. Importing merges: rows with the same ID are overwritten and everything else is kept.

## Add a 3D model of your car

Carlo shows a `.glb` model on the dashboard. Until you add one, it shows a placeholder.

1. Find a `.glb` file for your vehicle (see the prompt below).
2. Name it `vw_golf_mk7.glb` and put it in `app/src/main/assets/`. That is the default filename the app looks for. To use a different name, change the default in `Car3DView.kt`.
3. Rebuild the app. If you use the GitHub download, push the file to `main` and wait for the build to finish.

Good models are under about 15 MB, face the camera sideways or three-quarter on, and are licensed so you can use them (CC0 or CC-BY). Credit the author if the license asks for it.

### Prompt: ask an LLM to find a model for your car

Copy this into an LLM that can browse the web. Fill in your car at the top.

```text
I'm building an Android app that shows a 3D model of my car, and I need a .glb file for it.

My vehicle: <year> <make> <model> <trim, e.g. 2016 Volkswagen Golf TSI>

Please search for a 3D model that I can legally download and use in a personal app. Look at Sketchfab, Poly Pizza, Free3D, CGTrader (free section), TurboSquid (free section), Kenney, and the GitHub/Khronos glTF sample models, and anywhere else that fits.

Requirements:
- Format: .glb (binary glTF). If only .gltf, .fbx, .obj or .blend is offered, tell me and give me the conversion steps.
- Closest match to my exact car and generation. If there is no exact match, give the closest alternative and say how it differs.
- License must allow personal use: CC0 or CC-BY preferred. Tell me exactly what attribution I need to give.
- File size under 15 MB if possible, and low to medium polygon count (under about 300k triangles) so it runs smoothly on a phone.
- Reasonably clean model: single car, no big scene or background, wheels attached.

For each of your top 3 to 5 candidates, give me:
1. Name and a direct link to the page
2. Author and the license (with a link to the license text)
3. Whether it downloads as .glb, plus file size and triangle count if shown
4. How well it matches my car, in one sentence
5. Any catch (needs a free account, paid, wrong generation, very high poly)

Then tell me which one you recommend and the exact steps to download it. Only give links you actually found. If you can't verify something, say so instead of guessing.
```

If the best model you find is in another format, a free tool like [Blender](https://www.blender.org/) (File → Export → glTF 2.0, choose *glTF Binary (.glb)*) will convert it. For heavy files, the [gltf.report](https://gltf.report/) optimizer can shrink them.

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
| Backup/restore JSON | `data/Backup.kt` |
| Screens and dialogs | `ui/screens/` |
| 3D viewport and donut chart | `ui/components/` |
| Reminder alarms and notifications | `notifications/` |
| CI build and release | `.github/workflows/build-apk.yml` |

Reminders use an inexact alarm, so no special exact-alarm permission is needed. A notification can arrive a few minutes late on some phones. On Android 13 and later, Carlo asks for notification permission when you add your first reminder.
