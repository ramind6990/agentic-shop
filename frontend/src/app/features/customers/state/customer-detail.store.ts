import { Injectable, computed, inject } from '@angular/core';

import { loadOnChange } from '../../../shared/data/load-on-change';
import { pagedLoader } from '../../../shared/data/paged-loader';
import { injectRouteParams, intParam } from '../../../shared/data/url-query';
import { FlagApi } from '../../risk/data-access/flag-api';
import { CustomerApi } from '../data-access/customer-api';

/**
 * Customer page frame (/customers/:id). Provided by CustomerDetailPage, so the header and every tab (child routes
 * rendered in its outlet) share one instance: the tabs read `customerId` and `customer` from here.
 */
@Injectable()
export class CustomerDetailStore {
  private readonly api = inject(CustomerApi);
  private readonly params = injectRouteParams();

  readonly customerId = computed(() => intParam(this.params(), 'id') ?? undefined);
  private readonly customer$ = loadOnChange(this.customerId, (id) => this.api.get(id));

  readonly customer = this.customer$.value;
  readonly loading = this.customer$.loading;
  readonly error = this.customer$.error;
  readonly notFound = computed(
    () => this.customerId() === undefined || this.error()?.status === 404,
  );

  reload(): void {
    this.customer$.reload();
  }
}

// ---------- tab stores: each is provided by its tab component and reads the customer from the frame ----------

/** Overview tab: the five latest orders (the rest comes from the frame's customer). */
@Injectable()
export class CustomerOverviewStore {
  private readonly api = inject(CustomerApi);
  private readonly frame = inject(CustomerDetailStore);

  private readonly latest$ = loadOnChange(this.frame.customerId, (id) => this.api.orders(id, 0, 5));
  readonly latestOrders = computed(() => this.latest$.value()?.content ?? []);
  readonly loading = this.latest$.loading;
  readonly error = this.latest$.error;

  reload(): void {
    this.latest$.reload();
  }
}

@Injectable()
export class CustomerOrdersStore {
  private readonly api = inject(CustomerApi);
  readonly orders = pagedLoader(inject(CustomerDetailStore).customerId, (id, page) =>
    this.api.orders(id, page),
  );
}

@Injectable()
export class CustomerTicketsStore {
  private readonly api = inject(CustomerApi);
  readonly tickets = pagedLoader(inject(CustomerDetailStore).customerId, (id, page) =>
    this.api.tickets(id, page),
  );
}

@Injectable()
export class CustomerReviewsStore {
  private readonly api = inject(CustomerApi);
  readonly reviews = pagedLoader(inject(CustomerDetailStore).customerId, (id, page) =>
    this.api.reviews(id, page),
  );
}

@Injectable()
export class CustomerFlagsStore {
  private readonly api = inject(FlagApi);
  readonly flags = pagedLoader(inject(CustomerDetailStore).customerId, (id, page) =>
    this.api.list({ subjectType: 'CUSTOMER', subjectId: id }, page),
  );
}
