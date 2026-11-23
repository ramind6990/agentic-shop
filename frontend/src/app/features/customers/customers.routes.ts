import { Routes } from '@angular/router';

export const CUSTOMER_ROUTES: Routes = [
  {
    path: '',
    title: 'Customers · AgenticShop',
    loadComponent: () =>
      import('./pages/customer-list/customer-list').then((m) => m.CustomerList),
  },
  {
    path: ':id',
    title: 'Customer · AgenticShop',
    loadComponent: () =>
      import('./pages/customer-detail/customer-detail').then((m) => m.CustomerDetailPage),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./pages/customer-overview/customer-overview').then((m) => m.CustomerOverview),
      },
      {
        path: 'addresses',
        loadComponent: () =>
          import('./pages/customer-addresses/customer-addresses').then((m) => m.CustomerAddresses),
      },
      {
        path: 'orders',
        loadComponent: () =>
          import('./pages/customer-orders/customer-orders').then((m) => m.CustomerOrders),
      },
      {
        path: 'tickets',
        loadComponent: () =>
          import('./pages/customer-tickets/customer-tickets').then((m) => m.CustomerTickets),
      },
      {
        path: 'reviews',
        loadComponent: () =>
          import('./pages/customer-reviews/customer-reviews').then((m) => m.CustomerReviews),
      },
      {
        path: 'flags',
        loadComponent: () =>
          import('./pages/customer-flags/customer-flags').then((m) => m.CustomerFlags),
      },
    ],
  },
];
