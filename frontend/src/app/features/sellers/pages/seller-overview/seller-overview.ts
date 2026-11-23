import { CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SectionCard } from '../../../../shared/ui/section-card/section-card';
import { StatTile, StatTiles } from '../../../../shared/ui/stat-tiles/stat-tiles';
import { StarRating } from '../../../../shared/ui/star-rating/star-rating';
import { enumLabel } from '../../../../shared/utils/enum-label';
import { MOCK_SELLER, MOCK_SELLER_CONTRACTS, MOCK_SELLER_PAYOUTS } from '../../mocks/seller-detail.mock';

/** Seller overview tab: headline numbers, profile and the fee agreement in force. */
@Component({
  selector: 'app-seller-overview',
  imports: [CurrencyPipe, DatePipe, PercentPipe, RouterLink, SectionCard, StatTiles, StarRating],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-stat-tiles class="d-block mb-4" [tiles]="tiles" />

    <div class="row g-4">
      <div class="col-lg-6">
        <app-section-card title="Profile">
          <p class="small">{{ seller.description }}</p>
          <dl class="kv-list">
            <dt>Legal name</dt>
            <dd>{{ seller.legalName }}</dd>
            <dt>Email</dt>
            <dd>{{ seller.email }}</dd>
            <dt>Country</dt>
            <dd>{{ seller.country }}</dd>
            <dt>Rating</dt>
            <dd><app-star-rating [value]="seller.ratingAvg" /></dd>
            <dt>Slug</dt>
            <dd class="font-monospace">{{ seller.slug }}</dd>
          </dl>
        </app-section-card>
      </div>
      <div class="col-lg-6">
        <app-section-card title="Current fee agreement">
          <a slot="actions" class="small" routerLink="contracts">All contracts</a>
          <dl class="kv-list">
            <dt>Plan</dt>
            <dd>{{ label(contract.feePlan) }}</dd>
            <dt>Commission</dt>
            <dd class="num">{{ contract.commissionRate | percent: '1.0-1' }} of each sale</dd>
            <dt>Per-order fee</dt>
            <dd class="num">{{ contract.perOrderFee | currency }}</dd>
            <dt>Monthly fee</dt>
            <dd class="num">{{ contract.monthlyFee | currency }}</dd>
            <dt>Return window</dt>
            <dd>{{ contract.returnWindowDays }} days</dd>
            <dt>In force since</dt>
            <dd>{{ contract.effectiveFrom | date: 'mediumDate' }}</dd>
            <dt>Document</dt>
            <dd class="font-monospace">{{ contract.documentCode }}</dd>
          </dl>
        </app-section-card>
      </div>
    </div>
  `,
})
export class SellerOverview {
  protected readonly seller = MOCK_SELLER;
  protected readonly contract = MOCK_SELLER_CONTRACTS[0];
  protected readonly label = enumLabel;

  private readonly lastPaid = MOCK_SELLER_PAYOUTS.find((p) => p.status === 'PAID');

  protected readonly tiles: StatTile[] = [
    { label: 'Products', value: this.seller.stats.productCount.toLocaleString('en-US') },
    { label: 'Orders', value: this.seller.stats.orderCount.toLocaleString('en-US') },
    { label: 'Rating', value: this.seller.ratingAvg.toFixed(1), hint: 'Average across products' },
    {
      label: 'Last payout',
      value: (this.lastPaid?.netAmount ?? 0).toLocaleString('en-US', { style: 'currency', currency: 'USD' }),
      hint: this.lastPaid
        ? `Period ending ${new Date(this.lastPaid.periodEnd).toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric',
            timeZone: 'UTC',
          })}`
        : undefined,
    },
  ];
}
