# Carlo web app (PWA)

The iPhone-friendly version of Carlo. It is a Progressive Web App: open the site in Safari, tap
**Share → Add to Home Screen**, and it runs full-screen like an app and works offline.

Data lives in the browser (IndexedDB, via Dexie). Nothing is sent to a server. It reads and writes the
same backup file as the Android app (see [`../docs/backup-format.md`](../docs/backup-format.md)).

## Develop
```bash
cd web
npm install
npm run dev        # http://localhost:5173
npm run build      # type-checks, then builds to web/dist
```
`VITE_BASE` sets the URL prefix; GitHub Pages uses `/<repo-name>/` and the deploy workflow sets it.

## Where things are
| Area | File(s) |
| :--- | :--- |
| Data types (mirror the Android entities) | `src/types.ts` |
| Local database | `src/db.ts`, `src/store.ts` |
| Business rules (health, tread, brakes, alerts, tiles) | `src/logic/` |
| Backup import/export (Android-compatible) | `src/backup.ts` |
| Screens | `src/screens/` |
| Dialogs | `src/dialogs.tsx` |
| Styles and light/dark palette | `src/styles.css` |

## Status

**Everything on the home hub now works:** Maintenance (with a history of completed services: date, odometer,
cost and notes), Expenses (with "Other", donut chart and monthly insurance that adds itself), **Tires**
(wheel diagram with tread mm on each wheel, tire sets, seasonal swaps with a cost, punctures, rotation that
stays green until the next swap), **Brakes** (pad thickness with the thick/thin pad visual and a wear
estimate), Car wash, Notes, and **Reminders**. Costs entered on a service, tire or brake entry are added to
Expenses automatically and stay in sync when you edit or delete the entry.

Also: the **?** install help, light/dark/system theme, your own colours, backup export/import compatible
with Android, installable PWA with offline support.

**Reminders on iPhone:** browsers can't schedule alerts while the app is closed, so each reminder has
**Add to calendar**, which hands a `.ics` file (with an alarm) to your calendar app.

**Still to do:** the AI cartoon-car prompt and background removal for the vehicle picture, and an in-app
alert for due reminders.

## iPhone limits to keep in mind
- Safari can't write to a folder you choose, so there is no silent auto-backup. Back up with
  **Share → Save to Files** (iCloud Drive) and import with the file picker.
- Data survives as long as the home-screen app is installed. Deleting it or clearing Safari data
  deletes it. The app asks the browser to protect its storage.
