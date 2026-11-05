import { Seasons, TireEventType, type BrakeEntry, type TireEvent, type TireSet } from '../types'

export const WHEELS = { FL: 'Front left', FR: 'Front right', RL: 'Rear left', RR: 'Rear right' } as const
export type Wheel = keyof typeof WHEELS
export const TREAD_REPLACE_MM = 3
export const BRAKE_REPLACE_MM = 3
export const BRAKE_NEW_MM = 12

const byDateThenOdo = (a: { date: string; odometer: number }, b: { date: string; odometer: number }) =>
  a.date === b.date ? a.odometer - b.odometer : a.date < b.date ? -1 : 1

export const kmDriven = (s: TireSet, odometer: number) => s.kmBefore + (s.mounted ? Math.max(0, odometer - s.mountedAtOdometer) : 0)

export const treadFor = (e: TireEvent, w: Wheel): number | null =>
  w === 'FL' ? e.treadFl : w === 'FR' ? e.treadFr : w === 'RL' ? e.treadRl : e.treadRr

export const worstTread = (e: TireEvent): number | null => {
  const v = [e.treadFl, e.treadFr, e.treadRl, e.treadRr].filter((x): x is number => x != null)
  return v.length ? Math.min(...v) : null
}

/** Latest tread reading per wheel for one set. */
export function latestTread(events: TireEvent[], setId: string | null): Partial<Record<Wheel, number>> {
  const out: Partial<Record<Wheel, number>> = {}
  events
    .filter((e) => e.type === TireEventType.TREAD && e.setId === setId)
    .sort(byDateThenOdo)
    .forEach((e) => (Object.keys(WHEELS) as Wheel[]).forEach((w) => { const v = treadFor(e, w); if (v != null) out[w] = v }))
  return out
}

export function treadWear(events: TireEvent[], setId: string | null): { mmPer1000Km: number; kmLeft: number } | null {
  const pts = events.filter((e) => e.type === TireEventType.TREAD && e.setId === setId && worstTread(e) != null).sort(byDateThenOdo)
  if (pts.length < 2) return null
  const first = pts[0], last = pts[pts.length - 1]
  const km = last.odometer - first.odometer
  const worn = worstTread(first)! - worstTread(last)!
  if (km <= 0 || worn <= 0) return null
  const perKm = worn / km
  return { mmPer1000Km: perKm * 1000, kmLeft: Math.max(0, Math.floor((worstTread(last)! - TREAD_REPLACE_MM) / perKm)) }
}

/** Winter tires from 15 October to 15 April. */
export function isWinterPeriod(d: Date): boolean {
  const md = (d.getMonth() + 1) * 100 + d.getDate()
  return md >= 1015 || md < 415
}

/** True when the set on the car doesn't match the time of year. */
export function swapDue(mounted: TireSet | undefined, d: Date): boolean {
  if (mounted?.season === Seasons.SUMMER) return isWinterPeriod(d)
  if (mounted?.season === Seasons.WINTER) return !isWinterPeriod(d)
  return false
}

// ---- brakes ----
const brakeMm = (e: BrakeEntry, front: boolean) => (front ? e.frontMm : e.rearMm)
const sortedBrakes = (list: BrakeEntry[]) => [...list].sort(byDateThenOdo)

export function latestBrake(list: BrakeEntry[], front: boolean): BrakeEntry | undefined {
  return sortedBrakes(list).filter((e) => brakeMm(e, front) != null).pop()
}

/** Estimated km until the pads reach 3 mm, from wear since they were last replaced. */
export function brakeKmLeft(list: BrakeEntry[], front: boolean): number | null {
  const sorted = sortedBrakes(list)
  let start = 0
  sorted.forEach((e, i) => { if (e.type === 'Replaced') start = i })
  const pts = sorted.slice(start).filter((e) => brakeMm(e, front) != null)
  if (pts.length < 2) return null
  const first = pts[0], last = pts[pts.length - 1]
  const km = last.odometer - first.odometer
  const worn = brakeMm(first, front)! - brakeMm(last, front)!
  if (km <= 0 || worn <= 0) return null
  return Math.max(0, Math.floor((brakeMm(last, front)! - BRAKE_REPLACE_MM) / (worn / km)))
}

// ---- care tiles ----
export const CARE_KM: Record<string, number> = { Rotation: 10_000, Balancing: 20_000, Alignment: 20_000 }

/** Newest rotation, counting a seasonal swap as a rotation. */
export function lastRotation(events: TireEvent[]): TireEvent | undefined {
  return events.filter((e) => e.type === TireEventType.ROTATION || e.type === TireEventType.SWAP).sort(byDateThenOdo).pop()
}

export const latestOfType = (events: TireEvent[], type: string): TireEvent | undefined =>
  events.filter((e) => e.type === type).sort(byDateThenOdo).pop()
