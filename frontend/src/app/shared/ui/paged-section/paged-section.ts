import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { ApiError } from '../../../core/http/api-error';
import { PageResponse } from '../../models/api.model';
import { LoadState } from '../load-state/load-state';
import { Pager } from '../pager/pager';
import { SectionCard } from '../section-card/section-card';

/**
 * A titled card holding one paged API list (detail-page tabs such as a customer's orders): loading and error
 * states, the projected rows, and a pager when there is more than one page. Presentational.
 */
@Component({
  selector: 'app-paged-section',
  imports: [LoadState, Pager, SectionCard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-section-card [title]="title()" [flush]="flush()">
      <ng-content select="[slot=actions]" ngProjectAs="[slot=actions]" />
      <app-load-state
        [loading]="loading()"
        [error]="error()"
        [hasValue]="!!result()"
        (retry)="retry.emit()"
      >
        <ng-content />
      </app-load-state>
      @if (result(); as page) {
        @if (page.totalPages > 1) {
          <app-pager
            class="d-block border-top px-3 py-2"
            [page]="page.page"
            [size]="page.size"
            [totalElements]="page.totalElements"
            [totalPages]="page.totalPages"
            (pageChange)="pageChange.emit($event)"
          />
        }
      }
    </app-section-card>
  `,
})
export class PagedSection {
  readonly title = input.required<string>();
  readonly result = input<PageResponse<unknown>>();
  readonly loading = input(false);
  readonly error = input<ApiError>();
  /** Content touches the card edges (tables). */
  readonly flush = input(true);

  readonly pageChange = output<number>();
  readonly retry = output();
}
