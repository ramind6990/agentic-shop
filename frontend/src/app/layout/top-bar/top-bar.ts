import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

@Component({
  selector: 'app-top-bar',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="d-flex align-items-center gap-3 h-100 px-3">
      <a routerLink="/" class="fw-semibold text-decoration-none text-body fs-5 me-2">AgenticShop</a>

      <form
        class="flex-grow-1"
        role="search"
        style="max-width: 32rem"
        (submit)="search($event, globalSearch)"
      >
        <label for="global-search" class="visually-hidden"
          >Search orders, tickets, products or customers</label
        >
        <input
          #globalSearch
          id="global-search"
          type="search"
          class="form-control form-control-sm"
          placeholder="Order number, ticket number, product or customer"
        />
      </form>

      <div class="ms-auto d-flex align-items-center gap-2">
        <label for="view-as" class="small text-body-secondary text-nowrap">View as</label>
        <select id="view-as" class="form-select form-select-sm">
          <option>Admin</option>
        </select>
      </div>
    </div>
  `,
})
export class TopBar {
  private readonly router = inject(Router);

  /**
   * Sends the text to the screen that can answer it: an order number opens that order, a ticket number the
   * ticket search, an email the customer search, anything else the product search.
   */
  protected search(event: Event, input: HTMLInputElement): void {
    event.preventDefault();
    const text = input.value.trim();
    if (!text) {
      return;
    }
    if (/^AS-\d{6}-\d{6}$/i.test(text)) {
      void this.router.navigate(['/orders', text.toUpperCase()]);
    } else if (/^TCK-/i.test(text)) {
      void this.router.navigate(['/tickets'], { queryParams: { q: text.toUpperCase() } });
    } else if (text.includes('@')) {
      void this.router.navigate(['/customers'], { queryParams: { q: text } });
    } else {
      void this.router.navigate(['/products'], { queryParams: { q: text } });
    }
    input.value = '';
  }
}
