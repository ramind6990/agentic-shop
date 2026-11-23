import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

type PageSlot = number | 'gap';

/** Pager for PageResponse results (page index is 0-based, labels are 1-based). Emits the chosen page index. */
@Component({
  selector: 'app-pager',
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="d-flex flex-wrap align-items-center justify-content-between gap-2">
      <span class="small text-body-secondary num">
        {{ firstRow() | number }}–{{ lastRow() | number }} of {{ totalElements() | number }}
      </span>
      <nav aria-label="Pagination">
        <ul class="pagination pagination-sm mb-0">
          <li class="page-item" [class.disabled]="page() === 0">
            <button
              type="button"
              class="page-link"
              aria-label="Previous page"
              [disabled]="page() === 0"
              (click)="go(page() - 1)"
            >
              &lsaquo;
            </button>
          </li>
          @for (slot of slots(); track $index) {
            @if (slot === 'gap') {
              <li class="page-item disabled"><span class="page-link">…</span></li>
            } @else {
              <li class="page-item" [class.active]="slot === page()">
                <button
                  type="button"
                  class="page-link num"
                  [attr.aria-current]="slot === page() ? 'page' : null"
                  (click)="go(slot)"
                >
                  {{ slot + 1 | number }}
                </button>
              </li>
            }
          }
          <li class="page-item" [class.disabled]="page() >= totalPages() - 1">
            <button
              type="button"
              class="page-link"
              aria-label="Next page"
              [disabled]="page() >= totalPages() - 1"
              (click)="go(page() + 1)"
            >
              &rsaquo;
            </button>
          </li>
        </ul>
      </nav>
    </div>
  `,
})
export class Pager {
  readonly page = input.required<number>();
  readonly size = input.required<number>();
  readonly totalElements = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly pageChange = output<number>();

  protected readonly firstRow = computed(() =>
    this.totalElements() === 0 ? 0 : this.page() * this.size() + 1,
  );
  protected readonly lastRow = computed(() =>
    Math.min((this.page() + 1) * this.size(), this.totalElements()),
  );

  /** First, last, and up to two pages either side of the current one, with gaps in between. */
  protected readonly slots = computed<PageSlot[]>(() => {
    const last = this.totalPages() - 1;
    if (last < 0) {
      return [];
    }
    const current = this.page();
    const pages = new Set([0, last]);
    for (let p = current - 2; p <= current + 2; p++) {
      if (p >= 0 && p <= last) {
        pages.add(p);
      }
    }
    const sorted = [...pages].sort((a, b) => a - b);
    const result: PageSlot[] = [];
    sorted.forEach((p, i) => {
      if (i > 0 && p - sorted[i - 1] > 1) {
        result.push('gap');
      }
      result.push(p);
    });
    return result;
  });

  protected go(page: number): void {
    if (page !== this.page() && page >= 0 && page < this.totalPages()) {
      this.pageChange.emit(page);
    }
  }
}
