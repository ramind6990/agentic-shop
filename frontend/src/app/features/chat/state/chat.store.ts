import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';

import { loadOnChange, sameJson } from '../../../shared/data/load-on-change';
import { injectRouteParams, intParam } from '../../../shared/data/url-query';
import { ChatApi } from '../data-access/chat-api';
import { ChatSocket } from '../data-access/chat-socket';
import {
  ChatItem,
  ChatResponse,
  ConversationDetail,
  ConversationStatus,
  HumanAnswer,
  InteractionState,
  TimelineMessage,
} from '../models/chat.model';

const WORKING = 'Working on it…';
const THINKING = 'Thinking…';

/** A request sent on the socket, until its INPUT (or refusal) comes back. */
interface Pending {
  requestId: string;
  /** Null for the first message of a new chat. */
  conversationId: number | null;
  text?: string;
  interactionId?: string;
}

/**
 * The chat page (/chat, /chat/:conversationId). The URL names the open conversation; its history comes from REST and
 * live events from the socket, which delivers every run of the user (other conversations, other devices). Only the
 * open conversation's events change what is shown; the others refresh the list.
 */
@Injectable()
export class ChatStore {
  private readonly api = inject(ChatApi);
  private readonly socket = inject(ChatSocket);
  private readonly router = inject(Router);
  private readonly params = injectRouteParams();

  /** The conversation in the URL; undefined for a new chat. */
  private readonly routeId = computed(() => intParam(this.params(), 'conversationId') ?? undefined);
  /** A conversation this page just created: it is the open one at once, before the URL catches up. */
  private readonly adopted = signal<number | undefined>(undefined);
  readonly conversationId = computed(() => this.adopted() ?? this.routeId());

  private readonly list$ = loadOnChange(
    () => true,
    () => this.api.list(),
  );
  readonly conversations = computed(() => this.list$.value() ?? []);
  readonly listError = this.list$.error;

  /** Bumped to load the open conversation again (after a reconnect). */
  private readonly detailVersion = signal(0);
  private readonly detail$ = loadOnChange(
    computed(
      () => {
        const id = this.routeId();
        const version = this.detailVersion();
        // what this page just created is already on screen
        return id === undefined || id === untracked(this.adopted) ? undefined : { id, version };
      },
      { equal: sameJson },
    ),
    (r) => this.api.detail(r.id),
  );
  readonly detailLoading = this.detail$.loading;
  readonly detailError = this.detail$.error;

  /** Tokens of the open conversation and its context; reloaded whenever one of its runs ends. */
  private readonly chatUsage$ = loadOnChange(
    () => this.conversationId(),
    (id) => this.api.conversationUsage(id),
  );
  readonly chatUsage = computed(() => this.chatUsage$.value() ?? null);

  private readonly _items = signal<ChatItem[]>([]);
  private readonly _progress = signal<string | null>(null);
  private readonly _running = signal<{ runId: string; conversationId: number } | null>(null);
  private readonly _pending = signal<Pending | null>(null);
  private readonly _notice = signal<string | null>(null);
  private readonly _status = signal<ConversationStatus>('OPEN');
  private readonly _busy = signal<ReadonlySet<number>>(new Set());

  readonly items = this._items.asReadonly();
  /** The one transient progress line (replaced by each new notice), or null. */
  readonly progress = this._progress.asReadonly();
  readonly notice = this._notice.asReadonly();
  /** Conversations with a run in progress (for the list). */
  readonly busyConversations = this._busy.asReadonly();
  readonly socketStatus = this.socket.status;
  readonly closed = computed(() => this._status() === 'CLOSED');
  readonly title = computed(
    () => this.conversations().find((c) => c.id === this.conversationId())?.title ?? 'New chat',
  );
  /** A message is on its way or a run executes: no sending, the stop button shows. */
  readonly busy = computed(() => this._pending() !== null || this._running() !== null);
  readonly canStop = computed(() => this._running() !== null);
  readonly canSend = computed(
    () => !this.busy() && !this.closed() && this.socket.status() === 'open',
  );

  constructor() {
    this.socket.messages.pipe(takeUntilDestroyed()).subscribe((m) => this.handle(m));
    this.socket.connect();

    // another conversation (or a new chat) opened: start from a clean screen
    effect(() => {
      const id = this.routeId();
      untracked(() => {
        if (id !== undefined && id === this.adopted()) return;
        this.adopted.set(undefined);
        this.reset();
      });
    });

    effect(() => {
      const detail = this.detail$.value();
      if (detail) untracked(() => this.show(detail));
    });

    // back after a drop: what happened meanwhile is in the history
    effect(() => {
      if (this.socket.opened() > 1) {
        untracked(() => {
          this._pending.set(null);
          this.list$.reload();
          this.detailVersion.update((v) => v + 1);
        });
      }
    });
  }

  send(text: string): void {
    const trimmed = text.trim();
    if (!trimmed || !this.canSend()) return;
    const requestId = crypto.randomUUID();
    const conversationId = this.conversationId() ?? null;
    if (!this.socket.send({ type: 'chat', requestId, conversationId, text: trimmed })) {
      this._notice.set('Not connected. Reconnecting…');
      return;
    }
    this._pending.set({ requestId, conversationId, text: trimmed });
    this._notice.set(null);
    this._progress.set(WORKING);
    // a new message abandons an open question
    this._items.update((items) => [
      ...withInteractions(items, (i) => i.state === 'open', { state: 'not-answered' }),
      { kind: 'user', key: `req-${requestId}`, runId: null, text: trimmed },
    ]);
  }

  answer(interactionId: string, answer: HumanAnswer): void {
    const conversationId = this.conversationId();
    if (conversationId === undefined || this.busy()) return;
    const requestId = crypto.randomUUID();
    if (!this.socket.send({ type: 'chat', requestId, conversationId, interactionId, answer })) {
      this._notice.set('Not connected. Reconnecting…');
      return;
    }
    this._pending.set({ requestId, conversationId, interactionId });
    this._notice.set(null);
    this._progress.set(WORKING);
    this._items.update((items) =>
      withInteractions(items, (i) => i.interaction.interactionId === interactionId, {
        state: answer.type === 'dismissed' ? 'dismissed' : 'answered',
        answer,
      }),
    );
  }

  stop(): void {
    const run = this._running();
    if (run) {
      this.socket.send({
        type: 'cancel',
        requestId: crypto.randomUUID(),
        conversationId: run.conversationId,
      });
    }
  }

  // ---------------------------------------------------------------- incoming

  private handle(m: ChatResponse): void {
    const pending = this._pending();
    const mine = m.requestId !== undefined && m.requestId === pending?.requestId;
    if (m.type === 'ERROR' && m.seq === undefined) {
      if (mine && pending) this.refused(m, pending);
      return;
    }
    const conversationId = m.conversationId;
    if (conversationId === undefined) return;
    this.markBusy(conversationId, m.type === 'INPUT' || m.type === 'PROGRESS');
    const runEnded = m.type === 'REPLY' || m.type === 'INTERACTION' || m.type === 'ERROR';
    if (m.type === 'INPUT') {
      if (mine && pending?.conversationId === null) this.adopt(conversationId);
      if (!this.conversations().some((c) => c.id === conversationId)) this.list$.reload();
    }
    if (conversationId !== this.conversationId()) {
      if (m.type === 'REPLY') this.list$.reload();
      return;
    }
    if (runEnded) this.chatUsage$.reload();
    switch (m.type) {
      case 'INPUT':
        this.onInput(m, mine);
        break;
      case 'PROGRESS':
        this._progress.set(progressLabel(m.progress));
        break;
      case 'REPLY':
        if (m.reply && !this.hasItem('reply', m.runId)) {
          this.append({
            kind: 'reply',
            key: `reply-${m.runId}`,
            runId: m.runId ?? null,
            reply: m.reply,
          });
        }
        this.endRun();
        this.list$.reload();
        break;
      case 'INTERACTION': {
        const interaction = m.interaction!;
        const known = this._items().some(
          (i) =>
            i.kind === 'interaction' && i.interaction.interactionId === interaction.interactionId,
        );
        if (!known) {
          this.append({
            kind: 'interaction',
            key: `ask-${interaction.interactionId}`,
            runId: m.runId ?? null,
            interaction,
            state: 'open',
            answer: null,
          });
        }
        this.endRun();
        break;
      }
      case 'ERROR': {
        const code = m.error?.code;
        this.append({
          kind: 'error',
          key: `err-${m.runId}-${m.seq}`,
          runId: m.runId ?? null,
          message:
            code === 'CANCELLED' ? 'Stopped.' : (m.error?.message ?? 'Something went wrong.'),
          retryText: code === 'AGENT_ERROR' ? this.lastUserText() : null,
          stopped: code === 'CANCELLED',
        });
        this.endRun();
        break;
      }
    }
  }

  /** What the user sent, from this page (confirm) or from another device (show it). */
  private onInput(m: ChatResponse, mine: boolean): void {
    const input = m.input ?? {};
    const runId = m.runId ?? null;
    if (mine) {
      const key = `req-${this._pending()?.requestId}`;
      this._pending.set(null);
      this._items.update((items) => items.map((i) => (i.key === key ? { ...i, runId } : i)));
    } else if (input.text !== undefined) {
      if (!this.hasItem('user', m.runId)) {
        this._items.update((items) => [
          ...withInteractions(items, (i) => i.state === 'open', { state: 'not-answered' }),
          { kind: 'user', key: `user-${runId}-${m.seq}`, runId, text: input.text! },
        ]);
      }
    } else if (input.answered && input.answer) {
      const answer = input.answer;
      this._items.update((items) =>
        withInteractions(
          items,
          (i) => i.interaction.interactionId === input.answered!.interactionId,
          { state: answer.type === 'dismissed' ? 'dismissed' : 'answered', answer },
        ),
      );
    }
    this._running.set(runId ? { runId, conversationId: m.conversationId! } : null);
    this._progress.set(WORKING);
  }

  private refused(m: ChatResponse, pending: Pending): void {
    this._pending.set(null);
    this._progress.set(null);
    const error = m.error!;
    if (error.code === 'INTERACTION_CLOSED' && pending.interactionId) {
      this._items.update((items) =>
        withInteractions(items, (i) => i.interaction.interactionId === pending.interactionId, {
          state: 'closed',
        }),
      );
      return;
    }
    this.append({
      kind: 'error',
      key: `refused-${pending.requestId}`,
      runId: null,
      message: `Not sent: ${error.message}`,
      retryText: pending.text ?? null,
    });
  }

  // ---------------------------------------------------------------- helpers

  /** The conversation this page just created: keep the screen, put its id in the URL. */
  private adopt(conversationId: number): void {
    this.adopted.set(conversationId);
    void this.router
      .navigate(['/chat', conversationId], { replaceUrl: true })
      .then(() => this.adopted.set(undefined));
  }

  private show(detail: ConversationDetail): void {
    if (detail.id !== this.conversationId()) return;
    const run = detail.activeRun;
    this._items.set(toItems(detail));
    this._status.set(detail.status);
    const running = run?.status === 'RUNNING';
    this._running.set(running ? { runId: run.runId, conversationId: detail.id } : null);
    this._progress.set(running ? WORKING : null);
    this.markBusy(detail.id, running);
  }

  private reset(): void {
    this._items.set([]);
    this._progress.set(null);
    this._running.set(null);
    this._pending.set(null);
    this._notice.set(null);
    this._status.set('OPEN');
  }

  private endRun(): void {
    this._running.set(null);
    this._progress.set(null);
  }

  private append(item: ChatItem): void {
    this._items.update((items) => [...items, item]);
  }

  private hasItem(kind: ChatItem['kind'], runId: string | undefined): boolean {
    return runId !== undefined && this._items().some((i) => i.kind === kind && i.runId === runId);
  }

  private lastUserText(): string | null {
    const users = this._items().filter((i) => i.kind === 'user');
    const last = users[users.length - 1];
    return last?.kind === 'user' ? last.text : null;
  }

  private markBusy(conversationId: number, busy: boolean): void {
    if (this._busy().has(conversationId) === busy) return;
    this._busy.update((ids) => {
      const next = new Set(ids);
      if (busy) next.add(conversationId);
      else next.delete(conversationId);
      return next;
    });
  }
}

type InteractionItem = Extract<ChatItem, { kind: 'interaction' }>;

/** The items with the matching interaction boxes changed. */
function withInteractions(
  items: ChatItem[],
  match: (item: InteractionItem) => boolean,
  change: Partial<Pick<InteractionItem, 'state' | 'answer'>>,
): ChatItem[] {
  return items.map((i) => (i.kind === 'interaction' && match(i) ? { ...i, ...change } : i));
}

/** What a progress notice shows: the tool's label, a notice's own label, or "Thinking…" after a tool. */
function progressLabel(progress: ChatResponse['progress']): string {
  switch (progress?.stage) {
    case 'TOOL_STARTED':
      return progress.label ?? WORKING;
    case 'RETRYING':
      return progress.label ?? 'Request failed. Retrying…';
    default:
      return progress?.label ?? THINKING;
  }
}

/** The timeline as page items; a failed reply offers to send the user's message again. */
function toItems(detail: ConversationDetail): ChatItem[] {
  let lastUserText: string | null = null;
  return detail.messages.map((m) => {
    const item = toItem(m, detail, lastUserText);
    if (m.kind === 'USER') lastUserText = m.text;
    return item;
  });
}

function toItem(
  m: TimelineMessage,
  detail: ConversationDetail,
  lastUserText: string | null,
): ChatItem {
  const key = `m-${m.id}`;
  switch (m.kind) {
    case 'USER':
      return { kind: 'user', key, runId: m.runId, text: m.text ?? '' };
    case 'ASSISTANT': {
      const reply = m.reply ?? { status: 'ANSWERED', text: m.text ?? '', suggestedReplies: [] };
      if (reply.status === 'FAILED' || reply.status === 'STOPPED') {
        const stopped = reply.status === 'STOPPED';
        return {
          kind: 'error',
          key,
          runId: m.runId,
          message: reply.text,
          retryText: stopped ? null : lastUserText,
          stopped,
        };
      }
      return { kind: 'reply', key, runId: m.runId, reply };
    }
    case 'INTERACTION':
      return {
        kind: 'interaction',
        key,
        runId: m.runId,
        interaction: m.payload!,
        state: interactionState(m, detail),
        answer: m.answer,
      };
  }
}

function interactionState(m: TimelineMessage, detail: ConversationDetail): InteractionState {
  switch (m.interactionStatus) {
    case 'PENDING': {
      const run = detail.activeRun;
      const waiting =
        run?.status === 'PAUSED' &&
        run.pendingInteraction?.interactionId === m.payload?.interactionId;
      return waiting ? 'open' : 'not-answered';
    }
    case 'DISMISSED':
      return 'dismissed';
    case 'NOT_ANSWERED':
      return 'not-answered';
    default:
      return 'answered';
  }
}
