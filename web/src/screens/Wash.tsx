import { deleteExpense } from '../ops'
import { money, washSummary } from '../logic/format'
import { Categories, type ExpenseLog } from '../types'
import { Icon } from '../ui'

export function Wash({ expenses, onLog, onEdit }: { expenses: ExpenseLog[]; onLog: () => void; onEdit: (l: ExpenseLog) => void }) {
  const washes = expenses.filter((e) => e.category === Categories.CAR_WASH).sort((a, b) => (a.date < b.date ? 1 : -1))
  const total = washes.reduce((t, w) => t + w.cost, 0)
  return (
    <>
      <div className="card" style={{ display: 'grid', gap: 12 }}>
        <div className="row">
          <span style={{ width: 52, height: 52, borderRadius: '50%', display: 'grid', placeItems: 'center', background: 'var(--sky-bg)', color: 'var(--sky-fg)' }}><Icon name="wash" size={28} /></span>
          <div className="grow">
            <div style={{ fontSize: 20, fontWeight: 700 }}>{washSummary(washes[0]?.date)}</div>
            {washes.length > 0 && <div className="soft small">{washes.length} washes · {money(total)} total · {money(total / washes.length)} each</div>}
          </div>
        </div>
        <button className="btn block" onClick={onLog}><Icon name="add" /> Log a wash</button>
      </div>
      {washes.map((w) => (
        <div key={w.logId} className="card row">
          <div className="grow"><div className="title">{w.date}</div><div className="soft small">{money(w.cost)}</div></div>
          <button className="icon-btn" aria-label="Edit wash" onClick={() => onEdit(w)}><Icon name="edit" /></button>
          <button className="icon-btn" aria-label="Delete wash" onClick={() => deleteExpense(w.logId)}><Icon name="trash" /></button>
        </div>
      ))}
    </>
  )
}
