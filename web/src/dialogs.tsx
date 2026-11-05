import { useState } from 'react'
import { AttachmentsField, useAttachmentDraft } from './attachments'
import { num, validMoney, validOdometer, validYear } from './numbers'
import { db, newId, today } from './db'
import { saveServiceRecord } from './ops'
import { CAR_COLORS, ColorPicker } from './colors'
import { saveSettings } from './store'
import { Field, Sheet } from './ui'
import { entryLitresPer100 } from './logic/expenses'
import { CATEGORY_LIST } from './logic/expenses'
import { runRecurring } from './logic/recurring'
import { Categories, type CarNote, type ExpenseLog, type MaintenanceSchedule, type RecurringExpense, type Settings, type Vehicle } from './types'

const PRESET_COLORS = CAR_COLORS

/** Add a vehicle, or edit one when [initial] is given. Nothing is pre-filled for a new vehicle. */
export function VehicleDialog({ initial, settings, onClose, onSaved }: {
  initial?: Vehicle; settings: Settings; onClose: () => void; onSaved: (v: Vehicle) => void
}) {
  const [make, setMake] = useState(initial?.make ?? '')
  const [model, setModel] = useState(initial?.model ?? '')
  const [year, setYear] = useState(initial?.year?.toString() ?? '')
  const [odo, setOdo] = useState(initial?.currentOdometer?.toString() ?? '')
  const [color, setColor] = useState(initial?.colorHex ?? '#3B82F6')
  const [picking, setPicking] = useState(false)
  const palette = [...PRESET_COLORS, ...settings.savedColors.filter((c) => !PRESET_COLORS.includes(c))]
  const valid = make.trim() && model.trim() && validYear(year) && validOdometer(odo)

  const save = async () => {
    const v: Vehicle = {
      vehicleId: initial?.vehicleId ?? newId('CAR'), make: make.trim(), model: model.trim(),
      year: num(year), currentOdometer: num(odo), colorHex: color,
    }
    await db.vehicles.put(v)
    onSaved(v)
  }

  return (
    <Sheet title={initial ? 'Edit vehicle' : 'Add vehicle'} onClose={onClose}>
      <Field label="Make (e.g. Toyota)" value={make} onChange={setMake} />
      <Field label="Model / trim (e.g. Corolla SE)" value={model} onChange={setModel} />
      <Field label="Year" value={year} onChange={setYear} inputMode="numeric" />
      <Field label="Current odometer (km)" value={odo} onChange={setOdo} inputMode="numeric" />
      <div className="small soft">Colour tag</div>
      <div className="swatches">
        {palette.map((c) => (
          <button key={c} aria-label={`Colour ${c}`} className={`swatch ${c.toLowerCase() === color.toLowerCase() ? 'on' : ''}`}
            style={{ background: c }} onClick={() => setColor(c)} />
        ))}
        <button className={`swatch ${picking ? 'on' : ''}`} aria-label="Pick your own colour" aria-expanded={picking}
          style={{ background: 'conic-gradient(red, yellow, lime, cyan, blue, magenta, red)' }} onClick={() => setPicking(!picking)} />
      </div>
      {picking && <ColorPicker value={color.toUpperCase()} onChange={(c) => setColor(c)} />}
      {!palette.some((c) => c.toLowerCase() === color.toLowerCase()) && (
        <button className="btn text" onClick={() => saveSettings({ savedColors: [...settings.savedColors, color.toUpperCase()] })}>
          Save {color.toUpperCase()} to my colours
        </button>
      )}
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!valid} onClick={save}>Save</button>
      </div>
    </Sheet>
  )
}

export function OdometerDialog({ vehicle, onClose }: { vehicle: Vehicle; onClose: () => void }) {
  const [odo, setOdo] = useState(vehicle.currentOdometer.toString())
  return (
    <Sheet title="Update odometer" onClose={onClose}>
      <Field label="Odometer (km)" value={odo} onChange={setOdo} inputMode="numeric" />
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!validOdometer(odo)} onClick={async () => { await db.vehicles.update(vehicle.vehicleId, { currentOdometer: num(odo) }); onClose() }}>Save</button>
      </div>
    </Sheet>
  )
}

const SUGGESTIONS: [string, number][] = [['Engine oil & filter', 10000], ['Brake fluid', 40000], ['Air filter', 30000], ['Coolant', 60000], ['Cabin filter', 20000]]

/** Add a maintenance item, or edit one when [initial] is given. */
export function ScheduleDialog({ vehicle, initial, onClose }: { vehicle: Vehicle; initial?: MaintenanceSchedule; onClose: () => void }) {
  const [name, setName] = useState(initial?.serviceName ?? '')
  const [interval, setInterval] = useState(initial?.intervalKm?.toString() ?? '')
  const [last, setLast] = useState((initial?.lastDoneOdometer ?? vehicle.currentOdometer).toString())
  const [parts, setParts] = useState(initial?.parts ?? '')
  const [notes, setNotes] = useState(initial?.notes ?? '')
  const [cost, setCost] = useState('')
  const valid = name.trim() && num(interval) > 0 && validOdometer(last) && validMoney(cost, true)
  return (
    <Sheet title={initial ? 'Edit maintenance item' : 'Add maintenance item'} onClose={onClose}>
      {!initial && (
        <>
          <div className="small soft">Quick pick</div>
          <div className="chips">
            {SUGGESTIONS.map(([n, km]) => <button key={n} className="chip" onClick={() => { setName(n); setInterval(km.toString()) }}>{n}</button>)}
          </div>
        </>
      )}
      <Field label="Service (e.g. Engine oil & filter)" value={name} onChange={setName} />
      <Field label="Repeat every (km)" value={interval} onChange={setInterval} inputMode="numeric" />
      <Field label="Last done at (km)" value={last} onChange={setLast} inputMode="numeric" />
      {!initial && (
        <>
          <Field label="What it cost, if you just did it (optional)" value={cost} onChange={setCost} inputMode="decimal" />
          {cost.trim() !== '' && <div className="small soft">Saved as a Service expense dated today, linked to this item.</div>}
        </>
      )}
      <label className="f">Your parts and specs (optional)
        <textarea rows={2} value={parts} placeholder="e.g. Mobil 1 5W-30, 5.4 L · filter WIX 57060" onChange={(e) => setParts(e.target.value)} />
      </label>
      <label className="f">Notes (optional)
        <textarea rows={2} value={notes} placeholder="e.g. dealer does it free in the first 2 years" onChange={(e) => setNotes(e.target.value)} />
      </label>
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!valid} onClick={async () => {
          const sched = {
            scheduleId: initial?.scheduleId ?? newId('SCH'), vehicleId: vehicle.vehicleId,
            serviceName: name.trim(), intervalKm: num(interval), lastDoneOdometer: num(last),
            parts: parts.trim(), notes: notes.trim(),
          }
          await db.schedules.put(sched)
          // a cost entered here is the service just done: record it so it lands in Expenses, linked to this item
          if (!initial && num(cost) > 0) await saveServiceRecord(vehicle, sched, { date: today(), odometer: num(last), cost: num(cost), note: '' })
          onClose()
        }}>Save</button>
      </div>
    </Sheet>
  )
}

/** Add an expense, or edit one when [initial] is given. */
export function ExpenseDialog({ vehicle, initial, previousFuelOdometer, schedules, onClose }: {
  vehicle: Vehicle; initial?: ExpenseLog; previousFuelOdometer: number | null; schedules: MaintenanceSchedule[]; onClose: () => void
}) {
  const [date, setDate] = useState(initial?.date ?? today())
  const [odo, setOdo] = useState((initial?.odometer ?? vehicle.currentOdometer).toString())
  const [category, setCategory] = useState<string>(initial?.category ?? Categories.FUEL)
  const [cost, setCost] = useState(initial?.cost?.toString() ?? '')
  const [litres, setLitres] = useState(initial?.fuelLiters ? initial.fuelLiters.toString() : '')
  const [note, setNote] = useState(initial?.note ?? '')
  const [logId] = useState(initial?.logId ?? newId('LOG'))
  const [scheduleId, setScheduleId] = useState<string | null>(initial?.scheduleId ?? null)
  const draft = useAttachmentDraft(logId)
  const isFuel = category === Categories.FUEL
  const isOther = category === Categories.OTHER
  const rate = isFuel && validOdometer(odo) ? entryLitresPer100(previousFuelOdometer, num(odo), num(litres) || 0) : null
  const valid = date && validOdometer(odo) && validMoney(cost) && (!isOther || note.trim())

  const save = async () => {
    await db.expenses.put({
      logId, vehicleId: vehicle.vehicleId, date, odometer: num(odo), category,
      cost: num(cost), fuelLiters: isFuel ? num(litres) || 0 : 0,
      scheduleId: category === Categories.SERVICE ? scheduleId : null,
      // keep the description of a service/tire/brake expense; "Other" uses what was typed
      note: isOther ? note.trim() : initial && initial.category !== Categories.OTHER ? initial.note : '',
    })
    await draft.commit(vehicle.vehicleId, 'expense')
    if (!initial && num(odo) > vehicle.currentOdometer) await db.vehicles.update(vehicle.vehicleId, { currentOdometer: num(odo) })
    onClose()
  }
  return (
    <Sheet title={initial ? 'Edit entry' : 'Add entry'} onClose={onClose}>
      <label className="f">Date<input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></label>
      <Field label="Odometer (km)" value={odo} onChange={setOdo} inputMode="numeric" />
      <div className="small soft">Category</div>
      <div className="chips">
        {CATEGORY_LIST.map((c) => <button key={c} className={`chip ${c === category ? 'on' : ''}`} onClick={() => setCategory(c)}>{c}</button>)}
      </div>
      {isOther && <Field label="What was it for? (e.g. remote starter)" value={note} onChange={setNote} />}
      {category === Categories.SERVICE && schedules.length > 0 && (
        <>
          <div className="small soft">Link to a service (optional): it then shows on that service</div>
          <div className="chips">
            <button className={`chip ${scheduleId == null ? 'on' : ''}`} onClick={() => setScheduleId(null)}>None</button>
            {schedules.map((s) => <button key={s.scheduleId} className={`chip ${scheduleId === s.scheduleId ? 'on' : ''}`} onClick={() => setScheduleId(s.scheduleId)}>{s.serviceName}</button>)}
          </div>
        </>
      )}
      <Field label="Cost (CAD)" value={cost} onChange={setCost} inputMode="decimal" />
      {isFuel && <Field label="Fuel (litres)" value={litres} onChange={setLitres} inputMode="decimal" />}
      {rate != null && <div className="small soft">≈ {rate.toFixed(1)} L/100 km since the last fill-up</div>}
      <AttachmentsField draft={draft} />
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!valid} onClick={save}>Save</button>
      </div>
    </Sheet>
  )
}

/** Monthly insurance that adds itself to the expenses on the same day each month. */
export function InsuranceDialog({ vehicle, existing, onClose }: { vehicle: Vehicle; existing?: RecurringExpense; onClose: () => void }) {
  const [amount, setAmount] = useState(existing?.amount?.toString() ?? '')
  const [date, setDate] = useState(existing?.nextDue ?? today())
  const [active, setActive] = useState(existing?.active ?? true)
  const save = async () => {
    await db.recurring.put({
      recurringId: `REC_${vehicle.vehicleId}_Insurance`, vehicleId: vehicle.vehicleId, category: Categories.INSURANCE,
      amount: num(amount), dayOfMonth: Number(date.slice(8, 10)), nextDue: date, active,
    })
    await runRecurring()
    onClose()
  }
  return (
    <Sheet title="Monthly insurance" onClose={onClose}>
      <Field label="Monthly payment (CAD)" value={amount} onChange={setAmount} inputMode="decimal" />
      <label className="f">{existing ? 'Next payment date' : 'First payment date'}<input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></label>
      <div className="small soft">Carlo adds this to your expenses on the same day each month, including any payments from that date up to today.</div>
      <label className="row"><span className="grow">Add automatically</span><input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} style={{ width: 22, height: 22 }} /></label>
      {existing && <button className="btn text danger" onClick={async () => { if (!window.confirm('Stop adding this insurance every month? Payments already added stay.')) return; await db.recurring.delete(existing.recurringId); onClose() }}>Remove monthly insurance</button>}
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!(num(amount) > 0) || !date} onClick={save}>Save</button>
      </div>
    </Sheet>
  )
}

/** Date and price: that's all a car wash needs. */
export function WashDialog({ vehicle, initial, onClose }: { vehicle: Vehicle; initial?: ExpenseLog; onClose: () => void }) {
  const [date, setDate] = useState(initial?.date ?? today())
  const [cost, setCost] = useState(initial?.cost?.toString() ?? '')
  return (
    <Sheet title={initial ? 'Edit car wash' : 'Log a car wash'} onClose={onClose}>
      <label className="f">Date<input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></label>
      <Field label="Cost (CAD, 0 if free)" value={cost} onChange={setCost} inputMode="decimal" />
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!validMoney(cost) || !date} onClick={async () => {
          await db.expenses.put({
            logId: initial?.logId ?? newId('LOG'), vehicleId: vehicle.vehicleId, date, odometer: initial?.odometer ?? vehicle.currentOdometer,
            category: Categories.CAR_WASH, cost: num(cost), fuelLiters: 0, note: '',
          })
          onClose()
        }}>Save</button>
      </div>
    </Sheet>
  )
}

const NOTE_TAGS = ['Noise', 'Vibration', 'Leak', 'Smell', 'Warning light', 'Handling', 'General']

/** Add or edit something to mention to the mechanic. */
export function NoteDialog({ vehicle, initial, onClose }: { vehicle: Vehicle; initial?: CarNote; onClose: () => void }) {
  const [tag, setTag] = useState(initial?.tag ?? 'General')
  const [text, setText] = useState(initial?.text ?? '')
  const [date, setDate] = useState(initial?.date ?? today())
  const [odo, setOdo] = useState((initial?.odometer ?? vehicle.currentOdometer).toString())
  return (
    <Sheet title={initial ? 'Edit note' : 'Add a note'} onClose={onClose}>
      <div className="small soft">Type</div>
      <div className="chips">{NOTE_TAGS.map((t) => <button key={t} className={`chip ${t === tag ? 'on' : ''}`} onClick={() => setTag(t)}>{t}</button>)}</div>
      <label className="f">What did you notice?
        <textarea rows={3} value={text} placeholder="e.g. squeaking when braking, mostly cold mornings" onChange={(e) => setText(e.target.value)} />
      </label>
      <label className="f">Date<input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></label>
      <Field label="Odometer (km)" value={odo} onChange={setOdo} inputMode="numeric" />
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!text.trim() || !validOdometer(odo)} onClick={async () => {
          await db.notes.put({
            noteId: initial?.noteId ?? newId('NOTE'), vehicleId: vehicle.vehicleId, date, odometer: num(odo),
            tag, text: text.trim(), done: initial?.done ?? false,
          })
          onClose()
        }}>Save</button>
      </div>
    </Sheet>
  )
}
