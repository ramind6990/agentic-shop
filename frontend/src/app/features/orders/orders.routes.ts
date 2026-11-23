import { Routes } from '@angular/router';

export const ORDER_ROUTES: Routes = [
  {
    path: '',
    title: 'Orders · AgenticShop',
    loadComponent: () => import('./pages/order-list/order-list').then((m) => m.OrderList),
  },
  {
    path: ':idOrNumber',
    title: 'Order · AgenticShop',
    loadComponent: () =>
      import('./pages/order-detail/order-detail').then((m) => m.OrderDetailPage),
  },
];
