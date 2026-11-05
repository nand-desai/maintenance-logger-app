/** Asks before something is deleted for good. */
export const confirmDelete = (what: string) => window.confirm(`Delete ${what}? This can't be undone.`)
