import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface NameDialogData {
  title: string;
  label: string;
  initial?: string;
  confirmLabel?: string;
  maxLength?: number;
}

/** Asks for a single name (create/rename household). Closes with the trimmed name. */
@Component({
  selector: 'app-name-dialog',
  imports: [MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <mat-dialog-content>
        <mat-form-field class="full-width">
          <mat-label>{{ data.label }}</mat-label>
          <input matInput formControlName="name" [maxlength]="max" cdkFocusInitial />
          <mat-hint align="end">{{ form.controls.name.value.length }} / {{ max }}</mat-hint>
          @if (form.controls.name.hasError('required')) {
            <mat-error>Please enter a name.</mat-error>
          }
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancel</button>
        <button mat-flat-button type="submit" [disabled]="form.invalid">
          {{ data.confirmLabel ?? 'Save' }}
        </button>
      </mat-dialog-actions>
    </form>
  `,
})
export class NameDialog {
  readonly data = inject<NameDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<NameDialog, string>>(MatDialogRef);
  readonly max = this.data.maxLength ?? 100;
  readonly form = inject(NonNullableFormBuilder).group({
    name: [this.data.initial ?? '', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(this.max)]],
  });

  submit(): void {
    if (this.form.valid) {
      this.ref.close(this.form.controls.name.value.trim());
    }
  }
}
