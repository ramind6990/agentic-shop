import { HttpParams } from '@angular/common/http';

/** A value that can go into a query string. Arrays are sent comma-separated (`brandIds=1,2`). */
export type ParamValue =
  string | number | boolean | null | undefined | readonly (string | number)[];

/** Builds HttpParams, leaving out null, undefined, false, empty strings and empty arrays. */
export function toHttpParams(values: Record<string, ParamValue>): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value === null || value === undefined || value === '' || value === false) {
      continue;
    }
    if (Array.isArray(value)) {
      if (value.length > 0) {
        params = params.set(key, value.join(','));
      }
      continue;
    }
    params = params.set(key, String(value));
  }
  return params;
}
