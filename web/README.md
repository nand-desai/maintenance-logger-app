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

**Done:** app shell and navigation, light/dark/system theme, welcome screen, **? help with install
instructions** (iPhone Safari and Android Chrome), home hub with live status tiles and the attention banner,
vehicle photo, add/edit/delete vehicle (with your own colours), odometer, **Maintenance**, **Expenses**
(add/edit/delete, "Other" with a description, donut chart, monthly insurance that adds itself and catches
up missed months), **Car wash**, **Notes** for the mechanic (shareable list), backup export/import
compatible with Android, installable PWA with icons and offline support.

**Next, in order:**
1. Tires (wheel diagram, sets, swaps, tread depth, punctures)
2. Brakes (pad thickness with the thick/thin pad visual)
3. Reminders: iPhone Safari has no scheduled local notifications, so start with calendar (`.ics`)
   export and in-app alerts; real push needs a small server
4. AI cartoon-car prompt and background removal for the vehicle picture
5. Polish and tests

## iPhone limits to keep in mind
- Safari can't write to a folder you choose, so there is no silent auto-backup. Back up with
  **Share → Save to Files** (iCloud Drive) and import with the file picker.
- Data survives as long as the home-screen app is installed. Deleting it or clearing Safari data
  deletes it. The app asks the browser to protect its storage.
