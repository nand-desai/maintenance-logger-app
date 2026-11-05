import type { MaintenanceSchedule } from '../types'

export type Health = 'healthy' | 'dueSoon' | 'overdue'
export const kmRemaining = (s: MaintenanceSchedule, odometer: number) => s.lastDoneOdometer + s.intervalKm - odometer

export function health(s: MaintenanceSchedule, odometer: number, dueSoonKm: number): Health {
  const left = kmRemaining(s, odometer)
  return left < 0 ? 'overdue' : left <= dueSoonKm ? 'dueSoon' : 'healthy'
}

/** Share of the interval already used, 0..1. */
export const progress = (s: MaintenanceSchedule, odometer: number) =>
  s.intervalKm <= 0 ? 1 : Math.min(1, Math.max(0, (odometer - s.lastDoneOdometer) / s.intervalKm))

export function overallHealth(list: MaintenanceSchedule[], odometer: number, dueSoonKm: number): Health {
  if (list.some((s) => health(s, odometer, dueSoonKm) === 'overdue')) return 'overdue'
  if (list.some((s) => health(s, odometer, dueSoonKm) === 'dueSoon')) return 'dueSoon'
  return 'healthy'
}
