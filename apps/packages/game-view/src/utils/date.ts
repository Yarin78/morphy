import type { DateParts } from './tournament';

/**
 * A date as typed in a date field: yyyy-mm-dd, or with the day or the month and day left out when
 * they're not known, yyyy-mm or yyyy. '' is no date at all.
 */
export function formatDateText(date: DateParts | undefined): string {
  if (!date?.year) return '';
  const year = String(date.year).padStart(4, '0');
  if (!date.month) return year;
  const month = String(date.month).padStart(2, '0');
  if (!date.day) return `${year}-${month}`;
  return `${year}-${month}-${String(date.day).padStart(2, '0')}`;
}

/**
 * The date in a date field's text: undefined for none, null for text that isn't a date. Besides
 * '-', a '.' or '/' is taken between the parts.
 */
export function parseDateText(text: string): DateParts | undefined | null {
  const value = text.trim();
  if (!value) return undefined;
  const match = value.match(/^(\d{1,4})(?:[-./](\d{1,2})(?:[-./](\d{1,2}))?)?$/);
  if (!match) return null;
  const year = parseInt(match[1], 10);
  const month = match[2] ? parseInt(match[2], 10) : 0;
  const day = match[3] ? parseInt(match[3], 10) : 0;
  if (year < 1 || (match[2] && (month < 1 || month > 12))) return null;
  if (match[3] && (day < 1 || day > daysInMonth(year, month))) return null;
  return { year, month, day };
}

function daysInMonth(year: number, month: number): number {
  // Not Date.UTC, which takes years 0-99 as 1900-1999
  const date = new Date(0);
  date.setUTCFullYear(year, month, 0);
  return date.getUTCDate();
}

/** What's wrong with a date field's text, if anything. */
export function dateTextError(text: string): string | undefined {
  return parseDateText(text) === null ? 'Like 2024-03-15, 2024-03 or 2024' : undefined;
}

/** A date field's text written the usual way: '2024-3' is 2024-03. Text that isn't a date is kept. */
export function normalizeDateText(text: string): string {
  const date = parseDateText(text);
  return date === null ? text : formatDateText(date);
}
