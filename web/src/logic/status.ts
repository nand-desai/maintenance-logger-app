import {
  Categories, type BrakeEntry, type CarNote, type ExpenseLog, type MaintenanceSchedule,
  type Reminder, type TireEvent, type TireSet, type Vehicle,
} from '../types'
import { health, kmRemaining } from './health'
import { BRAKE_REPLACE_MM, TREAD_REPLACE_MM, latestBrake, latestTread, swapDue } from './tires'
import { inDays, km, mm, money, washSummary } from './format'


export type Level = 'good' | 'warn' | 'bad' | 'neutral'
export interface Alert { text: string; level: Level; target: SectionId }
export type SectionId = 'maintenance' | 'expenses' | 'tires' | 'brakes' | 'wash' | 'notes' | 'reminders'
export interface Tile { id: SectionId; title: string; status: string; level: Level; accent: string; icon: string }

const padLevel = (v: number): Level => (v >= 6 ? 'good' : v >= BRAKE_REPLACE_MM ? 'warn' : 'bad')
const treadLevel = (v: number): Level => (v >= 4 ? 'good' : v >= TREAD_REPLACE_MM ? 'warn' : 'bad')

export interface GarageData {
  vehicle: Vehicle
  schedules: MaintenanceSchedule[]
  expenses: ExpenseLog[]
  tireSets: TireSet[]
  tireEvents: TireEvent[]
  brakes: BrakeEntry[]
  reminders: Reminder[]
  notes: CarNote[]
  dueSoonKm: number
}

/** Everything that needs attention, most urgent first. */
export function buildAlerts(d: GarageData): Alert[] {
  const odo = d.vehicle.currentOdometer
  const out: Alert[] = []
  d.schedules.forEach((s) => {
    const left = kmRemaining(s, odo)
    const h = health(s, odo, d.dueSoonKm)
    if (h === 'overdue') out.push({ text: `${s.serviceName} is overdue by ${km(-left)}`, level: 'bad', target: 'maintenance' })
    else if (h === 'dueSoon') out.push({ text: `${s.serviceName} is due in ${km(left)}`, level: 'warn', target: 'maintenance' })
  })
  const mounted = d.tireSets.find((t) => t.mounted)
  if (swapDue(mounted, new Date())) {
    out.push({ text: mounted?.season === 'Summer' ? 'Time to swap to winter tires' : 'Time to swap to summer tires', level: 'warn', target: 'tires' })
  }
  const treads = Object.values(latestTread(d.tireEvents, mounted?.setId ?? null))
  if (treads.length) {
    const low = Math.min(...treads)
    if (treadLevel(low) === 'bad') out.push({ text: `Tire tread is down to ${mm(low)}`, level: 'bad', target: 'tires' })
    else if (treadLevel(low) === 'warn') out.push({ text: `Tire tread is getting low (${mm(low)})`, level: 'warn', target: 'tires' })
  }
  ;([['Front', true], ['Rear', false]] as const).forEach(([name, front]) => {
    const e = latestBrake(d.brakes, front)
    const v = e ? (front ? e.frontMm : e.rearMm) : null
    if (v != null && padLevel(v) === 'bad') out.push({ text: `${name} brake pads are at ${mm(v)}`, level: 'bad', target: 'brakes' })
    else if (v != null && padLevel(v) === 'warn') out.push({ text: `${name} brake pads are getting low (${mm(v)})`, level: 'warn', target: 'brakes' })
  })
  return out.sort((a, b) => (a.level === 'bad' ? 0 : 1) - (b.level === 'bad' ? 0 : 1))
}

/** One tile per section, each with a live one-line status. */
export function buildTiles(d: GarageData): Tile[] {
  const odo = d.vehicle.currentOdometer

  const next = [...d.schedules].sort((a, b) => kmRemaining(a, odo) - kmRemaining(b, odo))[0]
  let maint: [string, Level]
  if (!next) maint = ['Add your first service', 'neutral']
  else {
    const h = health(next, odo, d.dueSoonKm)
    maint = h === 'overdue' ? [`${next.serviceName} overdue`, 'bad']
      : h === 'dueSoon' ? [`${next.serviceName} in ${km(kmRemaining(next, odo))}`, 'warn']
      : [`All on track · next in ${km(kmRemaining(next, odo))}`, 'good']
  }

  const month = new Date().toISOString().slice(0, 7)
  const spent = d.expenses.filter((e) => e.date.startsWith(month)).reduce((t, e) => t + e.cost, 0)

  const mounted = d.tireSets.find((t) => t.mounted)
  const treads = Object.values(latestTread(d.tireEvents, mounted?.setId ?? null))
  let tires: [string, Level]
  if (!mounted) tires = [d.tireSets.length ? 'No set mounted' : 'Add your tire sets', 'neutral']
  else if (swapDue(mounted, new Date())) tires = [`${mounted.season} on · time to swap?`, 'warn']
  else if (treads.length) { const low = Math.min(...treads); tires = [`${mounted.season} · ${mm(low)}`, treadLevel(low)] }
  else tires = [`${mounted.season} set on the car`, 'neutral']

  const front = latestBrake(d.brakes, true)?.frontMm ?? null
  const rear = latestBrake(d.brakes, false)?.rearMm ?? null
  const parts = [front != null ? `Front ${front.toFixed(1)}` : null, rear != null ? `Rear ${rear.toFixed(1)}` : null].filter(Boolean)
  const brakeVals = [front, rear].filter((v): v is number => v != null)
  const brakes: [string, Level] = parts.length ? [`${parts.join(' · ')} mm`, padLevel(Math.min(...brakeVals))] : ['Not measured yet', 'neutral']

  const lastWash = d.expenses.filter((e) => e.category === Categories.CAR_WASH).map((e) => e.date).sort().pop()
  const upcoming = d.reminders.filter((r) => r.triggerAt > Date.now()).sort((a, b) => a.triggerAt - b.triggerAt)[0]
  const openNotes = d.notes.filter((n) => !n.done).length

  return [
    { id: 'maintenance', title: 'Maintenance', status: maint[0], level: maint[1], accent: '#5B7CFA', icon: 'build' },
    { id: 'expenses', title: 'Expenses', status: spent > 0 ? `${money(spent, 0)} this month` : 'Nothing yet this month', level: 'neutral', accent: '#8B5CF6', icon: 'donut' },
    { id: 'tires', title: 'Tires', status: tires[0], level: tires[1], accent: '#1FA58A', icon: 'tire' },
    { id: 'brakes', title: 'Brakes', status: brakes[0], level: brakes[1], accent: '#E8793B', icon: 'disc' },
    { id: 'wash', title: 'Car wash', status: washSummary(lastWash), level: 'neutral', accent: '#2B8CD6', icon: 'wash' },
    { id: 'notes', title: 'Notes', status: openNotes ? `${openNotes} to tell your mechanic` : 'Nothing to mention', level: 'neutral', accent: '#CA9A1B', icon: 'note' },
    { id: 'reminders', title: 'Reminders', status: upcoming ? `${upcoming.title} ${inDays(upcoming.triggerAt)}` : 'Nothing scheduled', level: 'neutral', accent: '#D6457F', icon: 'bell' },
  ]
}
