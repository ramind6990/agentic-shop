import { Injectable, computed, inject } from '@angular/core';

import { loadOnChange } from '../../../shared/data/load-on-change';
import { injectRouteParams, intParam } from '../../../shared/data/url-query';
import { RefundFilters, ReturnApi } from '../data-access/return-api';

/** Return detail page: the return named by /returns/:id, plus the refunds of its order. */
@Injectable()
export class ReturnDetailStore {
  private readonly api = inject(ReturnApi);
  private readonly params = injectRouteParams();

  readonly returnId = computed(() => intParam(this.params(), 'id') ?? undefined);
  private readonly return$ = loadOnChange(this.returnId, (id) => this.api.getReturn(id));

  readonly item = this.return$.value;
  readonly loading = this.return$.loading;
  readonly error = this.return$.error;
  readonly notFound = computed(() => this.returnId() === undefined || this.error()?.status === 404);

  /** Refunds of the same order; the ones linked to this return are highlighted by the page. */
  private readonly refundRequest = computed<RefundFilters | undefined>(() => {
    const orderId = this.item()?.order.id;
    return orderId === undefined ? undefined : { orderId: String(orderId) };
  });
  private readonly refunds$ = loadOnChange(this.refundRequest, (filters) =>
    this.api.refunds(filters, 0, 50),
  );
  readonly refunds = computed(() => this.refunds$.value()?.content ?? []);

  reload(): void {
    this.return$.reload();
    this.refunds$.reload();
  }
}
