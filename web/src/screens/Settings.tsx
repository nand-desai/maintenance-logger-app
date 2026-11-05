import { useEffect, useRef, useState } from 'react'
import { exportBackup, importBackup } from '../backup'
import { db } from '../db'
import { km } from '../logic/format'
import { saveSettings } from '../store'
import { Icon } from '../ui'
import type { Settings as S, ThemeMode, Vehicle } from '../types'

async function shareOrDownload(json: string) {
  const name = `carlo-backup-${new Date().toISOString().slice(0, 10)}.json`
  const file = new File([json], name, { type: 'application/json' })
  const nav = navigator as Navigator & { canShare?: (d: ShareData) => boolean }
  if (nav.canShare?.({ files: [file] })) {
    try { await navigator.share({ files: [file], title: name }); return 'Backup shared' } catch (e) { if ((e as Error).name === 'AbortError') return null }
  }
  const a = document.createElement('a')
  a.href = URL.createObjectURL(file); a.download = name; a.click()
  setTimeout(() => URL.revokeObjectURL(a.href), 5000)
  return 'Backup downloaded'
}

export function Settings({ settings, vehicle, onToast, onEditVehicle, onOdometer, onAddVehicle, onDeleted }: {
  settings: S; vehicle: Vehicle; onToast: (m: string) => void
  onEditVehicle: () => void; onOdometer: () => void; onAddVehicle: () => void; onDeleted: () => void
}) {
  const fileInput = useRef<HTMLInputElement>(null)
  const [persisted, setPersisted] = useState<boolean | null>(null)
  useEffect(() => { navigator.storage?.persisted?.().then(setPersisted) }, [])

  const doImport = async (f?: File) => {
    if (!f) return
    try {
      const c = await importBackup(await f.text())
      onToast(`Imported ${c.vehicles} vehicles, ${c.services} services, ${c.expenses} expenses, ${c.tireSets} tire sets`)
    } catch (e) { onToast(`Import failed: ${(e as Error).message}`) }
  }
  const deleteVehicle = async () => {
    if (!confirm(`Delete ${vehicle.make} ${vehicle.model} and everything logged for it? This can't be undone.`)) return
    const id = vehicle.vehicleId
    await db.transaction('rw', [db.vehicles, db.schedules, db.maintenanceRecords, db.expenses, db.tireSets, db.tireEvents, db.brakeEntries, db.notes, db.recurring, db.reminders, db.photos], async () => {
      await db.schedules.where('vehicleId').equals(id).delete(); await db.maintenanceRecords.where('vehicleId').equals(id).delete(); await db.expenses.where('vehicleId').equals(id).delete()
      await db.tireSets.where('vehicleId').equals(id).delete(); await db.tireEvents.where('vehicleId').equals(id).delete()
      await db.brakeEntries.where('vehicleId').equals(id).delete(); await db.notes.where('vehicleId').equals(id).delete()
      await db.recurring.where('vehicleId').equals(id).delete(); await db.reminders.where('vehicleId').equals(id).delete()
      await db.photos.delete(id); await db.vehicles.delete(id)
    })
    onDeleted()
  }

  return (
    <>
      <h2>App settings</h2>
      <div className="card" style={{ display: 'grid', gap: 12 }}>
        <div className="title">Appearance</div>
        <div className="seg" role="group" aria-label="Appearance">
          {(['system', 'light', 'dark'] as ThemeMode[]).map((m) => (
            <button key={m} className={settings.themeMode === m ? 'on' : ''} onClick={() => saveSettings({ themeMode: m })}>{m[0].toUpperCase() + m.slice(1)}</button>
          ))}
        </div>
      </div>
      <div className="card" style={{ display: 'grid', gap: 6 }}>
        <div className="title">Due-soon warning</div>
        <div className="soft small">Services turn amber within {km(settings.dueSoonKm)} of being due</div>
        <input type="range" min={500} max={5000} step={500} value={settings.dueSoonKm} onChange={(e) => saveSettings({ dueSoonKm: Number(e.target.value) })} />
      </div>
      <div className="card" style={{ display: 'grid', gap: 10 }}>
        <div className="title">Your data</div>
        <div className="soft small">
          Everything is stored in this browser on your device. Back it up often, especially before clearing Safari data or deleting the app icon.
          {persisted === false && ' Storage is not protected from being cleared yet.'}
          {persisted === true && ' Storage is protected from automatic clean-up.'}
        </div>
        <div className="row">
          <button className="btn grow" onClick={async () => { const m = await shareOrDownload(await exportBackup()); if (m) onToast(m) }}><Icon name="swap" size={18} /> Back up</button>
          <button className="btn outline grow" onClick={() => fileInput.current?.click()}>Import</button>
        </div>
        {persisted === false && (
          <button className="btn text" onClick={async () => setPersisted(await (navigator.storage?.persist?.() ?? Promise.resolve(false)))}>Protect my data from clean-up</button>
        )}
        <input ref={fileInput} type="file" accept="application/json,.json" hidden onChange={(e) => { doImport(e.target.files?.[0]); e.target.value = '' }} />
      </div>

      <h2>{vehicle.make} {vehicle.model}</h2>
      <div className="card" style={{ display: 'grid' }}>
        {[['edit', 'Edit details', `${vehicle.year} ${vehicle.make} ${vehicle.model}`, onEditVehicle],
          ['car', 'Set odometer', km(vehicle.currentOdometer), onOdometer],
          ['add', 'Add another vehicle', 'Switch between cars from the home screen', onAddVehicle]].map(([ic, t, sub, fn]) => (
          <button key={t as string} className="row" onClick={fn as () => void} style={{ padding: '10px 0', textAlign: 'left' }}>
            <Icon name={ic as string} />
            <span className="grow"><div className="title">{t as string}</div><div className="soft small">{sub as string}</div></span>
            <span className="soft"><Icon name="chevron" /></span>
          </button>
        ))}
      </div>
      <button className="btn outline danger block" onClick={deleteVehicle}><Icon name="trash" size={18} /> Delete this vehicle</button>
      <p className="soft small" style={{ textAlign: 'center' }}>Carlo web · km, litres, L/100 km · Canadian dollars</p>
    </>
  )
}
