/** Human label for a backend enum value: PARTIALLY_REFUNDED becomes "Partially refunded". */
export function enumLabel(value: string): string {
  const text = value.replaceAll('_', ' ').toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export interface SelectOption {
  value: string;
  label: string;
}

/** Dropdown options for enum values, labelled with enumLabel. */
export function enumOptions(values: readonly string[]): SelectOption[] {
  return values.map((value) => ({ value, label: enumLabel(value) }));
}
