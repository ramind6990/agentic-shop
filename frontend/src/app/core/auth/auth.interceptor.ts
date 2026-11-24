import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { AuthStore } from './auth.store';

const LOGIN_URL = '/api/auth/login';

/**
 * Adds the access token to every API call. A 401 on any call but the login itself means the token expired or was
 * rejected, so the user is signed out and sent to the login page.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthStore);
  const token = auth.token();
  const isApi = req.url.startsWith('/api/');
  const isLogin = req.url === LOGIN_URL;

  const request =
    isApi && !isLogin && token
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(request).pipe(
    catchError((error: unknown) => {
      if (isApi && !isLogin && error instanceof HttpErrorResponse && error.status === 401) {
        auth.expire();
      }
      return throwError(() => error);
    }),
  );
};
