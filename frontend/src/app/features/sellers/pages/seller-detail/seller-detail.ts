import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { Breadcrumbs, Crumb } from '../../../../shared/ui/breadcrumbs/breadcrumbs';
import { EntityHeader } from '../../../../shared/ui/entity-header/entity-header';
import { TabLink, TabNav } from '../../../../shared/ui/tab-nav/tab-nav';
import { enumLabel } from '../../../../shared/utils/enum-label';
import { flagsFor } from '../../../risk/mocks/flags.mock';
import {
  MOCK_SELLER,
  MOCK_SELLER_CONTRACTS,
  MOCK_SELLER_PAYOUTS,
} from '../../mocks/seller-detail.mock';

/** Seller page frame: header and tabs; each tab is a child route rendered in the outlet. */
@Component({
  selector: 'app-seller-detail',
  imports: [DatePipe, RouterOutlet, Breadcrumbs, EntityHeader, TabNav],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-breadcrumbs [items]="crumbs" />
    <app-entity-header
      [name]="seller.name"
      [status]="seller.status"
      [meta]="seller.legalName + ' · ' + seller.country + ' · ' + plan + ' plan'"
    >
      <span class="small text-body-secondary">
        Joined <time [attr.datetime]="seller.joinedAt">{{ seller.joinedAt | date: 'mediumDate' }}</time>
      </span>
    </app-entity-header>
    <app-tab-nav label="Seller sections" [tabs]="tabs" />
    <router-outlet />
  `,
})
export class SellerDetail {
  /** Route parameter (id or slug); unused until the API is wired. */
  readonly idOrSlug = input<string>();

  protected readonly seller = MOCK_SELLER;
  protected readonly plan = enumLabel(MOCK_SELLER.currentFeePlan);
  protected readonly crumbs: Crumb[] = [{ label: 'Sellers', link: '/sellers' }, { label: this.seller.name }];

  protected readonly tabs: TabLink[] = [
    { label: 'Overview', path: '' },
    { label: 'Products', path: 'products', count: this.seller.stats.productCount },
    { label: 'Orders', path: 'orders', count: this.seller.stats.orderCount },
    { label: 'Contracts', path: 'contracts', count: MOCK_SELLER_CONTRACTS.length },
    { label: 'Payouts', path: 'payouts', count: MOCK_SELLER_PAYOUTS.length },
    { label: 'Flags', path: 'flags', count: flagsFor('SELLER', this.seller.id).length },
  ];
}
