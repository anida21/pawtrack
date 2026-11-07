import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { Observable } from 'rxjs';
import { signal } from '@angular/core';
import { MedicationFrequency } from '../../core/api/models';
import { ApiError, toApiError } from '../../core/errors/api-error';

/** Local calendar date as `YYYY-MM-DD` (the health service compares against UTC "today"). */
export function todayIso(now = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/** `YYYY-MM-DD` dates must not be after today. */
export const notInFuture: ValidatorFn = (control: AbstractControl<string>): ValidationErrors | null =>
  control.value && control.value > todayIso() ? { future: true } : null;

/** Cross-field rule: `later` must not be before `earlier` (both `YYYY-MM-DD`, optional). */
export function notBefore(earlier: string, later: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const a = group.get(earlier)?.value as string | undefined;
    const b = group.get(later)?.value as string | undefined;
    return a && b && b < a ? { order: true } : null;
  };
}

/** ISO instant → value for `<input type="datetime-local">` in the browser's time zone. */
export function toLocalDateTime(iso: string | undefined): string {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${todayIso(date)}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** `<input type="datetime-local">` value (local time) → ISO instant. */
export function fromLocalDateTime(value: string): string {
  return new Date(value).toISOString();
}

/** Trims strings and drops empty optional fields, so PUT (full replace) clears them. */
export function compact<T extends object>(value: T): T {
  const entries = Object.entries(value)
    .map(([key, v]) => [key, typeof v === 'string' ? v.trim() : v] as const)
    .filter(([, v]) => v !== '' && v !== null && v !== undefined);
  return Object.fromEntries(entries) as T;
}

export const FREQUENCY_LABELS: Record<MedicationFrequency, string> = {
  ONCE: 'Once',
  DAILY: 'Daily',
  TWICE_DAILY: 'Twice a day',
  WEEKLY: 'Weekly',
  MONTHLY: 'Monthly',
};
export const FREQUENCIES = Object.keys(FREQUENCY_LABELS) as MedicationFrequency[];

/** Texts for health errors: the pet rules decide access, 503 when the pet service is unreachable. */
export const HEALTH_ERRORS = {
  403: 'You no longer have access to this pet.',
  404: 'This record does not exist anymore. The list has been refreshed.',
};

/** Loading/error/items signals for one record list. */
export class RecordList<T> {
  readonly items = signal<T[]>([]);
  readonly loading = signal(true);
  readonly error = signal<ApiError | null>(null);

  constructor(private readonly source: () => Observable<T[]>) {}

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.source().subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(toApiError(error));
        this.loading.set(false);
      },
    });
  }
}
