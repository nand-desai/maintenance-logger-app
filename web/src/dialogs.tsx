import { useState } from 'react'
import { db, newId, today } from './db'
import { saveSettings } from './store'
import { Field, Sheet } from './ui'
import { entryLitresPer100 } from './logic/expenses'
import { CATEGORY_LIST } from './logic/expenses'
import { runRecurring } from './logic/recurring'
import { Categories, type CarNote, type ExpenseLog, type MaintenanceSchedule, type RecurringExpense, type Settings, type Vehicle } from './types'

const PRESET_COLORS = ['#3B82F6', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#6B7280']
const num = (s: string) => (s.trim() === '' ? NaN : Number(s.replace(',', '.')))

/** Add a vehicle, or edit one when [initial] is given. Nothing is pre-filled for a new vehicle. */
export function VehicleDialog({ initial, settings, onClose, onSaved }: {
  initial?: Vehicle; settings: Settings; onClose: () => void; onSaved: (v: Vehicle) => void
}) {
  const [make, setMake] = useState(initial?.make ?? '')
  const [model, setModel] = useState(initial?.model ?? '')
  const [year, setYear] = useState(initial?.year?.toString() ?? '')
  const [odo, setOdo] = useState(initial?.currentOdometer?.toString() ?? '')
  const [color, setColor] = useState(initial?.colorHex ?? PRESET_COLORS[0])
  const palette = [...PRESET_COLORS, ...settings.savedColors.filter((c) => !PRESET_COLORS.includes(c))]
  const valid = make.trim() && model.trim() && Number.isInteger(num(year)) && Number.isInteger(num(odo)) && num(odo) >= 0

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
        {/* pick your own, then optionally keep it in "my colours" */}
        <label className="swatch" style={{ background: 'conic-gradient(red, yellow, lime, cyan, blue, magenta, red)', position: 'relative', overflow: 'hidden' }}>
          <input type="color" value={color} onChange={(e) => setColor(e.target.value.toUpperCase())}
            style={{ opacity: 0, position: 'absolute', inset: 0, width: '100%', height: '100%' }} aria-label="Pick your own colour" />
        </label>
      </div>
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
        <button className="btn" disabled={!Number.isInteger(num(odo))} onClick={async () => { await db.vehicles.update(vehicle.vehicleId, { currentOdometer: num(odo) }); onClose() }}>Save</button>
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
  const valid = name.trim() && num(interval) > 0 && Number.isInteger(num(last))
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
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!valid} onClick={async () => {
          await db.schedules.put({
            scheduleId: initial?.scheduleId ?? newId('SCH'), vehicleId: vehicle.vehicleId,
            serviceName: name.trim(), intervalKm: num(interval), lastDoneOdometer: num(last),
          })
          onClose()
        }}>Save</button>
      </div>
    </Sheet>
  )
}

/** Add an expense, or edit one when [initial] is given. */
export function ExpenseDialog({ vehicle, initial, previousFuelOdometer, onClose }: {
  vehicle: Vehicle; initial?: ExpenseLog; previousFuelOdometer: number | null; onClose: () => void
}) {
  const [date, setDate] = useState(initial?.date ?? today())
  const [odo, setOdo] = useState((initial?.odometer ?? vehicle.currentOdometer).toString())
  const [category, setCategory] = useState<string>(initial?.category ?? Categories.FUEL)
  const [cost, setCost] = useState(initial?.cost?.toString() ?? '')
  const [litres, setLitres] = useState(initial?.fuelLiters ? initial.fuelLiters.toString() : '')
  const [note, setNote] = useState(initial?.note ?? '')
  const isFuel = category === Categories.FUEL
  const isOther = category === Categories.OTHER
  const rate = isFuel && Number.isInteger(num(odo)) ? entryLitresPer100(previousFuelOdometer, num(odo), num(litres) || 0) : null
  const valid = date && Number.isInteger(num(odo)) && !Number.isNaN(num(cost)) && (!isOther || note.trim())

  const save = async () => {
    await db.expenses.put({
      logId: initial?.logId ?? newId('LOG'), vehicleId: vehicle.vehicleId, date, odometer: num(odo), category,
      cost: num(cost), fuelLiters: isFuel ? num(litres) || 0 : 0, note: isOther ? note.trim() : '',
    })
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
      <Field label="Cost (CAD)" value={cost} onChange={setCost} inputMode="decimal" />
      {isFuel && <Field label="Fuel (litres)" value={litres} onChange={setLitres} inputMode="decimal" />}
      {rate != null && <div className="small soft">≈ {rate.toFixed(1)} L/100 km since the last fill-up</div>}
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
      {existing && <button className="btn text danger" onClick={async () => { await db.recurring.delete(existing.recurringId); onClose() }}>Remove monthly insurance</button>}
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
        <button className="btn" disabled={Number.isNaN(num(cost)) || !date} onClick={async () => {
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
        <button className="btn" disabled={!text.trim() || !Number.isInteger(num(odo))} onClick={async () => {
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
