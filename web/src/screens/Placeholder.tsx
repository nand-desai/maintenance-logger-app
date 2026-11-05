import { Icon } from '../ui'

const NOTES: Record<string, string> = {
  expenses: 'Expense log, the donut chart, monthly insurance that adds itself, and "Other" expenses with a description.',
  tires: 'The top-down wheel diagram, tire sets and seasonal swaps, tread depth and punctures.',
  brakes: 'Brake pad thickness with the tiny thick/thin pad visual and a wear estimate.',
  wash: 'When the car was last washed, and what each wash cost.',
  notes: 'Things to mention to your mechanic, shareable as a list.',
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
