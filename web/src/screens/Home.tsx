import { useEffect, useRef, useState } from 'react'
import { db } from '../db'
import { km } from '../logic/format'
import { buildAlerts, buildTiles, type GarageData, type SectionId } from '../logic/status'
import { Icon, Sheet } from '../ui'
import type { Vehicle } from '../types'

const LEVEL_COLORS: Record<string, string> = { good: 'good', warn: 'warn', bad: 'bad', neutral: 'neutral' }

/** Downscales a picked image to at most 1600px and stores it in the browser database. */
async function savePhoto(vehicleId: string, file: File) {
  const bmp = await createImageBitmap(file)
  const scale = Math.min(1, 1600 / Math.max(bmp.width, bmp.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bmp.width * scale); canvas.height = Math.round(bmp.height * scale)
  canvas.getContext('2d')!.drawImage(bmp, 0, 0, canvas.width, canvas.height)
  const blob = await new Promise<Blob>((res, rej) => canvas.toBlob((b) => (b ? res(b) : rej(new Error('encode failed'))), 'image/jpeg', 0.9))
  await db.photos.put({ vehicleId, blob }) // a new picture starts centred
}

type Adjust = { zoom: number; x: number; y: number; fit: boolean }
const CENTRED: Adjust = { zoom: 1, x: 0, y: 0, fit: false }
const adjustOf = (p?: { zoom?: number; x?: number; y?: number; fit?: boolean }): Adjust => ({ ...CENTRED, ...(p ? { zoom: p.zoom ?? 1, x: p.x ?? 0, y: p.y ?? 0, fit: p.fit ?? false } : {}) })

function Picture({ url, adj, onDrag }: { url: string; adj: Adjust; onDrag?: (dx: number, dy: number) => void }) {
  const last = useRef<{ x: number; y: number } | null>(null)
  return (
    <div className={`photo ${onDrag ? 'adjusting' : ''}`}
      onPointerDown={onDrag ? (e) => { (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId); last.current = { x: e.clientX, y: e.clientY } } : undefined}
      onPointerMove={onDrag ? (e) => {
        if (!last.current) return
        const r = e.currentTarget.getBoundingClientRect()
        onDrag(((e.clientX - last.current.x) / r.width) * 100, ((e.clientY - last.current.y) / r.height) * 100)
        last.current = { x: e.clientX, y: e.clientY }
      } : undefined}
      onPointerUp={onDrag ? () => { last.current = null } : undefined}>
      <img src={url} alt="Vehicle" draggable={false} className={adj.fit ? 'cutout' : ''}
        style={{ transform: `translate(${adj.x}%, ${adj.y}%) scale(${adj.zoom})` }} />
    </div>
  )
}

/** Move, zoom or fit the picture inside the frame. */
function PhotoDialog({ vehicleId, url, start, onClose, onChoose }: { vehicleId: string; url: string; start: Adjust; onClose: (a?: Adjust) => void; onChoose: () => void }) {
  const [adj, setAdj] = useState(start)
  const clamp = (n: number) => Math.max(-100, Math.min(100, n))
  return (
    <Sheet title="Position your picture" onClose={() => onClose()}>
      <Picture url={url} adj={adj} onDrag={(dx, dy) => setAdj((a) => ({ ...a, x: clamp(a.x + dx), y: clamp(a.y + dy) }))} />
      <div className="small soft">Drag the picture to move it.</div>
      <label className="f">Zoom
        <input type="range" min={0.5} max={3} step={0.05} value={adj.zoom} onChange={(e) => setAdj({ ...adj, zoom: +e.target.value })} />
      </label>
      <div className="chips">
        <button className="chip" onClick={() => setAdj(CENTRED)}>Centre</button>
        <button className={`chip ${adj.fit ? 'on' : ''}`} onClick={() => setAdj({ ...adj, fit: !adj.fit })}>Show the whole picture</button>
        <button className="chip" onClick={onChoose}>Choose a different photo</button>
      </div>
      <div className="actions">
        <button className="btn text" onClick={() => onClose()}>Cancel</button>
        <button className="btn" onClick={async () => { await db.photos.update(vehicleId, adj); onClose(adj) }}>Save</button>
      </div>
    </Sheet>
  )
}

function Photo({ vehicle }: { vehicle: Vehicle }) {
  const [url, setUrl] = useState<string>()
  const [adj, setAdj] = useState<Adjust>(CENTRED)
  const [editing, setEditing] = useState(false)
  const input = useRef<HTMLInputElement>(null)
  useEffect(() => {
    let made: string | undefined
    let live = true
    db.photos.get(vehicle.vehicleId).then((p) => { if (live && p) { made = URL.createObjectURL(p.blob); setUrl(made); setAdj(adjustOf(p)) } else if (live) setUrl(undefined) })
    return () => { live = false; if (made) URL.revokeObjectURL(made) }
  }, [vehicle.vehicleId, vehicle.make])
  const pick = async (f?: File) => {
    if (!f) return
    await savePhoto(vehicle.vehicleId, f)
    const p = await db.photos.get(vehicle.vehicleId)
    if (p) { setUrl(URL.createObjectURL(p.blob)); setAdj(CENTRED); setEditing(true) }
  }
  return (
    <>
      {url ? <div onClick={() => setEditing(true)} style={{ cursor: 'pointer' }}><Picture url={url} adj={adj} /></div>
        : <div className="photo"><div style={{ display: 'grid', gap: 8, justifyItems: 'center' }}>
            <span style={{ color: 'var(--primary)' }}><Icon name="car" size={40} /></span>
            <span className="soft">Add a photo of your car</span>
            <button className="btn tonal" onClick={() => input.current?.click()}><Icon name="camera" /> Choose photo</button>
          </div></div>}
      {url && <div className="small soft" style={{ textAlign: 'center', marginTop: 4 }}>Tap the picture to move, zoom or change it</div>}
      {editing && url && <PhotoDialog key={url} vehicleId={vehicle.vehicleId} url={url} start={adj} onChoose={() => input.current?.click()}
        onClose={(a) => { if (a) setAdj(a); setEditing(false) }} />}
      <input ref={input} type="file" accept="image/*" hidden onChange={(e) => { pick(e.target.files?.[0]); e.target.value = '' }} />
    </>
  )
}

export function Home({ data, vehicles, onOpen, onEditOdometer, onSwitch, onAddVehicle }: {
  data: GarageData; vehicles: Vehicle[]; onOpen: (id: SectionId) => void
  onEditOdometer: () => void; onSwitch: (id: string) => void; onAddVehicle: () => void
}) {
  const alerts = buildAlerts(data)
  const tiles = buildTiles(data)
  const top = alerts[0]
  const v = data.vehicle
  const level = top?.level ?? 'good'
  return (
    <>
      <Photo vehicle={v} />
      <div className="card row">
        <span style={{ width: 14, height: 14, borderRadius: '50%', background: v.colorHex, flex: 'none' }} />
        <div className="grow">
          <div className="title" style={{ fontSize: 17 }}>{v.make} {v.model}</div>
          <button className="soft" onClick={onEditOdometer} aria-label="Edit odometer" style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
            {v.year} · {km(v.currentOdometer)} <span style={{ color: 'var(--primary)' }}><Icon name="edit" size={14} /></span>
          </button>
        </div>
        <span style={{ position: 'relative', width: 40, height: 40, display: 'grid', placeItems: 'center' }} className="soft">
          <Icon name="unfold" />
          <select aria-label="Switch vehicle" value={v.vehicleId} onChange={(e) => (e.target.value === '__add' ? onAddVehicle() : onSwitch(e.target.value))}
            style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', opacity: 0 }}>
            {vehicles.map((x) => <option key={x.vehicleId} value={x.vehicleId}>{x.make} {x.model}</option>)}
            <option value="__add">+ Add vehicle</option>
          </select>
        </span>
      </div>

      <button className={`banner ${LEVEL_COLORS[level]}`} disabled={!top} onClick={() => top && onOpen(top.target)}>
        <span className="badge"><Icon name={top ? 'warning' : 'ok'} /></span>
        <span className="grow">
          <div className="title">{top ? top.text : 'Everything looks good'}</div>
          <div className="small" style={{ opacity: 0.8 }}>
            {!top ? 'Nothing needs attention right now' : alerts.length > 1 ? `+${alerts.length - 1} more · tap to take a look` : 'Tap to open'}
          </div>
        </span>
        {top && <Icon name="chevron" />}
      </button>

      <div className="tiles">
        {tiles.map((t) => (
          <button key={t.id} className="tile" onClick={() => onOpen(t.id)}>
            <div className="row" style={{ alignItems: 'flex-start' }}>
              <span className="ico" style={{ background: t.accent + '29', color: t.accent }}><Icon name={t.icon} /></span>
              <span className="grow" />
              {t.level !== 'neutral' && <span className={`dot ${t.level}`} />}
            </div>
            <div>
              <div className="title">{t.title}</div>
              <div className={`st ${t.level}`}>{t.status}</div>
            </div>
          </button>
        ))}
      </div>
    </>
  )
}
