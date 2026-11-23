import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { VersionSummary } from '../../models/document.model';

/** All versions of a document, newest first, with the period each one was in force. */
@Component({
  selector: 'app-version-timeline',
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol class="list-unstyled mb-0 timeline">
      @for (v of versions(); track v.versionNo) {
        <li class="item" [class.selected]="v.versionNo === selected()">
          <div class="d-flex flex-wrap align-items-center gap-2">
            <span class="fw-semibold">Version {{ v.versionNo }}</span>
            @if (!v.effectiveTo) {
              <span class="badge bg-success-subtle text-success-emphasis border border-success-subtle">Current</span>
            }
            @if (v.versionNo === selected()) {
              <span class="badge bg-primary-subtle text-primary-emphasis border border-primary-subtle">Showing</span>
            }
          </div>
          <div class="text-body-secondary">
            <time [attr.datetime]="v.effectiveFrom">{{ v.effectiveFrom | date: 'mediumDate' }}</time>
            –
            @if (v.effectiveTo) {
              <time [attr.datetime]="v.effectiveTo">{{ v.effectiveTo | date: 'mediumDate' }}</time>
            } @else {
              today
            }
          </div>
          <p class="mb-0 mt-1">{{ v.summary }}</p>
        </li>
      }
    </ol>
  `,
  styleUrl: './version-timeline.scss',
})
export class VersionTimeline {
  readonly versions = input.required<VersionSummary[]>();
  readonly selected = input.required<number>();
}
