import { useCallback, useEffect, useState } from 'react'
import { ExpenseDialog, InsuranceDialog, NoteDialog, OdometerDialog, ScheduleDialog, VehicleDialog, WashDialog } from './dialogs'
import { HelpSheet } from './help'
import { runRecurring } from './logic/recurring'
import { Expenses } from './screens/Expenses'
import { Notes } from './screens/Notes'
import { Wash } from './screens/Wash'
import { importBackup } from './backup'
import { selectVehicle, useGarage, useSettings } from './store'
import { Categories, type BrakeEntry, type CarNote, type ExpenseLog, type MaintenanceRecord, type MaintenanceSchedule, type Reminder, type TireEvent, type TireSet, type Vehicle } from './types'
import { Icon } from './ui'
import { Home } from './screens/Home'
import { Landing } from './screens/Landing'
import { Maintenance } from './screens/Maintenance'
import { BrakeDialog, ReminderDialog, ServiceDoneDialog, SwapDialog, TireEventDialog, TireSetDialog } from './dialogs2'
import { Brakes } from './screens/Brakes'
import { Reminders } from './screens/Reminders'
import { Tires } from './screens/Tires'
import { db } from './db'
import { km } from './logic/format'
import { kmRemaining } from './logic/health'
import { Settings } from './screens/Settings'
import type { SectionId } from './logic/status'

type Screen = 'home' | SectionId | 'settings'
const TITLES: Record<Screen, string> = {
  home: 'My Garage', maintenance: 'Maintenance', expenses: 'Expenses', tires: 'Tires', brakes: 'Brakes',
  wash: 'Car wash', notes: 'Notes', reminders: 'Reminders', settings: 'Settings',
}

type Dialog =
  | { kind: 'vehicle'; edit?: Vehicle } | { kind: 'odometer' } | { kind: 'schedule'; edit?: MaintenanceSchedule }
  | { kind: 'expense'; edit?: ExpenseLog } | { kind: 'insurance' } | { kind: 'wash'; edit?: ExpenseLog }
  | { kind: 'note'; edit?: CarNote }
  | { kind: 'done'; schedule: MaintenanceSchedule; edit?: MaintenanceRecord }
  | { kind: 'tireSet'; edit?: TireSet } | { kind: 'swap'; target?: TireSet }
  | { kind: 'tireEvent'; edit?: TireEvent; type?: string; wheel?: string }
  | { kind: 'brake'; edit?: BrakeEntry; type?: string }
  | { kind: 'reminder'; edit?: Reminder; prefill?: { title: string; note: string } } | null

export default function App() {
  const garage = useGarage()
  const settings = useSettings()
  const [screen, setScreen] = useState<Screen>('home')
  const [dialog, setDialog] = useState<Dialog>(null)
  const [toast, setToast] = useState<string | null>(null)
  const [help, setHelp] = useState(false)

  const say = useCallback((m: string) => { setToast(m); setTimeout(() => setToast(null), 3500) }, [])

  // theme: "system" follows the phone, otherwise force light or dark
  useEffect(() => {
    const root = document.documentElement
    if (settings.themeMode === 'system') root.removeAttribute('data-theme')
    else root.setAttribute('data-theme', settings.themeMode)
    const dark = settings.themeMode === 'dark' || (settings.themeMode === 'system' && matchMedia('(prefers-color-scheme: dark)').matches)
    document.querySelector('meta[name=theme-color]')?.setAttribute('content', dark ? '#12141C' : '#F8F9FE')
  }, [settings.themeMode])

  // add any monthly payments (insurance) that have come due, now and whenever the app is reopened
  useEffect(() => {
    runRecurring()
    const onVisible = () => document.visibilityState === 'visible' && runRecurring()
    document.addEventListener('visibilitychange', onVisible)
    return () => document.removeEventListener('visibilitychange', onVisible)
  }, [])

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
        <button className="icon-btn help-fab" aria-label="Help: save Carlo to your phone" onClick={() => setHelp(true)}><Icon name="help" /></button>
        <Landing onAdd={() => setDialog({ kind: 'vehicle' })} onImport={() => document.getElementById('restore-file')?.click()} />
        <input id="restore-file" type="file" accept="application/json,.json" hidden onChange={(e) => { importFile(e.target.files?.[0]); e.target.value = '' }} />
        {dialog?.kind === 'vehicle' && (
          <VehicleDialog settings={settings} onClose={() => setDialog(null)} onSaved={(v) => { selectVehicle(v.vehicleId); setDialog(null) }} />
        )}
        {help && <HelpSheet onClose={() => setHelp(false)} />}
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
        <button className="icon-btn" aria-label="Help: save Carlo to your phone" onClick={() => setHelp(true)}><Icon name="help" /></button>
      </header>
      <main className="content" key={screen}>
        {screen === 'home' && (
          <Home data={data} vehicles={garage.vehicles} onOpen={setScreen}
            onEditOdometer={() => setDialog({ kind: 'odometer' })} onSwitch={selectVehicle} onAddVehicle={() => setDialog({ kind: 'vehicle' })} />
        )}
        {screen === 'maintenance' && (
          <Maintenance vehicle={vehicle} schedules={data.schedules} records={garage.records ?? []} dueSoonKm={settings.dueSoonKm}
            onAdd={() => setDialog({ kind: 'schedule' })} onEdit={(s) => setDialog({ kind: 'schedule', edit: s })}
            onDone={(s) => setDialog({ kind: 'done', schedule: s })} onEditRecord={(s, r) => setDialog({ kind: 'done', schedule: s, edit: r })}
            onRemind={(s) => {
              const left = kmRemaining(s, vehicle.currentOdometer)
              setDialog({ kind: 'reminder', prefill: { title: `${s.serviceName} due`, note: left < 0 ? `Overdue by ${km(-left)}` : `About ${km(left)} left (due at ${km(s.lastDoneOdometer + s.intervalKm)})` } })
            }} />
        )}
        {screen === 'settings' && (
          <Settings settings={settings} vehicle={vehicle} onToast={say}
            onEditVehicle={() => setDialog({ kind: 'vehicle', edit: vehicle })} onOdometer={() => setDialog({ kind: 'odometer' })}
            onAddVehicle={() => setDialog({ kind: 'vehicle' })} onDeleted={() => { setScreen('home') }} />
        )}
        {screen === 'expenses' && (
          <Expenses vehicle={vehicle} expenses={data.expenses} insurance={garage.recurring?.find((r) => r.category === Categories.INSURANCE)}
            onAdd={() => setDialog({ kind: 'expense' })} onEdit={(l) => setDialog({ kind: 'expense', edit: l })} onInsurance={() => setDialog({ kind: 'insurance' })} />
        )}
        {screen === 'wash' && <Wash expenses={data.expenses} onLog={() => setDialog({ kind: 'wash' })} onEdit={(l) => setDialog({ kind: 'wash', edit: l })} />}
        {screen === 'notes' && <Notes vehicle={vehicle} notes={data.notes} onAdd={() => setDialog({ kind: 'note' })} onEdit={(n) => setDialog({ kind: 'note', edit: n })} onToast={say} />}
        {screen === 'tires' && (
          <Tires vehicle={vehicle} sets={data.tireSets} events={data.tireEvents}
            onAddSet={() => setDialog({ kind: 'tireSet' })} onEditSet={(t) => setDialog({ kind: 'tireSet', edit: t })}
            onDeleteSet={(t) => db.tireSets.delete(t.setId)} onSwap={(t) => setDialog({ kind: 'swap', target: t })}
            onLog={(type, wheel) => setDialog({ kind: 'tireEvent', type, wheel })} onEditEvent={(e) => setDialog({ kind: 'tireEvent', edit: e })} />
        )}
        {screen === 'brakes' && <Brakes vehicle={vehicle} entries={data.brakes} onLog={(type) => setDialog({ kind: 'brake', type })} onEdit={(e) => setDialog({ kind: 'brake', edit: e })} />}
        {screen === 'reminders' && <Reminders reminders={data.reminders} onAdd={() => setDialog({ kind: 'reminder' })} onEdit={(r) => setDialog({ kind: 'reminder', edit: r })} />}
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
      {dialog?.kind === 'expense' && (
        <ExpenseDialog vehicle={vehicle} initial={dialog.edit} onClose={() => setDialog(null)}
          previousFuelOdometer={data.expenses.filter((e) => e.category === Categories.FUEL && e.logId !== dialog.edit?.logId).reduce<number | null>((m, e) => (m == null || e.odometer > m ? e.odometer : m), null)} />
      )}
      {dialog?.kind === 'insurance' && (
        <InsuranceDialog vehicle={vehicle} existing={garage.recurring?.find((r) => r.category === Categories.INSURANCE)} onClose={() => setDialog(null)} />
      )}
      {dialog?.kind === 'wash' && <WashDialog vehicle={vehicle} initial={dialog.edit} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'note' && <NoteDialog vehicle={vehicle} initial={dialog.edit} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'done' && <ServiceDoneDialog vehicle={vehicle} schedule={dialog.schedule} initial={dialog.edit} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'tireSet' && <TireSetDialog vehicle={vehicle} initial={dialog.edit} isFirst={data.tireSets.length === 0} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'swap' && (() => {
        const mounted = data.tireSets.find((t) => t.mounted)
        const candidates = dialog.target ? [dialog.target] : data.tireSets.filter((t) => !t.mounted)
        const opposite = mounted?.season === 'Summer' ? 'Winter' : mounted?.season === 'Winter' ? 'Summer' : undefined
        return <SwapDialog vehicle={vehicle} candidates={candidates} preselected={dialog.target ?? candidates.find((c) => c.season === opposite)} onClose={() => setDialog(null)} />
      })()}
      {dialog?.kind === 'tireEvent' && (
        <TireEventDialog vehicle={vehicle} initial={dialog.edit} preset={{ type: dialog.type, wheel: dialog.wheel }}
          mountedSetId={data.tireSets.find((t) => t.mounted)?.setId ?? null} onClose={() => setDialog(null)} />
      )}
      {dialog?.kind === 'brake' && <BrakeDialog vehicle={vehicle} initial={dialog.edit} presetType={dialog.type} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'reminder' && <ReminderDialog vehicle={vehicle} initial={dialog.edit} prefill={dialog.prefill} defaultHour={settings.reminderHour} onClose={() => setDialog(null)} />}
      {help && <HelpSheet onClose={() => setHelp(false)} />}
      {toast && <div className="toast">{toast}</div>}
    </div>
  )
}

