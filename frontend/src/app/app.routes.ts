import { Routes } from '@angular/router';

import { authGuard, guestGuard, permissionGuard } from './core/auth/auth.guards';
import { Shell } from './layout/shell/shell';

/**
 * The login page stands alone; every other screen renders inside the shell and needs a signed-in user.
 * Each area is a lazily loaded feature; routes inside each feature live in its *.routes.ts.
 */
const AREA_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'products' },

  // Catalog
  {
    path: 'products',
    canMatch: [permissionGuard],
    loadChildren: () => import('./features/catalog/catalog.routes').then((m) => m.CATALOG_ROUTES),
  },
  {
    path: 'categories',
    canMatch: [permissionGuard],
    loadChildren: () => import('./features/catalog/catalog.routes').then((m) => m.CATEGORY_ROUTES),
  },
  {
    path: 'brands',
    canMatch: [permissionGuard],
    loadChildren: () => import('./features/catalog/catalog.routes').then((m) => m.BRAND_ROUTES),
  },

  // Sales
  {
    path: 'orders',
    canMatch: [permissionGuard],
    loadChildren: () => import('./features/orders/orders.routes').then((m) => m.ORDER_ROUTES),
  },
  {
    path: 'returns',
    canMatch: [permissionGuard],
    loadChildren: () => import('./features/returns/returns.routes').then((m) => m.RETURN_ROUTES),
  },
  {
    path: 'refunds',
    canMatch: [permissionGuard],
    loadChildren: () => import('./features/returns/returns.routes').then((m) => m.REFUND_ROUTES),
  },

  // People
  {
    path: 'customers',
    canMatch: [permissionGuard],
    loadChildren: () =>
      import('./features/customers/customers.routes').then((m) => m.CUSTOMER_ROUTES),
  },
  {
    path: 'sellers',
    canMatch: [permissionGuard],
    loadChildren: () => import('./features/sellers/sellers.routes').then((m) => m.SELLER_ROUTES),
  },
  {
    path: 'users',
    canMatch: [permissionGuard],
    loadChildren: () => import('./features/staff/staff.routes').then((m) => m.STAFF_ROUTES),
  },

  // Support, risk, knowledge
  {
    path: 'tickets',
    canMatch: [permissionGuard],
    loadChildren: () => import('./features/support/support.routes').then((m) => m.SUPPORT_ROUTES),
  },
  {
    path: 'flags',
    canMatch: [permissionGuard],
    loadChildren: () => import('./features/risk/risk.routes').then((m) => m.RISK_ROUTES),
  },
  {
    path: 'documents',
    canMatch: [permissionGuard],
    loadChildren: () =>
      import('./features/documents/documents.routes').then((m) => m.DOCUMENT_ROUTES),
  },

  {
    path: 'forbidden',
    title: 'No access · AgenticShop',
    data: { title: 'No access', message: 'Your role does not give you access to this page.' },
    loadComponent: () => import('./shared/ui/not-built/not-built').then((m) => m.NotBuilt),
  },

  {
    path: '**',
    title: 'Page not found · AgenticShop',
    data: { title: 'Page not found', message: 'There is no page at this address.' },
    loadComponent: () => import('./shared/ui/not-built/not-built').then((m) => m.NotBuilt),
  },
];

export const routes: Routes = [
  {
    path: 'login',
    title: 'Sign in � AgenticShop',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/pages/login/login').then((m) => m.Login),
  },
  { path: '', component: Shell, canActivate: [authGuard], children: AREA_ROUTES },
];
