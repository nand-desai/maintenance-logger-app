import { useState } from 'react'
import { AttachmentCount, AttachmentList } from '../attachments'
import { deleteTireEvent } from '../ops'
import { km, mm, money } from '../logic/format'
import { CARE_KM, WHEELS, TREAD_REPLACE_MM, kmDriven, lastRotation, latestOfType, latestTread, swapDue, treadWear, treadFor, worstTread, type Wheel } from '../logic/tires'
import { seasonOf } from '../seasons'
import { TireEventType, type TireEvent, type TireSet, type Vehicle } from '../types'
import { TireDiagram } from '../components'
import { Detail, Icon, Pill } from '../ui'

const treadLevel = (v: number) => (v >= 4 ? 'good' : v >= TREAD_REPLACE_MM ? 'warn' : 'bad')
const EVENT_ICON: Record<string, string> = { Rotation: 'rotate', Balancing: 'tune', Alignment: 'ruler', Puncture: 'warning', Tread: 'height', Swap: 'swap' }

function CareTile({ icon, title, line, level, bar, onClick }: { icon: string; title: string; line: string; level: string; bar?: number; onClick: () => void }) {
  return (
    <button className="card" onClick={onClick} style={{ display: 'grid', gap: 6, padding: 12, justifyItems: 'start' }}>
      <span className={`pill ${level}`} style={{ padding: 8, borderRadius: '50%', display: 'grid' }}><Icon name={icon} size={20} /></span>
      <span className="title" style={{ fontSize: 15 }}>{title}</span>
      <span className={`small st ${level}`}>{line}</span>
      {bar != null && <span className={`bar ${level}`} style={{ width: '100%', height: 6, background: `var(--${level === 'good' ? 'mint' : level === 'warn' ? 'amber' : 'coral'}-bg)` }}><i style={{ width: `${Math.min(1, bar) * 100}%` }} /></span>}
    </button>
  )
}

function EventCard({ e, setName, onEdit }: { e: TireEvent; setName?: string; onEdit: () => void }) {
  const [open, setOpen] = useState(false)
  const title = e.type === 'Puncture' && e.wheel ? `Puncture · ${WHEELS[e.wheel as Wheel] ?? e.wheel}` : e.type === 'Tread' ? `Tread depth${worstTread(e) != null ? ` · lowest ${mm(worstTread(e)!)}` : ''}` : e.type
  return (
    <div className="card" role="button" tabIndex={0} style={{ cursor: 'pointer' }} onClick={() => setOpen(!open)}>
      <div className="row">
        <span style={{ color: 'var(--primary)' }}><Icon name={EVENT_ICON[e.type] ?? 'tire'} /></span>
        <div className="grow"><div className="title">{title} <AttachmentCount ownerId={e.eventId} /></div><div className="soft small">{e.date} · {km(e.odometer)}</div></div>
        <span className="soft" style={{ transform: open ? 'rotate(90deg)' : 'none' }}><Icon name="chevron" /></span>
      </div>
      {open && (
        <div onClick={(ev) => ev.stopPropagation()} style={{ marginTop: 8 }}>
          {setName && <Detail label="Tire set" value={setName} />}
          {e.cost > 0 && <Detail label="Cost" value={money(e.cost)} />}
          {e.type === 'Tread' && (Object.keys(WHEELS) as Wheel[]).map((w) => treadFor(e, w) != null && <Detail key={w} label={WHEELS[w]} value={mm(treadFor(e, w)!)} />)}
          {e.note && <Detail label="Note" value={e.note} />}
          <AttachmentList ownerId={e.eventId} />
          <div className="actions">
            <button className="btn text" onClick={onEdit}><Icon name="edit" size={18} /> Edit</button>
            <button className="btn text danger" onClick={() => deleteTireEvent(e)}><Icon name="trash" size={18} /> Delete</button>
          </div>
        </div>
      )}
    </div>
  )
}

export function Tires({ vehicle, sets, events, onAddSet, onEditSet, onDeleteSet, onSwap, onLog, onEditEvent }: {
  vehicle: Vehicle; sets: TireSet[]; events: TireEvent[]
  onAddSet: () => void; onEditSet: (s: TireSet) => void; onDeleteSet: (s: TireSet) => void
  onSwap: (target?: TireSet) => void; onLog: (type?: string, wheel?: string) => void; onEditEvent: (e: TireEvent) => void
}) {
  const [selected, setSelected] = useState<string | null>(null)
  const [wheelInfo, setWheelInfo] = useState<Wheel | null>(null)
  const mounted = sets.find((s) => s.mounted)
  const look = seasonOf(mounted?.season)
  const tread = latestTread(events, mounted?.setId ?? null)
  const wear = treadWear(events, mounted?.setId ?? null)
  const punctures: Partial<Record<Wheel, number>> = {}
  events.filter((e) => e.type === TireEventType.PUNCTURE && e.wheel && e.setId === (mounted?.setId ?? null)).forEach((e) => { punctures[e.wheel as Wheel] = (punctures[e.wheel as Wheel] ?? 0) + 1 })
  const sel = sets.find((s) => s.setId === selected)
  const odo = vehicle.currentOdometer
  const readings = events.filter((e) => e.type === 'Tread' && e.setId === (mounted?.setId ?? null))

  // Rotation happens at the seasonal swap, so it stays green until it's time to swap again.
  const seasonal = mounted?.season === 'Summer' || mounted?.season === 'Winter'
  const rot = lastRotation(events)
  const due = swapDue(mounted, new Date())
  const rotation = seasonal
    ? { level: !rot ? 'neutral' : due ? 'warn' : 'good', line: !rot ? 'Not logged' : due ? (mounted?.season === 'Summer' ? 'Swap to winter?' : 'Swap to summer?') : rot.type === 'Swap' ? 'Done at swap' : `Done ${rot.date}` }
    : (() => { const since = rot ? Math.max(0, odo - rot.odometer) : null; const r = since == null ? null : since / CARE_KM.Rotation
        return { level: r == null ? 'neutral' : r < 0.8 ? 'good' : r < 1 ? 'warn' : 'bad', line: since == null ? 'Not logged' : `${km(since)} ago`, bar: r ?? undefined } })()
  const care = (type: 'Balancing' | 'Alignment') => {
    const e = latestOfType(events, type); const since = e ? Math.max(0, odo - e.odometer) : null; const r = since == null ? null : since / CARE_KM[type]
    return { level: r == null ? 'neutral' : r < 0.8 ? 'good' : r < 1 ? 'warn' : 'bad', line: since == null ? 'Not logged' : `${km(since)} ago`, bar: r ?? undefined }
  }
  const bal = care('Balancing'), ali = care('Alignment')

  return (
    <>
      <div className="card" style={{ display: 'grid', gap: 8 }}>
        <div className="row"><span className="title grow" style={{ fontSize: 17 }}>On the car now</span>{mounted && <Pill level={look.pill}>{mounted.season}</Pill>}</div>
        {mounted ? (
          <div>
            <div className="title">{mounted.name}</div>
            {(mounted.brand || mounted.size) && <div className="soft small">{[mounted.brand, mounted.size].filter(Boolean).join(' · ')}</div>}
            <div className="soft small">{km(kmDriven(mounted, odo))} driven on this set</div>
          </div>
        ) : <div className="soft small">{sets.length ? 'No set is mounted. Open a set below and tap “Mount on car”.' : "Add your tire sets to track what's on the car."}</div>}
        <div style={{ display: 'grid', justifyItems: 'center' }}>
          <TireDiagram bodyColor={vehicle.colorHex} wheelColor={look.color} centerIcon={mounted ? (mounted.season === 'Winter' ? '❄' : mounted.season === 'Summer' ? '☀' : '☁') : undefined}
            punctures={punctures} tread={tread} onWheel={setWheelInfo} />
        </div>
        <div className="soft small" style={{ textAlign: 'center' }}>Tap a wheel for its punctures and tread depth</div>
        {mounted && sets.length > 1 && <button className="btn block" onClick={() => onSwap()}><Icon name="swap" size={18} /> Swap tires</button>}
        {sets.length === 0 && <button className="btn block" onClick={onAddSet}><Icon name="add" /> Add your first tire set</button>}
      </div>

      <div className="card" style={{ display: 'grid', gap: 8 }}>
        <div className="row">
          <div className="grow"><div className="title" style={{ fontSize: 17 }}>Tread depth</div>
            <div className="soft small">{!mounted ? 'Mount a set to track its tread' : Object.keys(tread).length ? `Replace around ${TREAD_REPLACE_MM} mm` : 'No measurements yet'}</div></div>
          <button className="btn tonal" disabled={!mounted} onClick={() => onLog('Tread')}>Log mm</button>
        </div>
        {(Object.keys(WHEELS) as Wheel[]).map((w) => tread[w] != null && (
          <div key={w} className="row" style={{ gap: 10 }}>
            <span style={{ width: 90, fontSize: 14 }}>{WHEELS[w]}</span>
            <span className={`bar ${treadLevel(tread[w]!)} grow`} style={{ background: `var(--${treadLevel(tread[w]!) === 'good' ? 'mint' : treadLevel(tread[w]!) === 'warn' ? 'amber' : 'coral'}-bg)`, height: 10 }}>
              <i style={{ width: `${Math.min(100, (tread[w]! / 10) * 100)}%` }} />
            </span>
            <b style={{ width: 62, textAlign: 'right', fontSize: 14 }}>{mm(tread[w]!)}</b>
          </div>
        ))}
        {readings.length > 0 && (wear ? (
          <>
            <Detail label="Wear" value={`${wear.mmPer1000Km.toFixed(2)} mm per 1,000 km`} />
            <Detail label={`Est. until ${TREAD_REPLACE_MM} mm`} value={`≈ ${km(wear.kmLeft)}`} />
          </>
        ) : <div className="soft small">Log a second measurement to see how fast the tires wear.</div>)}
      </div>

      <h2>Your tire sets</h2>
      <div className="chips">
        {sets.map((s) => { const l = seasonOf(s.season); return (
          <button key={s.setId} className={`chip ${selected === s.setId ? 'on' : ''}`} onClick={() => setSelected(selected === s.setId ? null : s.setId)}>
            <Icon name={l.icon} size={14} /> {s.name}{s.mounted ? ' · on car' : ''}
          </button>) })}
        <button className="chip" onClick={onAddSet}>+ Add set</button>
      </div>
      {sel && (
        <div className="card" style={{ display: 'grid', gap: 6 }}>
          <div className="row"><span className="title grow">{sel.name}</span><Pill level={seasonOf(sel.season).pill}>{sel.season}</Pill></div>
          {sel.brand && <Detail label="Brand / model" value={sel.brand} />}
          {sel.size && <Detail label="Size" value={sel.size} />}
          <Detail label="Driven on this set" value={km(kmDriven(sel, odo))} />
          <Detail label="Status" value={sel.mounted ? 'On the car' : 'In storage'} />
          {!sel.mounted && <button className="btn block" onClick={() => onSwap(sel)}><Icon name="swap" size={18} /> Mount on car</button>}
          <div className="actions">
            <button className="btn text" onClick={() => onEditSet(sel)}><Icon name="edit" size={18} /> Edit</button>
            <button className="btn text danger" onClick={() => { if (confirm(`Delete ${sel.name}? Its history entries stay.`)) { onDeleteSet(sel); setSelected(null) } }}><Icon name="trash" size={18} /> Delete</button>
          </div>
        </div>
      )}

      <h2>Tire care</h2>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10 }}>
        <CareTile icon={rotation.level === 'good' ? 'check' : 'rotate'} title="Rotation" line={rotation.line} level={rotation.level} bar={'bar' in rotation ? rotation.bar : undefined} onClick={() => onLog('Rotation')} />
        <CareTile icon="tune" title="Balancing" line={bal.line} level={bal.level} bar={bal.bar} onClick={() => onLog('Balancing')} />
        <CareTile icon="ruler" title="Alignment" line={ali.line} level={ali.level} bar={ali.bar} onClick={() => onLog('Alignment')} />
      </div>

      <h2>History</h2>
      {events.length === 0 && <div className="card"><div className="title">Nothing logged yet</div><div className="soft small">Tap a care tile or a wheel, or use “Log tire event”.</div></div>}
      {events.map((e) => <EventCard key={e.eventId} e={e} setName={sets.find((s) => s.setId === e.setId)?.name} onEdit={() => onEditEvent(e)} />)}
      <button className="btn fab" onClick={() => onLog()}><Icon name="add" /> Log tire event</button>

      {wheelInfo && (
        <div className="scrim" onClick={() => setWheelInfo(null)}>
          <div className="sheet" onClick={(e) => e.stopPropagation()}>
            <h3>{WHEELS[wheelInfo]}</h3>
            {tread[wheelInfo] != null && <div className="title">Tread depth: {mm(tread[wheelInfo]!)}</div>}
            {events.filter((e) => e.type === 'Puncture' && e.wheel === wheelInfo && e.setId === (mounted?.setId ?? null)).map((e) => (
              <div key={e.eventId}><div className="title">{e.date} · {km(e.odometer)}</div>{e.note && <div className="soft small">{e.note}</div>}</div>
            ))}
            {!punctures[wheelInfo] && <div className="soft">No punctures logged on this wheel for the current set.</div>}
            <div className="actions">
              <button className="btn text" onClick={() => setWheelInfo(null)}>Close</button>
              <button className="btn" onClick={() => { const w = wheelInfo; setWheelInfo(null); onLog('Puncture', w) }}>Log puncture here</button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
