import { useState } from 'react'
import { db } from '../db'
import { km } from '../logic/format'
import { Icon } from '../ui'
import type { CarNote, Vehicle } from '../types'

function NoteCard({ n, onEdit }: { n: CarNote; onEdit: () => void }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="card" role="button" tabIndex={0} style={{ cursor: 'pointer' }} onClick={() => setOpen(!open)}>
      <div className="row" style={{ alignItems: 'flex-start' }}>
        <input type="checkbox" checked={n.done} aria-label="Mentioned to the mechanic" style={{ width: 22, height: 22, marginTop: 2 }}
          onClick={(e) => e.stopPropagation()} onChange={() => db.notes.update(n.noteId, { done: !n.done })} />
        <div className="grow">
          <div className="soft small">{n.tag} · {n.date} · {km(n.odometer)}</div>
          <div style={{ marginTop: 4, textDecoration: n.done ? 'line-through' : 'none', color: n.done ? 'var(--ink-soft)' : 'inherit',
            ...(open ? {} : { display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical' as const, overflow: 'hidden' }) }}>{n.text}</div>
        </div>
      </div>
      {open && (
        <div className="actions" onClick={(e) => e.stopPropagation()}>
          <button className="btn text" onClick={onEdit}><Icon name="edit" size={18} /> Edit</button>
          <button className="btn text danger" onClick={() => db.notes.delete(n.noteId)}><Icon name="trash" size={18} /> Delete</button>
        </div>
      )}
    </div>
  )
}

export function Notes({ vehicle, notes, onAdd, onEdit, onToast }: {
  vehicle: Vehicle; notes: CarNote[]; onAdd: () => void; onEdit: (n: CarNote) => void; onToast: (m: string) => void
}) {
  const [showDone, setShowDone] = useState(false)
  const open = notes.filter((n) => !n.done)
  const done = notes.filter((n) => n.done)
  const share = async () => {
    const text = `Things to mention about my ${vehicle.make} ${vehicle.model} (${km(vehicle.currentOdometer)}):\n` +
      [...open].sort((a, b) => (a.date < b.date ? -1 : 1)).map((n) => `• ${n.tag === 'General' ? '' : `[${n.tag}] `}${n.text} (${n.date}, ${km(n.odometer)})`).join('\n')
    try {
      if (navigator.share) await navigator.share({ title: 'Notes for my mechanic', text })
      else { await navigator.clipboard.writeText(text); onToast('Copied the list') }
    } catch (e) { if ((e as Error).name !== 'AbortError') onToast("Couldn't share the list") }
  }
  return (
    <>
      <div className="card" style={{ display: 'grid', gap: 12 }}>
        <div className="row">
          <span style={{ color: 'var(--primary)' }}><Icon name="note" size={32} /></span>
          <div className="grow">
            <div className="title" style={{ fontSize: 17 }}>For your next mechanic visit</div>
            <div className="soft small">{open.length ? `${open.length} thing${open.length === 1 ? '' : 's'} to mention` : 'Nothing to mention right now'}</div>
          </div>
        </div>
        {open.length > 0 && <button className="btn outline block" onClick={share}><Icon name="share" size={18} /> Share this list</button>}
      </div>
      {notes.length === 0 && (
        <button className="card" onClick={onAdd}>
          <div className="title">Jot down anything odd</div>
          <div className="soft small">A noise when braking, a vibration at highway speed, a smell, a warning light. Show the list to your mechanic so nothing gets forgotten.</div>
        </button>
      )}
      {open.map((n) => <NoteCard key={n.noteId} n={n} onEdit={() => onEdit(n)} />)}
      {done.length > 0 && <button className="btn text" onClick={() => setShowDone(!showDone)}>Already mentioned ({done.length}) {showDone ? '▴' : '▾'}</button>}
      {showDone && done.map((n) => <NoteCard key={n.noteId} n={n} onEdit={() => onEdit(n)} />)}
      <button className="btn fab" onClick={onAdd}><Icon name="add" /> Add note</button>
    </>
  )
}
