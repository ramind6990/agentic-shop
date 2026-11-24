import { Directive, ElementRef, afterEveryRender, inject, input } from '@angular/core';

import { LIST_STATE } from '../../data/list-query';

/**
 * Binds a filter input or select to one URL parameter of the page's list state:
 *   <select appFilter="status">…</select>
 * The element shows the value from the URL; on change it writes the new value back through the store.
 * Requires `provideListState(...)` on the page.
 */
@Directive({
  selector: 'input[appFilter], select[appFilter]',
  host: { '(change)': 'onChange()', '(keydown.enter)': 'onEnter($event)' },
})
export class FilterControl {
  readonly key = input.required<string>({ alias: 'appFilter' });

  private readonly list = inject(LIST_STATE);
  private readonly element = inject<ElementRef<HTMLInputElement | HTMLSelectElement>>(ElementRef);

  constructor() {
    // Runs after every render, so a <select> also picks up its value once its options have loaded.
    // A focused field is left alone so typing is never overwritten.
    afterEveryRender(() => {
      const el = this.element.nativeElement;
      const value = this.list.filters()[this.key()] ?? '';
      if (el.value !== value && el.ownerDocument.activeElement !== el) {
        el.value = value;
      }
    });
  }

  /** Enter applies the filter right away and stops the browser from submitting (and reloading) the form. */
  protected onEnter(event: Event): void {
    event.preventDefault();
    this.onChange();
  }

  protected onChange(): void {
    this.list.setFilter(this.key(), this.element.nativeElement.value);
  }
}
