import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { parseMarkdown } from './markdown';
import { MarkdownInline } from './markdown-inline';

/**
 * Markdown text as it comes from the assistant: paragraphs, bullet and numbered lists, headings (shown as bold lines,
 * so they never break the page's heading order), bold, italic, code and links. Rendered without innerHTML (see
 * {@link parseMarkdown}). For document bodies that only need bold, `app-rich-text` is enough.
 */
@Component({
  selector: 'app-markdown-text',
  imports: [MarkdownInline],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (b of blocks(); track $index) {
      @switch (b.kind) {
        @case ('heading') {
          <p class="md-heading"><app-md-inline [nodes]="b.content" /></p>
        }
        @case ('bullets') {
          <ul>
            @for (item of b.items; track $index) {
              <li><app-md-inline [nodes]="item" /></li>
            }
          </ul>
        }
        @case ('numbers') {
          <ol [start]="b.start">
            @for (item of b.items; track $index) {
              <li><app-md-inline [nodes]="item" /></li>
            }
          </ol>
        }
        @case ('paragraph') {
          <p>
            @for (line of b.lines; track $index; let first = $first) {
              @if (!first) {
                <br />
              }
              <app-md-inline [nodes]="line" />
            }
          </p>
        }
      }
    }
  `,
  styles: `
    :host {
      display: block;
      overflow-wrap: anywhere;
    }

    p,
    ul,
    ol {
      margin-bottom: 0.5rem;
    }

    ul,
    ol {
      padding-left: 1.25rem;
    }

    li + li {
      margin-top: 0.15rem;
    }

    .md-heading {
      font-weight: 600;
    }

    :host > :last-child {
      margin-bottom: 0;
    }
  `,
})
export class MarkdownText {
  readonly text = input.required<string>();

  protected readonly blocks = computed(() => parseMarkdown(this.text()));
}
