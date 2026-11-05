import { useLiveQuery } from 'dexie-react-hooks'
import { db } from './db'
import { DEFAULT_SETTINGS, type Settings } from './types'

export async function getSettings(): Promise<Settings> {
  const row = await db.meta.get('settings')
  return { ...DEFAULT_SETTINGS, ...((row?.value as Partial<Settings>) ?? {}) }
}

export async function saveSettings(patch: Partial<Settings>) {
  await db.meta.put({ key: 'settings', value: { ...(await getSettings()), ...patch } })
}

export function useSettings(): Settings {
  return useLiveQuery(getSettings, [], DEFAULT_SETTINGS)
}

export const selectVehicle = (id: string) => db.meta.put({ key: 'selectedVehicleId', value: id })

/** Everything for the active vehicle; `undefined` while the first read is in flight. */
export function useGarage() {
  return useLiveQuery(async () => {
    const vehicles = (await db.vehicles.toArray()).sort((a, b) => `${a.make} ${a.model}`.localeCompare(`${b.make} ${b.model}`))
    const selected = (await db.meta.get('selectedVehicleId'))?.value as string | undefined
    const vehicle = vehicles.find((v) => v.vehicleId === selected) ?? vehicles[0]
    if (!vehicle) return { vehicles, vehicle: undefined }
    const id = vehicle.vehicleId
    const [schedules, records, expenses, reminders, tireSets, tireEvents, brakes, notes, recurring] = await Promise.all([
      db.schedules.where('vehicleId').equals(id).toArray(),
      db.maintenanceRecords.where('vehicleId').equals(id).toArray(),
      db.expenses.where('vehicleId').equals(id).reverse().sortBy('date'),
      db.reminders.toArray(),
      db.tireSets.where('vehicleId').equals(id).toArray(),
      db.tireEvents.where('vehicleId').equals(id).reverse().sortBy('date'),
      db.brakeEntries.where('vehicleId').equals(id).reverse().sortBy('date'),
      db.notes.where('vehicleId').equals(id).reverse().sortBy('date'),
      db.recurring.where('vehicleId').equals(id).toArray(),
    ])
    return { vehicles, vehicle, schedules, records, expenses, reminders, tireSets, tireEvents, brakes, notes, recurring }
  }, [])
}
