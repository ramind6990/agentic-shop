// People and companies reused by several mock lists, so names stay consistent across screens.
// Temporary: goes away when the screens are wired to the API.
import { Ref } from '../models/api.model';

export const MOCK_CUSTOMER_REFS: Ref[] = [
  { id: 3310, name: 'Jordan King' },
  { id: 1204, name: 'Emily Roberts' },
  { id: 877, name: 'Lukas Meyer' },
  { id: 15021, name: 'Priya Shah' },
  { id: 9142, name: 'Chloé Tremblay' },
  { id: 6620, name: 'Tom Hughes' },
  { id: 12007, name: 'Anna Schmidt' },
  { id: 4588, name: 'Marcus Lee' },
  { id: 18240, name: 'Olivia Bennett' },
  { id: 7731, name: 'Noah Fischer' },
];

export const MOCK_SELLER_REFS: Ref[] = [
  { id: 41, name: 'Maple Street Electronics' },
  { id: 112, name: 'Harbor Tech Supply' },
  { id: 58, name: 'Maple Street Apparel Outlet' },
  { id: 203, name: 'Northgate Digital' },
  { id: 317, name: 'Copper Kettle Kitchenware' },
  { id: 266, name: 'Trailhead Outdoor Co.' },
];

export const MOCK_STAFF_REFS = {
  t1: { id: 31, name: 'Dana Whitfield' },
  t1b: { id: 36, name: 'Sam Okafor' },
  t2: { id: 18, name: 'Marco Ruiz' },
  fraud: { id: 22, name: 'Priya Natarajan' },
} satisfies Record<string, Ref>;

/** Pick deterministically from a list, so mock rows are stable between reloads. */
export const pick = <T>(list: readonly T[], i: number): T => list[i % list.length];

/** ISO instant `hours` before the seed's "now" (2026-11-20). */
export const hoursAgo = (hours: number): string =>
  new Date(Date.parse('2026-11-20T09:00:00Z') - hours * 3600_000).toISOString();
