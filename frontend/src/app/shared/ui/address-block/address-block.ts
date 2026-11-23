import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { Address } from '../../models/api.model';

/** Postal address on separate lines. Used for order ship-to and customer addresses. */
@Component({
  selector: 'app-address-block',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <address class="mb-0">
      <span class="fw-semibold">{{ address().recipient }}</span><br />
      {{ address().line1 }}<br />
      @if (address().line2) {
        {{ address().line2 }}<br />
      }
      {{ address().city }}@if (address().region) {, {{ address().region }}}
      {{ address().postalCode }}<br />
      {{ address().country }}
    </address>
  `,
})
export class AddressBlock {
  readonly address = input.required<Address>();
}
