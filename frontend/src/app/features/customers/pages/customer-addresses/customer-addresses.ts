import { ChangeDetectionStrategy, Component } from '@angular/core';

import { CustomerAddressCard } from '../../components/customer-address-card/customer-address-card';
import { MOCK_CUSTOMER } from '../../mocks/customer-detail.mock';

/** Customer addresses tab. */
@Component({
  selector: 'app-customer-addresses',
  imports: [CustomerAddressCard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="row row-cols-1 row-cols-md-2 row-cols-xl-3 g-3 list-unstyled">
      @for (address of addresses; track address.id) {
        <li class="col"><app-customer-address-card [address]="address" /></li>
      }
    </ul>
  `,
})
export class CustomerAddresses {
  protected readonly addresses = MOCK_CUSTOMER.addresses;
}
