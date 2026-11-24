import { inject } from '@angular/core';
import { CanActivateFn, CanMatchFn, Router } from '@angular/router';

import { AREA_PERMISSIONS } from './access';
import { Permission } from './auth.models';
import { AuthStore } from './auth.store';

/** Screens inside the shell need a signed-in user; others go to the login page and come back afterwards. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthStore);
  return auth.isLoggedIn()
    ? true
    : inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** The login page is only for signed-out users. */
export const guestGuard: CanActivateFn = () =>
  inject(AuthStore).isLoggedIn() ? inject(Router).createUrlTree(['/']) : true;

/**
 * A route opens only with its permission: `data.permission`, else the area permission of its path
 * (AREA_PERMISSIONS). Otherwise the user sees the "No access" page instead.
 */
export const permissionGuard: CanMatchFn = (route) => {
  const permission =
    (route.data?.['permission'] as Permission | undefined) ?? AREA_PERMISSIONS[route.path ?? ''];
  return !permission || inject(AuthStore).can(permission)
    ? true
    : inject(Router).createUrlTree(['/forbidden']);
};
