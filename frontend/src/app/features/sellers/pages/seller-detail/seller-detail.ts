import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { Breadcrumbs, Crumb } from '../../../../shared/ui/breadcrumbs/breadcrumbs';
import { EntityHeader } from '../../../../shared/ui/entity-header/entity-header';
import { LoadState } from '../../../../shared/ui/load-state/load-state';
import { NotFoundPanel } from '../../../../shared/ui/not-found-panel/not-found-panel';
import { TabLink, TabNav } from '../../../../shared/ui/tab-nav/tab-nav';
import { enumLabel } from '../../../../shared/utils/enum-label';
import { SellerDetailStore } from '../../state/seller-detail.store';

/**
 * Seller page frame: header and tabs; each tab is a child route rendered in the outlet.
 * Provides SellerDetailStore, which the tabs inject to know which seller they show.
 */
@Component({
  selector: 'app-seller-detail',
  imports: [DatePipe, RouterOutlet, Breadcrumbs, EntityHeader, LoadState, NotFoundPanel, TabNav],
  providers: [SellerDetailStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (store.notFound()) {
      <app-not-found-panel what="Seller" backLink="/sellers" />
    } @else {
      <app-load-state
        [loading]="store.loading()"
        [error]="store.error()"
        [hasValue]="!!store.seller()"
        (retry)="store.reload()"
      >
        @if (store.seller(); as seller) {
          <app-breadcrumbs [items]="crumbs()" />
          <app-entity-header
            [name]="seller.name"
            [status]="seller.status"
            [meta]="
              seller.legalName +
              ' · ' +
              seller.country +
              ' · ' +
              label(seller.currentFeePlan) +
              ' plan'
            "
          >
            <span class="small text-body-secondary">
              Joined
              <time [attr.datetime]="seller.joinedAt">{{
                seller.joinedAt | date: 'mediumDate'
              }}</time>
            </span>
          </app-entity-header>
          <app-tab-nav label="Seller sections" [tabs]="tabs()" />
          <router-outlet />
        }
      </app-load-state>
    }
  `,
})
export class SellerDetail {
  protected readonly store = inject(SellerDetailStore);
  protected readonly label = enumLabel;

  protected readonly crumbs = computed<Crumb[]>(() => [
    { label: 'Sellers', link: '/sellers' },
    { label: this.store.seller()?.name ?? '' },
  ]);

  protected readonly tabs = computed<TabLink[]>(() => {
    const seller = this.store.seller();
    return [
      { label: 'Overview', path: '' },
      { label: 'Products', path: 'products', count: seller?.stats.productCount },
      { label: 'Orders', path: 'orders', count: seller?.stats.orderCount },
      { label: 'Contracts', path: 'contracts', count: this.store.contracts().length || undefined },
      { label: 'Payouts', path: 'payouts', count: this.store.payoutCount() },
      { label: 'Flags', path: 'flags' },
    ];
  });
}
