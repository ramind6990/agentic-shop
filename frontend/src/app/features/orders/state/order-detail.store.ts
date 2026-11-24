import { Injectable, computed, inject } from '@angular/core';

import { loadOnChange } from '../../../shared/data/load-on-change';
import { injectRouteParams } from '../../../shared/data/url-query';
import { OrderApi } from '../data-access/order-api';

/** Order detail page: the order named by /orders/:idOrNumber, with lines, returns, refunds, payments, shipments. */
@Injectable()
export class OrderDetailStore {
  private readonly api = inject(OrderApi);
  private readonly params = injectRouteParams();

  readonly idOrNumber = computed(() => this.params().get('idOrNumber') ?? undefined);
  private readonly order$ = loadOnChange(this.idOrNumber, (key) => this.api.get(key));

  readonly order = this.order$.value;
  readonly loading = this.order$.loading;
  readonly error = this.order$.error;
  readonly notFound = computed(() => this.error()?.status === 404);

  reload(): void {
    this.order$.reload();
  }
}
