import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';

import { HumanAnswer, PendingInteraction, questionOf } from '../../models/chat.model';
import { Composer } from '../composer/composer';

/**
 * A question or approval the assistant waits for, as a small card: the question in a tinted header, the controls its
 * type needs in the body (options to pick, or an answer field like the chat's own), and Dismiss plus the answer
 * buttons in the footer.
 */
@Component({
  selector: 'app-interaction-box',
  imports: [Composer],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './interaction-box.scss',
  template: `
    @let i = interaction();
    <section class="ask" [class.approval]="i.type === 'approval'" [attr.aria-labelledby]="ids().head">
      <header class="ask-head" [id]="ids().head">
        @if (i.type === 'approval') {
          <svg class="ask-icon" viewBox="0 0 16 16" aria-hidden="true">
            <path d="M8 1.5 2.75 3.4v3.9c0 3.1 2.2 5.7 5.25 7.2 3.05-1.5 5.25-4.1 5.25-7.2V3.4Z" />
            <path d="m5.6 8 1.7 1.7 3.1-3.3" />
          </svg>
        } @else {
          <svg class="ask-icon" viewBox="0 0 16 16" aria-hidden="true">
            <circle cx="8" cy="8" r="6.5" />
            <path d="M6.2 6.2a1.9 1.9 0 1 1 2.6 1.75c-.5.2-.8.6-.8 1.1v.35" />
            <circle class="dot" cx="8" cy="11.5" r="0.8" />
          </svg>
        }
        <span class="ask-title">{{ question() }}</span>
      </header>

      @switch (i.type) {
        @case ('choice') {
          @if (choice(); as c) {
            <fieldset class="ask-body opts" [disabled]="disabled()" (keydown.enter)="submitChoice()">
              <legend class="visually-hidden">{{ question() }}</legend>
              @if (c.multiSelect) {
                <div class="hint">Select all that apply</div>
              }
              @for (o of c.options; track o.value) {
                <label class="opt" [class.checked]="selected().has(o.value)">
                  <input
                    class="opt-input"
                    [type]="c.multiSelect ? 'checkbox' : 'radio'"
                    [name]="ids().group"
                    [value]="o.value"
                    [checked]="selected().has(o.value)"
                    (change)="pick(o.value, c.multiSelect)"
                  />
                  <span class="mark" [class.square]="c.multiSelect" aria-hidden="true">
                    @if (c.multiSelect) {
                      <svg viewBox="0 0 16 16" fill="currentColor">
                        <path
                          d="M12.74 4.26a.75.75 0 0 1 0 1.06l-5.5 5.5a.75.75 0 0 1-1.06 0l-2.5-2.5a.75.75 0 1 1 1.06-1.06l1.97 1.97 4.97-4.97a.75.75 0 0 1 1.06 0Z"
                        />
                      </svg>
                    }
                  </span>
                  <span class="opt-text">
                    <span class="opt-label">{{ o.label }}</span>
                    @if (o.description) {
                      <span class="opt-desc">{{ o.description }}</span>
                    }
                  </span>
                </label>
              }
            </fieldset>
          }
        }
        @case ('input') {
          <div class="ask-body">
            <app-chat-composer
              [inputId]="ids().field"
              label="Your answer"
              placeholder="Type your answer…"
              [canSend]="!disabled()"
              (send)="answer.emit({ type: 'input', text: $event.trim() })"
            />
          </div>
        }
        @case ('approval') {
          @if (approval()?.detail; as detail) {
            <div class="ask-body detail">{{ detail }}</div>
          }
        }
      }

      <footer class="ask-foot">
        <button
          type="button"
          class="btn btn-sm btn-link link-secondary text-decoration-none me-auto"
          [disabled]="disabled()"
          (click)="answer.emit({ type: 'dismissed' })"
        >
          Dismiss
        </button>
        @switch (i.type) {
          @case ('choice') {
            <button
              type="button"
              class="btn btn-sm btn-primary px-3"
              [disabled]="disabled() || selected().size === 0"
              (click)="submitChoice()"
            >
              Submit
            </button>
          }
          @case ('input') {
            <span class="hint">Enter to send</span>
          }
          @case ('yesNo') {
            <button
              type="button"
              class="btn btn-sm btn-outline-secondary px-3"
              [disabled]="disabled()"
              (click)="answer.emit({ type: 'yesNo', yes: false })"
            >
              No
            </button>
            <button
              type="button"
              class="btn btn-sm btn-primary px-3"
              [disabled]="disabled()"
              (click)="answer.emit({ type: 'yesNo', yes: true })"
            >
              Yes
            </button>
          }
          @case ('approval') {
            <button
              type="button"
              class="btn btn-sm btn-outline-danger px-3"
              [disabled]="disabled()"
              (click)="answer.emit({ type: 'approval', approved: false })"
            >
              Deny
            </button>
            <button
              type="button"
              class="btn btn-sm btn-primary px-3"
              [disabled]="disabled()"
              (click)="answer.emit({ type: 'approval', approved: true })"
            >
              Approve
            </button>
          }
        }
      </footer>
    </section>
  `,
})
export class InteractionBox {
  readonly interaction = input.required<PendingInteraction>();
  readonly disabled = input(false);
  readonly answer = output<HumanAnswer>();

  protected readonly question = computed(() => questionOf(this.interaction()));
  protected readonly choice = computed(() => {
    const i = this.interaction();
    return i.type === 'choice' ? i : null;
  });
  protected readonly approval = computed(() => {
    const i = this.interaction();
    return i.type === 'approval' ? i : null;
  });
  /** Ids unique to this question, for the header label, the radio group and the answer field. */
  protected readonly ids = computed(() => {
    const id = this.interaction().interactionId;
    return { head: `ask-${id}-q`, group: `ask-${id}-opts`, field: `ask-${id}-text` };
  });
  protected readonly selected = signal<ReadonlySet<string>>(new Set());

  /** One option picked: a single choice replaces the selection, a multiple choice toggles it. */
  protected pick(value: string, multi: boolean): void {
    this.selected.update((s) => {
      if (!multi) return new Set([value]);
      const next = new Set(s);
      if (!next.delete(value)) next.add(value);
      return next;
    });
  }

  protected submitChoice(): void {
    const values = [...this.selected()];
    if (values.length > 0 && !this.disabled()) this.answer.emit({ type: 'choice', values });
  }
}
