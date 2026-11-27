import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { ConversationUsage, TokenTotals, UserUsage } from '../../models/chat.model';

const COMPACT = new Intl.NumberFormat('en', { notation: 'compact', maximumFractionDigits: 1 });

/** "12.4k" for 12 400 tokens. */
function tokens(n: number): string {
  return COMPACT.format(n);
}

function breakdown(t: TokenTotals): string {
  return (
    `${t.calls} model calls: ${t.inputTokens.toLocaleString('en')} input ` +
    `(${t.cachedInputTokens.toLocaleString('en')} cached), ${t.outputTokens.toLocaleString('en')} output ` +
    `(${t.reasoningTokens.toLocaleString('en')} reasoning)`
  );
}

/**
 * Token usage above the conversation: this chat's tokens (the user's own requests and the system's work, such as
 * summarizing the memory), how full its context is (the last model call's input, and the memory: messages kept word
 * for word until the next summary) and the user's tokens this month. Details are in the tooltips.
 */
@Component({
  selector: 'app-usage-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (chat(); as c) {
      <span [title]="chatTitle()">
        This chat <strong class="num">{{ fmt(c.total.totalTokens) }}</strong> tokens
        <span class="text-body-secondary">
          (you {{ fmt(c.byIssuer.USER.totalTokens) }} · system
          {{ fmt(c.byIssuer.SYSTEM.totalTokens) }})
        </span>
      </span>
      <span [title]="contextTitle()">
        Context
        <strong class="num">{{
          c.context.lastCallInputTokens === null ? '–' : fmt(c.context.lastCallInputTokens)
        }}</strong>
        <span class="text-body-secondary">
          · memory {{ c.context.memory.messagesSinceSummary }}/{{
            c.context.memory.summarizeAfter
          }}
          msgs{{ c.context.memory.summarized ? ' + summary' : '' }}
        </span>
      </span>
    }
    @if (me(); as m) {
      <span class="ms-md-auto" [title]="meTitle()">
        This month <strong class="num">{{ fmt(m.total.totalTokens) }}</strong> tokens
      </span>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      column-gap: 1.25rem;
      row-gap: 0.15rem;
      font-size: 0.8125rem;
    }
  `,
})
export class UsageBar {
  readonly chat = input<ConversationUsage | null>(null);
  readonly me = input<UserUsage | null>(null);

  protected readonly fmt = tokens;

  protected readonly chatTitle = computed(() => {
    const c = this.chat();
    if (!c) return '';
    return `Your requests: ${breakdown(c.byIssuer.USER)}\nSystem (memory summaries): ${breakdown(c.byIssuer.SYSTEM)}`;
  });

  protected readonly contextTitle = computed(() => {
    const c = this.chat();
    if (!c) return '';
    const m = c.context.memory;
    const last =
      c.context.lastCallInputTokens === null
        ? 'No model call yet.'
        : `The last model call read ${c.context.lastCallInputTokens.toLocaleString('en')} tokens ` +
          '(instructions, tools, summary, earlier messages and that request).';
    const summary = m.summarized
      ? `Summary: ${m.summaryChars} of ${m.maxSummaryChars} characters (the chat closes above ${m.hardLimitChars}).`
      : 'No summary yet.';
    return (
      `${last}\n${m.messagesSinceSummary} messages are kept word for word; ` +
      `above ${m.summarizeAfter} the older ones are summarized (the newest ${m.keepRecent} stay).\n${summary}`
    );
  });

  protected readonly meTitle = computed(() => {
    const m = this.me();
    if (!m) return '';
    return `You: ${breakdown(m.byIssuer.USER)}\nSystem: ${breakdown(m.byIssuer.SYSTEM)}`;
  });
}
