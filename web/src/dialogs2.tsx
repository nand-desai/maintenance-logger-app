import { useState } from 'react'
import { AttachmentsField, useAttachmentDraft } from './attachments'
import { db, newId, today } from './db'
import { deleteServiceRecord, mountSet, saveBrakeEntry, saveServiceRecord, saveTireEvent } from './ops'
import { WHEELS, type Wheel } from './logic/tires'
import { Field, Sheet } from './ui'
import { Seasons, TireEventType, type BrakeEntry, type MaintenanceRecord, type MaintenanceSchedule, type Reminder, type TireEvent, type TireSet, type Vehicle } from './types'

const num = (s: string) => (s.trim() === '' ? NaN : Number(s.replace(',', '.')))
const DateField = ({ value, onChange, label = 'Date' }: { value: string; onChange: (v: string) => void; label?: string }) => (
  <label className="f">{label}<input type="date" value={value} onChange={(e) => onChange(e.target.value)} /></label>
)

/** Mark a service as done: when, at what odometer, what it cost, and any notes. Cost also lands in Expenses. */
export function ServiceDoneDialog({ vehicle, schedule, initial, onClose }: {
  vehicle: Vehicle; schedule: MaintenanceSchedule; initial?: MaintenanceRecord; onClose: () => void
}) {
  const [date, setDate] = useState(initial?.date ?? today())
  const [odo, setOdo] = useState((initial?.odometer ?? vehicle.currentOdometer).toString())
  const [cost, setCost] = useState(initial?.cost ? initial.cost.toString() : '')
  const [note, setNote] = useState(initial?.note ?? '')
  const [recordId] = useState(initial?.recordId ?? newId('MNT'))
  const draft = useAttachmentDraft(recordId)
  const valid = date && Number.isInteger(num(odo)) && (cost.trim() === '' || !Number.isNaN(num(cost)))
  return (
    <Sheet title={initial ? `Edit · ${schedule.serviceName}` : `Done · ${schedule.serviceName}`} onClose={onClose}>
      <DateField value={date} onChange={setDate} />
      <Field label="Odometer (km)" value={odo} onChange={setOdo} inputMode="numeric" />
      <Field label="Cost (CAD, optional)" value={cost} onChange={setCost} inputMode="decimal" />
      <label className="f">Notes (optional)
        <textarea rows={3} value={note} placeholder="e.g. synthetic 5W-30, garage name, what the mechanic said" onChange={(e) => setNote(e.target.value)} />
      </label>
      <AttachmentsField draft={draft} />
      <div className="small soft">A cost is added to your Expenses automatically.</div>
      <div className="actions">
        {initial && <button className="btn text danger" onClick={async () => { await deleteServiceRecord(initial); onClose() }}>Delete</button>}
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!valid} onClick={async () => {
          await saveServiceRecord(vehicle, schedule, { recordId, date, odometer: num(odo), cost: num(cost) || 0, note: note.trim() })
          await draft.commit(vehicle.vehicleId, 'maintenance')
          onClose()
        }}>Save</button>
      </div>
    </Sheet>
  )
}

/** Add or edit a tire set. */
export function TireSetDialog({ vehicle, initial, isFirst, onClose }: { vehicle: Vehicle; initial?: TireSet; isFirst: boolean; onClose: () => void }) {
  const [season, setSeason] = useState<string | undefined>(initial?.season)
  const [name, setName] = useState(initial?.name ?? '')
  const [brand, setBrand] = useState(initial?.brand ?? '')
  const [size, setSize] = useState(initial?.size ?? '')
  const [km, setKm] = useState(initial?.kmBefore ? initial.kmBefore.toString() : '')
  const [mountNow, setMountNow] = useState(isFirst)
  const valid = season && name.trim() && (km.trim() === '' || Number.isInteger(num(km)))
  return (
    <Sheet title={initial ? 'Edit tire set' : 'Add tire set'} onClose={onClose}>
      <div className="small soft">Season</div>
      <div className="chips">
        {Object.values(Seasons).map((s) => <button key={s} className={`chip ${s === season ? 'on' : ''}`} onClick={() => setSeason(s)}>{s}</button>)}
      </div>
      <Field label="Name (e.g. Winter set)" value={name} onChange={setName} />
      <Field label="Brand / model (optional)" value={brand} onChange={setBrand} />
      <Field label="Size (e.g. 225/45 R17, optional)" value={size} onChange={setSize} />
      <Field label="Km already on these tires (optional)" value={km} onChange={setKm} inputMode="numeric" />
      {!initial && <label className="row"><span className="grow">Put these on the car now</span><input type="checkbox" checked={mountNow} onChange={(e) => setMountNow(e.target.checked)} style={{ width: 22, height: 22 }} /></label>}
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!valid} onClick={async () => {
          const set: TireSet = {
            setId: initial?.setId ?? newId('SET'), vehicleId: vehicle.vehicleId, name: name.trim(), season: season!, brand: brand.trim(), size: size.trim(),
            mounted: initial?.mounted ?? false, mountedAtOdometer: initial?.mountedAtOdometer ?? 0, kmBefore: num(km) || 0,
          }
          await db.tireSets.put(set)
          if (!initial && mountNow) await mountSet(vehicle, set, { odometer: vehicle.currentOdometer })
          onClose()
        }}>Save</button>
      </div>
    </Sheet>
  )
}

/** Swap to another set (e.g. summer to winter) with the date, odometer and what it cost. */
export function SwapDialog({ vehicle, candidates, preselected, onClose }: { vehicle: Vehicle; candidates: TireSet[]; preselected?: TireSet; onClose: () => void }) {
  const [target, setTarget] = useState<TireSet | undefined>(preselected ?? (candidates.length === 1 ? candidates[0] : undefined))
  const [date, setDate] = useState(today())
  const [odo, setOdo] = useState(vehicle.currentOdometer.toString())
  const [cost, setCost] = useState('')
  const [note, setNote] = useState('')
  return (
    <Sheet title="Swap tires" onClose={onClose}>
      <div className="small soft">Put on</div>
      <div className="chips">
        {candidates.map((c) => <button key={c.setId} className={`chip ${target?.setId === c.setId ? 'on' : ''}`} onClick={() => setTarget(c)}>{c.name}</button>)}
      </div>
      <div className="small soft">The set on the car now comes off, and a swap counts as a tire rotation.</div>
      <DateField value={date} onChange={setDate} />
      <Field label="Odometer (km)" value={odo} onChange={setOdo} inputMode="numeric" />
      <Field label="Cost of the swap (CAD, optional)" value={cost} onChange={setCost} inputMode="decimal" />
      <Field label="Note (optional)" value={note} onChange={setNote} />
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!target || !Number.isInteger(num(odo)) || (cost.trim() !== '' && Number.isNaN(num(cost)))} onClick={async () => {
          await mountSet(vehicle, target!, { odometer: num(odo), date, cost: num(cost) || 0, note: note.trim() })
          onClose()
        }}>Swap</button>
      </div>
    </Sheet>
  )
}

const EVENT_TYPES = [TireEventType.ROTATION, TireEventType.BALANCING, TireEventType.ALIGNMENT, TireEventType.PUNCTURE, TireEventType.TREAD] as const

/** Log (or edit) a rotation, balancing, alignment, puncture or tread-depth measurement. */
export function TireEventDialog({ vehicle, initial, preset, mountedSetId, onClose }: {
  vehicle: Vehicle; initial?: TireEvent; preset?: { type?: string; wheel?: string }; mountedSetId: string | null; onClose: () => void
}) {
  const [type, setType] = useState<string | undefined>(initial?.type ?? preset?.type)
  const [wheel, setWheel] = useState<string | null>(initial?.wheel ?? preset?.wheel ?? null)
  const [date, setDate] = useState(initial?.date ?? today())
  const [odo, setOdo] = useState((initial?.odometer ?? vehicle.currentOdometer).toString())
  const [note, setNote] = useState(initial?.note ?? '')
  const [cost, setCost] = useState(initial?.cost ? initial.cost.toString() : '')
  const [eventId] = useState(initial?.eventId ?? newId('TIR'))
  const draft = useAttachmentDraft(eventId)
  const [tread, setTread] = useState<Record<Wheel, string>>({
    FL: initial?.treadFl?.toString() ?? '', FR: initial?.treadFr?.toString() ?? '', RL: initial?.treadRl?.toString() ?? '', RR: initial?.treadRr?.toString() ?? '',
  })
  const isSwap = type === TireEventType.SWAP, isPuncture = type === TireEventType.PUNCTURE, isTread = type === TireEventType.TREAD
  const anyTread = (Object.keys(WHEELS) as Wheel[]).some((w) => tread[w].trim() !== '' && !Number.isNaN(num(tread[w])))
  const treadsOk = (Object.keys(WHEELS) as Wheel[]).every((w) => tread[w].trim() === '' || !Number.isNaN(num(tread[w])))
  const valid = type && Number.isInteger(num(odo)) && (!isPuncture || wheel) && (!isTread || (anyTread && treadsOk)) && (isTread || cost.trim() === '' || !Number.isNaN(num(cost)))

  return (
    <Sheet title={initial ? 'Edit tire event' : 'Log tire event'} onClose={onClose}>
      {isSwap ? <div className="title">Tire swap</div> : (
        <>
          <div className="small soft">What happened?</div>
          <div className="chips">
            {EVENT_TYPES.map((t) => <button key={t} className={`chip ${t === type ? 'on' : ''}`} onClick={() => setType(t)}>{t === 'Tread' ? 'Tread depth' : t}</button>)}
          </div>
        </>
      )}
      {isPuncture && (
        <>
          <div className="small soft">Which wheel?</div>
          <div className="chips">{(Object.keys(WHEELS) as Wheel[]).map((w) => <button key={w} className={`chip ${wheel === w ? 'on' : ''}`} onClick={() => setWheel(w)}>{WHEELS[w]}</button>)}</div>
        </>
      )}
      <DateField value={date} onChange={setDate} />
      <Field label="Odometer (km)" value={odo} onChange={setOdo} inputMode="numeric" />
      {isTread ? (
        <>
          <div className="small soft">Tread depth in mm (fill in what you measured)</div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            {(Object.keys(WHEELS) as Wheel[]).map((w) => <Field key={w} label={WHEELS[w]} value={tread[w]} onChange={(v) => setTread({ ...tread, [w]: v })} inputMode="decimal" />)}
          </div>
        </>
      ) : <Field label="Cost (CAD, optional)" value={cost} onChange={setCost} inputMode="decimal" />}
      <Field label="Note (optional)" value={note} onChange={setNote} />
      {!isTread && <AttachmentsField draft={draft} />}
      {!isTread && <div className="small soft">A cost is added to your Expenses automatically.</div>}
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!valid} onClick={async () => {
          const t = (w: Wheel) => (isTread && tread[w].trim() !== '' ? num(tread[w]) : null)
          await saveTireEvent(vehicle, initial, {
            type: type!, date, odometer: num(odo), wheel: isPuncture ? wheel : null, note: note.trim(), cost: isTread ? 0 : num(cost) || 0,
            treadFl: t('FL'), treadFr: t('FR'), treadRl: t('RL'), treadRr: t('RR'),
          }, mountedSetId, eventId)
          await draft.commit(vehicle.vehicleId, 'tire')
          onClose()
        }}>Save</button>
      </div>
    </Sheet>
  )
}

/** Log (or edit) a brake pad measurement or replacement. Thickness is per axle, in mm. */
export function BrakeDialog({ vehicle, initial, presetType, onClose }: { vehicle: Vehicle; initial?: BrakeEntry; presetType?: string; onClose: () => void }) {
  const [type, setType] = useState<string | undefined>(initial?.type ?? presetType)
  const [date, setDate] = useState(initial?.date ?? today())
  const [odo, setOdo] = useState((initial?.odometer ?? vehicle.currentOdometer).toString())
  const [front, setFront] = useState(initial?.frontMm?.toString() ?? '')
  const [rear, setRear] = useState(initial?.rearMm?.toString() ?? '')
  const [cost, setCost] = useState(initial?.cost ? initial.cost.toString() : '')
  const [note, setNote] = useState(initial?.note ?? '')
  const [entryId] = useState(initial?.entryId ?? newId('BRK'))
  const draft = useAttachmentDraft(entryId)
  const f = num(front), r = num(rear)
  const mmOk = (front.trim() === '' || (f >= 0 && f <= 30)) && (rear.trim() === '' || (r >= 0 && r <= 30)) && (!Number.isNaN(f) || !Number.isNaN(r))
  return (
    <Sheet title={initial ? 'Edit brake entry' : 'Log brake pads'} onClose={onClose}>
      <div className="chips">
        {[['Measured', 'Measured'], ['Replaced', 'Replaced (new pads)']].map(([t, l]) => <button key={t} className={`chip ${type === t ? 'on' : ''}`} onClick={() => setType(t)}>{l}</button>)}
      </div>
      <DateField value={date} onChange={setDate} />
      <Field label="Odometer (km)" value={odo} onChange={setOdo} inputMode="numeric" />
      <div className="small soft">Pad thickness in mm (fill in what you measured)</div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
        <Field label="Front" value={front} onChange={setFront} inputMode="decimal" />
        <Field label="Rear" value={rear} onChange={setRear} inputMode="decimal" />
      </div>
      <Field label="Cost (CAD, optional)" value={cost} onChange={setCost} inputMode="decimal" />
      <Field label="Note (optional)" value={note} onChange={setNote} />
      <AttachmentsField draft={draft} />
      <div className="small soft">A cost is added to your Expenses automatically.</div>
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!type || !Number.isInteger(num(odo)) || !mmOk || (cost.trim() !== '' && Number.isNaN(num(cost)))} onClick={async () => {
          await saveBrakeEntry(vehicle, initial, {
            type: type!, date, odometer: num(odo), frontMm: front.trim() === '' ? null : f, rearMm: rear.trim() === '' ? null : r,
            cost: num(cost) || 0, note: note.trim(),
          }, entryId)
          await draft.commit(vehicle.vehicleId, 'brake')
          onClose()
        }}>Save</button>
      </div>
    </Sheet>
  )
}

/** Schedule a reminder; it can be exported to the phone's calendar for a real alert. */
export function ReminderDialog({ vehicle, initial, prefill, defaultHour, onClose }: {
  vehicle: Vehicle; initial?: Reminder; prefill?: { title: string; note: string }; defaultHour: number; onClose: () => void
}) {
  const start = initial ? new Date(initial.triggerAt) : (() => { const d = new Date(); d.setDate(d.getDate() + 1); d.setHours(defaultHour, 0, 0, 0); return d })()
  const [title, setTitle] = useState(initial?.title ?? prefill?.title ?? '')
  const [note, setNote] = useState(initial?.note ?? prefill?.note ?? '')
  const [date, setDate] = useState(start.toLocaleDateString('en-CA'))
  const [time, setTime] = useState(`${String(start.getHours()).padStart(2, '0')}:${String(start.getMinutes()).padStart(2, '0')}`)
  const at = new Date(`${date}T${time}`).getTime()
  return (
    <Sheet title={initial ? 'Edit reminder' : 'Schedule reminder'} onClose={onClose}>
      <Field label="What for? (e.g. Oil change)" value={title} onChange={setTitle} />
      <Field label="Note (optional)" value={note} onChange={setNote} />
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
        <DateField value={date} onChange={setDate} />
        <label className="f">Time<input type="time" value={time} onChange={(e) => setTime(e.target.value)} /></label>
      </div>
      <div className="small soft">After saving, tap “Add to calendar” on the reminder so your phone alerts you at that time.</div>
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!title.trim() || Number.isNaN(at)} onClick={async () => {
          await db.reminders.put({ reminderId: initial?.reminderId ?? newId('REM'), vehicleId: vehicle.vehicleId, title: title.trim(), note: note.trim(), triggerAt: at })
          onClose()
        }}>Save</button>
      </div>
    </Sheet>
  )
}
