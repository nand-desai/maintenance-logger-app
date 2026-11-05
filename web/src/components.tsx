import { WHEELS, type Wheel } from './logic/tires'

const WHEEL_SPOTS: Record<Wheel, [number, number]> = { FL: [44, 28], FR: [190, 28], RL: [44, 158], RR: [190, 158] }

/**
 * Top-down car with four tappable wheels. Wheels take the colour of the mounted set's season,
 * a red badge counts punctures, and the latest tread depth (mm) is printed on each wheel. Front is at the top.
 */
export function TireDiagram({ bodyColor, wheelColor, centerIcon, punctures, tread, onWheel }: {
  bodyColor: string; wheelColor: string; centerIcon?: string
  punctures: Partial<Record<Wheel, number>>; tread: Partial<Record<Wheel, number>>; onWheel: (w: Wheel) => void
}) {
  return (
    <svg viewBox="0 0 260 240" width="100%" style={{ maxWidth: 300 }} role="img" aria-label="Car seen from above with four wheels">
      <rect x="75" y="15" width="110" height="210" rx="42" fill={bodyColor} opacity="0.88" />
      <rect x="89" y="63" width="82" height="36" rx="12" fill="#fff" opacity="0.75" />
      <rect x="89" y="107" width="82" height="48" rx="10" fill="#000" opacity="0.1" />
      <rect x="91" y="155" width="78" height="26" rx="10" fill="#fff" opacity="0.75" />
      {centerIcon && <text x="130" y="135" textAnchor="middle" fontSize="30" fill="#fff">{centerIcon}</text>}
      {(Object.keys(WHEELS) as Wheel[]).map((w) => {
        const [x, y] = WHEEL_SPOTS[w]
        const n = punctures[w] ?? 0
        return (
          <g key={w} onClick={() => onWheel(w)} style={{ cursor: 'pointer' }} role="button" aria-label={`${WHEELS[w]} wheel`}>
            {/* generous invisible tap area around the wheel */}
            <rect x={x - 10} y={y - 6} width="46" height="66" fill="transparent" />
            <rect x={x} y={y} width="26" height="54" rx="8" fill={wheelColor} stroke="rgba(0,0,0,.18)" strokeWidth="2" />
            <text x={x + 13} y={y + 24} textAnchor="middle" fontSize="10" fontWeight="700" fill="#1F2430" opacity=".75">{w}</text>
            {tread[w] != null && <text x={x + 13} y={y + 39} textAnchor="middle" fontSize="10" fontWeight="700" fill="#1F2430">{tread[w]!.toFixed(1)}</text>}
            {n > 0 && (
              <g>
                <circle cx={x + 24} cy={y - 2} r="10" fill="#D9534F" />
                <text x={x + 24} y={y + 2} textAnchor="middle" fontSize="11" fontWeight="700" fill="#fff">{n}</text>
              </g>
            )}
          </g>
        )
      })}
    </svg>
  )
}

/** A tiny side view of a brake pad: the plate sits on the friction material, which thins as it wears. */
export function BrakePad({ mm, color, newMm = 12 }: { mm: number | null; color: string; newMm?: number }) {
  const maxPad = 26, plate = 7
  const padBottom = 46 - 5 - 2
  const h = mm == null ? 0 : Math.max(1, Math.min(1, mm / newMm) * maxPad)
  return (
    <svg width="64" height="46" viewBox="0 0 64 46" aria-hidden="true">
      <rect x="0" y="41" width="64" height="5" rx="2" fill="#9AA0B4" />
      <rect x="8" y={padBottom - maxPad - plate} width="48" height={maxPad + plate} rx="3" fill="none" stroke="#9AA0B4" strokeWidth="1.5" strokeDasharray="4 4" opacity=".7" />
      {mm != null && (
        <>
          <rect x="8" y={padBottom - h} width="48" height={h} rx="3" fill={color} />
          <rect x="8" y={padBottom - h - plate} width="48" height={plate} rx="3" fill="#7A8094" />
        </>
      )}
    </svg>
  )
}
