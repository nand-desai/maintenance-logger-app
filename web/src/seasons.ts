import { Seasons } from './types'

export interface SeasonLook { color: string; pill: string; icon: string }

/** Wheel colour, badge colour and icon for a tire season. */
export function seasonOf(season?: string): SeasonLook {
  switch (season) {
    case Seasons.SUMMER: return { color: '#FFB84D', pill: 'warn', icon: 'sun' }
    case Seasons.WINTER: return { color: '#6BB8FF', pill: 'sky', icon: 'snow' }
    case Seasons.ALL_SEASON: return { color: '#6FCF97', pill: 'good', icon: 'cloud' }
    default: return { color: '#C9CEDD', pill: 'neutral', icon: 'car' }
  }
}
