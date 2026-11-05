const cad = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' })
const cad0 = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD', maximumFractionDigits: 0 })
const num = new Intl.NumberFormat('en-CA')

export const money = (n: number, decimals = 2) => (decimals === 0 ? cad0 : cad).format(n)
export const km = (n: number) => `${num.format(Math.round(n))} km`
export const mm = (n: number) => `${n.toFixed(1)} mm`

export function washSummary(lastDate: string | undefined): string {
  if (!lastDate) return 'No washes logged yet'
  const days = Math.round((Date.now() - new Date(lastDate + 'T00:00:00').getTime()) / 86_400_000)
  return days <= 0 ? 'Washed today' : days === 1 ? 'Washed yesterday' : `Washed ${days} days ago`
}

export function inDays(millis: number): string {
  const days = Math.round((millis - Date.now()) / 86_400_000)
  return days <= 0 ? 'today' : days === 1 ? 'tomorrow' : `in ${days} days`
}
