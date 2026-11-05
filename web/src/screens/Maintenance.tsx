import { useState } from 'react'
import { AttachmentCount, AttachmentList } from '../attachments'
import { db } from '../db'
import { deleteServiceRecord } from '../ops'
import { km, money } from '../logic/format'
import { health, kmRemaining, overallHealth, progress } from '../logic/health'
import { Detail, Icon, Pill } from '../ui'
import type { MaintenanceRecord, MaintenanceSchedule, Vehicle } from '../types'

const LABEL = { healthy: 'All services healthy', dueSoon: 'Service due soon', overdue: 'Service overdue' } as const
const LEVEL = { healthy: 'good', dueSoon: 'warn', overdue: 'bad' } as const

function ServiceCard({ s, vehicle, dueSoonKm, records, onEdit, onDone, onEditRecord, onRemind }: {
  s: MaintenanceSchedule; vehicle: Vehicle; dueSoonKm: number; records: MaintenanceRecord[]
  onEdit: () => void; onDone: () => void; onEditRecord: (r: MaintenanceRecord) => void; onRemind: () => void
}) {
  const [open, setOpen] = useState(false)
  const odo = vehicle.currentOdometer
  const h = health(s, odo, dueSoonKm)
  const left = kmRemaining(s, odo)
  return (
    <div className="card" onClick={() => setOpen(!open)} role="button" tabIndex={0} style={{ cursor: 'pointer' }}>
      <div className="row">
        <span className={`grow title`}>{s.serviceName}</span>
        <Pill level={LEVEL[h]}>{left < 0 ? `${km(-left)} overdue` : `${km(left)} left`}</Pill>
      </div>
      <div className={`bar ${LEVEL[h]}`} style={{ marginTop: 12, height: open ? 14 : 8, background: `var(--${h === 'healthy' ? 'mint' : h === 'dueSoon' ? 'amber' : 'coral'}-bg)` }}>
        <i style={{ width: `${progress(s, odo) * 100}%` }} />
      </div>
      {open && (
        <div onClick={(e) => e.stopPropagation()} style={{ marginTop: 14 }}>
          <Detail label="Service interval" value={`Every ${km(s.intervalKm)}`} />
          <Detail label="Last done at" value={km(s.lastDoneOdometer)} />
          <Detail label="Next due at" value={km(s.lastDoneOdometer + s.intervalKm)} />
          <Detail label="Status" value={LABEL[h]} />
          {records.length > 0 && (
            <div style={{ marginTop: 10 }}>
              <div className="small soft" style={{ marginBottom: 4 }}>History</div>
              {records.map((r) => (
                <div key={r.recordId} style={{ borderTop: '1px solid var(--outline)', padding: '6px 0' }}>
                  <button className="row" onClick={() => onEditRecord(r)} style={{ width: '100%', textAlign: 'left' }}>
                    <span className="grow">
                      <div className="small"><b>{r.date}</b> · {km(r.odometer)}{r.cost > 0 ? ` · ${money(r.cost)}` : ''} <AttachmentCount ownerId={r.recordId} /></div>
                      {r.note && <div className="soft small">{r.note}</div>}
                    </span>
                    <span className="soft"><Icon name="edit" size={16} /></span>
                  </button>
                  <AttachmentList ownerId={r.recordId} />
                </div>
              ))}
            </div>
          )}
          <div className="actions" style={{ justifyContent: 'space-between', flexWrap: 'wrap', marginTop: 10 }}>
            <button className="btn" onClick={onDone}><Icon name="check" size={18} /> Done</button>
            <span>
              <button className="btn text" onClick={onRemind}><Icon name="bell" size={18} /> Remind me</button>
              <button className="btn text" onClick={onEdit}><Icon name="edit" size={18} /> Edit</button>
              <button className="btn text danger" onClick={async () => { if (confirm(`Delete ${s.serviceName} and its history?`)) { for (const r of records) await deleteServiceRecord(r); await db.schedules.delete(s.scheduleId) } }}><Icon name="trash" size={18} /> Delete</button>
            </span>
          </div>
        </div>
      )}
    </div>
  )
}

export function Maintenance({ vehicle, schedules, records, dueSoonKm, onAdd, onEdit, onDone, onEditRecord, onRemind }: {
  vehicle: Vehicle; schedules: MaintenanceSchedule[]; records: MaintenanceRecord[]; dueSoonKm: number
  onAdd: () => void; onEdit: (s: MaintenanceSchedule) => void; onDone: (s: MaintenanceSchedule) => void
  onEditRecord: (s: MaintenanceSchedule, r: MaintenanceRecord) => void; onRemind: (s: MaintenanceSchedule) => void
}) {
  const overall = overallHealth(schedules, vehicle.currentOdometer, dueSoonKm)
  const sorted = [...schedules].sort((a, b) => kmRemaining(a, vehicle.currentOdometer) - kmRemaining(b, vehicle.currentOdometer))
  return (
    <>
      <div><Pill level={schedules.length ? LEVEL[overall] : 'neutral'}>{schedules.length ? LABEL[overall] : 'No services tracked yet'}</Pill></div>
      {schedules.length === 0 && (
        <button className="card" onClick={onAdd}>
          <div className="title">Add your first service</div>
          <div className="soft small">Oil change, air filter, brake fluid… Carlo will tell you when each one is due.</div>
        </button>
      )}
      {sorted.map((s) => (
        <ServiceCard key={s.scheduleId} s={s} vehicle={vehicle} dueSoonKm={dueSoonKm}
          records={records.filter((r) => r.scheduleId === s.scheduleId).sort((a, b) => (a.date < b.date ? 1 : -1))}
          onEdit={() => onEdit(s)} onDone={() => onDone(s)} onEditRecord={(r) => onEditRecord(s, r)} onRemind={() => onRemind(s)} />
      ))}
      <button className="btn fab" onClick={onAdd}><Icon name="add" /> Add service</button>
    </>
  )
}
