import { Signal, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Params, Router } from '@angular/router';

import { ParamValue } from '../../core/http/http-params';

/** Query parameters of the current route as a signal. Updates on every navigation, typed URL or back/forward. */
export function injectQueryParams(): Signal<ParamMap> {
  const route = inject(ActivatedRoute);
  return toSignal(route.queryParamMap, { initialValue: route.snapshot.queryParamMap });
}

/** Path parameters of the current route as a signal. */
export function injectRouteParams(): Signal<ParamMap> {
  const route = inject(ActivatedRoute);
  return toSignal(route.paramMap, { initialValue: route.snapshot.paramMap });
}

export type QueryPatch = Record<string, ParamValue>;

/**
 * Returns a function that writes query parameters to the URL of the current route. By default it merges with the
 * existing ones; null, false, empty strings and empty arrays remove a parameter. With `merge: false` the patch
 * replaces all query parameters.
 *
 * The URL is the single source of truth: stores never set their query directly, they read it back through
 * `injectQueryParams()`.
 */
export function injectQueryUpdater(): (patch: QueryPatch, options?: { merge?: boolean }) => void {
  const router = inject(Router);
  const route = inject(ActivatedRoute);
  return (patch, options) => {
    const merge = options?.merge ?? true;
    void router.navigate([], {
      relativeTo: route,
      queryParams: toQueryParams(patch),
      queryParamsHandling: merge ? 'merge' : '',
    });
  };
}

function toQueryParams(patch: QueryPatch): Params {
  const params: Params = {};
  for (const [key, value] of Object.entries(patch)) {
    if (value === null || value === undefined || value === '' || value === false) {
      params[key] = null;
    } else if (Array.isArray(value)) {
      params[key] = value.length > 0 ? value.join(',') : null;
    } else {
      params[key] = String(value);
    }
  }
  return params;
}

// ---------- parsing helpers for stores ----------

/** A finite number, or null when the parameter is missing or not a number. */
export function numberParam(params: ParamMap, key: string): number | null {
  const raw = params.get(key);
  if (raw === null || raw.trim() === '') {
    return null;
  }
  const value = Number(raw);
  return Number.isFinite(value) ? value : null;
}

/** A non-negative integer (page index, id), or null when missing or invalid. */
export function intParam(params: ParamMap, key: string): number | null {
  const value = numberParam(params, key);
  return value !== null && Number.isInteger(value) && value >= 0 ? value : null;
}

/** Comma-separated values (`brandIds=1,2`), also accepting repeated keys. */
export function listParam(params: ParamMap, key: string): string[] {
  return params
    .getAll(key)
    .flatMap((raw) => raw.split(','))
    .map((v) => v.trim())
    .filter((v) => v !== '');
}

/** One of the allowed values, or the fallback. */
export function enumParam<T extends string>(
  params: ParamMap,
  key: string,
  allowed: readonly T[],
  fallback: T,
): T {
  const raw = params.get(key);
  return allowed.includes(raw as T) ? (raw as T) : fallback;
}
