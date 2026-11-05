import { useState } from 'react'
import { db } from '../db'
import { CATEGORY_LIST, CHART_COLORS, expenseTitle, summarize } from '../logic/expenses'
import { km, money } from '../logic/format'
import { Detail, Icon } from '../ui'
import type { ExpenseLog, RecurringExpense, Vehicle } from '../types'

/** Donut chart drawn with SVG circles (circumference normalised to 100). */
function Donut({ values, size }: { values: number[]; size: number }) {
  const total = values.reduce((a, b) => a + b, 0)
  let offset = 25
  return (
    <svg width={size} height={size} viewBox="-1 -1 38 38" role="img" aria-label="Spending by category">
      <circle cx="18" cy="18" r="15.9155" fill="none" stroke="var(--neutral)" strokeWidth="5" />
      {total > 0 && values.map((v, i) => {
        const pct = (v / total) * 100
        const el = pct > 0 && (
          <circle key={i} cx="18" cy="18" r="15.9155" fill="none" stroke={CHART_COLORS[i % CHART_COLORS.length]} strokeWidth="5"
            strokeDasharray={`${Math.max(0, pct - (values.filter((x) => x > 0).length > 1 ? 0.8 : 0))} ${100 - pct}`} strokeDashoffset={offset} />
        )
        offset -= pct
        return el
      })}
    </svg>
  )
}

function ExpenseCard({ l, onEdit }: { l: ExpenseLog; onEdit: () => void }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="card" role="button" tabIndex={0} style={{ cursor: 'pointer' }} onClick={() => setOpen(!open)}>
      <div className="row">
        <div className="grow">
          <div className="title">{expenseTitle(l)} · {money(l.cost)}</div>
          <div className="soft small">{l.date}</div>
        </div>
        <span className="soft" style={{ transform: open ? 'rotate(90deg)' : 'none', transition: 'transform .2s' }}><Icon name="chevron" /></span>
      </div>
      {open && (
        <div onClick={(e) => e.stopPropagation()} style={{ marginTop: 10 }}>
          {l.category === 'Other' && <Detail label="Category" value="Other" />}
          {l.note && l.category !== 'Other' && <Detail label="Note" value={l.note} />}
          <Detail label="Odometer" value={km(l.odometer)} />
          {l.fuelLiters > 0 && <Detail label="Fuel" value={`${l.fuelLiters.toFixed(1)} L`} />}
          {l.fuelLiters > 0 && l.cost > 0 && <Detail label="Price per litre" value={money(l.cost / l.fuelLiters, 3)} />}
          <div className="actions">
            <button className="btn text" onClick={onEdit}><Icon name="edit" size={18} /> Edit</button>
            <button className="btn text danger" onClick={() => db.expenses.delete(l.logId)}><Icon name="trash" size={18} /> Delete</button>
          </div>
        </div>
      )}
    </div>
  )
}

export function Expenses({ vehicle, expenses, insurance, onAdd, onEdit, onInsurance }: {
  vehicle: Vehicle; expenses: ExpenseLog[]; insurance?: RecurringExpense
  onAdd: () => void; onEdit: (l: ExpenseLog) => void; onInsurance: () => void
}) {
  const [totalOpen, setTotalOpen] = useState(false)
  const [donutOpen, setDonutOpen] = useState(false)
  const s = summarize(expenses, vehicle.currentOdometer)
  const values = CATEGORY_LIST.map((c) => s.byCategory[c] ?? 0)
  return (
    <>
      <button className="card row" onClick={onInsurance}>
        <span className="tile-ico" style={{ width: 44, height: 44, borderRadius: '50%', display: 'grid', placeItems: 'center', background: 'color-mix(in srgb, var(--primary) 16%, transparent)', color: 'var(--primary)' }}>
          <Icon name="ok" />
        </span>
        <span className="grow">
          <div className="title">Insurance premium</div>
          <div className="soft small">
            {insurance ? `${money(insurance.amount)} every month on day ${insurance.dayOfMonth}${insurance.active ? ` · next ${insurance.nextDue}` : ' · paused'}` : 'Add your monthly premium and Carlo logs it for you'}
          </div>
        </span>
        <span className="soft"><Icon name={insurance ? 'edit' : 'add'} /></span>
      </button>

      <button className="card" onClick={() => setTotalOpen(!totalOpen)} style={{ display: 'grid', gap: 10 }}>
        <div className="row"><span className="soft small grow">Total cost of ownership</span><span className="soft" style={{ transform: totalOpen ? 'rotate(90deg)' : 'none' }}><Icon name="chevron" /></span></div>
        <div style={{ fontSize: 30, fontWeight: 700 }}>{money(s.lifetime)}</div>
        <div className="row" style={{ justifyContent: 'space-around' }}>
          <div style={{ textAlign: 'center' }}><div className="title">{s.costPerKm != null ? money(s.costPerKm) : '—'}</div><div className="soft small">Cost / km</div></div>
          <div style={{ textAlign: 'center' }}><div className="title">{s.litresPer100 != null ? `${s.litresPer100.toFixed(1)} L/100 km` : '—'}</div><div className="soft small">Fuel use</div></div>
        </div>
        {totalOpen && (
          <div>
            <Detail label="Entries logged" value={expenses.length} />
            <Detail label="Distance tracked" value={km(s.distance)} />
            <Detail label="Fuel bought" value={`${s.fuelLitres.toFixed(1)} L`} />
          </div>
        )}
      </button>

      <button className="card" onClick={() => setDonutOpen(!donutOpen)} style={{ display: 'grid', gap: 12 }}>
        <div className="row"><span className="title grow">Where the money goes</span><span className="soft" style={{ transform: donutOpen ? 'rotate(90deg)' : 'none' }}><Icon name="chevron" /></span></div>
        <div className={donutOpen ? '' : 'row'} style={{ display: 'flex', flexDirection: donutOpen ? 'column' : 'row', alignItems: 'center', gap: 20 }}>
          <Donut values={values} size={donutOpen ? 240 : 150} />
          <div style={{ display: 'grid', gap: 6 }}>
            {CATEGORY_LIST.map((c, i) => (
              <div key={c} className="row" style={{ gap: 8, fontSize: 14 }}>
                <i style={{ width: 10, height: 10, borderRadius: '50%', background: CHART_COLORS[i % CHART_COLORS.length], display: 'inline-block' }} />
                <span>{c} {donutOpen ? `${money(values[i])} (${s.lifetime > 0 ? Math.round((values[i] / s.lifetime) * 100) : 0}%)` : money(values[i], 0)}</span>
              </div>
            ))}
          </div>
        </div>
      </button>

      <h2>History</h2>
      {expenses.length === 0 && (
        <div className="card"><div className="title">No entries yet</div><div className="soft small">Tap “Add entry” to log fuel, a service, insurance or an upgrade.</div></div>
      )}
      {expenses.map((l) => <ExpenseCard key={l.logId} l={l} onEdit={() => onEdit(l)} />)}
      <button className="btn fab" onClick={onAdd}><Icon name="add" /> Add entry</button>
    </>
  )
}
