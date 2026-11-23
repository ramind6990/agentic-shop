import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { AddressBlock } from '../../../../shared/ui/address-block/address-block';
import { CustomerAddress } from '../../models/customer.model';

/** Saved address with its label and a "Default" marker. */
@Component({
  selector: 'app-customer-address-card',
  imports: [AddressBlock],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block h-100' },
  template: `
    <div class="panel p-3 h-100 small">
      <div class="d-flex align-items-center gap-2 mb-2">
        <span class="section-label">{{ address().label }}</span>
        @if (address().defaultAddress) {
          <span class="badge bg-primary-subtle text-primary-emphasis border border-primary-subtle">Default</span>
        }
      </div>
      <app-address-block [address]="address()" />
    </div>
  `,
})
export class CustomerAddressCard {
  readonly address = input.required<CustomerAddress>();
}
