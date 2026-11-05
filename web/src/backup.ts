import { db } from './db'
import { DEFAULT_SETTINGS, type Settings, type ThemeMode } from './types'
import { getSettings, saveSettings } from './store'

const ACCEPTED_APPS = ['carlo', 'wheelhouse', 'pitstop']

/* eslint-disable @typescript-eslint/no-explicit-any */
type Row = Record<string, any>
const arr = (v: unknown): Row[] => (Array.isArray(v) ? (v as Row[]) : [])
const nz = <T,>(v: T | undefined | null): T | null => (v === undefined ? null : v)

export async function exportBackup(): Promise<string> {
  const s = await getSettings()
  const out = {
    app: 'carlo',
    version: 2,
    exported_at: new Date().toISOString(),
    vehicles: (await db.vehicles.toArray()).map((v) => ({ vehicle_id: v.vehicleId, make: v.make, model: v.model, year: v.year, current_odometer: v.currentOdometer, color_hex: v.colorHex })),
    maintenance_schedules: (await db.schedules.toArray()).map((x) => ({ schedule_id: x.scheduleId, vehicle_id: x.vehicleId, service_name: x.serviceName, interval_km: x.intervalKm, last_done_odometer: x.lastDoneOdometer })),
    maintenance_records: (await db.maintenanceRecords.toArray()).map((x) => ({ record_id: x.recordId, schedule_id: x.scheduleId, vehicle_id: x.vehicleId, service_name: x.serviceName, date: x.date, odometer: x.odometer, cost: x.cost, note: x.note })),
    expense_logs: (await db.expenses.toArray()).map((x) => ({ log_id: x.logId, vehicle_id: x.vehicleId, date: x.date, odometer: x.odometer, category: x.category, cost: x.cost, fuel_liters: x.fuelLiters, note: x.note })),
    reminders: (await db.reminders.toArray()).map((x) => ({ reminder_id: x.reminderId, vehicle_id: x.vehicleId, title: x.title, note: x.note, trigger_at: x.triggerAt })),
    tire_sets: (await db.tireSets.toArray()).map((x) => ({ set_id: x.setId, vehicle_id: x.vehicleId, name: x.name, season: x.season, brand: x.brand, size: x.size, mounted: x.mounted, mounted_at_odometer: x.mountedAtOdometer, km_before: x.kmBefore })),
    tire_events: (await db.tireEvents.toArray()).map((x) => ({ event_id: x.eventId, vehicle_id: x.vehicleId, set_id: x.setId, type: x.type, date: x.date, odometer: x.odometer, wheel: x.wheel, note: x.note, cost: x.cost, tread_fl: x.treadFl, tread_fr: x.treadFr, tread_rl: x.treadRl, tread_rr: x.treadRr })),
    brake_entries: (await db.brakeEntries.toArray()).map((x) => ({ entry_id: x.entryId, vehicle_id: x.vehicleId, date: x.date, odometer: x.odometer, type: x.type, front_mm: x.frontMm, rear_mm: x.rearMm, cost: x.cost, note: x.note })),
    car_notes: (await db.notes.toArray()).map((x) => ({ note_id: x.noteId, vehicle_id: x.vehicleId, date: x.date, odometer: x.odometer, tag: x.tag, text: x.text, done: x.done })),
    recurring_expenses: (await db.recurring.toArray()).map((x) => ({ recurring_id: x.recurringId, vehicle_id: x.vehicleId, category: x.category, amount: x.amount, day_of_month: x.dayOfMonth, next_due: x.nextDue, active: x.active })),
    settings: { due_soon_km: s.dueSoonKm, reminder_hour: s.reminderHour, theme: s.themeMode, saved_colors: s.savedColors },
  }
  return JSON.stringify(out, null, 2)
}

export interface ImportCounts { vehicles: number; services: number; expenses: number; tireSets: number; tireEvents: number; brakes: number; notes: number; reminders: number }

/** Merges a backup (from this app or the Android app) into the local database. */
export async function importBackup(text: string): Promise<ImportCounts> {
  const root = JSON.parse(text) as Row
  if (!ACCEPTED_APPS.includes(root.app)) throw new Error("This isn't a Carlo backup file")
  if ((root.version ?? 1) < 2) throw new Error("This backup uses miles/gallons and can't be imported")

  const vehicles = arr(root.vehicles).map((r) => ({ vehicleId: r.vehicle_id, make: r.make, model: r.model, year: r.year, currentOdometer: r.current_odometer, colorHex: r.color_hex ?? '#3B82F6' }))
  const schedules = arr(root.maintenance_schedules).map((r) => ({ scheduleId: r.schedule_id, vehicleId: r.vehicle_id, serviceName: r.service_name, intervalKm: r.interval_km, lastDoneOdometer: r.last_done_odometer }))
  const records = arr(root.maintenance_records).map((r) => ({ recordId: r.record_id, scheduleId: r.schedule_id, vehicleId: r.vehicle_id, serviceName: r.service_name, date: r.date, odometer: r.odometer, cost: r.cost ?? 0, note: r.note ?? '' }))
  const expenses = arr(root.expense_logs).map((r) => ({ logId: r.log_id, vehicleId: r.vehicle_id, date: r.date, odometer: r.odometer, category: r.category, cost: r.cost, fuelLiters: r.fuel_liters ?? 0, note: r.note ?? '' }))
  const reminders = arr(root.reminders).map((r) => ({ reminderId: r.reminder_id, vehicleId: nz(r.vehicle_id), title: r.title, note: r.note ?? '', triggerAt: r.trigger_at }))
  const tireSets = arr(root.tire_sets).map((r) => ({ setId: r.set_id, vehicleId: r.vehicle_id, name: r.name, season: r.season, brand: r.brand ?? '', size: r.size ?? '', mounted: !!r.mounted, mountedAtOdometer: r.mounted_at_odometer ?? 0, kmBefore: r.km_before ?? 0 }))
  const tireEvents = arr(root.tire_events).map((r) => ({ eventId: r.event_id, vehicleId: r.vehicle_id, setId: nz(r.set_id), type: r.type, date: r.date, odometer: r.odometer, wheel: nz(r.wheel), note: r.note ?? '', cost: r.cost ?? 0, treadFl: nz(r.tread_fl), treadFr: nz(r.tread_fr), treadRl: nz(r.tread_rl), treadRr: nz(r.tread_rr) }))
  const brakes = arr(root.brake_entries).map((r) => ({ entryId: r.entry_id, vehicleId: r.vehicle_id, date: r.date, odometer: r.odometer, type: r.type, frontMm: nz(r.front_mm), rearMm: nz(r.rear_mm), cost: r.cost ?? 0, note: r.note ?? '' }))
  const notes = arr(root.car_notes).map((r) => ({ noteId: r.note_id, vehicleId: r.vehicle_id, date: r.date, odometer: r.odometer, tag: r.tag ?? 'General', text: r.text, done: !!r.done }))
  const recurring = arr(root.recurring_expenses).map((r) => ({ recurringId: r.recurring_id, vehicleId: r.vehicle_id, category: r.category, amount: r.amount, dayOfMonth: r.day_of_month, nextDue: r.next_due, active: r.active !== false }))

  await db.transaction('rw', [db.vehicles, db.schedules, db.maintenanceRecords, db.expenses, db.reminders, db.tireSets, db.tireEvents, db.brakeEntries, db.notes, db.recurring], async () => {
    await db.vehicles.bulkPut(vehicles); await db.schedules.bulkPut(schedules); await db.maintenanceRecords.bulkPut(records); await db.expenses.bulkPut(expenses)
    await db.reminders.bulkPut(reminders); await db.tireSets.bulkPut(tireSets); await db.tireEvents.bulkPut(tireEvents)
    await db.brakeEntries.bulkPut(brakes); await db.notes.bulkPut(notes); await db.recurring.bulkPut(recurring)
  })

  if (root.settings) {
    const st = root.settings as Row
    const patch: Partial<Settings> = {}
    if (typeof st.due_soon_km === 'number') patch.dueSoonKm = st.due_soon_km
    if (typeof st.reminder_hour === 'number') patch.reminderHour = st.reminder_hour
    if (['system', 'light', 'dark'].includes(st.theme)) patch.themeMode = st.theme as ThemeMode
    if (Array.isArray(st.saved_colors)) {
      const cur = (await getSettings()).savedColors
      patch.savedColors = [...new Set([...cur, ...st.saved_colors.map((c: string) => c.toUpperCase())])]
    }
    await saveSettings({ ...DEFAULT_SETTINGS, ...(await getSettings()), ...patch })
  }
  return { vehicles: vehicles.length, services: schedules.length, expenses: expenses.length, tireSets: tireSets.length, tireEvents: tireEvents.length, brakes: brakes.length, notes: notes.length, reminders: reminders.length }
}
