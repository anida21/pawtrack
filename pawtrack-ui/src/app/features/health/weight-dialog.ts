import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { finalize } from 'rxjs';
import { HealthService } from '../../core/api/health.service';
import { Weight } from '../../core/api/models';
import { toApiError } from '../../core/errors/api-error';
import { HEALTH_ERRORS, notInFuture, todayIso } from './health-utils';
import { RecordDialogData } from './vaccination-dialog';

@Component({
  selector: 'app-weight-dialog',
  imports: [MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Add weight</h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content class="form-grid">
        <mat-form-field>
          <mat-label>Weight (kg)</mat-label>
          <input matInput type="number" formControlName="weightKg" min="0.01" max="200" step="0.001"
            inputmode="decimal" cdkFocusInitial />
          @if (form.controls.weightKg.invalid) {
            <mat-error>Between 0.01 and 200 kg.</mat-error>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>Measured on</mat-label>
          <input matInput type="date" formControlName="measuredOn" [max]="today" />
          @if (form.controls.measuredOn.hasError('future')) {
            <mat-error>Cannot be in the future.</mat-error>
          } @else if (form.controls.measuredOn.invalid) {
            <mat-error>Please enter a date.</mat-error>
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
export class WeightDialog {
  protected readonly data = inject<RecordDialogData<Weight>>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<WeightDialog, Weight>>(MatDialogRef);
  private readonly health = inject(HealthService);
  protected readonly today = todayIso();
  protected readonly saving = signal(false);
  protected readonly serverError = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    weightKg: [null as number | null, [Validators.required, Validators.min(0.01), Validators.max(200)]],
    measuredOn: [this.today, [Validators.required, notInFuture]],
  });

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const { weightKg, measuredOn } = this.form.getRawValue();
    this.saving.set(true);
    this.serverError.set(null);
    this.health
      .createWeight(this.data.petId, { weightKg: weightKg as number, measuredOn })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (saved) => this.ref.close(saved),
        error: (error: unknown) => this.serverError.set(toApiError(error, HEALTH_ERRORS).message),
      });
  }
}
