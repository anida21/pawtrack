import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { finalize } from 'rxjs';
import { HealthService } from '../../core/api/health.service';
import { Medication, MedicationFrequency, MedicationRequest } from '../../core/api/models';
import { toApiError } from '../../core/errors/api-error';
import { FREQUENCIES, FREQUENCY_LABELS, HEALTH_ERRORS, compact, notBefore, todayIso } from './health-utils';
import { RecordDialogData } from './vaccination-dialog';

@Component({
  selector: 'app-medication-dialog',
  imports: [MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ data.record ? 'Edit medication' : 'Add medication' }}</h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content class="form-grid">
        <mat-form-field>
          <mat-label>Medication</mat-label>
          <input matInput formControlName="name" maxlength="100" cdkFocusInitial />
          @if (form.controls.name.invalid) {
            <mat-error>Please enter the medication.</mat-error>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>Dosage</mat-label>
          <input matInput formControlName="dosage" maxlength="60" placeholder="e.g. 1 tablet" />
          @if (form.controls.dosage.invalid) {
            <mat-error>Please enter the dosage.</mat-error>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>How often</mat-label>
          <mat-select formControlName="frequency">
            @for (f of frequencies; track f) {
              <mat-option [value]="f">{{ labels[f] }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Time of day</mat-label>
          <input matInput type="time" formControlName="timeOfDay" />
          <mat-hint>Reminder time (optional)</mat-hint>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Start</mat-label>
          <input matInput type="date" formControlName="startDate" />
          @if (form.controls.startDate.invalid) {
            <mat-error>Please enter a date.</mat-error>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>End</mat-label>
          <input matInput type="date" formControlName="endDate" [min]="form.controls.startDate.value" />
          <mat-hint>Empty = ongoing</mat-hint>
        </mat-form-field>
        @if (form.hasError('order')) {
          <p class="field-error span-2" role="alert">The end date cannot be before the start date.</p>
        }
        <mat-form-field class="span-2">
          <mat-label>Notes</mat-label>
          <textarea matInput formControlName="notes" rows="3" maxlength="2000"></textarea>
        </mat-form-field>
        @if (serverError(); as message) {
          <p class="server-error span-2" role="alert">{{ message }}</p>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancel</button>
        <button mat-flat-button type="submit" [disabled]="saving()">{{ saving() ? 'Saving…' : 'Save' }}</button>
      </mat-dialog-actions>
    </form>
  `,
})
export class MedicationDialog {
  protected readonly data = inject<RecordDialogData<Medication>>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<MedicationDialog, Medication>>(MatDialogRef);
  private readonly health = inject(HealthService);
  protected readonly frequencies = FREQUENCIES;
  protected readonly labels = FREQUENCY_LABELS;
  protected readonly saving = signal(false);
  protected readonly serverError = signal<string | null>(null);

  private readonly record = this.data.record;
  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      name: [this.record?.name ?? '', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]],
      dosage: [this.record?.dosage ?? '', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(60)]],
      frequency: [this.record?.frequency ?? ('DAILY' as MedicationFrequency), Validators.required],
      timeOfDay: [this.record?.timeOfDay ?? '', Validators.pattern(/^([01][0-9]|2[0-3]):[0-5][0-9]$/)],
      startDate: [this.record?.startDate ?? todayIso(), Validators.required],
      endDate: [this.record?.endDate ?? ''],
      notes: [this.record?.notes ?? '', Validators.maxLength(2000)],
    },
    { validators: notBefore('startDate', 'endDate') },
  );

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const request = compact<MedicationRequest>(this.form.getRawValue());
    const { petId } = this.data;
    this.saving.set(true);
    this.serverError.set(null);
    (this.record?.id
      ? this.health.updateMedication(petId, this.record.id, request)
      : this.health.createMedication(petId, request)
    )
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (saved) => this.ref.close(saved),
        error: (error: unknown) => this.serverError.set(toApiError(error, HEALTH_ERRORS).message),
      });
  }
}
