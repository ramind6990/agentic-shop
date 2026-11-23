import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Five stars filled to the average, followed by the numeric value and optional review count. */
@Component({
  selector: 'app-star-rating',
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="d-inline-flex align-items-center gap-1 small">
      <span class="stars" aria-hidden="true">
        <span class="stars-base">★★★★★</span>
        <span class="stars-fill" [style.width.%]="percent()">★★★★★</span>
      </span>
      <span class="visually-hidden">Rated {{ value() | number: '1.1-1' }} out of 5</span>
      @if (showValue()) {
        <span class="num" aria-hidden="true">{{ value() | number: '1.1-1' }}</span>
      }
      @if (count() !== undefined) {
        <span class="text-body-secondary num">({{ count() | number }})</span>
      }
    </span>
  `,
  styleUrl: './star-rating.scss',
})
export class StarRating {
  readonly value = input.required<number>();
  readonly count = input<number>();
  /** Show the number next to the stars (hide it when the value is already shown larger nearby). */
  readonly showValue = input(true);

  protected readonly percent = computed(() => Math.max(0, Math.min(100, (this.value() / 5) * 100)));
}
