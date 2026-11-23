import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { PageResponse } from '../../models/api.model';
import { PageHeader } from '../page-header/page-header';
import { Pager } from '../pager/pager';

/**
 * Frame shared by every list screen: header, filter bar, a scrolling results panel and the pager.
 * Only the results panel scrolls (from lg up).
 *   <app-list-layout title="Orders" [result]="page">
 *     <form slot="filters">…</form>
 *     <app-data-table … />
 *   </app-list-layout>
 */
@Component({
  selector: 'app-list-layout',
  imports: [DecimalPipe, PageHeader, Pager],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'page-fill' },
  template: `
    <app-page-header [title]="title()" [subtitle]="(result().totalElements | number) + ' ' + noun()">
      <ng-content select="[slot=actions]" />
    </app-page-header>

    <div class="panel px-3 py-2 mb-3">
      <ng-content select="[slot=filters]" />
    </div>

    <div class="panel page-fill-body d-flex flex-column overflow-hidden">
      <div class="results" tabindex="0" [attr.aria-label]="title() + ' results'">
        <ng-content />
      </div>
      <app-pager
        class="d-block border-top px-3 py-2"
        [page]="result().page"
        [size]="result().size"
        [totalElements]="result().totalElements"
        [totalPages]="result().totalPages"
      />
    </div>
  `,
  styleUrl: './list-layout.scss',
})
export class ListLayout {
  readonly title = input.required<string>();
  readonly result = input.required<PageResponse<unknown>>();
  /** What the total counts, shown in the subtitle ("orders", "tickets"…). */
  readonly noun = input('results');
}
