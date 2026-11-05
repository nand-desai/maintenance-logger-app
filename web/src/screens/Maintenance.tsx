import { useState } from 'react'
import { db } from '../db'
import { km } from '../logic/format'
import { health, kmRemaining, overallHealth, progress } from '../logic/health'
import { Detail, Icon, Pill } from '../ui'
import type { MaintenanceSchedule, Vehicle } from '../types'

const LABEL = { healthy: 'All services healthy', dueSoon: 'Service due soon', overdue: 'Service overdue' } as const
const LEVEL = { healthy: 'good', dueSoon: 'warn', overdue: 'bad' } as const

function ServiceCard({ s, vehicle, dueSoonKm, onEdit }: { s: MaintenanceSchedule; vehicle: Vehicle; dueSoonKm: number; onEdit: () => void }) {
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
          <div className="actions" style={{ justifyContent: 'space-between', flexWrap: 'wrap', marginTop: 10 }}>
            <button className="btn" onClick={() => db.schedules.update(s.scheduleId, { lastDoneOdometer: odo })}><Icon name="check" size={18} /> Done now</button>
            <span>
              <button className="btn text" onClick={onEdit}><Icon name="edit" size={18} /> Edit</button>
              <button className="btn text danger" onClick={() => db.schedules.delete(s.scheduleId)}><Icon name="trash" size={18} /> Delete</button>
            </span>
          </div>
        </div>
      )}
    </div>
  )
}

export function Maintenance({ vehicle, schedules, dueSoonKm, onAdd, onEdit }: {
  vehicle: Vehicle; schedules: MaintenanceSchedule[]; dueSoonKm: number
  onAdd: () => void; onEdit: (s: MaintenanceSchedule) => void
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
      {sorted.map((s) => <ServiceCard key={s.scheduleId} s={s} vehicle={vehicle} dueSoonKm={dueSoonKm} onEdit={() => onEdit(s)} />)}
      <button className="btn fab" onClick={onAdd}><Icon name="add" /> Add service</button>
    </>
  )
}
