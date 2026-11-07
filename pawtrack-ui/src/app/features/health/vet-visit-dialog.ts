import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { finalize } from 'rxjs';
import { HealthService } from '../../core/api/health.service';
import { VetVisit, VetVisitRequest } from '../../core/api/models';
import { toApiError } from '../../core/errors/api-error';
import { HEALTH_ERRORS, compact, fromLocalDateTime, toLocalDateTime } from './health-utils';
import { RecordDialogData } from './vaccination-dialog';

@Component({
  selector: 'app-vet-visit-dialog',
  imports: [MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ data.record ? 'Edit vet visit' : 'Add vet visit' }}</h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content class="form-grid">
        <mat-form-field>
          <mat-label>Date and time</mat-label>
          <input matInput type="datetime-local" formControlName="visitDate" cdkFocusInitial />
          <mat-hint>Future dates are planned visits</mat-hint>
          @if (form.controls.visitDate.invalid) {
            <mat-error>Please enter the date.</mat-error>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>Reason</mat-label>
          <input matInput formControlName="reason" maxlength="200" placeholder="e.g. Annual check-up" />
          @if (form.controls.reason.invalid) {
            <mat-error>Please enter a reason.</mat-error>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>Clinic</mat-label>
          <input matInput formControlName="clinic" maxlength="120" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Vet</mat-label>
          <input matInput formControlName="vetName" maxlength="100" />
        </mat-form-field>
        <mat-form-field class="span-2">
          <mat-label>Diagnosis</mat-label>
          <textarea matInput formControlName="diagnosis" rows="2" maxlength="2000"></textarea>
        </mat-form-field>
        <mat-form-field class="span-2">
          <mat-label>Treatment</mat-label>
          <textarea matInput formControlName="treatment" rows="2" maxlength="2000"></textarea>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Cost</mat-label>
          <input matInput type="number" formControlName="cost" min="0" step="0.01" inputmode="decimal" />
          @if (form.controls.cost.invalid) {
            <mat-error>Enter an amount of 0 or more.</mat-error>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>Currency</mat-label>
          <input matInput formControlName="currency" maxlength="3" />
          @if (form.controls.currency.invalid) {
            <mat-error>Three capital letters, e.g. BAM or EUR.</mat-error>
          }
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
export class VetVisitDialog {
  protected readonly data = inject<RecordDialogData<VetVisit>>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<VetVisitDialog, VetVisit>>(MatDialogRef);
  private readonly health = inject(HealthService);
  protected readonly saving = signal(false);
  protected readonly serverError = signal<string | null>(null);

  private readonly record = this.data.record;
  protected readonly form = inject(NonNullableFormBuilder).group({
    visitDate: [toLocalDateTime(this.record?.visitDate) || toLocalDateTime(new Date().toISOString()), Validators.required],
    reason: [this.record?.reason ?? '', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(200)]],
    clinic: [this.record?.clinic ?? '', Validators.maxLength(120)],
    vetName: [this.record?.vetName ?? '', Validators.maxLength(100)],
    diagnosis: [this.record?.diagnosis ?? '', Validators.maxLength(2000)],
    treatment: [this.record?.treatment ?? '', Validators.maxLength(2000)],
    cost: [this.record?.cost ?? (null as number | null), [Validators.min(0), Validators.max(1_000_000_000)]],
    currency: [this.record?.currency ?? 'BAM', Validators.pattern(/^[A-Z]{3}$/)],
  });

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    const { cost, ...rest } = value;
    // `currency` has a default in the contract, so the generated type requires it; always send it.
    const request = compact<VetVisitRequest>({
      ...rest,
      visitDate: fromLocalDateTime(value.visitDate),
      ...(cost === null ? {} : { cost }),
    });
    const { petId } = this.data;
    this.saving.set(true);
    this.serverError.set(null);
    (this.record?.id
      ? this.health.updateVetVisit(petId, this.record.id, request)
      : this.health.createVetVisit(petId, request)
    )
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (saved) => this.ref.close(saved),
        error: (error: unknown) => this.serverError.set(toApiError(error, HEALTH_ERRORS).message),
      });
  }
}
