import { Signal, effect, signal, untracked } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiError, toApiError } from '../../core/http/api-error';

/** Read-only state of data that is (re)loaded whenever its request changes. */
export interface Loaded<T> {
  /** Last successful response. Kept while a newer request is in flight, so the UI does not flash. */
  readonly value: Signal<T | undefined>;
  readonly loading: Signal<boolean>;
  readonly error: Signal<ApiError | undefined>;
  /** Runs the current request again (e.g. a Retry button). */
  reload(): void;
}

/**
 * Loads data with `load(request())` every time the `request` signal changes, and exposes the result as signals.
 *
 * An effect tracks `request`. When it changes, the effect subscribes to the new call, and its cleanup unsubscribes
 * the previous one, so a slow old response can never overwrite a newer one. The response is written to `value`,
 * and templates that read `value` re-render. Returning `undefined` from `request` means "nothing to load" and
 * clears the value.
 *
 * Must be called in an injection context (a field initializer of a service or component).
 */
export function loadOnChange<R, T>(
  request: () => R | undefined,
  load: (request: R) => Observable<T>,
): Loaded<T> {
  const value = signal<T | undefined>(undefined);
  const loading = signal(false);
  const error = signal<ApiError | undefined>(undefined);
  const reloads = signal(0);

  effect((onCleanup) => {
    reloads();
    const current = request();
    // Everything below must not become a dependency of the effect.
    untracked(() => {
      if (current === undefined) {
        value.set(undefined);
        loading.set(false);
        error.set(undefined);
        return;
      }
      loading.set(true);
      error.set(undefined);
      const subscription = load(current).subscribe({
        next: (response) => {
          value.set(response);
          loading.set(false);
        },
        error: (e: unknown) => {
          error.set(toApiError(e));
          loading.set(false);
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  });

  return {
    value: value.asReadonly(),
    loading: loading.asReadonly(),
    error: error.asReadonly(),
    reload: () => reloads.update((n) => n + 1),
  };
}

/** Equality for computed request objects: only a real change in content triggers a new request. */
export function sameJson<T>(a: T, b: T): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}
