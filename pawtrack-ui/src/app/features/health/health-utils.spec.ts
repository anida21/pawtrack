import { FormControl, FormGroup } from '@angular/forms';
import { Vaccination } from '../../core/api/models';
import { rangeStart, vaccinationDue } from './health-section';
import { compact, fromLocalDateTime, notBefore, notInFuture, toLocalDateTime, todayIso } from './health-utils';
import { niceTicks } from './weight-chart';

const vac = (vaccine: string, administeredOn: string, nextDueDate?: string): Vaccination => ({
  id: `${vaccine}-${administeredOn}`,
  vaccine,
  administeredOn,
  nextDueDate,
});

describe('health utils', () => {
  it('formats local dates', () => {
    expect(todayIso(new Date(2026, 0, 5))).toBe('2026-01-05');
  });

  it('round-trips datetime-local values', () => {
    const local = '2026-03-04T09:30';
    expect(toLocalDateTime(fromLocalDateTime(local))).toBe(local);
    expect(toLocalDateTime(undefined)).toBe('');
    expect(toLocalDateTime('nonsense')).toBe('');
  });

  it('rejects future dates and wrong order', () => {
    expect(notInFuture(new FormControl('2999-01-01'))).toEqual({ future: true });
    expect(notInFuture(new FormControl('2000-01-01'))).toBeNull();
    expect(notInFuture(new FormControl(''))).toBeNull();

    const group = new FormGroup({ start: new FormControl('2026-05-01'), end: new FormControl('2026-04-01') });
    expect(notBefore('start', 'end')(group)).toEqual({ order: true });
    group.controls.end.setValue('');
    expect(notBefore('start', 'end')(group)).toBeNull();
  });

  it('compacts optional fields', () => {
    expect(compact({ a: ' x ', b: '', c: undefined, d: 0, e: false, f: null })).toEqual({ a: 'x', d: 0, e: false });
  });

  it('computes weight range starts', () => {
    expect(rangeStart('3m', '2026-11-07')).toBe('2026-08-07');
    expect(rangeStart('1y', '2026-11-07')).toBe('2025-11-07');
    expect(rangeStart('all', '2026-11-07')).toBeUndefined();
  });

  it('builds rounded axis ticks', () => {
    expect(niceTicks(4.2, 5.9)).toEqual([4, 4.5, 5, 5.5, 6]);
    const flat = niceTicks(10, 10);
    expect(flat[0]).toBeLessThan(10);
    expect(flat[flat.length - 1]).toBeGreaterThan(10);
  });
});

describe('vaccinationDue', () => {
  const today = '2026-11-07';

  it('marks overdue, due soon (≤30 days) and planned', () => {
    const rabies = vac('Rabies', '2025-10-01', '2026-10-01');
    const dhpp = vac('DHPP', '2026-01-01', '2026-11-20');
    const lepto = vac('Lepto', '2026-06-01', '2027-06-01');
    const due = vaccinationDue([rabies, dhpp, lepto], today);
    expect(due.get(rabies)).toEqual({ kind: 'overdue', date: '2026-10-01' });
    expect(due.get(dhpp)?.kind).toBe('soon');
    expect(due.get(lepto)?.kind).toBe('planned');
  });

  it('only counts the latest administration of each vaccine (case-insensitive)', () => {
    const old = vac('Rabies', '2024-10-01', '2025-10-01');
    const renewed = vac('rabies ', '2025-10-02', '2026-10-02');
    const due = vaccinationDue([renewed, old], '2025-12-01');
    expect(due.get(old)).toBeNull();
    expect(due.get(renewed)?.kind).toBe('planned');
  });
});
