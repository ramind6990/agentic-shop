import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ConversationSummary } from '../../models/chat.model';

/** The user's conversations, newest first, and "+" for a new chat. */
@Component({
  selector: 'app-conversation-list',
  imports: [RouterLink, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-flex flex-column h-100' },
  template: `
    <div class="d-flex align-items-center justify-content-between px-3 py-2 border-bottom">
      <h2 class="h6 mb-0">Chats</h2>
      <a
        class="btn btn-sm btn-outline-primary d-inline-flex align-items-center gap-1"
        routerLink="/chat"
        title="New chat"
      >
        <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
          <path d="M8 2a.75.75 0 0 1 .75.75v4.5h4.5a.75.75 0 0 1 0 1.5h-4.5v4.5a.75.75 0 0 1-1.5 0v-4.5h-4.5a.75.75 0 0 1 0-1.5h4.5v-4.5A.75.75 0 0 1 8 2Z" />
        </svg>
        New
      </a>
    </div>
    <nav class="flex-grow-1 overflow-auto p-2" aria-label="Conversations">
      @for (c of conversations(); track c.id) {
        <a
          class="conversation"
          [routerLink]="['/chat', c.id]"
          [class.active]="c.id === activeId()"
          [attr.aria-current]="c.id === activeId() ? 'page' : null"
        >
          <span class="text-truncate" [title]="c.title ?? ''">{{ c.title ?? 'New conversation' }}</span>
          <span class="d-flex align-items-center gap-2 small text-body-secondary text-nowrap">
            @if (busy().has(c.id)) {
              <span class="spinner-grow spinner-grow-sm text-primary" aria-label="Working"></span>
            }
            @if (c.status === 'CLOSED') {
              <span>full</span>
            }
            {{ c.updatedAt | date: 'MMM d' }}
          </span>
        </a>
      } @empty {
        <p class="small text-body-secondary px-2 py-1 mb-0">No conversations yet.</p>
      }
    </nav>
  `,
  styles: `
    .conversation {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 0.5rem;
      padding: 0.4rem 0.6rem;
      border-radius: 0.375rem;
      min-width: 0;
      color: var(--bs-body-color);
      text-decoration: none;

      &:hover {
        background: var(--bs-tertiary-bg);
      }

      &.active {
        background: var(--bs-primary-bg-subtle);
        font-weight: 600;
      }
    }
  `,
})
export class ConversationList {
  readonly conversations = input.required<ConversationSummary[]>();
  readonly activeId = input<number | undefined>();
  readonly busy = input<ReadonlySet<number>>(new Set());
}
