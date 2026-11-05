import { useRef } from 'react'

/** Common car colours, shown as swatches. */
export const CAR_COLORS = [
  '#FFFFFF', '#E5E7EB', '#9CA3AF', '#6B7280', '#374151', '#111827',
  '#EF4444', '#B91C1C', '#7F1D1D', '#F97316', '#F59E0B', '#EAB308',
  '#84CC16', '#22C55E', '#10B981', '#14B8A6', '#06B6D4', '#3B82F6',
  '#1D4ED8', '#1E3A8A', '#8B5CF6', '#A855F7', '#EC4899', '#A16207',
  '#D6C3A3', '#78350F',
]

function hexToHsv(hex: string): [number, number, number] {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex)
  const n = parseInt(m ? m[1] : '3B82F6', 16)
  const r = ((n >> 16) & 255) / 255, g = ((n >> 8) & 255) / 255, b = (n & 255) / 255
  const max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min
  let h = 0
  if (d) h = max === r ? ((g - b) / d) % 6 : max === g ? (b - r) / d + 2 : (r - g) / d + 4
  h = (h * 60 + 360) % 360
  return [h, max ? d / max : 0, max]
}

function hsvToHex(h: number, s: number, v: number): string {
  const f = (n: number) => {
    const k = (n + h / 60) % 6
    const x = v - v * s * Math.max(0, Math.min(k, 4 - k, 1))
    return Math.round(x * 255).toString(16).padStart(2, '0')
  }
  return `#${f(5)}${f(3)}${f(1)}`.toUpperCase()
}

/** A drag-anywhere colour picker (shade square + hue strip + hex box) that works on every phone, unlike the browser's own picker. */
export function ColorPicker({ value, onChange }: { value: string; onChange: (hex: string) => void }) {
  const [h, s, v] = hexToHsv(value)
  const square = useRef<HTMLDivElement>(null)
  const strip = useRef<HTMLDivElement>(null)

  function move(e: React.PointerEvent) {
    const el = e.currentTarget as HTMLElement
    const r = el.getBoundingClientRect()
    const x = Math.min(1, Math.max(0, (e.clientX - r.left) / r.width))
    const y = Math.min(1, Math.max(0, (e.clientY - r.top) / r.height))
    if (el === square.current) onChange(hsvToHex(h, x, 1 - y))
    else onChange(hsvToHex(x * 360, s || 0.6, v || 0.9))
  }
  const handlers = {
    onPointerDown: (e: React.PointerEvent) => { e.preventDefault(); (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId); move(e) },
    onPointerMove: (e: React.PointerEvent) => { if (e.buttons || e.pointerType === 'touch') move(e) },
  }

  return (
    <div className="cpicker">
      <div ref={square} className="cp-square" {...handlers}
        style={{ background: `linear-gradient(to top,#000,transparent),linear-gradient(to right,#fff,hsl(${h},100%,50%))` }}
        role="slider" aria-label="Shade" aria-valuetext={value}>
        <span className="cp-dot" style={{ left: `${s * 100}%`, top: `${(1 - v) * 100}%`, background: value }} />
      </div>
      <div ref={strip} className="cp-hue" {...handlers} role="slider" aria-label="Hue" aria-valuetext={`hue ${Math.round(h)}`}>
        <span className="cp-dot" style={{ left: `${(h / 360) * 100}%`, top: '50%', background: `hsl(${h},100%,50%)` }} />
      </div>
      <label className="f">Hex colour
        <input value={value} maxLength={7} autoCapitalize="characters"
          onChange={(e) => { const t = e.target.value.toUpperCase(); if (/^#[0-9A-F]{6}$/.test(t)) onChange(t) }}
          key={value} defaultValue={value} />
      </label>
    </div>
  )
}
