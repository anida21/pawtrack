import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { ApiError } from '../core/errors/api-error';

/** Full-page error card for load failures (403/404/410/5xx …). */
@Component({
  selector: 'app-error-state',
  imports: [MatButtonModule, MatIconModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="state-card" role="alert">
      <mat-icon class="state-icon">{{ icon() }}</mat-icon>
      <h2>{{ error().title }}</h2>
      <p>{{ error().message }}</p>
      <div class="state-actions">
        @if (retryable()) {
          <button mat-stroked-button (click)="retry.emit()"><mat-icon>refresh</mat-icon> Try again</button>
        }
        @if (backLink(); as link) {
          <a mat-flat-button [routerLink]="link">{{ backLabel() }}</a>
        }
      </div>
    </div>
  `,
})
export class ErrorState {
  readonly error = input.required<ApiError>();
  readonly backLink = input<string | null>(null);
  readonly backLabel = input('Go back');
  readonly retry = output();

  protected readonly icon = computed(() => {
    switch (this.error().status) {
      case 403:
        return 'lock';
      case 404:
        return 'search_off';
      case 410:
        return 'timer_off';
      case 0:
        return 'cloud_off';
      default:
        return 'error';
    }
  });

  protected readonly retryable = computed(() => {
    const status = this.error().status;
    return status === 0 || status >= 500;
  });
}
