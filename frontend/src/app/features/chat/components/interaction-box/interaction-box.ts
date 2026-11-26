import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { HumanAnswer, PendingInteraction, questionOf } from '../../models/chat.model';

/**
 * A question or approval the assistant waits for, with the controls its type needs: options (one or several), a text
 * field, Yes/No, or Approve/Deny. The close button dismisses it.
 */
@Component({
  selector: 'app-interaction-box',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card border-primary-subtle shadow-sm" [attr.aria-label]="question()">
      <div class="card-body p-3 d-flex flex-column gap-2">
        <div class="d-flex align-items-start gap-2">
          <div class="flex-grow-1">
            <div class="fw-semibold">{{ question() }}</div>
            @if (approval()?.detail; as detail) {
              <div class="small text-body-secondary">{{ detail }}</div>
            }
          </div>
          <button
            type="button"
            class="btn-close"
            aria-label="Dismiss"
            title="Dismiss"
            [disabled]="disabled()"
            (click)="answer.emit({ type: 'dismissed' })"
          ></button>
        </div>

        @if (choice(); as c) {
          @if (c.multiSelect) {
            @for (o of c.options; track o.value) {
              <div class="form-check">
                <input
                  class="form-check-input"
                  type="checkbox"
                  [id]="'opt-' + o.value"
                  [checked]="selected().has(o.value)"
                  [disabled]="disabled()"
                  (change)="toggle(o.value)"
                />
                <label class="form-check-label" [for]="'opt-' + o.value">
                  {{ o.label }}
                  @if (o.description) {
                    <span class="d-block small text-body-secondary">{{ o.description }}</span>
                  }
                </label>
              </div>
            }
            <div>
              <button
                type="button"
                class="btn btn-sm btn-primary"
                [disabled]="disabled() || selected().size === 0"
                (click)="answer.emit({ type: 'choice', values: [...selected()] })"
              >
                Send
              </button>
            </div>
          } @else {
            <div class="d-flex flex-column gap-1">
              @for (o of c.options; track o.value) {
                <button
                  type="button"
                  class="btn btn-outline-primary text-start"
                  [disabled]="disabled()"
                  (click)="answer.emit({ type: 'choice', values: [o.value] })"
                >
                  {{ o.label }}
                  @if (o.description) {
                    <span class="d-block small opacity-75">{{ o.description }}</span>
                  }
                </button>
              }
            </div>
          }
        }

        @switch (interaction().type) {
          @case ('input') {
            <form class="d-flex gap-2" (submit)="sendText($event)">
              <label class="visually-hidden" for="ask-input">Your answer</label>
              <input id="ask-input" class="form-control form-control-sm" [formControl]="text" />
              <button type="submit" class="btn btn-sm btn-primary" [disabled]="disabled()">Send</button>
            </form>
          }
          @case ('yesNo') {
            <div class="d-flex gap-2">
              <button type="button" class="btn btn-sm btn-primary" [disabled]="disabled()"
                      (click)="answer.emit({ type: 'yesNo', yes: true })">Yes</button>
              <button type="button" class="btn btn-sm btn-outline-secondary" [disabled]="disabled()"
                      (click)="answer.emit({ type: 'yesNo', yes: false })">No</button>
            </div>
          }
          @case ('approval') {
            <div class="d-flex gap-2">
              <button type="button" class="btn btn-sm btn-primary" [disabled]="disabled()"
                      (click)="answer.emit({ type: 'approval', approved: true })">Approve</button>
              <button type="button" class="btn btn-sm btn-outline-danger" [disabled]="disabled()"
                      (click)="answer.emit({ type: 'approval', approved: false })">Deny</button>
            </div>
          }
        }
      </div>
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
  protected readonly selected = signal<ReadonlySet<string>>(new Set());
  protected readonly text = new FormControl('', { nonNullable: true });

  protected toggle(value: string): void {
    this.selected.update((s) => {
      const next = new Set(s);
      if (!next.delete(value)) next.add(value);
      return next;
    });
  }

  protected sendText(event: Event): void {
    event.preventDefault();
    const text = this.text.value.trim();
    if (text && !this.disabled()) this.answer.emit({ type: 'input', text });
  }
}
