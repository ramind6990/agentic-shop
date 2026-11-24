import { Routes } from '@angular/router';

import { permissionGuard } from '../../core/auth/auth.guards';

export const SELLER_ROUTES: Routes = [
  {
    path: '',
    title: 'Sellers · AgenticShop',
    loadComponent: () => import('./pages/seller-list/seller-list').then((m) => m.SellerList),
  },
  {
    path: ':idOrSlug',
    title: 'Seller · AgenticShop',
    loadComponent: () => import('./pages/seller-detail/seller-detail').then((m) => m.SellerDetail),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./pages/seller-overview/seller-overview').then((m) => m.SellerOverview),
      },
      {
        path: 'products',
        loadComponent: () =>
          import('./pages/seller-products/seller-products').then((m) => m.SellerProducts),
      },
      {
        path: 'orders',
        canMatch: [permissionGuard],
        data: { permission: 'ORDERS_READ' },
        loadComponent: () =>
          import('./pages/seller-orders/seller-orders').then((m) => m.SellerOrders),
      },
      {
        path: 'contracts',
        canMatch: [permissionGuard],
        data: { permission: 'SELLER_FINANCE_READ' },
        loadComponent: () =>
          import('./pages/seller-contracts/seller-contracts').then((m) => m.SellerContracts),
      },
      {
        path: 'payouts',
        canMatch: [permissionGuard],
        data: { permission: 'SELLER_FINANCE_READ' },
        loadComponent: () =>
          import('./pages/seller-payouts/seller-payouts').then((m) => m.SellerPayouts),
      },
      {
        path: 'flags',
        canMatch: [permissionGuard],
        data: { permission: 'FLAGS_READ' },
        loadComponent: () => import('./pages/seller-flags/seller-flags').then((m) => m.SellerFlags),
      },
    ],
  },
];
