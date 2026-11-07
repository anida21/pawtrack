import { initialsOf } from '../layout/shell';
import { SPECIES, formatAge, isSpecies } from './species';

describe('species helpers', () => {
  it('lists every contract species', () => {
    expect(SPECIES).toEqual(['DOG', 'CAT', 'RABBIT', 'BIRD', 'FISH', 'REPTILE', 'RODENT', 'OTHER']);
    expect(isSpecies('CAT')).toBe(true);
    expect(isSpecies('DRAGON')).toBe(false);
    expect(isSpecies(undefined)).toBe(false);
  });

  it.each([
    [undefined, ''],
    [0, '< 1 mo'],
    [1, '1 mo'],
    [5, '5 mos'],
    [12, '1 yr'],
    [26, '2 yrs 2 mos'],
  ])('formats %s months as "%s"', (months, expected) => {
    expect(formatAge(months)).toBe(expected);
  });
});

describe('initialsOf', () => {
  it.each([
    ['Alice Test', 'AT'],
    ['bob', 'B'],
    ['Anna Maria van Berg', 'AB'],
    ['  ', '?'],
  ])('%s → %s', (name, expected) => {
    expect(initialsOf(name)).toBe(expected);
  });
});
