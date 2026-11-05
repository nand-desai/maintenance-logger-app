import { Icon, Sheet } from './ui'

const isIos = () => /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
const isInstalled = () => matchMedia('(display-mode: standalone)').matches || (navigator as Navigator & { standalone?: boolean }).standalone === true

/** The iOS "Share" glyph: a box with an arrow leaving the top. */
const ShareGlyph = () => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ verticalAlign: 'middle' }} aria-label="Share button">
    <path d="M12 15V3M8 7l4-4 4 4M7 11H6a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-8a1 1 0 0 0-1-1h-1" />
  </svg>
)

function Steps({ children }: { children: React.ReactNode }) {
  return <ol style={{ margin: '4px 0 0', paddingLeft: 22, display: 'grid', gap: 8 }}>{children}</ol>
}

/** The "?" help: how to install Carlo on the home screen, and how your data is kept. */
export function HelpSheet({ onClose }: { onClose: () => void }) {
  const ios = isIos()
  const iphone = (
    <div className="card" key="ios" style={{ display: 'grid', gap: 6, background: 'var(--neutral)', boxShadow: 'none' }}>
      <div className="title">iPhone or iPad (Safari)</div>
      <Steps>
        <li>Open this page in <b>Safari</b>.</li>
        <li>Tap the <b>Share</b> button <ShareGlyph /> (the square with an arrow, at the bottom or top of the screen).</li>
        <li>Scroll down and tap <b>Add to Home Screen</b>.</li>
        <li>Tap <b>Add</b>. Carlo now sits on your home screen like an app.</li>
      </Steps>
      <div className="small soft">Always open Carlo from that icon. It runs full-screen and works offline.</div>
    </div>
  )
  const android = (
    <div className="card" key="and" style={{ display: 'grid', gap: 6, background: 'var(--neutral)', boxShadow: 'none' }}>
      <div className="title">Android (Chrome)</div>
      <Steps>
        <li>Open this page in <b>Chrome</b>.</li>
        <li>Tap the <b>⋮</b> menu in the top corner.</li>
        <li>Tap <b>Install app</b> (or <b>Add to Home screen</b>).</li>
      </Steps>
    </div>
  )
  return (
    <Sheet title="Save Carlo to your phone" onClose={onClose}>
      {isInstalled() && (
        <div className="pill good" style={{ display: 'flex', gap: 8, alignItems: 'center' }}><Icon name="ok" size={18} /> You're already using the installed app.</div>
      )}
      {ios ? [iphone, android] : [android, iphone]}
      <div className="card" style={{ display: 'grid', gap: 6, background: 'var(--neutral)', boxShadow: 'none' }}>
        <div className="title">Keeping your data safe</div>
        <div className="small">
          Your cars are stored privately on this phone, nothing is uploaded. If you delete the home-screen icon or clear
          Safari's data, they are deleted too. Open <b>Settings → Back up</b> now and then and save the file to <b>Files</b> or iCloud Drive.
          To restore, use <b>Settings → Import</b>.
        </div>
      </div>
      <div className="actions"><button className="btn" onClick={onClose}>Got it</button></div>
    </Sheet>
  )
}
