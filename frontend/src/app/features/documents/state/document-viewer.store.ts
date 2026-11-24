import { Injectable, computed, inject } from '@angular/core';

import { loadOnChange, sameJson } from '../../../shared/data/load-on-change';
import {
  injectQueryParams,
  injectQueryUpdater,
  injectRouteParams,
  intParam,
} from '../../../shared/data/url-query';
import { DocumentApi } from '../data-access/document-api';

type VersionRequest = { code: string; versionNo: number } | { code: string; at: string };

/**
 * One document (/documents/:code). Which version is shown comes from the URL:
 * `?version=2` shows that version, `?at=2025-03-01` the version in force on that date, neither the current one.
 */
@Injectable()
export class DocumentViewerStore {
  private readonly api = inject(DocumentApi);
  private readonly routeParams = injectRouteParams();
  private readonly queryParams = injectQueryParams();
  private readonly updateUrl = injectQueryUpdater();

  readonly code = computed(() => this.routeParams().get('code') ?? undefined);
  readonly requestedVersion = computed(() => intParam(this.queryParams(), 'version'));
  readonly at = computed(() => {
    const at = this.queryParams().get('at') ?? '';
    return /^\d{4}-\d{2}-\d{2}$/.test(at) ? at : null;
  });

  private readonly document$ = loadOnChange(this.code, (code) => this.api.get(code));
  readonly document = this.document$.value;
  readonly loading = this.document$.loading;
  readonly error = this.document$.error;
  readonly notFound = computed(() => this.error()?.status === 404);

  /** The current version (open-ended), used when the URL names neither a version nor a date. */
  private readonly currentVersionNo = computed(() => {
    const doc = this.document();
    if (!doc || doc.code !== this.code()) {
      return undefined;
    }
    const current = doc.versions.find((v) => v.effectiveTo === null);
    return current?.versionNo ?? Math.max(...doc.versions.map((v) => v.versionNo));
  });

  private readonly versionRequest = computed<VersionRequest | undefined>(
    () => {
      const code = this.code();
      if (!code) {
        return undefined;
      }
      const at = this.at();
      if (at) {
        return { code, at };
      }
      const versionNo = this.requestedVersion() ?? this.currentVersionNo();
      return versionNo === undefined ? undefined : { code, versionNo };
    },
    { equal: sameJson },
  );
  private readonly version$ = loadOnChange(this.versionRequest, (r) =>
    'at' in r ? this.api.effective(r.code, r.at) : this.api.version(r.code, r.versionNo),
  );

  readonly version = this.version$.value;
  readonly versionLoading = this.version$.loading;
  readonly versionError = this.version$.error;

  // ---------- actions: each one only writes the URL ----------

  selectVersion(versionNo: number): void {
    this.updateUrl({ version: versionNo, at: null });
  }

  showInForceOn(date: string): void {
    this.updateUrl({ at: date, version: null });
  }

  reload(): void {
    this.document$.reload();
    this.version$.reload();
  }
}
