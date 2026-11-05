import { Categories, type ExpenseLog } from '../types'

export const CATEGORY_LIST = [Categories.FUEL, Categories.SERVICE, Categories.INSURANCE, Categories.UPGRADES, Categories.CAR_WASH, Categories.OTHER]
export const CHART_COLORS = ['#7C9CFF', '#FFB38A', '#8FD6B3', '#C9A8F5', '#F5A3C7', '#9FB8C9']

export interface ExpenseSummary {
  lifetime: number
  costPerKm: number | null
  litresPer100: number | null
  byCategory: Record<string, number>
  distance: number
  fuelLitres: number
}

/**
 * Fuel use (L/100 km) comes from the fuel logs: the litres added after the first fill-up divided by
 * the distance between the first and last fill-up (the first tank has no known starting point).
 */
export function summarize(logs: ExpenseLog[], currentOdometer: number): ExpenseSummary {
  const lifetime = logs.reduce((t, l) => t + l.cost, 0)
  const start = logs.length ? Math.min(...logs.map((l) => l.odometer)) : currentOdometer
  const distance = Math.max(0, currentOdometer - start)
  const fills = logs.filter((l) => l.category === Categories.FUEL && l.fuelLiters > 0).sort((a, b) => a.odometer - b.odometer)
  let litresPer100: number | null = null
  if (fills.length >= 2) {
    const dist = fills[fills.length - 1].odometer - fills[0].odometer
    const litres = fills.slice(1).reduce((t, l) => t + l.fuelLiters, 0)
    if (dist > 0 && litres > 0) litresPer100 = (litres / dist) * 100
  }
  const byCategory: Record<string, number> = {}
  logs.forEach((l) => { byCategory[l.category] = (byCategory[l.category] ?? 0) + l.cost })
  return {
    lifetime, distance, byCategory, litresPer100,
    costPerKm: distance > 0 && lifetime > 0 ? lifetime / distance : null,
    fuelLitres: logs.reduce((t, l) => t + l.fuelLiters, 0),
  }
}

/** L/100 km for one new fill-up, given the previous fuel log's odometer. */
export const entryLitresPer100 = (prevOdometer: number | null, odometer: number, litres: number) =>
  prevOdometer != null && litres > 0 && odometer > prevOdometer ? (litres / (odometer - prevOdometer)) * 100 : null

export const expenseTitle = (l: ExpenseLog) => (l.category === Categories.OTHER && l.note ? l.note : l.category)
