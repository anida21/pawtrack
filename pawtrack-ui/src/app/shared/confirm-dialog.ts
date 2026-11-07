import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { Observable, map } from 'rxjs';

export interface ConfirmData {
  title: string;
  message: string;
  confirmLabel?: string;
  danger?: boolean;
}

@Component({
  selector: 'app-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>{{ data.message }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">Cancel</button>
      <button
        mat-flat-button
        [class.danger]="data.danger"
        [mat-dialog-close]="true"
        cdkFocusInitial
      >
        {{ data.confirmLabel ?? 'Confirm' }}
      </button>
    </mat-dialog-actions>
  `,
})
export class ConfirmDialog {
  readonly data = inject<ConfirmData>(MAT_DIALOG_DATA);
}

/** Opens the dialog and emits true only when the user confirmed. */
export function confirm(dialog: MatDialog, data: ConfirmData): Observable<boolean> {
  return dialog
    .open<ConfirmDialog, ConfirmData, boolean>(ConfirmDialog, { data, width: '420px' })
    .afterClosed()
    .pipe(map((result) => result === true));
}
