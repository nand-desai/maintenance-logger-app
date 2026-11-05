import { useEffect, useRef, useState } from 'react'
import { db } from '../db'
import { km } from '../logic/format'
import { buildAlerts, buildTiles, type GarageData, type SectionId } from '../logic/status'
import { Icon } from '../ui'
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
  await db.photos.put({ vehicleId, blob })
}

function Photo({ vehicle }: { vehicle: Vehicle }) {
  const [url, setUrl] = useState<string>()
  const input = useRef<HTMLInputElement>(null)
  useEffect(() => {
    let made: string | undefined
    let live = true
    db.photos.get(vehicle.vehicleId).then((p) => { if (live && p) { made = URL.createObjectURL(p.blob); setUrl(made) } else if (live) setUrl(undefined) })
    return () => { live = false; if (made) URL.revokeObjectURL(made) }
  }, [vehicle.vehicleId, vehicle.make])
  const pick = async (f?: File) => {
    if (!f) return
    await savePhoto(vehicle.vehicleId, f)
    const p = await db.photos.get(vehicle.vehicleId)
    if (p) setUrl(URL.createObjectURL(p.blob))
  }
  return (
    <div className="photo">
      {url ? <img src={url} alt="Vehicle" onClick={() => input.current?.click()} />
        : <div style={{ display: 'grid', gap: 8, justifyItems: 'center' }}>
            <span style={{ color: 'var(--primary)' }}><Icon name="car" size={40} /></span>
            <span className="soft">Add a photo of your car</span>
            <button className="btn tonal" onClick={() => input.current?.click()}><Icon name="camera" /> Choose photo</button>
          </div>}
      <input ref={input} type="file" accept="image/*" hidden onChange={(e) => pick(e.target.files?.[0])} />
    </div>
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
