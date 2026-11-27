import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { BlockOf, BlockType, ReplyBlock as Block } from '../../models/chat.model';
import { AddressCards, CustomerCards, CustomerDetailBlock } from './customer-blocks';
import { OrderCards } from './order-cards';
import { OrderDetailBlock } from './order-detail-block';
import { AddressChangeBlock, CancellationBlock } from './order-outcome-blocks';
import { ProductCards } from './product-cards';
import { ProductDetailBlock } from './product-detail-block';
import { RefundCards, ReturnCards } from './return-refund-cards';
import { ReviewCards } from './review-cards';
import { ShipmentRows } from './shipment-rows';
import { TicketCards, TicketThread } from './ticket-blocks';
import { TrackingBlock } from './tracking-block';

/** Shows a reply's block with the card made for its type. */
@Component({
  selector: 'app-reply-block',
  imports: [
    ProductCards,
    ProductDetailBlock,
    ReviewCards,
    OrderCards,
    OrderDetailBlock,
    CancellationBlock,
    AddressChangeBlock,
    TrackingBlock,
    ShipmentRows,
    ReturnCards,
    RefundCards,
    TicketCards,
    TicketThread,
    CustomerCards,
    CustomerDetailBlock,
    AddressCards,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let b = block();
    @switch (b.type) {
      @case ('PRODUCT_LIST') {
        <app-product-cards [items]="narrow(b, 'PRODUCT_LIST').items" />
      }
      @case ('PRODUCT_DETAIL') {
        <app-product-detail-block [product]="narrow(b, 'PRODUCT_DETAIL').product" />
      }
      @case ('REVIEW_LIST') {
        <app-review-cards [items]="narrow(b, 'REVIEW_LIST').items" />
      }
      @case ('ORDER_LIST') {
        <app-order-cards [items]="narrow(b, 'ORDER_LIST').items" />
      }
      @case ('ORDER_DETAIL') {
        <app-order-detail-block [order]="narrow(b, 'ORDER_DETAIL').order" />
      }
      @case ('ORDER_CANCELLATION') {
        <app-cancellation-block [cancellation]="narrow(b, 'ORDER_CANCELLATION').cancellation" />
      }
      @case ('ADDRESS_CHANGE') {
        <app-address-change-block
          [orderNumber]="narrow(b, 'ADDRESS_CHANGE').change.orderNumber"
          [address]="narrow(b, 'ADDRESS_CHANGE').change.shippingAddress"
        />
      }
      @case ('TRACKING') {
        <app-tracking-block [tracking]="narrow(b, 'TRACKING').tracking" />
      }
      @case ('SHIPMENT_LIST') {
        <app-shipment-rows [items]="narrow(b, 'SHIPMENT_LIST').items" />
      }
      @case ('RETURN_LIST') {
        <app-return-cards [items]="narrow(b, 'RETURN_LIST').items" />
      }
      @case ('REFUND_LIST') {
        <app-refund-cards [items]="narrow(b, 'REFUND_LIST').items" />
      }
      @case ('TICKET_LIST') {
        <app-ticket-cards [items]="narrow(b, 'TICKET_LIST').items" />
      }
      @case ('TICKET_DETAIL') {
        <app-ticket-thread [ticket]="narrow(b, 'TICKET_DETAIL').ticket" />
      }
      @case ('CUSTOMER_LIST') {
        <app-customer-cards [items]="narrow(b, 'CUSTOMER_LIST').items" />
      }
      @case ('CUSTOMER_DETAIL') {
        <app-customer-detail-block [customer]="narrow(b, 'CUSTOMER_DETAIL').customer" />
      }
      @case ('ADDRESS_LIST') {
        <app-address-cards [items]="narrow(b, 'ADDRESS_LIST').items" />
      }
    }
  `,
})
export class ReplyBlock {
  readonly block = input.required<Block>();

  /** The block as its type (the template's switch already checked it). */
  protected narrow<T extends BlockType>(block: Block, _type: T): BlockOf<T> {
    return block as BlockOf<T>;
  }
}
