import { Icon } from '../ui'

const NOTES: Record<string, string> = {
  tires: 'The top-down wheel diagram, tire sets and seasonal swaps, tread depth and punctures.',
  brakes: 'Brake pad thickness with the tiny thick/thin pad visual and a wear estimate.',
  reminders: 'Scheduled reminders. On iPhone these will start as calendar (.ics) events and in-app alerts.',
}

export function Placeholder({ id, title }: { id: string; title: string }) {
  return (
    <div className="card" style={{ display: 'grid', gap: 8 }}>
      <div className="row"><Icon name="build" /><div className="title">{title} is coming to the web app next</div></div>
      <div className="soft">{NOTES[id]}</div>
      <div className="soft small">
        Your imported data for this section is already here and shows up on the home tiles. Use the Android app for full editing until it lands.
      </div>
    </div>
  )
}
