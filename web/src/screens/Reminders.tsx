import { db } from '../db'
import { Icon, Pill } from '../ui'
import type { Reminder } from '../types'

const icsStamp = (ms: number) => new Date(ms).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z')
const esc = (s: string) => s.replace(/\\/g, '\\\\').replace(/;/g, '\;').replace(/,/g, '\\,').replace(/\n/g, '\\n')

/** A calendar file with an alarm at the reminder's time, so the phone itself alerts you. */
export function reminderIcs(r: Reminder): string {
  return [
    'BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Carlo//Reminders//EN', 'CALSCALE:GREGORIAN', 'BEGIN:VEVENT',
    `UID:${r.reminderId}@carlo`, `DTSTAMP:${icsStamp(Date.now())}`, `DTSTART:${icsStamp(r.triggerAt)}`, `DTEND:${icsStamp(r.triggerAt + 30 * 60_000)}`,
    `SUMMARY:${esc(r.title)}`, `DESCRIPTION:${esc(r.note || 'Reminder from Carlo')}`,
    'BEGIN:VALARM', 'TRIGGER:PT0S', 'ACTION:DISPLAY', `DESCRIPTION:${esc(r.title)}`, 'END:VALARM', 'END:VEVENT', 'END:VCALENDAR',
  ].join('\r\n')
}

async function addToCalendar(r: Reminder) {
  const name = `${r.title.replace(/[^\w-]+/g, '-').slice(0, 30) || 'reminder'}.ics`
  const file = new File([reminderIcs(r)], name, { type: 'text/calendar' })
  const nav = navigator as Navigator & { canShare?: (d: ShareData) => boolean }
  if (nav.canShare?.({ files: [file] })) {
    try { await navigator.share({ files: [file], title: r.title }); return } catch (e) { if ((e as Error).name === 'AbortError') return }
  }
  const a = document.createElement('a')
  a.href = URL.createObjectURL(file); a.download = name; a.click()
  setTimeout(() => URL.revokeObjectURL(a.href), 5000)
}

export function Reminders({ reminders, onAdd, onEdit }: { reminders: Reminder[]; onAdd: () => void; onEdit: (r: Reminder) => void }) {
  const now = Date.now()
  const upcoming = reminders.filter((r) => r.triggerAt > now).sort((a, b) => a.triggerAt - b.triggerAt)
  const past = reminders.filter((r) => r.triggerAt <= now).sort((a, b) => b.triggerAt - a.triggerAt)
  const when = (ms: number) => new Date(ms).toLocaleString('en-CA', { dateStyle: 'medium', timeStyle: 'short' })
  const Card = ({ r, isPast }: { r: Reminder; isPast: boolean }) => (
    <div className="card" style={{ display: 'grid', gap: 6 }}>
      <div className="row">
        <div className="grow"><div className="title" style={{ color: isPast ? 'var(--ink-soft)' : undefined }}>{r.title}</div>
          {r.note && <div className="soft small">{r.note}</div>}</div>
        <button className="icon-btn" aria-label="Edit reminder" onClick={() => onEdit(r)}><Icon name="edit" /></button>
        <button className="icon-btn" aria-label="Delete reminder" onClick={() => db.reminders.delete(r.reminderId)}><Icon name="trash" /></button>
      </div>
      <div className="row"><Pill level={isPast ? 'neutral' : 'good'}>{when(r.triggerAt)}</Pill>
        {!isPast && <button className="btn text" onClick={() => addToCalendar(r)}><Icon name="calendar" size={18} /> Add to calendar</button>}</div>
    </div>
  )
  return (
    <>
      {reminders.length === 0 && (
        <button className="card" onClick={onAdd}>
          <div className="title">No reminders yet</div>
          <div className="soft small">Schedule one for an oil change, insurance renewal, tire swap… then tap “Add to calendar” so your phone alerts you at that time.</div>
        </button>
      )}
      {upcoming.map((r) => <Card key={r.reminderId} r={r} isPast={false} />)}
      {past.length > 0 && <h2>Earlier</h2>}
      {past.map((r) => <Card key={r.reminderId} r={r} isPast />)}
      <div className="soft small">Browsers can't schedule alerts on their own while the app is closed, so “Add to calendar” hands the reminder to your calendar app, which will.</div>
      <button className="btn fab" onClick={onAdd}><Icon name="add" /> Add reminder</button>
    </>
  )
}
