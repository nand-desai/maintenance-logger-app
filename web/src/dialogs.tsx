import { useState } from 'react'
import { db, newId } from './db'
import { saveSettings } from './store'
import { Field, Sheet } from './ui'
import type { MaintenanceSchedule, Settings, Vehicle } from './types'

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
