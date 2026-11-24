import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthStore } from '../../../core/auth/auth.store';

/**
 * A link to another screen that the user may not be allowed to open (a customer, a seller…): a router link when
 * they can open it, plain text otherwise. `<app-access-link [to]="['/customers', id]">{{ name }}</app-access-link>`
 */
@Component({
  selector: 'app-access-link',
  imports: [NgTemplateOutlet, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (allowed()) {
      <a [routerLink]="to()" [class]="linkClass()"><ng-container [ngTemplateOutlet]="content" /></a>
    } @else {
      <span [class]="linkClass()"><ng-container [ngTemplateOutlet]="content" /></span>
    }
    <ng-template #content><ng-content /></ng-template>
  `,
})
export class AccessLink {
  private readonly auth = inject(AuthStore);

  /** Router commands, e.g. ['/customers', 5]. */
  readonly to = input.required<readonly unknown[]>();
  /** Classes for the link (or the text in its place), e.g. 'font-monospace'. */
  readonly linkClass = input('');

  protected readonly allowed = computed(() => this.auth.canOpen(this.to()));
}
