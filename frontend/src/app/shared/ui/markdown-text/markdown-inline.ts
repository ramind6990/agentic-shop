import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { MdInline } from './markdown';

/** Inline markdown nodes (text, bold, italic, code, links), rendered recursively. */
@Component({
  selector: 'app-md-inline',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // One line on purpose (and kept so by prettier-ignore): line breaks around a text node would render as stray
  // spaces, e.g. before the comma in "**bold**, text".
  // prettier-ignore
  template: `@for (n of nodes(); track $index) {@switch (n.kind) {@case ('strong') {<strong><app-md-inline [nodes]="n.children" /></strong>} @case ('em') {<em><app-md-inline [nodes]="n.children" /></em>} @case ('code') {<code>{{ n.text }}</code>} @case ('link') {<a [href]="n.href" target="_blank" rel="noopener noreferrer"><app-md-inline [nodes]="n.children" /></a>} @default {{{ n.text }}}}}`,
  styles: `
    :host {
      display: contents;
    }
  `,
})
export class MarkdownInline {
  readonly nodes = input.required<MdInline[]>();
}
