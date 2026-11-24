import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { CustomerAddressCard } from '../../components/customer-address-card/customer-address-card';
import { CustomerDetailStore } from '../../state/customer-detail.store';

/** Customer addresses tab; the addresses come with the customer loaded by the page frame. */
@Component({
  selector: 'app-customer-addresses',
  imports: [CustomerAddressCard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="row row-cols-1 row-cols-md-2 row-cols-xl-3 g-3 list-unstyled">
      @for (address of addresses(); track address.id) {
        <li class="col"><app-customer-address-card [address]="address" /></li>
      } @empty {
        <li class="col text-body-secondary small">No saved addresses.</li>
      }
    </ul>
  `,
})
export class CustomerAddresses {
  private readonly frame = inject(CustomerDetailStore);
  protected readonly addresses = computed(() => this.frame.customer()?.addresses ?? []);
}
