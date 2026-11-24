import { Directive, TemplateRef, ViewContainerRef, effect, inject, input } from '@angular/core';

import { Permission } from './auth.models';
import { AuthStore } from './auth.store';

/**
 * Renders its element only when the signed-in user has the permission:
 * `<button *appCan="'FLAGS_READ'">…</button>`.
 */
@Directive({ selector: '[appCan]' })
export class Can {
  private readonly auth = inject(AuthStore);
  private readonly template = inject(TemplateRef);
  private readonly container = inject(ViewContainerRef);

  readonly appCan = input.required<Permission>();

  constructor() {
    effect(() => {
      const allowed = this.auth.can(this.appCan());
      this.container.clear();
      if (allowed) {
        this.container.createEmbeddedView(this.template);
      }
    });
  }
}
