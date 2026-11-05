import { useCallback, useEffect, useState } from 'react'
import { OdometerDialog, ScheduleDialog, VehicleDialog } from './dialogs'
import { importBackup } from './backup'
import { selectVehicle, useGarage, useSettings } from './store'
import type { MaintenanceSchedule, Vehicle } from './types'
import { Icon } from './ui'
import { Home } from './screens/Home'
import { Landing } from './screens/Landing'
import { Maintenance } from './screens/Maintenance'
import { Placeholder } from './screens/Placeholder'
import { Settings } from './screens/Settings'
import type { SectionId } from './logic/status'

type Screen = 'home' | SectionId | 'settings'
const TITLES: Record<Screen, string> = {
  home: 'My Garage', maintenance: 'Maintenance', expenses: 'Expenses', tires: 'Tires', brakes: 'Brakes',
  wash: 'Car wash', notes: 'Notes', reminders: 'Reminders', settings: 'Settings',
}

type Dialog = { kind: 'vehicle'; edit?: Vehicle } | { kind: 'odometer' } | { kind: 'schedule'; edit?: MaintenanceSchedule } | null

export default function App() {
  const garage = useGarage()
  const settings = useSettings()
  const [screen, setScreen] = useState<Screen>('home')
  const [dialog, setDialog] = useState<Dialog>(null)
  const [toast, setToast] = useState<string | null>(null)

  const say = useCallback((m: string) => { setToast(m); setTimeout(() => setToast(null), 3500) }, [])

  // theme: "system" follows the phone, otherwise force light or dark
  useEffect(() => {
    const root = document.documentElement
    if (settings.themeMode === 'system') root.removeAttribute('data-theme')
    else root.setAttribute('data-theme', settings.themeMode)
    const dark = settings.themeMode === 'dark' || (settings.themeMode === 'system' && matchMedia('(prefers-color-scheme: dark)').matches)
    document.querySelector('meta[name=theme-color]')?.setAttribute('content', dark ? '#12141C' : '#F8F9FE')
  }, [settings.themeMode])

  // ask the browser not to clear our data when space is tight
  useEffect(() => { navigator.storage?.persist?.() }, [])

  const importFile = async (f?: File) => {
    if (!f) return
    try { const c = await importBackup(await f.text()); say(`Imported ${c.vehicles} vehicles and ${c.expenses} expenses`) }
    catch (e) { say(`Import failed: ${(e as Error).message}`) }
  }

  if (!garage) return <div className="app" />

  const vehicle = garage.vehicle
  if (!vehicle) {
    return (
      <div className="app">
        <Landing onAdd={() => setDialog({ kind: 'vehicle' })} onImport={() => document.getElementById('restore-file')?.click()} />
        <input id="restore-file" type="file" accept="application/json,.json" hidden onChange={(e) => { importFile(e.target.files?.[0]); e.target.value = '' }} />
        {dialog?.kind === 'vehicle' && (
          <VehicleDialog settings={settings} onClose={() => setDialog(null)} onSaved={(v) => { selectVehicle(v.vehicleId); setDialog(null) }} />
        )}
        {toast && <div className="toast">{toast}</div>}
      </div>
    )
  }

  const data = {
    vehicle, schedules: garage.schedules ?? [], expenses: garage.expenses ?? [], tireSets: garage.tireSets ?? [],
    tireEvents: garage.tireEvents ?? [], brakes: garage.brakes ?? [], reminders: garage.reminders ?? [],
    notes: garage.notes ?? [], dueSoonKm: settings.dueSoonKm,
  }
  const isRoot = screen === 'home' || screen === 'settings' || screen === 'reminders'

  return (
    <div className="app">
      <header className="topbar">
        {!isRoot && <button className="icon-btn" aria-label="Back" onClick={() => setScreen('home')}><Icon name="back" /></button>}
        <h1>{TITLES[screen]}</h1>
      </header>
      <main className="content" key={screen}>
        {screen === 'home' && (
          <Home data={data} vehicles={garage.vehicles} onOpen={setScreen}
            onEditOdometer={() => setDialog({ kind: 'odometer' })} onSwitch={selectVehicle} onAddVehicle={() => setDialog({ kind: 'vehicle' })} />
        )}
        {screen === 'maintenance' && (
          <Maintenance vehicle={vehicle} schedules={data.schedules} dueSoonKm={settings.dueSoonKm}
            onAdd={() => setDialog({ kind: 'schedule' })} onEdit={(s) => setDialog({ kind: 'schedule', edit: s })} />
        )}
        {screen === 'settings' && (
          <Settings settings={settings} vehicle={vehicle} onToast={say}
            onEditVehicle={() => setDialog({ kind: 'vehicle', edit: vehicle })} onOdometer={() => setDialog({ kind: 'odometer' })}
            onAddVehicle={() => setDialog({ kind: 'vehicle' })} onDeleted={() => { setScreen('home') }} />
        )}
        {!['home', 'maintenance', 'settings'].includes(screen) && <Placeholder id={screen} title={TITLES[screen]} />}
      </main>

      <nav className="nav"><div className="nav-inner">
        <button className={screen !== 'reminders' && screen !== 'settings' ? 'active' : ''} onClick={() => setScreen('home')}><Icon name="home" />Home</button>
        <button className={screen === 'reminders' ? 'active' : ''} onClick={() => setScreen('reminders')}><Icon name="bell" />Reminders</button>
        <button className={screen === 'settings' ? 'active' : ''} onClick={() => setScreen('settings')}><Icon name="settings" />Settings</button>
      </div></nav>

      {dialog?.kind === 'vehicle' && (
        <VehicleDialog initial={dialog.edit} settings={settings} onClose={() => setDialog(null)} onSaved={(v) => { selectVehicle(v.vehicleId); setDialog(null) }} />
      )}
      {dialog?.kind === 'odometer' && <OdometerDialog vehicle={vehicle} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'schedule' && <ScheduleDialog vehicle={vehicle} initial={dialog.edit} onClose={() => setDialog(null)} />}
      {toast && <div className="toast">{toast}</div>}
    </div>
  )
}

