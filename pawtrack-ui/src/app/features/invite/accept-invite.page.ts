import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Router, RouterLink } from '@angular/router';
import { finalize, switchMap, tap } from 'rxjs';
import { HouseholdService } from '../../core/api/household.service';
import { ApiError, toApiError } from '../../core/errors/api-error';
import { NotifyService } from '../../core/notify.service';
import { SessionStore } from '../../core/session.store';
import { ErrorState } from '../../shared/error-state';

/**
 * Landing page of invitation links (/invite/:code). Accepting is an explicit click, so following a
 * link never silently changes which households you belong to.
 */
@Component({
  selector: 'app-accept-invite-page',
  imports: [MatButtonModule, MatIconModule, RouterLink, ErrorState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (error(); as err) {
      <app-error-state [error]="err" backLink="/households" backLabel="My households" />
    } @else {
      <section class="state-card invite">
        <mat-icon class="state-icon">mail</mat-icon>
        <h2>You've been invited to a household</h2>
        <p>
          Accept to see and care for its pets together. You are signed in as
          <strong>{{ session.user()?.email }}</strong> – the invitation must have been sent to this address.
        </p>
        <div class="state-actions">
          <a mat-button routerLink="/pets">Not now</a>
          <button mat-flat-button (click)="accept()" [disabled]="accepting()">
            {{ accepting() ? 'Joining…' : 'Accept invitation' }}
          </button>
        </div>
      </section>
    }
  `,
  styles: `
    .invite {
      max-width: 560px;
      margin: 32px auto;
    }
  `,
})
export class AcceptInvitePage {
  private readonly households = inject(HouseholdService);
  protected readonly session = inject(SessionStore);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);

  readonly code = input.required<string>();
  protected readonly accepting = signal(false);
  protected readonly error = signal<ApiError | null>(null);

  protected accept(): void {
    this.accepting.set(true);
    let householdId = '';
    this.households
      .acceptInvitation(this.code())
      .pipe(
        tap((household) => {
          householdId = household.id;
          this.notify.success(`Welcome to “${household.name}”!`);
        }),
        switchMap(() => this.session.reloadHouseholds()),
        finalize(() => this.accepting.set(false)),
      )
      .subscribe({
        next: () => void this.router.navigate(['/households', householdId]),
        error: (error: unknown) =>
          this.error.set(
            toApiError(error, {
              403: `This invitation was sent to a different email address than ${this.session.user()?.email ?? 'yours'}. Sign in with the invited account to accept it.`,
              404: 'This invitation link is not valid. Check that you copied the whole link.',
              410: 'This invitation has expired or was already used. Ask the household owner for a new one.',
            }),
          ),
      });
  }
}
