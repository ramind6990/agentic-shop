import { Injectable, computed, inject } from '@angular/core';

import { loadOnChange } from '../../../shared/data/load-on-change';
import { pagedLoader } from '../../../shared/data/paged-loader';
import { injectRouteParams } from '../../../shared/data/url-query';
import { ProductApi } from '../../catalog/data-access/product-api';
import { emptyProductQuery } from '../../catalog/models/product-query';
import { OrderApi } from '../../orders/data-access/order-api';
import { FlagApi } from '../../risk/data-access/flag-api';
import { SellerApi } from '../data-access/seller-api';

/**
 * Seller page frame (/sellers/:idOrSlug). Provided by the SellerDetail page and shared with its tabs.
 * Contracts and the first payouts load with the seller: the header counts and the overview need them.
 */
@Injectable()
export class SellerDetailStore {
  private readonly api = inject(SellerApi);
  private readonly params = injectRouteParams();

  readonly idOrSlug = computed(() => this.params().get('idOrSlug') ?? undefined);
  private readonly seller$ = loadOnChange(this.idOrSlug, (key) => this.api.get(key));

  readonly seller = this.seller$.value;
  readonly loading = this.seller$.loading;
  readonly error = this.seller$.error;
  readonly notFound = computed(() => this.error()?.status === 404);

  /** Numeric id once the seller has loaded (the URL may hold a slug). */
  readonly sellerId = computed(() => this.seller()?.id);

  private readonly contracts$ = loadOnChange(this.sellerId, (id) => this.api.contracts(id));
  readonly contracts = computed(() => this.contracts$.value() ?? []);
  readonly contractsLoading = this.contracts$.loading;
  readonly contractsError = this.contracts$.error;
  /** The agreement in force (effectiveTo is null), else the newest. */
  readonly currentContract = computed(
    () => this.contracts().find((c) => c.effectiveTo === null) ?? this.contracts()[0],
  );

  private readonly recentPayouts$ = loadOnChange(this.sellerId, (id) =>
    this.api.payouts(id, 0, 10),
  );
  readonly payoutCount = computed(() => this.recentPayouts$.value()?.totalElements);
  readonly lastPaidPayout = computed(() =>
    this.recentPayouts$.value()?.content.find((p) => p.status === 'PAID'),
  );

  reload(): void {
    this.seller$.reload();
    this.contracts$.reload();
    this.recentPayouts$.reload();
  }

  reloadContracts(): void {
    this.contracts$.reload();
  }
}

// ---------- tab stores: each is provided by its tab component and reads the seller from the frame ----------

/** Products tab: the product search limited to this seller (GET /api/products?sellerId=), page in the URL. */
@Injectable()
export class SellerProductsStore {
  private readonly api = inject(ProductApi);
  readonly products = pagedLoader(inject(SellerDetailStore).sellerId, (sellerId, page) =>
    this.api.search({ ...emptyProductQuery(), sellerId, sort: 'bestselling', page }),
  );
}

@Injectable()
export class SellerOrdersStore {
  private readonly api = inject(OrderApi);
  readonly orders = pagedLoader(inject(SellerDetailStore).sellerId, (sellerId, page) =>
    this.api.list({ sellerId }, page),
  );
}

@Injectable()
export class SellerPayoutsStore {
  private readonly api = inject(SellerApi);
  readonly payouts = pagedLoader(inject(SellerDetailStore).sellerId, (id, page) =>
    this.api.payouts(id, page),
  );
}

@Injectable()
export class SellerFlagsStore {
  private readonly api = inject(FlagApi);
  readonly flags = pagedLoader(inject(SellerDetailStore).sellerId, (id, page) =>
    this.api.list({ subjectType: 'SELLER', subjectId: id }, page),
  );
}
