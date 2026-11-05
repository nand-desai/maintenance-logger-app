import { useEffect, useRef, useState } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { db, newId } from './db'
import type { Attachment, AttachmentOwner } from './types'
import { Icon } from './ui'

export const MAX_FILE_BYTES = 25 * 1024 * 1024
const MAX_IMAGE_PX = 1600

/** Photos are shrunk to a JPEG of at most 2000 px; anything else (PDFs...) is kept as it is. */
export async function prepareFile(file: File): Promise<{ blob: Blob; fileName: string; mime: string }> {
  if (file.size > MAX_FILE_BYTES) throw new Error(`${file.name} is over 25 MB`)
  if (file.type.startsWith('image/') && file.type !== 'image/gif') {
    try {
      const bmp = await createImageBitmap(file, { imageOrientation: 'from-image' })
      const scale = Math.min(1, MAX_IMAGE_PX / Math.max(bmp.width, bmp.height))
      const canvas = document.createElement('canvas')
      canvas.width = Math.round(bmp.width * scale); canvas.height = Math.round(bmp.height * scale)
      const ctx = canvas.getContext('2d')!
      ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, canvas.width, canvas.height)
      ctx.drawImage(bmp, 0, 0, canvas.width, canvas.height)
      const blob = await new Promise<Blob>((res, rej) => canvas.toBlob((b) => (b ? res(b) : rej(new Error('encode failed'))), 'image/jpeg', 0.8))
      return { blob, fileName: file.name.replace(/\.[^.]+$/, '') + '.jpg', mime: 'image/jpeg' }
    } catch { /* fall through and keep the original */ }
  }
  return { blob: file, fileName: file.name || 'attachment', mime: file.type || 'application/octet-stream' }
}

/** Adds the new files to a record and removes the ones the user took off. */
export async function commitAttachments(vehicleId: string, ownerType: AttachmentOwner, ownerId: string, add: File[], removeIds: string[]) {
  for (const f of add) {
    const p = await prepareFile(f)
    await db.attachments.put({
      attachmentId: newId('ATT'), vehicleId, ownerType, ownerId, fileName: p.fileName, mime: p.mime,
      size: p.blob.size, createdAt: Date.now(), blob: p.blob,
    })
  }
  if (removeIds.length) await db.attachments.bulkDelete(removeIds)
}

export const deleteAttachmentsFor = (ownerId: string) => db.attachments.where('ownerId').equals(ownerId).delete()

/** Opens the file in a new tab (images and PDFs preview; anything else downloads). */
export function openAttachment(a: Attachment) {
  const url = URL.createObjectURL(a.blob)
  const w = window.open(url, '_blank')
  if (!w) { const el = document.createElement('a'); el.href = url; el.download = a.fileName; el.click() }
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}

const prettySize = (n: number) => (n > 1_000_000 ? `${(n / 1_000_000).toFixed(1)} MB` : `${Math.max(1, Math.round(n / 1000))} KB`)

function Thumb({ blob, mime }: { blob: Blob; mime: string }) {
  const [url, setUrl] = useState<string>()
  useEffect(() => {
    if (!mime.startsWith('image/')) return
    const u = URL.createObjectURL(blob); setUrl(u)
    return () => URL.revokeObjectURL(u)
  }, [blob, mime])
  return url
    ? <img src={url} alt="" style={{ width: 44, height: 44, objectFit: 'cover', borderRadius: 10 }} />
    : <span style={{ width: 44, height: 44, borderRadius: 10, background: 'var(--neutral)', display: 'grid', placeItems: 'center', color: 'var(--ink-soft)' }}><Icon name="note" /></span>
}

/** Small "📎 2" marker for list rows. */
export function AttachmentCount({ ownerId }: { ownerId: string }) {
  const n = useLiveQuery(() => db.attachments.where('ownerId').equals(ownerId).count(), [ownerId], 0)
  return n > 0 ? <span className="soft small" title="Has attachments" style={{ display: 'inline-flex', alignItems: 'center', gap: 2 }}><Icon name="clip" size={14} />{n}</span> : null
}

/** The receipts on a record, shown in its expanded card. */
export function AttachmentList({ ownerId }: { ownerId: string }) {
  const items = useLiveQuery(() => db.attachments.where('ownerId').equals(ownerId).toArray(), [ownerId], [] as Attachment[])
  if (!items.length) return null
  return (
    <div style={{ display: 'grid', gap: 6, marginTop: 8 }}>
      <div className="small soft">Receipts and files</div>
      {items.map((a) => (
        <button key={a.attachmentId} className="row" onClick={() => openAttachment(a)} style={{ textAlign: 'left' }}>
          <Thumb blob={a.blob} mime={a.mime} />
          <span className="grow"><div className="small" style={{ fontWeight: 600 }}>{a.fileName}</div><div className="soft small">{prettySize(a.size)} · tap to open</div></span>
        </button>
      ))}
    </div>
  )
}

/** State for the attachments being edited in a dialog. Nothing is saved until [commit] runs. */
export function useAttachmentDraft(ownerId: string) {
  const existing = useLiveQuery(() => db.attachments.where('ownerId').equals(ownerId).toArray(), [ownerId], [] as Attachment[])
  const [pending, setPending] = useState<File[]>([])
  const [removed, setRemoved] = useState<string[]>([])
  return {
    existing: existing.filter((a) => !removed.includes(a.attachmentId)), pending,
    // copy the files out right away: the input's FileList is emptied as soon as the picker is reset
    add: (files: FileList | null) => { if (files) { const list = Array.from(files); setPending((p) => [...p, ...list]) } },
    removePending: (i: number) => setPending((p) => p.filter((_, j) => j !== i)),
    removeExisting: (id: string) => setRemoved((r) => [...r, id]),
    commit: (vehicleId: string, ownerType: AttachmentOwner) => commitAttachments(vehicleId, ownerType, ownerId, pending, removed),
  }
}

/** "Attach a receipt" section for a dialog: take a photo, or pick a photo / PDF. */
export function AttachmentsField({ draft }: { draft: ReturnType<typeof useAttachmentDraft> }) {
  const camera = useRef<HTMLInputElement>(null)
  const files = useRef<HTMLInputElement>(null)
  return (
    <div style={{ display: 'grid', gap: 8 }}>
      <div className="small soft">Receipts and files (optional)</div>
      {draft.existing.map((a) => (
        <div key={a.attachmentId} className="row">
          <Thumb blob={a.blob} mime={a.mime} />
          <span className="grow small">{a.fileName}</span>
          <button className="icon-btn" aria-label="Remove file" onClick={() => draft.removeExisting(a.attachmentId)}><Icon name="trash" size={20} /></button>
        </div>
      ))}
      {draft.pending.map((f, i) => (
        <div key={i} className="row">
          <Thumb blob={f} mime={f.type} />
          <span className="grow small">{f.name} <span className="soft">· new</span></span>
          <button className="icon-btn" aria-label="Remove file" onClick={() => draft.removePending(i)}><Icon name="trash" size={20} /></button>
        </div>
      ))}
      <div className="row">
        <button className="btn outline grow" onClick={() => camera.current?.click()}><Icon name="camera" size={18} /> Take photo</button>
        <button className="btn outline grow" onClick={() => files.current?.click()}><Icon name="clip" size={18} /> Add file</button>
      </div>
      <input ref={camera} type="file" accept="image/*" capture="environment" hidden onChange={(e) => { draft.add(e.target.files); e.target.value = '' }} />
      <input ref={files} type="file" accept="image/*,application/pdf" multiple hidden onChange={(e) => { draft.add(e.target.files); e.target.value = '' }} />
    </div>
  )
}
