import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import {
  HumanAnswer,
  InteractionState,
  PendingInteraction,
  describeAnswer,
  questionOf,
} from '../../models/chat.model';

/** A question or approval that is no longer open: what was asked and what the user answered (or why not). */
@Component({
  selector: 'app-answered-box',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="answered" [class.open-ended]="state() !== 'answered'">
      <div class="q">{{ question() }}</div>
      <div class="a">
        @if (state() === 'answered') {
          @if (denied()) {
            <svg class="deny" viewBox="0 0 16 16" aria-hidden="true">
              <path d="m4.5 4.5 7 7m0-7-7 7" />
            </svg>
          } @else {
            <svg viewBox="0 0 16 16" aria-hidden="true">
              <path d="m3.5 8.5 3 3 6-7" />
            </svg>
          }
        }
        <span>{{ outcome() }}</span>
      </div>
    </div>
  `,
  styles: `
    /* capped on the host: it is the flex item the page aligns to the right */
    :host {
      display: block;
      max-width: min(100%, 24rem);
    }

    .answered {
      display: inline-flex;
      flex-direction: column;
      max-width: 100%;
      overflow: hidden;
      background: var(--bs-body-bg);
      border: 1px solid var(--bs-border-color);
      border-radius: 0.75rem;
    }

    .q {
      padding: 0.35rem 0.75rem;
      font-size: 0.8125rem;
      color: var(--bs-secondary-color);
      background: var(--bs-tertiary-bg);
      border-bottom: 1px solid var(--bs-border-color);
      overflow-wrap: anywhere;
    }

    .a {
      display: flex;
      align-items: flex-start;
      gap: 0.4rem;
      padding: 0.45rem 0.75rem;
      font-weight: 600;
      overflow-wrap: anywhere;

      svg {
        flex-shrink: 0;
        width: 1rem;
        height: 1rem;
        margin-top: 0.15rem;
        fill: none;
        stroke: var(--bs-success);
        stroke-width: 1.8;
        stroke-linecap: round;
        stroke-linejoin: round;

        &.deny {
          stroke: var(--bs-danger);
        }
      }

      .open-ended & {
        font-weight: 400;
        font-style: italic;
        color: var(--bs-secondary-color);
      }
    }
  `,
})
export class AnsweredBox {
  readonly interaction = input.required<PendingInteraction>();
  readonly answer = input<HumanAnswer | null>(null);
  readonly state = input.required<InteractionState>();

  protected readonly question = computed(() => questionOf(this.interaction()));
  protected readonly denied = computed(() => {
    const a = this.answer();
    return a?.type === 'approval' && !a.approved;
  });
  protected readonly outcome = computed(() => {
    const answer = this.answer();
    switch (this.state()) {
      case 'answered':
        return answer ? describeAnswer(this.interaction(), answer) : 'Answered';
      case 'dismissed':
        return 'Dismissed';
      case 'closed':
        return 'This question is closed.';
      default:
        return 'Not answered';
    }
  });
}
