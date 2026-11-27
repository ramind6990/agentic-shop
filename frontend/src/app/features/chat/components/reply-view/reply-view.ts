import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { MarkdownText } from '../../../../shared/ui/markdown-text/markdown-text';
import { ReplyEnvelope } from '../../models/chat.model';
import { ReplyBlock } from '../blocks/reply-block';

/** The assistant's reply: its text (markdown), then its block as cards, then the suggested replies. */
@Component({
  selector: 'app-reply-view',
  imports: [MarkdownText, ReplyBlock],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-flex flex-column gap-2' },
  template: `
    <app-markdown-text
      [text]="reply().text"
      [class.text-body-secondary]="reply().status === 'REFUSED'"
    />
    @if (reply().block; as block) {
      <app-reply-block [block]="block" />
    }
    @if (showSuggestions() && reply().suggestedReplies.length > 0) {
      <div class="d-flex flex-wrap gap-2" role="group" aria-label="Suggested replies">
        @for (s of reply().suggestedReplies; track s.label) {
          <button
            type="button"
            class="btn btn-sm btn-outline-primary rounded-pill"
            [title]="s.text"
            [disabled]="suggestionsDisabled()"
            (click)="suggest.emit(s.text)"
          >
            {{ s.label }}
          </button>
        }
      </div>
    }
  `,
})
export class ReplyView {
  readonly reply = input.required<ReplyEnvelope>();
  /** Only the latest reply offers its suggestions. */
  readonly showSuggestions = input(false);
  readonly suggestionsDisabled = input(false);
  readonly suggest = output<string>();
}
