import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

interface Segment {
  text: string;
  bold: boolean;
}

/**
 * Minimal markdown for document bodies: keeps line breaks and renders **bold**. Everything else
 * shows as plain text. Built without innerHTML, so document text can never inject markup.
 * Swap for a full markdown renderer if tables or lists are needed later.
 */
@Component({
  selector: 'app-rich-text',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- one line on purpose: line breaks between segments would render as stray spaces -->
    <p class="mb-0 rich-text">@for (s of segments(); track $index) {@if (s.bold) {<strong>{{ s.text }}</strong>} @else {{{ s.text }}}}</p>
  `,
  styles: `
    .rich-text {
      white-space: pre-line;
    }
  `,
})
export class RichText {
  readonly text = input.required<string>();

  /** Odd parts of a split on "**" are the bold ones. */
  protected readonly segments = computed<Segment[]>(() =>
    this.text()
      .split('**')
      .map((text, i) => ({ text, bold: i % 2 === 1 }))
      .filter((s) => s.text.length > 0),
  );
}
