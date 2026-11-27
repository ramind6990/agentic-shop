import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';

/** Milliseconds per character when the old label disappears from its end. */
const ERASE_MS = 12;
/** Milliseconds per character when the new label appears from its start. */
const TYPE_MS = 22;

/**
 * The run's progress notice ("Thinking…", "Searching products…", "Request failed. Retrying (2/5)"). A new label never
 * just replaces the old one: the old text disappears from its end, character by character, then the new one types in
 * from its start. A shared beginning is kept, so "Retrying (2/5)" → "(3/5)" only retypes the digit. A label that
 * changes mid-animation becomes the new target. Under `prefers-reduced-motion` the label changes at once.
 * Screen readers get the full label (the animated text is hidden from them).
 */
@Component({
  selector: 'app-progress-line',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { role: 'status' },
  template: `
    <span class="spinner-border spinner-border-sm text-primary" aria-hidden="true"></span>
    <span class="shown" aria-hidden="true">{{ shown() }}</span>
    <span class="visually-hidden">{{ label() }}</span>
  `,
  styles: `
    .shown {
      white-space: pre;
      min-height: 1lh;
    }
  `,
})
export class ProgressLine {
  readonly label = input.required<string>();

  /** The characters on screen right now. */
  protected readonly shown = signal('');
  private timer: ReturnType<typeof setTimeout> | undefined;
  private readonly reducedMotion =
    typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;

  constructor() {
    effect(() => {
      const target = this.label();
      untracked(() => this.animateTo(target));
    });
    inject(DestroyRef).onDestroy(() => clearTimeout(this.timer));
  }

  private animateTo(target: string): void {
    clearTimeout(this.timer);
    if (this.reducedMotion) {
      this.shown.set(target);
      return;
    }
    this.step([...target]);
  }

  /** One character per tick: erase down to the shared beginning, then type the rest of the target. */
  private step(target: string[]): void {
    const current = [...this.shown()];
    const keep = sharedPrefix(current, target);
    if (current.length > keep) {
      this.shown.set(current.slice(0, -1).join(''));
      this.timer = setTimeout(() => this.step(target), ERASE_MS);
    } else if (current.length < target.length) {
      this.shown.set(target.slice(0, current.length + 1).join(''));
      this.timer = setTimeout(() => this.step(target), TYPE_MS);
    }
  }
}

function sharedPrefix(a: string[], b: string[]): number {
  let i = 0;
  while (i < a.length && i < b.length && a[i] === b[i]) i++;
  return i;
}
