import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { ConversationUsage } from '../../models/chat.model';

/** "0%", "1.8%", "25%": one decimal below 10. */
function percent(used: number, limit: number): string {
  const p = limit > 0 ? (used / limit) * 100 : 0;
  if (p === 0) return '0%';
  return `${p < 10 ? Math.max(p, 0.1).toFixed(1) : Math.round(p)}%`;
}

const n = (v: number) => v.toLocaleString('en');

/**
 * This conversation's usage, above it: the tokens it used so far (left) and the size of its history (right), each as
 * a share of the limit the server is configured with. The numbers are in the tooltips.
 */
@Component({
  selector: 'app-usage-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span [title]="chatTitle()">
      Chat usage <strong class="num" [class]="level(chatShare())">{{ chatPercent() }}</strong>
    </span>
    <span [title]="contextTitle()">
      Context <strong class="num" [class]="level(contextShare())">{{ contextPercent() }}</strong>
    </span>
  `,
  styles: `
    :host {
      display: flex;
      justify-content: space-between;
      gap: 1rem;
      font-size: 0.8125rem;
    }
  `,
})
export class UsageBar {
  readonly usage = input.required<ConversationUsage>();

  protected readonly chatShare = computed(() => {
    const u = this.usage();
    return u.total.totalTokens / u.tokenLimit;
  });
  protected readonly contextShare = computed(() => {
    const c = this.usage().context;
    return c.historyTokens / c.tokenLimit;
  });
  protected readonly chatPercent = computed(() =>
    percent(this.usage().total.totalTokens, this.usage().tokenLimit),
  );
  protected readonly contextPercent = computed(() =>
    percent(this.usage().context.historyTokens, this.usage().context.tokenLimit),
  );

  protected readonly chatTitle = computed(() => {
    const u = this.usage();
    return (
      `${n(u.total.totalTokens)} of ${n(u.tokenLimit)} tokens used in this chat ` +
      `(your messages ${n(u.byIssuer.USER.totalTokens)}, memory summaries ${n(u.byIssuer.SYSTEM.totalTokens)}).`
    );
  });

  protected readonly contextTitle = computed(() => {
    const { historyTokens, summaryTokens, tokenLimit, memory: m } = this.usage().context;
    const parts = m.summarized
      ? `summary ~${n(summaryTokens)} + ${m.messagesSinceSummary} messages ~${n(historyTokens - summaryTokens)}`
      : `${m.messagesSinceSummary} messages`;
    return (
      `~${n(historyTokens)} of ${n(tokenLimit)} tokens of history go with your next message (${parts}; estimated).\n` +
      `Above ${m.summarizeAfter} messages the older ones are summarized (the newest ${m.keepRecent} stay).`
    );
  });

  /** Muted text normally; amber from 75%, red from 90%. */
  protected level(share: number): string {
    return share >= 0.9 ? 'text-danger' : share >= 0.75 ? 'text-warning-emphasis' : 'text-body';
  }
}
