import { deleteAttachmentsFor } from './attachments'
import { db, newId, today } from './db'
import { TireEventType, type BrakeEntry, type MaintenanceRecord, type MaintenanceSchedule, type TireEvent, type TireSet, type Vehicle } from './types'

/** Keeps a "Service" expense in step with a cost entered on a service, tire or brake record. */
export async function mirrorCost(sourceId: string, vehicleId: string, date: string, odometer: number, cost: number, what: string) {
  const id = `EXP_${sourceId}`
  if (cost > 0) await db.expenses.put({ logId: id, vehicleId, date, odometer, category: 'Service', cost, fuelLiters: 0, note: what })
  else await db.expenses.delete(id)
}

const bumpOdometer = async (v: Vehicle, odometer: number) => {
  if (odometer > v.currentOdometer) await db.vehicles.update(v.vehicleId, { currentOdometer: odometer })
}

// ---- maintenance records ------------------------------------------------------------------------

async function refreshLastDone(scheduleId: string) {
  const recs = await db.maintenanceRecords.where('scheduleId').equals(scheduleId).toArray()
  if (recs.length) await db.schedules.update(scheduleId, { lastDoneOdometer: Math.max(...recs.map((r) => r.odometer)) })
}

export async function saveServiceRecord(v: Vehicle, s: MaintenanceSchedule, f: { recordId?: string; date: string; odometer: number; cost: number; note: string }) {
  const rec: MaintenanceRecord = {
    recordId: f.recordId ?? newId('MNT'), scheduleId: s.scheduleId, vehicleId: v.vehicleId, serviceName: s.serviceName,
    date: f.date, odometer: f.odometer, cost: f.cost, note: f.note,
  }
  await db.transaction('rw', [db.maintenanceRecords, db.schedules, db.expenses, db.vehicles], async () => {
    await db.maintenanceRecords.put(rec)
    await db.schedules.update(s.scheduleId, { lastDoneOdometer: f.recordId ? (await db.schedules.get(s.scheduleId))!.lastDoneOdometer : f.odometer })
    await refreshLastDone(s.scheduleId)
    await mirrorCost(rec.recordId, v.vehicleId, f.date, f.odometer, f.cost, s.serviceName + (f.note ? ` · ${f.note}` : ''))
    await bumpOdometer(v, f.odometer)
  })
}

export async function deleteServiceRecord(r: MaintenanceRecord) {
  await db.transaction('rw', [db.maintenanceRecords, db.schedules, db.expenses], async () => {
    await db.maintenanceRecords.delete(r.recordId)
    await db.expenses.delete(`EXP_${r.recordId}`)
    await refreshLastDone(r.scheduleId)
  })
  await deleteAttachmentsFor(r.recordId)
}

// ---- tires ------------------------------------------------------------------------------------

/** Puts [set] on the car (taking the old set off and banking its km), logging the swap and its cost. */
export async function mountSet(v: Vehicle, set: TireSet, f: { odometer: number; date?: string; cost?: number; note?: string }) {
  const date = f.date ?? today()
  const cost = f.cost ?? 0
  await db.transaction('rw', [db.tireSets, db.tireEvents, db.expenses, db.vehicles], async () => {
    await db.tireSets.where('vehicleId').equals(v.vehicleId).filter((s) => s.mounted).modify((s) => {
      s.mounted = false
      s.kmBefore += Math.max(0, f.odometer - s.mountedAtOdometer)
    })
    await db.tireSets.put({ ...set, mounted: true, mountedAtOdometer: f.odometer })
    const e: TireEvent = {
      eventId: newId('TIR'), vehicleId: v.vehicleId, setId: set.setId, type: TireEventType.SWAP, date, odometer: f.odometer,
      wheel: null, note: f.note || `Mounted ${set.name}`, cost, treadFl: null, treadFr: null, treadRl: null, treadRr: null,
    }
    await db.tireEvents.put(e)
    await mirrorCost(e.eventId, v.vehicleId, date, f.odometer, cost, `Tire swap · ${set.name}`)
    await bumpOdometer(v, f.odometer)
  })
}

export async function saveTireEvent(v: Vehicle, existing: TireEvent | undefined, f: Omit<TireEvent, 'eventId' | 'vehicleId' | 'setId'>, mountedSetId: string | null, id?: string) {
  const e: TireEvent = { ...f, eventId: existing?.eventId ?? id ?? newId('TIR'), vehicleId: v.vehicleId, setId: existing ? existing.setId : mountedSetId }
  await db.transaction('rw', [db.tireEvents, db.expenses, db.vehicles], async () => {
    await db.tireEvents.put(e)
    await mirrorCost(e.eventId, v.vehicleId, e.date, e.odometer, e.cost, `Tires · ${e.type}${e.note ? ` · ${e.note}` : ''}`)
    await bumpOdometer(v, e.odometer)
  })
}

export async function deleteTireEvent(e: TireEvent) {
  await db.transaction('rw', [db.tireEvents, db.expenses], async () => {
    await db.tireEvents.delete(e.eventId)
    await db.expenses.delete(`EXP_${e.eventId}`)
  })
  await deleteAttachmentsFor(e.eventId)
}

// ---- brakes -----------------------------------------------------------------------------------

export async function saveBrakeEntry(v: Vehicle, existing: BrakeEntry | undefined, f: Omit<BrakeEntry, 'entryId' | 'vehicleId'>, id?: string) {
  const e: BrakeEntry = { ...f, entryId: existing?.entryId ?? id ?? newId('BRK'), vehicleId: v.vehicleId }
  await db.transaction('rw', [db.brakeEntries, db.expenses, db.vehicles], async () => {
    await db.brakeEntries.put(e)
    await mirrorCost(e.entryId, v.vehicleId, e.date, e.odometer, e.cost, `Brakes · ${e.type === 'Replaced' ? 'new pads' : 'checked'}${e.note ? ` · ${e.note}` : ''}`)
    await bumpOdometer(v, e.odometer)
  })
}

export async function deleteBrakeEntry(e: BrakeEntry) {
  await db.transaction('rw', [db.brakeEntries, db.expenses], async () => {
    await db.brakeEntries.delete(e.entryId)
    await db.expenses.delete(`EXP_${e.entryId}`)
  })
  await deleteAttachmentsFor(e.entryId)
}

/** Deletes an expense and any receipts attached to it. */
export async function deleteExpense(logId: string) {
  await db.expenses.delete(logId)
  await deleteAttachmentsFor(logId)
}
