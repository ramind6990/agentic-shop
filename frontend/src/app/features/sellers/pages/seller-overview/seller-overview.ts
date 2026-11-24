import { CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SectionCard } from '../../../../shared/ui/section-card/section-card';
import { StarRating } from '../../../../shared/ui/star-rating/star-rating';
import { StatTile, StatTiles } from '../../../../shared/ui/stat-tiles/stat-tiles';
import { enumLabel } from '../../../../shared/utils/enum-label';
import { SellerDetailStore } from '../../state/seller-detail.store';

/** Seller overview tab: headline numbers, profile and the fee agreement in force. Data comes from the frame. */
@Component({
  selector: 'app-seller-overview',
  imports: [CurrencyPipe, DatePipe, PercentPipe, RouterLink, SectionCard, StatTiles, StarRating],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (store.seller(); as seller) {
      <app-stat-tiles class="d-block mb-4" [tiles]="tiles()" />

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
        @if (store.canSeeFinance()) {
          <div class="col-lg-6">
            <app-section-card title="Current fee agreement">
              <a slot="actions" class="small" routerLink="contracts">All contracts</a>
              @if (store.currentContract(); as contract) {
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
                  <dd>
                    <a
                      class="font-monospace"
                      [routerLink]="['/documents', contract.documentCode]"
                      >{{ contract.documentCode }}</a
                    >
                  </dd>
                </dl>
              } @else if (store.contractsLoading()) {
                <p class="small text-body-secondary mb-0">Loading…</p>
              } @else {
                <p class="small text-body-secondary mb-0">No fee agreement on file.</p>
              }
            </app-section-card>
          </div>
        }
      </div>
    }
  `,
})
export class SellerOverview {
  protected readonly store = inject(SellerDetailStore);
  protected readonly label = enumLabel;

  protected readonly tiles = computed<StatTile[]>(() => {
    const seller = this.store.seller();
    if (!seller) {
      return [];
    }
    const lastPaid = this.store.lastPaidPayout();
    const tiles: StatTile[] = [
      { label: 'Products', value: seller.stats.productCount.toLocaleString('en-US') },
      { label: 'Orders', value: seller.stats.orderCount.toLocaleString('en-US') },
      { label: 'Rating', value: seller.ratingAvg.toFixed(1), hint: 'Average across products' },
    ];
    if (!this.store.canSeeFinance()) {
      return tiles;
    }
    return [
      ...tiles,
      {
        label: 'Last payout',
        value: lastPaid
          ? lastPaid.netAmount.toLocaleString('en-US', { style: 'currency', currency: 'USD' })
          : '–',
        hint: lastPaid
          ? `Period ending ${new Date(lastPaid.periodEnd).toLocaleDateString('en-US', {
              month: 'short',
              day: 'numeric',
              year: 'numeric',
              timeZone: 'UTC',
            })}`
          : undefined,
      },
    ];
  });
}
