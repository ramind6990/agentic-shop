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
    <div class="answered">
      <div class="small text-body-secondary">{{ question() }}</div>
      <div [class.fw-semibold]="state() === 'answered'" [class.text-body-secondary]="state() !== 'answered'">
        {{ outcome() }}
      </div>
    </div>
  `,
  styles: `
    .answered {
      display: inline-block;
      max-width: 100%;
      padding: 0.5rem 0.75rem;
      border: 1px solid var(--bs-border-color);
      border-left: 3px solid var(--bs-primary);
      border-radius: 0.375rem;
      background: var(--bs-body-bg);
    }
  `,
})
export class AnsweredBox {
  readonly interaction = input.required<PendingInteraction>();
  readonly answer = input<HumanAnswer | null>(null);
  readonly state = input.required<InteractionState>();

  protected readonly question = computed(() => questionOf(this.interaction()));
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
