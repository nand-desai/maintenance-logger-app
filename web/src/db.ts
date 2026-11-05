import Dexie, { type Table } from 'dexie'
import type {
  BrakeEntry, CarNote, ExpenseLog, MaintenanceRecord, MaintenanceSchedule, Reminder, RecurringExpense, TireEvent, TireSet, Vehicle,
} from './types'

class CarloDB extends Dexie {
  vehicles!: Table<Vehicle, string>
  schedules!: Table<MaintenanceSchedule, string>
  maintenanceRecords!: Table<MaintenanceRecord, string>
  expenses!: Table<ExpenseLog, string>
  reminders!: Table<Reminder, string>
  tireSets!: Table<TireSet, string>
  tireEvents!: Table<TireEvent, string>
  brakeEntries!: Table<BrakeEntry, string>
  notes!: Table<CarNote, string>
  recurring!: Table<RecurringExpense, string>
  photos!: Table<{ vehicleId: string; blob: Blob }, string>
  meta!: Table<{ key: string; value: unknown }, string>

  constructor() {
    super('carlo')
    this.version(1).stores({
      vehicles: 'vehicleId',
      schedules: 'scheduleId, vehicleId',
      expenses: 'logId, vehicleId, date',
      reminders: 'reminderId, vehicleId, triggerAt',
      tireSets: 'setId, vehicleId',
      tireEvents: 'eventId, vehicleId',
      brakeEntries: 'entryId, vehicleId',
      notes: 'noteId, vehicleId',
      recurring: 'recurringId, vehicleId',
      photos: 'vehicleId',
      meta: 'key',
    })
    // v2: a history of completed services (date, cost, notes)
    this.version(2).stores({ maintenanceRecords: 'recordId, vehicleId, scheduleId' })
  }
}

export const db = new CarloDB()

export const newId = (prefix: string) => `${prefix}_${crypto.randomUUID().slice(0, 8)}`
/** Today as YYYY-MM-DD in the phone's own time zone. */
export const today = () => new Date().toLocaleDateString('en-CA')
