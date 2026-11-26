import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { ReplyEnvelope } from '../../models/chat.model';
import { OrderCards } from '../item-cards/order-cards';
import { ProductCards } from '../item-cards/product-cards';

/** The assistant's reply: its text, then its items as cards, then the suggested replies. */
@Component({
  selector: 'app-reply-view',
  imports: [ProductCards, OrderCards],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-flex flex-column gap-2' },
  template: `
    <p class="mb-0 reply-text" [class.text-body-secondary]="reply().status === 'REFUSED'">{{ reply().text }}</p>
    @switch (reply().blockType) {
      @case ('PRODUCT_LIST') {
        <app-product-cards [items]="reply().productList ?? []" />
      }
      @case ('ORDER_LIST') {
        <app-order-cards [items]="reply().orderList ?? []" />
      }
    }
    @if (showSuggestions() && reply().suggestedReplies.length > 0) {
      <div class="d-flex flex-wrap gap-2" role="group" aria-label="Suggested replies">
        @for (s of reply().suggestedReplies; track s.label) {
          <button
            type="button"
            class="btn btn-sm btn-outline-primary rounded-pill"
            [title]="s.text"
            [disabled]="suggestionsDisabled()"
            (click)="suggest.emit(s.text)"
          >
            {{ s.label }}
          </button>
        }
      </div>
    }
  `,
  styles: `
    .reply-text {
      white-space: pre-wrap;
    }
  `,
})
export class ReplyView {
  readonly reply = input.required<ReplyEnvelope>();
  /** Only the latest reply offers its suggestions. */
  readonly showSuggestions = input(false);
  readonly suggestionsDisabled = input(false);
  readonly suggest = output<string>();
}
