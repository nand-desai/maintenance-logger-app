import { db, today } from '../db'

function nextMonth(iso: string, day: number): string {
  const [y, m] = iso.split('-').map(Number)
  const ny = m === 12 ? y + 1 : y
  const nm = m === 12 ? 1 : m + 1
  const last = new Date(ny, nm, 0).getDate()
  return `${ny}-${String(nm).padStart(2, '0')}-${String(Math.min(day, last)).padStart(2, '0')}`
}

/**
 * Adds every monthly payment that has come due (e.g. insurance). Each payment has a fixed id, so
 * running this twice can never add the same month twice.
 */
export async function runRecurring() {
  const now = today()
  const vehicles = new Map((await db.vehicles.toArray()).map((v) => [v.vehicleId, v]))
  const plans = await db.recurring.filter((p) => p.active).toArray()
  for (const plan of plans) {
    const vehicle = vehicles.get(plan.vehicleId)
    if (!vehicle) continue
    let due = plan.nextDue
    let added = 0
    while (due <= now && added < 600) {
      await db.expenses.put({
        logId: `${plan.recurringId}_${due}`, vehicleId: plan.vehicleId, date: due,
        odometer: vehicle.currentOdometer, category: plan.category, cost: plan.amount, fuelLiters: 0, note: '',
      })
      due = nextMonth(due, plan.dayOfMonth)
      added++
    }
    if (added > 0) await db.recurring.put({ ...plan, nextDue: due })
  }
}
