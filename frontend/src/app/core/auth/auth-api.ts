import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { LoginResponse, Session } from './auth.models';

/** Sign-in and session endpoints. Stateless; the token and session live in AuthStore. */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', { username, password });
  }

  me(): Observable<Session> {
    return this.http.get<Session>('/api/auth/me');
  }
}
