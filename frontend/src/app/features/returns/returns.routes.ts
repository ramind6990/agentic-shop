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
    data: { title: 'Return detail' },
    loadComponent: () => import('../../shared/ui/not-built/not-built').then((m) => m.NotBuilt),
  },
];

export const REFUND_ROUTES: Routes = [
  {
    path: '',
    title: 'Refunds · AgenticShop',
    loadComponent: () => import('./pages/refund-list/refund-list').then((m) => m.RefundList),
  },
];
