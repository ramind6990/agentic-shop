import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { ApiError } from '../../../core/http/api-error';

/**
 * Wraps content that comes from the API: a spinner on the first load, an error with Retry, and a dimmed state while
 * newer data loads. Guard the projected content with `@if (value; as v)`.
 */
@Component({
  selector: 'app-load-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block' },
  template: `
    @if (error(); as err) {
      <div class="alert alert-danger d-flex flex-wrap align-items-center gap-2" role="alert">
        <span class="me-auto">{{ err.message }}</span>
        <button type="button" class="btn btn-sm btn-outline-danger" (click)="retry.emit()">
          Retry
        </button>
      </div>
    } @else if (loading() && !hasValue()) {
      <div class="d-flex justify-content-center py-5">
        <div class="spinner-border text-secondary" role="status">
          <span class="visually-hidden">Loading</span>
        </div>
      </div>
    }
    <div
      class="load-state-content"
      [class.d-none]="!hasValue() || error()"
      [class.is-loading]="loading()"
      [attr.aria-busy]="loading()"
    >
      <ng-content />
    </div>
  `,
  styles: `
    .load-state-content {
      transition: opacity 0.15s;
    }
    .is-loading {
      opacity: 0.55;
      pointer-events: none;
    }
  `,
})
export class LoadState {
  readonly loading = input(false);
  readonly error = input<ApiError>();
  /** True once a response is available (it may be stale while `loading`). */
  readonly hasValue = input(false);
  readonly retry = output();
}
