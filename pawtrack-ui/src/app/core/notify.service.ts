import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ErrorMessages, toApiError } from './errors/api-error';

@Injectable({ providedIn: 'root' })
export class NotifyService {
  private readonly snackBar = inject(MatSnackBar);

  success(message: string): void {
    this.snackBar.open(message, undefined, { duration: 3000, panelClass: 'snack-success' });
  }

  /** A plain error text (e.g. client-side validation). */
  warn(message: string): void {
    this.snackBar.open(message, 'Dismiss', { duration: 6000, panelClass: 'snack-error' });
  }

  /** Shows a ProblemDetail-aware message for any HTTP error. */
  error(error: unknown, messages?: ErrorMessages): void {
    const apiError = toApiError(error, messages);
    this.snackBar.open(apiError.message, 'Dismiss', { duration: 6000, panelClass: 'snack-error' });
  }
}
