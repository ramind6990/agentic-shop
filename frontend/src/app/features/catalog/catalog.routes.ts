import { Routes } from '@angular/router';

/** Mounted at /products. */
export const CATALOG_ROUTES: Routes = [
  {
    path: '',
    title: 'Products · AgenticShop',
    loadComponent: () =>
      import('./pages/product-search/product-search').then((m) => m.ProductSearch),
  },
  {
    path: ':id',
    title: 'Product · AgenticShop',
    loadComponent: () =>
      import('./pages/product-detail/product-detail').then((m) => m.ProductDetailPage),
  },
];

/** Mounted at /categories. */
export const CATEGORY_ROUTES: Routes = [
  {
    path: '',
    title: 'Categories · AgenticShop',
    loadComponent: () =>
      import('./pages/category-browser/category-browser').then((m) => m.CategoryBrowser),
  },
];

/** Mounted at /brands. */
export const BRAND_ROUTES: Routes = [
  {
    path: '',
    title: 'Brands · AgenticShop',
    loadComponent: () => import('./pages/brand-list/brand-list').then((m) => m.BrandList),
  },
];
