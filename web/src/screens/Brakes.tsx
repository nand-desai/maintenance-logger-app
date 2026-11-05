import { useState } from 'react'
import { deleteBrakeEntry } from '../ops'
import { km, mm, money } from '../logic/format'
import { BRAKE_NEW_MM, BRAKE_REPLACE_MM, brakeKmLeft, latestBrake } from '../logic/tires'
import { BrakePad } from '../components'
import { Detail, Icon, Pill } from '../ui'
import type { BrakeEntry, Vehicle } from '../types'

const padLevel = (v: number) => (v >= 6 ? 'good' : v >= BRAKE_REPLACE_MM ? 'warn' : 'bad')
const WORD = { good: 'Good', warn: 'Getting low', bad: 'Replace soon' } as const
const COLOR = { good: 'var(--mint-fg)', warn: 'var(--amber-fg)', bad: 'var(--coral-fg)' } as const

function Gauge({ label, front, entries, odometer }: { label: string; front: boolean; entries: BrakeEntry[]; odometer: number }) {
  const e = latestBrake(entries, front)
  const v = e ? (front ? e.frontMm : e.rearMm) : null
  const level = v == null ? 'neutral' : padLevel(v)
  const left = brakeKmLeft(entries, front)
  return (
    <div className="row" style={{ alignItems: 'center', gap: 16 }}>
      <BrakePad mm={v} color={v == null ? 'var(--ink-soft)' : COLOR[level as 'good']} newMm={BRAKE_NEW_MM} />
      <div className="grow" style={{ display: 'grid', gap: 6 }}>
        <div className="row" style={{ flexWrap: "wrap", gap: 6 }}><b className="grow" style={{ whiteSpace: "nowrap" }}>{label}</b>{v == null ? <span className="soft">—</span> : <Pill level={level}>{mm(v)} · {WORD[level as 'good']}</Pill>}</div>
        {v != null && e && (
          <>
            <span className={`bar ${level}`} style={{ background: `var(--${level === 'good' ? 'mint' : level === 'warn' ? 'amber' : 'coral'}-bg)` }}><i style={{ width: `${Math.min(100, (v / BRAKE_NEW_MM) * 100)}%` }} /></span>
            <span className="soft small">
              Measured {e.date}{odometer > e.odometer ? ` (${km(odometer - e.odometer)} ago)` : ''}{left != null ? ` · ≈ ${km(left)} until ${BRAKE_REPLACE_MM} mm` : ''}
            </span>
          </>
        )}
      </div>
    </div>
  )
}

function BrakeCard({ e, onEdit }: { e: BrakeEntry; onEdit: () => void }) {
  const [open, setOpen] = useState(false)
  const summary = [e.frontMm != null ? `Front ${e.frontMm.toFixed(1)}` : null, e.rearMm != null ? `Rear ${e.rearMm.toFixed(1)}` : null].filter(Boolean).join(' · ')
  return (
    <div className="card" role="button" tabIndex={0} style={{ cursor: 'pointer' }} onClick={() => setOpen(!open)}>
      <div className="row">
        <span style={{ color: 'var(--primary)' }}><Icon name={e.type === 'Replaced' ? 'build' : 'ruler'} /></span>
        <div className="grow"><div className="title">{e.type === 'Replaced' ? 'New pads fitted' : 'Pads measured'}</div><div className="soft small">{e.date} · {km(e.odometer)}</div></div>
        <span className="soft small">{summary}{summary ? ' mm' : ''}</span>
      </div>
      {open && (
        <div onClick={(ev) => ev.stopPropagation()} style={{ marginTop: 8 }}>
          {e.frontMm != null && <Detail label="Front pads" value={mm(e.frontMm)} />}
          {e.rearMm != null && <Detail label="Rear pads" value={mm(e.rearMm)} />}
          {e.cost > 0 && <Detail label="Cost" value={money(e.cost)} />}
          {e.note && <Detail label="Note" value={e.note} />}
          <div className="actions">
            <button className="btn text" onClick={onEdit}><Icon name="edit" size={18} /> Edit</button>
            <button className="btn text danger" onClick={() => deleteBrakeEntry(e)}><Icon name="trash" size={18} /> Delete</button>
          </div>
        </div>
      )}
    </div>
  )
}

export function Brakes({ vehicle, entries, onLog, onEdit }: { vehicle: Vehicle; entries: BrakeEntry[]; onLog: (type?: string) => void; onEdit: (e: BrakeEntry) => void }) {
  const any = entries.some((e) => e.frontMm != null || e.rearMm != null)
  return (
    <>
      <div className="card" style={{ display: 'grid', gap: 14 }}>
        <div><div className="title" style={{ fontSize: 17 }}>Brake pads</div>
          <div className="soft small">Replace around {BRAKE_REPLACE_MM} mm. New pads are about {BRAKE_NEW_MM - 2}–{BRAKE_NEW_MM} mm.</div></div>
        {any ? (
          <>
            <Gauge label="Front pads" front entries={entries} odometer={vehicle.currentOdometer} />
            <Gauge label="Rear pads" front={false} entries={entries} odometer={vehicle.currentOdometer} />
          </>
        ) : <div className="soft">No measurements yet. Measure the pad thickness (your mechanic can tell you) and log it here.</div>}
        <div className="row">
          <button className="btn grow" onClick={() => onLog('Measured')}><Icon name="ruler" size={18} /> Log mm</button>
          <button className="btn outline grow" onClick={() => onLog('Replaced')}><Icon name="build" size={18} /> New pads</button>
        </div>
      </div>
      <h2>History</h2>
      {entries.length === 0 && <div className="card"><div className="title">Nothing logged yet</div><div className="soft small">Log a measurement, or when new pads go on.</div></div>}
      {entries.map((e) => <BrakeCard key={e.entryId} e={e} onEdit={() => onEdit(e)} />)}
      <button className="btn fab" onClick={() => onLog()}><Icon name="add" /> Log brake pads</button>
    </>
  )
}
