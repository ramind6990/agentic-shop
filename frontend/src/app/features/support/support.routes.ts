import { Routes } from '@angular/router';

export const SUPPORT_ROUTES: Routes = [
  {
    path: '',
    title: 'Tickets · AgenticShop',
    loadComponent: () => import('./pages/ticket-list/ticket-list').then((m) => m.TicketList),
  },
  {
    path: ':id',
    title: 'Ticket · AgenticShop',
    loadComponent: () =>
      import('./pages/ticket-thread/ticket-thread').then((m) => m.TicketThread),
  },
];
