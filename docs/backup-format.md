# Carlo backup format (version 2)

A backup is either a plain **`.json`** file (data only) or, preferably, a **`.zip`** that also carries
vehicle photos and receipt attachments:

```
carlo-backup-2026-11-05.zip
├── backup.json                         <- exactly the JSON described below
├── photos/<vehicle_id>.img             <- the vehicle picture (JPEG or PNG bytes)
└── attachments/<attachment_id>         <- one file per attachment (the original bytes)
```

Import auto-detects the type (a zip starts with `PK`). Importing a `.json` brings in the data but not
any attachment files. The Android automatic-backup folder holds the same things as loose files:
`carlo-backup.json`, `carlo-photo-<vehicle_id>.jpg` and an `attachments/` sub-folder.

Both the Android app (`android/`) and the web app (`web/`) read and write this JSON, so a backup
made in one can be imported into the other. Rows are matched by their ID: importing **merges**, so a
row with an existing ID is overwritten and everything else is kept.

Units are metric: distances in **km**, fuel in **litres**, money in **Canadian dollars**.
Dates are ISO `YYYY-MM-DD`. Missing optional fields are treated as `null` / empty / `0`.

```jsonc
{
  "app": "carlo",              // also accepted on import: "wheelhouse", "pitstop"
  "version": 2,                // must be >= 2 (version 1 used miles/gallons and is rejected)
  "exported_at": "2026-11-05T17:00:00Z",

  "vehicles": [
    { "vehicle_id": "CAR_ab12cd34", "make": "Toyota", "model": "Corolla SE", "year": 2018,
      "current_odometer": 94000, "color_hex": "#3B82F6" }
  ],
  "maintenance_schedules": [
    { "schedule_id": "SCH_...", "vehicle_id": "CAR_...", "service_name": "Engine oil & filter",
      "interval_km": 10000, "last_done_odometer": 88000,
      "parts": "Mobil 1 5W-30, 5.4 L · filter WIX 57060",   // optional: what you usually use
      "notes": "Dealer does the first two free" }          // optional
  ],
  "maintenance_records": [      // optional; written and read by both apps
    { "record_id": "MNT_...", "schedule_id": "SCH_...", "vehicle_id": "CAR_...", "service_name": "Brake fluid",
      "date": "2026-11-05", "odometer": 94000, "cost": 149.99, "note": "Dealer flush, DOT 4" }
  ],
  "expense_logs": [
    { "log_id": "LOG_...", "vehicle_id": "CAR_...", "date": "2026-11-01", "odometer": 94000,
      "category": "Fuel",       // Fuel | Service | Insurance | Upgrades | Car wash | Other
      "cost": 62.4, "fuel_liters": 41.2, "note": "",     // note = what an "Other" expense was for
      "schedule_id": null }                              // optional: the maintenance item this cost belongs to
  ],
  "reminders": [
    { "reminder_id": "REM_...", "vehicle_id": "CAR_..." /* or null */, "title": "Oil change",
      "note": "", "trigger_at": 1793000000000 }          // epoch milliseconds
  ],
  "tire_sets": [
    { "set_id": "SET_...", "vehicle_id": "CAR_...", "name": "Winter set",
      "season": "Winter",       // Summer | Winter | All-season
      "brand": "", "size": "225/45 R17", "mounted": true,
      "mounted_at_odometer": 90000, "km_before": 0 }
  ],
  "tire_events": [
    { "event_id": "TIR_...", "vehicle_id": "CAR_...", "set_id": "SET_..." /* or null */,
      "type": "Tread",          // Rotation | Balancing | Alignment | Puncture | Swap | Tread
      "date": "2026-11-01", "odometer": 94000,
      "wheel": null,            // FL | FR | RL | RR (punctures)
      "note": "", "cost": 0,
      "tread_fl": 6.5, "tread_fr": 6.4, "tread_rl": null, "tread_rr": null }   // mm
  ],
  "brake_entries": [
    { "entry_id": "BRK_...", "vehicle_id": "CAR_...", "date": "2026-11-01", "odometer": 94000,
      "type": "Measured",       // Measured | Replaced
      "front_mm": 7.0, "rear_mm": 6.5, "cost": 0, "note": "" }
  ],
  "attachments": [               // metadata only; the bytes live in attachments/<attachment_id> in the zip
    { "attachment_id": "ATT_ab12cd34", "vehicle_id": "CAR_...",
      "owner_type": "maintenance",   // maintenance | tire | brake | expense
      "owner_id": "MNT_...",         // the record_id / event_id / entry_id / log_id it belongs to
      "file_name": "receipt.jpg", "mime": "image/jpeg", "size": 183204, "created_at": 1793000000000 }
  ],
  "car_notes": [
    { "note_id": "NOTE_...", "vehicle_id": "CAR_...", "date": "2026-11-01", "odometer": 94000,
      "tag": "Noise",           // Noise | Vibration | Leak | Smell | Warning light | Handling | General
      "text": "Squeak when braking", "done": false }
  ],
  "recurring_expenses": [
    { "recurring_id": "REC_..._Insurance", "vehicle_id": "CAR_...", "category": "Insurance",
      "amount": 120, "day_of_month": 1, "next_due": "2026-12-01", "active": true }
  ],
  "settings": {                // optional
    "due_soon_km": 1500, "reminder_hour": 9, "theme": "system", "saved_colors": ["#FF8800"]
  }
}
```

## Not in the file
The JSON only lists attachments; their **bytes** (and vehicle photos) are in the `.zip`, or loose files in the
Android automatic-backup folder. An `attachments` row whose file is missing is skipped on import.

## Rules both apps must apply the same way
- **Due soon** when a service is within `due_soon_km` (default 1500) of `last_done_odometer + interval_km`; **overdue** once past it.
- **Tread**: green at 4 mm and above, amber down to 3 mm, coral below. Brake pads: green at 6 mm and above, amber down to 3 mm, coral below.
- **Rotation** counts a seasonal *Swap* as a rotation. With a Summer or Winter set on the car it stays green until the set no longer matches the time of year (winter tires from 15 October to 15 April); otherwise it falls back to a 10,000 km guideline.
- **Insurance** payments added automatically use the id `<recurring_id>_<YYYY-MM-DD>` so running the catch-up twice never double-adds a month.
- A **cost** entered on a completed service, tire entry or brake entry is mirrored as a *Service* expense with id `EXP_<record_id, event_id or entry_id>`. Its `note` says what it was for (for example `Brake fluid · Dealer flush`, `Tire swap · Winter set`, `Tires · Puncture`, `Brakes · new pads`), and the Expenses list shows it as `Service · <note>`. Editing the entry updates that expense; deleting the entry deletes it.
- Marking a service **done** adds a `maintenance_records` entry and moves the service's `last_done_odometer` to the newest record's odometer. Deleting a service deletes its records and their mirrored expenses.
- **Attachments** are receipts and similar: photos are shrunk to at most 1600 px (JPEG); PDFs and other files are kept as they are, up to 25 MB each. Deleting a record, entry or expense deletes its attachments.
- An expense can be **linked to a maintenance item** (`schedule_id`). Costs copied from a service record are linked automatically; you can also link a *Service* expense yourself. A copied cost (`EXP_…`) is edited by opening its source record, so the two never disagree.
