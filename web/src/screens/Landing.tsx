import { Icon } from '../ui'

export function Landing({ onAdd, onImport }: { onAdd: () => void; onImport: () => void }) {
  return (
    <div className="center">
      <div className="logo"><Icon name="car" size={56} /></div>
      <h2 style={{ fontSize: 26 }}>Welcome to Carlo</h2>
      <p className="soft" style={{ margin: 0 }}>
        Keep every car's services, costs and reminders in one friendly place. Your data stays on this device.
      </p>
      <div style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: 10, marginTop: 12 }}>
        <button className="btn block" onClick={onAdd}><Icon name="add" /> Add your first vehicle</button>
        <button className="btn outline block" onClick={onImport}>Restore from a backup file</button>
      </div>
      <p className="soft small">Have the Android app? Back it up there and restore the same file here.</p>
    </div>
  )
}
