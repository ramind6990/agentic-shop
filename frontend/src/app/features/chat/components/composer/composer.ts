import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

/**
 * Where the user types. Enter sends (Shift+Enter: new line). While a reply is on its way the send button turns
 * into a stop button.
 */
@Component({
  selector: 'app-chat-composer',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="composer" (submit)="submit($event)">
      <label class="visually-hidden" for="chat-input">Message</label>
      <textarea
        id="chat-input"
        class="form-control border-0 shadow-none"
        rows="2"
        maxlength="4000"
        [formControl]="text"
        [placeholder]="placeholder()"
        (keydown.enter)="onEnter($event)"
      ></textarea>
      @if (busy()) {
        <button
          type="button"
          class="btn btn-dark round-btn"
          [disabled]="!canStop()"
          (click)="stop.emit()"
          aria-label="Stop"
          title="Stop"
        >
          <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
            <rect width="12" height="12" rx="2" fill="currentColor" />
          </svg>
        </button>
      } @else {
        <button
          type="submit"
          class="btn btn-primary round-btn"
          [disabled]="!sendable()"
          aria-label="Send"
          title="Send"
        >
          <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
            <path d="M8 1.5a.75.75 0 0 1 .53.22l4.5 4.5a.75.75 0 0 1-1.06 1.06L8.75 4.06v9.69a.75.75 0 0 1-1.5 0V4.06L4.03 7.28a.75.75 0 0 1-1.06-1.06l4.5-4.5A.75.75 0 0 1 8 1.5Z" />
          </svg>
        </button>
      }
    </form>
  `,
  styles: `
    .composer {
      display: flex;
      align-items: flex-end;
      gap: 0.5rem;
      padding: 0.5rem 0.5rem 0.5rem 0.25rem;
      background: var(--bs-body-bg);
      border: 1px solid var(--bs-border-color);
      border-radius: 1rem;
      box-shadow: 0 1px 4px rgb(0 0 0 / 0.06);
    }

    textarea {
      resize: none;
      max-height: 10rem;
    }

    .round-btn {
      width: 2.25rem;
      height: 2.25rem;
      padding: 0;
      border-radius: 50%;
      flex-shrink: 0;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }
  `,
})
export class Composer {
  /** Whether a message may be sent now. */
  readonly canSend = input(false);
  /** A reply is on its way: show the stop button instead of send. */
  readonly busy = input(false);
  readonly canStop = input(false);
  readonly placeholder = input('Ask about products or your orders…');
  readonly send = output<string>();
  readonly stop = output<void>();

  protected readonly text = new FormControl('', { nonNullable: true });
  private readonly value = toSignal(this.text.valueChanges, { initialValue: '' });
  protected readonly sendable = computed(() => this.canSend() && this.value().trim().length > 0);

  protected onEnter(event: Event): void {
    if ((event as KeyboardEvent).shiftKey) return;
    this.submit(event);
  }

  protected submit(event: Event): void {
    event.preventDefault();
    if (!this.sendable()) return;
    this.send.emit(this.text.value);
    this.text.setValue('');
  }
}
