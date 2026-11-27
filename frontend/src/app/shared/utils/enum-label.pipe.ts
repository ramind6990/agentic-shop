import { Pipe, PipeTransform } from '@angular/core';

import { enumLabel } from './enum-label';

/** `{{ 'NOT_AS_DESCRIBED' | enumLabel }}` → "Not as described". */
@Pipe({ name: 'enumLabel' })
export class EnumLabelPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return value ? enumLabel(value) : '';
  }
}
