import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-top-bar',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="d-flex align-items-center gap-3 h-100 px-3">
      <a routerLink="/" class="fw-semibold text-decoration-none text-body fs-5 me-2">AgenticShop</a>

      <form class="flex-grow-1" role="search" style="max-width: 32rem">
        <label for="global-search" class="visually-hidden">Search orders, products or customers</label>
        <input
          id="global-search"
          type="search"
          class="form-control form-control-sm"
          placeholder="Order number, product or customer"
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
export class TopBar {}
