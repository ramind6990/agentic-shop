import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AREA_PERMISSIONS, firstSegment } from './access';
import { AuthApi } from './auth-api';
import { Permission, Session } from './auth.models';

const TOKEN_KEY = 'agenticshop.token';
const EXPIRES_KEY = 'agenticshop.tokenExpiresAt';

/**
 * App-wide sign-in state: the access token (kept in localStorage so a reload stays signed in) and the session
 * from /api/auth/me (profile, role, permissions). Screens, guards and the nav ask {@link can} what to show.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly api = inject(AuthApi);
  private readonly router = inject(Router);

  private readonly _token = signal<string | null>(readStoredToken());
  private readonly _session = signal<Session | null>(null);

  readonly token = this._token.asReadonly();
  readonly session = this._session.asReadonly();
  readonly isLoggedIn = computed(() => this._session() !== null);
  private readonly permissions = computed(() => new Set(this._session()?.permissions ?? []));

  /** Customers only see their own data and seller users their seller's: their customer/seller filters are moot. */
  readonly scopedToCustomer = computed(() => this._session()?.role === 'CUSTOMER');
  readonly scopedToSeller = computed(() => this._session()?.role === 'SELLER');

  can(permission: Permission): boolean {
    return this.permissions().has(permission);
  }

  /**
   * Whether a link to these router commands (or URL) would open for this user: the area's permission, and for a
   * seller user, only their own seller's page. Paths outside the known areas are always allowed.
   */
  canOpen(target: string | readonly unknown[]): boolean {
    const area = firstSegment(target);
    const permission = AREA_PERMISSIONS[area];
    if (permission && !this.can(permission)) {
      return false;
    }
    if (
      area === 'sellers' &&
      this.scopedToSeller() &&
      typeof target !== 'string' &&
      target.length > 1
    ) {
      return String(target[1]) === String(this._session()?.seller?.id);
    }
    return true;
  }

  /** Checks the credentials, stores the token and loads the session. Rejects with the HTTP error. */
  async login(username: string, password: string): Promise<void> {
    const res = await firstValueFrom(this.api.login(username, password));
    storeToken(res.token, res.expiresAt);
    this._token.set(res.token);
    await this.loadSession();
  }

  /**
   * Loads the session for a stored token (app start). A rejected or expired token is dropped, so the guard sends
   * the user to the login page.
   */
  async loadSession(): Promise<void> {
    if (!this._token()) {
      return;
    }
    try {
      this._session.set(await firstValueFrom(this.api.me()));
    } catch {
      this.clear();
    }
  }

  logout(): void {
    this.clear();
    void this.router.navigate(['/login']);
  }

  /** The token was rejected mid-session (expired): sign out and come back here after signing in again. */
  expire(): void {
    if (!this._session()) {
      // Still starting up (loadSession): the route guard redirects, with the URL the user asked for.
      this.clear();
      return;
    }
    const returnUrl = this.router.url;
    this.clear();
    void this.router.navigate(['/login'], { queryParams: { returnUrl } });
  }

  private clear(): void {
    try {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(EXPIRES_KEY);
    } catch {
      // storage unavailable: nothing stored
    }
    this._token.set(null);
    this._session.set(null);
  }
}

function readStoredToken(): string | null {
  try {
    const token = localStorage.getItem(TOKEN_KEY);
    const expiresAt = localStorage.getItem(EXPIRES_KEY);
    if (!token || !expiresAt || Date.parse(expiresAt) <= Date.now()) {
      return null;
    }
    return token;
  } catch {
    return null;
  }
}

function storeToken(token: string, expiresAt: string): void {
  try {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(EXPIRES_KEY, expiresAt);
  } catch {
    // storage unavailable: the session lasts until the page is reloaded
  }
}
