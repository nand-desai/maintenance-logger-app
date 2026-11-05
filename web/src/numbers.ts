/**
 * Reads what people actually type. Handles 94,000 · 94 000 · 1,200.50 · 62,4 · 6.5.
 * A comma followed by exactly three digits (1,200 / 1,234,567.50) is a thousands separator;
 * any other single comma is a decimal comma. Returns NaN when it isn't a number.
 */
export function num(input: string): number {
  let t = input.trim().replace(/[\s '’]/g, '')
  if (t === '') return NaN
  t = /^\d{1,3}(,\d{3})+(\.\d+)?$/.test(t) ? t.replace(/,/g, '') : t.replace(',', '.')
  return /^-?(\d+\.?\d*|\.\d+)$/.test(t) ? Number(t) : NaN
}

/** A whole-number reading in a believable range, e.g. 94,000 km. */
export const validOdometer = (s: string) => { const n = num(s); return Number.isInteger(n) && n >= 0 && n <= 3_000_000 }

/** A cost: zero or more. Pass `optional` to allow an empty field. */
export const validMoney = (s: string, optional = false) => (optional && s.trim() === '') || (num(s) >= 0 && num(s) <= 1_000_000)

/** Tread or pad thickness in mm. */
export const validMm = (s: string) => num(s) >= 0 && num(s) <= 30

export const validYear = (s: string) => { const n = num(s); return Number.isInteger(n) && n >= 1950 && n <= new Date().getFullYear() + 1 }
