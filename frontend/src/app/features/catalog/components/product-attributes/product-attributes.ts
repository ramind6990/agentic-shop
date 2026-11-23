import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { ProductAttribute } from '../../models/product.model';

/** Specification table built from the product's EAV attribute values. */
@Component({
  selector: 'app-product-attributes',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <table class="table table-sm mb-0 small">
      <caption class="visually-hidden">Product specifications</caption>
      <tbody>
        @for (attribute of attributes(); track attribute.code) {
          <tr>
            <th scope="row" class="fw-normal text-body-secondary ps-3 w-50">{{ attribute.name }}</th>
            <td class="pe-3">
              {{ attribute.labels.join(', ') }}
              @if (attribute.unit) {
                {{ attribute.unit }}
              }
            </td>
          </tr>
        }
      </tbody>
    </table>
  `,
  styles: `
    tr:last-child > * {
      border-bottom: 0;
    }
  `,
})
export class ProductAttributes {
  readonly attributes = input.required<ProductAttribute[]>();
}
