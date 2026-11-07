import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ComponentType } from '@angular/cdk/portal';
import { Observable, filter, switchMap } from 'rxjs';
import { HealthService } from '../../core/api/health.service';
import { HealthSummary, Medication, Vaccination, VetVisit, Weight } from '../../core/api/models';
import { ApiError, toApiError } from '../../core/errors/api-error';
import { NotifyService } from '../../core/notify.service';
import { confirm } from '../../shared/confirm-dialog';
import { FREQUENCY_LABELS, HEALTH_ERRORS, RecordList, todayIso } from './health-utils';
import { MedicationDialog } from './medication-dialog';
import { RecordDialogData, VaccinationDialog } from './vaccination-dialog';
import { VetVisitDialog } from './vet-visit-dialog';
import { WeightChart } from './weight-chart';
import { WeightDialog } from './weight-dialog';

export type DueStatus = { kind: 'overdue' | 'soon' | 'planned'; date: string } | null;
export type WeightRange = '3m' | '1y' | 'all';

/** Due state of each vaccination; only the latest administration of a vaccine (case-insensitive) counts. */
export function vaccinationDue(vaccinations: Vaccination[], today = todayIso()): Map<Vaccination, DueStatus> {
  const latest = new Map<string, Vaccination>();
  for (const v of vaccinations) {
    const key = v.vaccine.trim().toLowerCase();
    const current = latest.get(key);
    if (!current || v.administeredOn > current.administeredOn) latest.set(key, v);
  }
  const soonLimit = addDays(today, 30);
  const result = new Map<Vaccination, DueStatus>();
  for (const v of vaccinations) {
    const due = latest.get(v.vaccine.trim().toLowerCase()) === v ? v.nextDueDate : undefined;
    result.set(
      v,
      !due ? null : { kind: due < today ? 'overdue' : due <= soonLimit ? 'soon' : 'planned', date: due },
    );
  }
  return result;
}

export function rangeStart(range: WeightRange, today = todayIso()): string | undefined {
  if (range === 'all') return undefined;
  const date = new Date(`${today}T00:00:00`);
  date.setMonth(date.getMonth() - (range === '3m' ? 3 : 12));
  return todayIso(date);
}

function addDays(iso: string, days: number): string {
  const date = new Date(`${iso}T00:00:00`);
  date.setDate(date.getDate() + days);
  return todayIso(date);
}

/** Health tab group on the pet page: summary tiles + vaccinations, vet visits, medications, weight. */
@Component({
  selector: 'app-health-section',
  imports: [
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressBarModule,
    MatSlideToggleModule,
    MatTabsModule,
    MatTooltipModule,
    WeightChart,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './health-section.html',
  styleUrl: './health-section.scss',
})
export class HealthSection {
  private readonly health = inject(HealthService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotifyService);

  readonly petId = input.required<string>();
  readonly petName = input('');

  protected readonly frequencyLabels = FREQUENCY_LABELS;
  protected readonly summary = signal<HealthSummary | null>(null);
  protected readonly summaryError = signal<ApiError | null>(null);
  protected readonly activeOnly = signal(false);
  protected readonly weightRange = signal<WeightRange>('1y');

  protected readonly vaccinations = new RecordList<Vaccination>(() => this.health.vaccinations(this.petId()));
  protected readonly vetVisits = new RecordList<VetVisit>(() => this.health.vetVisits(this.petId()));
  protected readonly medications = new RecordList<Medication>(() =>
    this.health.medications(this.petId(), this.activeOnly()),
  );
  protected readonly weights = new RecordList<Weight>(() =>
    this.health.weights(this.petId(), { from: rangeStart(this.weightRange()) }),
  );

  protected readonly due = computed(() => vaccinationDue(this.vaccinations.items()));
  protected readonly now = new Date().toISOString();
  /** Newest first for the table view next to the chart. */
  protected readonly weightsNewestFirst = computed(() => [...this.weights.items()].reverse());

  constructor() {
    effect(() => {
      this.petId();
      untracked(() => {
        this.loadSummary();
        this.vaccinations.load();
        this.vetVisits.load();
        this.medications.load();
        this.weights.load();
      });
    });
  }

  protected loadSummary(): void {
    this.summaryError.set(null);
    this.health.summary(this.petId()).subscribe({
      next: (summary) => this.summary.set(summary),
      error: (error: unknown) => this.summaryError.set(toApiError(error)),
    });
  }

  protected setActiveOnly(value: boolean): void {
    this.activeOnly.set(value);
    this.medications.load();
  }

  protected setRange(range: WeightRange): void {
    this.weightRange.set(range);
    this.weights.load();
  }

  protected edit<T>(component: ComponentType<unknown>, list: RecordList<T>, label: string, record?: T): void {
    this.dialog
      .open<unknown, RecordDialogData<T>, T>(component, {
        data: { petId: this.petId(), record },
        width: '560px',
        maxWidth: '95vw',
      })
      .afterClosed()
      .pipe(filter(Boolean))
      .subscribe(() => {
        this.notify.success(`${label} saved`);
        list.load();
        this.loadSummary();
      });
  }

  protected remove<T>(list: RecordList<T>, title: string, request: () => Observable<void>): void {
    confirm(this.dialog, { title, message: 'This cannot be undone.', confirmLabel: 'Delete', danger: true })
      .pipe(
        filter(Boolean),
        switchMap(() => request()),
      )
      .subscribe({
        next: () => {
          this.notify.success('Deleted');
          list.load();
          this.loadSummary();
        },
        error: (error: unknown) => {
          this.notify.error(error, HEALTH_ERRORS);
          list.load();
        },
      });
  }

  // Template helpers (typed per record kind).
  protected readonly VaccinationDialog = VaccinationDialog;
  protected readonly VetVisitDialog = VetVisitDialog;
  protected readonly MedicationDialog = MedicationDialog;
  protected readonly WeightDialog = WeightDialog;

  protected deleteVaccination(v: Vaccination): void {
    this.remove(this.vaccinations, `Delete ${v.vaccine} vaccination?`, () =>
      this.health.deleteVaccination(this.petId(), v.id ?? ''),
    );
  }

  protected deleteVetVisit(v: VetVisit): void {
    this.remove(this.vetVisits, 'Delete this vet visit?', () => this.health.deleteVetVisit(this.petId(), v.id ?? ''));
  }

  protected deleteMedication(m: Medication): void {
    this.remove(this.medications, `Delete ${m.name}?`, () => this.health.deleteMedication(this.petId(), m.id ?? ''));
  }
}
