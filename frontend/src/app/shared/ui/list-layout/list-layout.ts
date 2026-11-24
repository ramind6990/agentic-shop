import { DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  input,
  output,
  viewChild,
} from '@angular/core';

import { ApiError } from '../../../core/http/api-error';
import { PageResponse } from '../../models/api.model';
import { LoadState } from '../load-state/load-state';
import { PageHeader } from '../page-header/page-header';
import { Pager } from '../pager/pager';

/**
 * Frame shared by every list screen: header, filter bar, a scrolling results panel and the pager.
 * Only the results panel scrolls (from lg up). Presentational: the page passes the store's signals in and
 * forwards `pageChange` / `retry` to the store.
 *   <app-list-layout title="Orders" [result]="store.result()" [loading]="…" (pageChange)="store.list.setPage($event)">
 *     <form slot="filters">…</form>
 *     <app-data-table … />
 *   </app-list-layout>
 */
@Component({
  selector: 'app-list-layout',
  imports: [DecimalPipe, LoadState, PageHeader, Pager],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'page-fill' },
  template: `
    <app-page-header
      [title]="title()"
      [subtitle]="result() ? (result()!.totalElements | number) + ' ' + noun() : 'Loading…'"
    >
      <ng-content select="[slot=actions]" />
    </app-page-header>

    <div class="panel px-3 py-2 mb-3">
      <ng-content select="[slot=filters]" />
    </div>

    <div class="panel page-fill-body d-flex flex-column overflow-hidden">
      <div #results class="results" tabindex="0" [attr.aria-label]="title() + ' results'">
        <app-load-state
          [loading]="loading()"
          [error]="error()"
          [hasValue]="!!result()"
          (retry)="retry.emit()"
        >
          <ng-content />
        </app-load-state>
      </div>
      @if (result(); as page) {
        <app-pager
          class="d-block border-top px-3 py-2"
          [page]="page.page"
          [size]="page.size"
          [totalElements]="page.totalElements"
          [totalPages]="page.totalPages"
          (pageChange)="onPage($event)"
        />
      }
    </div>
  `,
  styleUrl: './list-layout.scss',
})
export class ListLayout {
  readonly title = input.required<string>();
  /** Undefined until the first response arrives. */
  readonly result = input<PageResponse<unknown>>();
  readonly loading = input(false);
  readonly error = input<ApiError>();
  /** What the total counts, shown in the subtitle ("orders", "tickets"…). */
  readonly noun = input('results');

  readonly pageChange = output<number>();
  readonly retry = output();

  private readonly results = viewChild.required<ElementRef<HTMLElement>>('results');

  protected onPage(page: number): void {
    this.pageChange.emit(page);
    this.results().nativeElement.scrollTop = 0;
  }
}
