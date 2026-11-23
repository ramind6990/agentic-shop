import { Routes } from '@angular/router';

export const RISK_ROUTES: Routes = [
  {
    path: '',
    title: 'Account flags · AgenticShop',
    loadComponent: () => import('./pages/flag-list/flag-list').then((m) => m.FlagList),
  },
];
