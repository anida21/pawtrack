import { Pipe, PipeTransform } from '@angular/core';
import { Sex, Species } from '../core/api/models';

/** Record<Species, …> keeps this list in sync with the contract enum at compile time. */
export const SPECIES_INFO: Record<Species, { label: string; icon: string }> = {
  DOG: { label: 'Dog', icon: 'sound_detection_dog_barking' },
  CAT: { label: 'Cat', icon: 'pets' },
  RABBIT: { label: 'Rabbit', icon: 'cruelty_free' },
  BIRD: { label: 'Bird', icon: 'flutter_dash' },
  FISH: { label: 'Fish', icon: 'set_meal' },
  REPTILE: { label: 'Reptile', icon: 'emoji_nature' },
  RODENT: { label: 'Rodent', icon: 'pest_control_rodent' },
  OTHER: { label: 'Other', icon: 'pets' },
};

export const SPECIES = Object.keys(SPECIES_INFO) as Species[];

export const SEX_LABELS: Record<Sex, string> = { MALE: 'Male', FEMALE: 'Female', UNKNOWN: 'Unknown' };
export const SEXES = Object.keys(SEX_LABELS) as Sex[];

export function isSpecies(value: unknown): value is Species {
  return typeof value === 'string' && value in SPECIES_INFO;
}

/** "3 yrs 2 mos", "5 mos", "< 1 mo". */
export function formatAge(months: number | undefined | null): string {
  if (months === undefined || months === null || months < 0) {
    return '';
  }
  if (months < 1) {
    return '< 1 mo';
  }
  const years = Math.floor(months / 12);
  const rest = months % 12;
  const parts: string[] = [];
  if (years) parts.push(`${years} ${years === 1 ? 'yr' : 'yrs'}`);
  if (rest) parts.push(`${rest} ${rest === 1 ? 'mo' : 'mos'}`);
  return parts.join(' ');
}

@Pipe({ name: 'species' })
export class SpeciesPipe implements PipeTransform {
  transform(value: Species | undefined, what: 'label' | 'icon' = 'label'): string {
    return value ? SPECIES_INFO[value][what] : '';
  }
}

@Pipe({ name: 'age' })
export class AgePipe implements PipeTransform {
  transform(months: number | undefined | null): string {
    return formatAge(months);
  }
}
