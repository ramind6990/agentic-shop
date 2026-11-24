/**
 * Converts the date inputs of a filter bar (yyyy-mm-dd, as kept in the URL) to the ISO instants the API expects.
 * Days are taken in the browser's time zone, the same zone the tables display times in: `from` is the start of
 * that day and `to` the start of the next one, so both ends are inclusive days.
 */
export function dayStart(date: string): string | null {
  return isDate(date) ? new Date(`${date}T00:00:00`).toISOString() : null;
}

export function dayAfter(date: string): string | null {
  if (!isDate(date)) {
    return null;
  }
  const next = new Date(`${date}T00:00:00`);
  next.setDate(next.getDate() + 1);
  return next.toISOString();
}

function isDate(value: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(value);
}
