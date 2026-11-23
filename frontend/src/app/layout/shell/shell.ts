import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { SideNav } from '../side-nav/side-nav';
import { TopBar } from '../top-bar/top-bar';

/** Application frame: top bar, left navigation and the routed page. Layout rules live in styles/_layout.scss. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, TopBar, SideNav],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="app-shell">
      <header class="app-topbar"><app-top-bar /></header>
      <aside class="app-sidenav"><app-side-nav /></aside>
      <main class="app-content"><router-outlet /></main>
    </div>
  `,
})
export class Shell {}
