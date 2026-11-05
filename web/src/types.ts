// Mirrors the Android Room entities (see docs/backup-format.md).

export interface Vehicle { vehicleId: string; make: string; model: string; year: number; currentOdometer: number; colorHex: string }
export interface MaintenanceSchedule { scheduleId: string; vehicleId: string; serviceName: string; intervalKm: number; lastDoneOdometer: number }
/** One completed service: when, at what odometer, what it cost and any notes. */
export interface MaintenanceRecord { recordId: string; scheduleId: string; vehicleId: string; serviceName: string; date: string; odometer: number; cost: number; note: string }
export interface ExpenseLog { logId: string; vehicleId: string; date: string; odometer: number; category: string; cost: number; fuelLiters: number; note: string }
export interface Reminder { reminderId: string; vehicleId: string | null; title: string; note: string; triggerAt: number }
export interface TireSet { setId: string; vehicleId: string; name: string; season: string; brand: string; size: string; mounted: boolean; mountedAtOdometer: number; kmBefore: number }
export interface TireEvent {
  eventId: string; vehicleId: string; setId: string | null; type: string; date: string; odometer: number
  wheel: string | null; note: string; cost: number
  treadFl: number | null; treadFr: number | null; treadRl: number | null; treadRr: number | null
}
export interface BrakeEntry { entryId: string; vehicleId: string; date: string; odometer: number; type: string; frontMm: number | null; rearMm: number | null; cost: number; note: string }
export interface CarNote { noteId: string; vehicleId: string; date: string; odometer: number; tag: string; text: string; done: boolean }
export interface RecurringExpense { recurringId: string; vehicleId: string; category: string; amount: number; dayOfMonth: number; nextDue: string; active: boolean }

export type ThemeMode = 'system' | 'light' | 'dark'
export interface Settings { dueSoonKm: number; reminderHour: number; themeMode: ThemeMode; savedColors: string[] }
export const DEFAULT_SETTINGS: Settings = { dueSoonKm: 1500, reminderHour: 9, themeMode: 'system', savedColors: [] }

export const Categories = {
  FUEL: 'Fuel', SERVICE: 'Service', INSURANCE: 'Insurance', UPGRADES: 'Upgrades', CAR_WASH: 'Car wash', OTHER: 'Other',
} as const
export const Seasons = { SUMMER: 'Summer', WINTER: 'Winter', ALL_SEASON: 'All-season' } as const
export const TireEventType = { ROTATION: 'Rotation', BALANCING: 'Balancing', ALIGNMENT: 'Alignment', PUNCTURE: 'Puncture', SWAP: 'Swap', TREAD: 'Tread' } as const
export const BrakeType = { MEASURED: 'Measured', REPLACED: 'Replaced' } as const
