import { Routes } from '@angular/router';

/** Every top-level area is a lazily loaded feature; routes inside each feature live in its *.routes.ts. */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'products' },

  // Catalog
  {
    path: 'products',
    loadChildren: () => import('./features/catalog/catalog.routes').then((m) => m.CATALOG_ROUTES),
  },
  {
    path: 'categories',
    loadChildren: () => import('./features/catalog/catalog.routes').then((m) => m.CATEGORY_ROUTES),
  },
  {
    path: 'brands',
    loadChildren: () => import('./features/catalog/catalog.routes').then((m) => m.BRAND_ROUTES),
  },

  // Sales
  {
    path: 'orders',
    loadChildren: () => import('./features/orders/orders.routes').then((m) => m.ORDER_ROUTES),
  },
  {
    path: 'returns',
    loadChildren: () => import('./features/returns/returns.routes').then((m) => m.RETURN_ROUTES),
  },
  {
    path: 'refunds',
    loadChildren: () => import('./features/returns/returns.routes').then((m) => m.REFUND_ROUTES),
  },

  // People
  {
    path: 'customers',
    loadChildren: () =>
      import('./features/customers/customers.routes').then((m) => m.CUSTOMER_ROUTES),
  },
  {
    path: 'sellers',
    loadChildren: () => import('./features/sellers/sellers.routes').then((m) => m.SELLER_ROUTES),
  },
  {
    path: 'users',
    loadChildren: () => import('./features/staff/staff.routes').then((m) => m.STAFF_ROUTES),
  },

  // Support, risk, knowledge
  {
    path: 'tickets',
    loadChildren: () => import('./features/support/support.routes').then((m) => m.SUPPORT_ROUTES),
  },
  {
    path: 'flags',
    loadChildren: () => import('./features/risk/risk.routes').then((m) => m.RISK_ROUTES),
  },
  {
    path: 'documents',
    loadChildren: () =>
      import('./features/documents/documents.routes').then((m) => m.DOCUMENT_ROUTES),
  },

  {
    path: '**',
    title: 'Page not found · AgenticShop',
    data: { title: 'Page not found', message: 'There is no page at this address.' },
    loadComponent: () => import('./shared/ui/not-built/not-built').then((m) => m.NotBuilt),
  },
];
