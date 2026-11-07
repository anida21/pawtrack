import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { finalize } from 'rxjs';
import { Pet, Reminder } from '../../core/api/models';
import { ReminderService } from '../../core/api/reminder.service';
import { toApiError } from '../../core/errors/api-error';
import { compact, fromLocalDateTime, toLocalDateTime } from '../health/health-utils';

export interface ReminderDialogData {
  pets: Pet[];
  petId?: string;
}

/** Custom reminder. The notification service emails all household members with email notifications on. */
@Component({
  selector: 'app-reminder-dialog',
  imports: [MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>New reminder</h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content class="form-grid">
        <mat-form-field class="span-2">
          <mat-label>Pet</mat-label>
          <mat-select formControlName="petId">
            @for (pet of data.pets; track pet.id) {
              <mat-option [value]="pet.id">{{ pet.name }}</mat-option>
            }
          </mat-select>
          @if (form.controls.petId.invalid) {
            <mat-error>Please choose a pet.</mat-error>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>Title</mat-label>
          <input matInput formControlName="title" maxlength="120" placeholder="e.g. Buy flea treatment" cdkFocusInitial />
          @if (form.controls.title.invalid) {
            <mat-error>Please enter a title.</mat-error>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>When</mat-label>
          <input matInput type="datetime-local" formControlName="dueAt" />
          @if (form.controls.dueAt.invalid) {
            <mat-error>Please enter date and time.</mat-error>
          }
        </mat-form-field>
        <mat-form-field class="span-2">
          <mat-label>Message</mat-label>
          <textarea matInput formControlName="message" rows="3" maxlength="1000"></textarea>
          <mat-hint>Sent by email to everyone in the pet's household who has email reminders on.</mat-hint>
        </mat-form-field>
        @if (serverError(); as message) {
          <p class="server-error span-2" role="alert">{{ message }}</p>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancel</button>
        <button mat-flat-button type="submit" [disabled]="saving()">{{ saving() ? 'Saving…' : 'Create' }}</button>
      </mat-dialog-actions>
    </form>
  `,
})
export class ReminderDialog {
  protected readonly data = inject<ReminderDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<ReminderDialog, Reminder>>(MatDialogRef);
  private readonly reminders = inject(ReminderService);
  protected readonly saving = signal(false);
  protected readonly serverError = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    petId: [this.data.petId ?? (this.data.pets.length === 1 ? this.data.pets[0].id : ''), Validators.required],
    title: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(120)]],
    message: ['', Validators.maxLength(1000)],
    dueAt: [defaultDueAt(), Validators.required],
  });

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.serverError.set(null);
    this.reminders
      .create(compact({ ...value, dueAt: fromLocalDateTime(value.dueAt) }))
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (reminder) => this.ref.close(reminder),
        error: (error: unknown) =>
          this.serverError.set(
            toApiError(error, {
              403: 'You are not a member of this pet’s household.',
              404: 'Reminders don’t know this pet yet. Try again in a moment.',
            }).message,
          ),
      });
  }
}

/** Tomorrow 09:00 local time. */
function defaultDueAt(now = new Date()): string {
  const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 9, 0);
  return toLocalDateTime(d.toISOString());
}
