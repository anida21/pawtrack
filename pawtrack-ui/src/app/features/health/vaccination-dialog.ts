import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { finalize } from 'rxjs';
import { HealthService } from '../../core/api/health.service';
import { Vaccination, VaccinationRequest } from '../../core/api/models';
import { toApiError } from '../../core/errors/api-error';
import { HEALTH_ERRORS, compact, notBefore, notInFuture, todayIso } from './health-utils';

export interface RecordDialogData<T> {
  petId: string;
  record?: T;
}

@Component({
  selector: 'app-vaccination-dialog',
  imports: [MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ data.record ? 'Edit vaccination' : 'Add vaccination' }}</h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content class="form-grid">
        <mat-form-field class="span-2">
          <mat-label>Vaccine</mat-label>
          <input matInput formControlName="vaccine" maxlength="100" placeholder="e.g. Rabies" cdkFocusInitial />
          @if (form.controls.vaccine.invalid) {
            <mat-error>Please enter the vaccine.</mat-error>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>Given on</mat-label>
          <input matInput type="date" formControlName="administeredOn" [max]="today" />
          @if (form.controls.administeredOn.hasError('future')) {
            <mat-error>Cannot be in the future.</mat-error>
          } @else if (form.controls.administeredOn.invalid) {
            <mat-error>Please enter a date.</mat-error>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>Next due</mat-label>
          <input matInput type="date" formControlName="nextDueDate" [min]="form.controls.administeredOn.value" />
          <mat-hint>You'll get a reminder</mat-hint>
        </mat-form-field>
        @if (form.hasError('order')) {
          <p class="field-error span-2" role="alert">The next due date cannot be before the vaccination date.</p>
        }
        <mat-form-field>
          <mat-label>Clinic</mat-label>
          <input matInput formControlName="clinic" maxlength="120" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Batch number</mat-label>
          <input matInput formControlName="batchNumber" maxlength="50" />
        </mat-form-field>
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
export class VaccinationDialog {
  protected readonly data = inject<RecordDialogData<Vaccination>>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<VaccinationDialog, Vaccination>>(MatDialogRef);
  private readonly health = inject(HealthService);
  protected readonly today = todayIso();
  protected readonly saving = signal(false);
  protected readonly serverError = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      vaccine: [this.data.record?.vaccine ?? '', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]],
      administeredOn: [this.data.record?.administeredOn ?? this.today, [Validators.required, notInFuture]],
      nextDueDate: [this.data.record?.nextDueDate ?? ''],
      batchNumber: [this.data.record?.batchNumber ?? '', Validators.maxLength(50)],
      clinic: [this.data.record?.clinic ?? '', Validators.maxLength(120)],
      notes: [this.data.record?.notes ?? '', Validators.maxLength(2000)],
    },
    { validators: notBefore('administeredOn', 'nextDueDate') },
  );

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const request = compact<VaccinationRequest>(this.form.getRawValue());
    const { petId, record } = this.data;
    this.saving.set(true);
    this.serverError.set(null);
    (record?.id
      ? this.health.updateVaccination(petId, record.id, request)
      : this.health.createVaccination(petId, request)
    )
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (saved) => this.ref.close(saved),
        error: (error: unknown) => this.serverError.set(toApiError(error, HEALTH_ERRORS).message),
      });
  }
}
