import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  ConversationDetail,
  ConversationSummary,
  ConversationUsage,
  UserUsage,
} from '../models/chat.model';

/**
 * REST calls of the chat: conversations to list and open, token usage, and the one-time token for the socket
 * handshake.
 */
@Injectable({ providedIn: 'root' })
export class ChatApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/ai2';

  /** Most recently active first. */
  list(): Observable<ConversationSummary[]> {
    return this.http.get<ConversationSummary[]>(`${this.baseUrl}/conversations`);
  }

  detail(id: number): Observable<ConversationDetail> {
    return this.http.get<ConversationDetail>(`${this.baseUrl}/conversations/${id}`);
  }

  /** A conversation's tokens per issuer, and its context. */
  conversationUsage(id: number): Observable<ConversationUsage> {
    return this.http.get<ConversationUsage>(`${this.baseUrl}/conversations/${id}/usage`);
  }

  /** The user's tokens this month (UTC). */
  myUsage(): Observable<UserUsage> {
    return this.http.get<UserUsage>(`${this.baseUrl}/usage/me`);
  }

  connectToken(): Observable<{ connectToken: string; expiresAt: string }> {
    return this.http.post<{ connectToken: string; expiresAt: string }>(
      `${this.baseUrl}/ws/connect-tokens`,
      null,
    );
  }
}
