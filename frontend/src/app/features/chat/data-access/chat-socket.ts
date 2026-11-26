import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { Subject, firstValueFrom } from 'rxjs';

import { ChatResponse, ClientMessage } from '../models/chat.model';
import { ChatApi } from './chat-api';

export type SocketStatus = 'connecting' | 'open' | 'reconnecting' | 'closed';

const MAX_BACKOFF_MS = 15_000;

/**
 * The chat WebSocket of one page: fetches a one-time connect token (REST, with the JWT), opens
 * `/api/ai2/ws?connectToken=…` and reconnects with backoff (a new token each time) until the page is left. Every
 * message of every run of the user arrives on {@link messages}; {@link opened} counts the openings, so the page can
 * reload what it missed while the socket was down.
 */
@Injectable()
export class ChatSocket {
  private readonly api = inject(ChatApi);

  private readonly _status = signal<SocketStatus>('closed');
  private readonly _opened = signal(0);
  readonly status = this._status.asReadonly();
  readonly opened = this._opened.asReadonly();
  readonly messages = new Subject<ChatResponse>();

  private ws: WebSocket | undefined;
  private attempt = 0;
  private stopped = true;
  private retryTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.close());
  }

  connect(): void {
    if (!this.stopped) return;
    this.stopped = false;
    void this.open();
  }

  /** False when the socket is not open (the message is not queued). */
  send(message: ClientMessage): boolean {
    if (this.ws?.readyState !== WebSocket.OPEN) return false;
    this.ws.send(JSON.stringify(message));
    return true;
  }

  close(): void {
    this.stopped = true;
    clearTimeout(this.retryTimer);
    this.ws?.close(1000);
    this.ws = undefined;
    this._status.set('closed');
  }

  private async open(): Promise<void> {
    this._status.set(this.attempt === 0 ? 'connecting' : 'reconnecting');
    let token: string;
    try {
      token = (await firstValueFrom(this.api.connectToken())).connectToken;
    } catch (e) {
      // 401: the session expired (the interceptor signs out); 403: no chat permission. Retrying cannot help.
      if (e instanceof HttpErrorResponse && (e.status === 401 || e.status === 403)) {
        this.close();
      } else {
        this.retryLater();
      }
      return;
    }
    if (this.stopped) return;
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    const ws = new WebSocket(
      `${scheme}://${location.host}/api/ai2/ws?connectToken=${encodeURIComponent(token)}`,
    );
    this.ws = ws;
    ws.onopen = () => {
      this.attempt = 0;
      this._status.set('open');
      this._opened.update((n) => n + 1);
    };
    ws.onmessage = (event) => this.messages.next(JSON.parse(event.data) as ChatResponse);
    ws.onclose = () => {
      if (this.ws === ws) this.ws = undefined;
      if (!this.stopped) this.retryLater();
    };
  }

  private retryLater(): void {
    if (this.stopped) return;
    this._status.set('reconnecting');
    const delay = Math.min(MAX_BACKOFF_MS, 500 * 2 ** this.attempt++);
    this.retryTimer = setTimeout(() => void this.open(), delay);
  }
}
