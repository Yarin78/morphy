import { describe, expect, it } from 'vitest';
import { dateTag, dateTextError, formatDateTag, formatDateText, parseDateTag, parseDateText } from './date';

describe('date tags', () => {
  it('writes unknown parts as question marks', () => {
    expect(formatDateTag({ year: 2013, month: 11, day: 21 })).toBe('2013.11.21');
    expect(formatDateTag({ year: 1886, month: 0, day: 0 })).toBe('1886.??.??');
    expect(formatDateTag(undefined)).toBe('????.??.??');
  });

  it('leaves out a date of which nothing is known', () => {
    expect(dateTag(undefined)).toBe('');
    expect(dateTag({ year: 0, month: 0, day: 0 })).toBe('');
    expect(dateTag({ year: 2013, month: 3, day: 0 })).toBe('2013.03.??');
  });

  it('reads what is known', () => {
    expect(parseDateTag('2013.11.21')).toEqual({ year: 2013, month: 11, day: 21 });
    expect(parseDateTag('2013.??.??')).toEqual({ year: 2013, month: 0, day: 0 });
    expect(parseDateTag('????.??.??')).toBeUndefined();
    expect(parseDateTag('')).toBeUndefined();
  });
});

describe('date text', () => {
  it('writes as much as is known', () => {
    expect(formatDateText({ year: 2013, month: 11, day: 21 })).toBe('2013-11-21');
    expect(formatDateText({ year: 2013, month: 3, day: 0 })).toBe('2013-03');
    expect(formatDateText({ year: 1886, month: 0, day: 0 })).toBe('1886');
    expect(formatDateText({ year: 0, month: 5, day: 0 })).toBe('');
    expect(formatDateText(undefined)).toBe('');
  });

  it('reads a year, a month or a whole date, with any separator', () => {
    expect(parseDateText('2013-11-21')).toEqual({ year: 2013, month: 11, day: 21 });
    expect(parseDateText(' 2013.3 ')).toEqual({ year: 2013, month: 3, day: 0 });
    expect(parseDateText('2013/03/01')).toEqual({ year: 2013, month: 3, day: 1 });
    expect(parseDateText('1886')).toEqual({ year: 1886, month: 0, day: 0 });
    expect(parseDateText('')).toBeUndefined();
  });

  it('turns down dates that do not exist', () => {
    expect(parseDateText('2013-02-30')).toBeNull();
    expect(parseDateText('2013-13')).toBeNull();
    expect(parseDateText('2013-00-01')).toBeNull();
    expect(parseDateText('0')).toBeNull();
    expect(parseDateText('13-11-21x')).toBeNull();
    expect(dateTextError('2013-02-30')).toBeDefined();
    expect(dateTextError('2013-02')).toBeUndefined();
  });

  it('knows leap years, in any century', () => {
    expect(parseDateText('2024-02-29')).not.toBeNull();
    expect(parseDateText('1900-02-29')).toBeNull();
    expect(parseDateText('2000-02-29')).not.toBeNull();
    // Not taken as 1904, which Date.UTC would
    expect(parseDateText('4-02-29')).not.toBeNull();
    expect(parseDateText('5-02-29')).toBeNull();
  });
});
