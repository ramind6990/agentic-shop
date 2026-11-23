import { Routes } from '@angular/router';

export const STAFF_ROUTES: Routes = [
  {
    path: '',
    title: 'Staff users · AgenticShop',
    loadComponent: () => import('./pages/staff-list/staff-list').then((m) => m.StaffList),
  },
];
