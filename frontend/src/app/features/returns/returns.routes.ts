import { Routes } from '@angular/router';

/** Returns and refunds live in one feature; app.routes mounts these under /returns and /refunds. */
export const RETURN_ROUTES: Routes = [
  {
    path: '',
    title: 'Returns · AgenticShop',
    loadComponent: () => import('./pages/return-list/return-list').then((m) => m.ReturnList),
  },
  {
    path: ':id',
    title: 'Return · AgenticShop',
    loadComponent: () => import('./pages/return-detail/return-detail').then((m) => m.ReturnDetail),
  },
];

export const REFUND_ROUTES: Routes = [
  {
    path: '',
    title: 'Refunds · AgenticShop',
    loadComponent: () => import('./pages/refund-list/refund-list').then((m) => m.RefundList),
  },
];
