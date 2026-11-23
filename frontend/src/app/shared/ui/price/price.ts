import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Current price, plus the struck-through "compare at" list price and discount when on sale. */
@Component({
  selector: 'app-price',
  imports: [CurrencyPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="d-inline-flex flex-wrap align-items-baseline gap-2 num">
      <span class="fw-semibold" [class.text-danger-emphasis]="onSale()">
        {{ price() | currency: currency() }}
      </span>
      @if (onSale()) {
        <s class="small text-body-secondary">
          <span class="visually-hidden">Was </span>{{ listPrice() | currency: currency() }}
        </s>
        <span class="badge bg-danger-subtle text-danger-emphasis">-{{ discountPercent() }}%</span>
      }
    </span>
  `,
})
export class Price {
  readonly price = input.required<number>();
  readonly listPrice = input<number | null>(null);
  readonly currency = input('USD');

  protected readonly onSale = computed(() => {
    const list = this.listPrice();
    return list !== null && list > this.price();
  });
  protected readonly discountPercent = computed(() => {
    const list = this.listPrice();
    return list ? Math.round((1 - this.price() / list) * 100) : 0;
  });
}
