import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { DatePipe } from '@angular/common';
import { finalize, map, startWith } from 'rxjs';
import { MeService } from '../../core/api/me.service';
import { UpdateUserRequest } from '../../core/api/models';
import { NotifyService } from '../../core/notify.service';
import { SessionStore } from '../../core/session.store';

const TIMEZONES: string[] = (() => {
  try {
    return Intl.supportedValuesOf('timeZone');
  } catch {
    return ['UTC', 'Europe/Sarajevo', 'Europe/Berlin', 'Europe/London', 'America/New_York'];
  }
})();

@Component({
  selector: 'app-profile-page',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatSlideToggleModule,
    MatAutocompleteModule,
    DatePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="page-header">
      <h1>Profile</h1>
      <p class="subtitle">Signed in as {{ session.user()?.email }}</p>
    </header>

    <form class="surface-card profile-form" [formGroup]="form" (ngSubmit)="save()">
      <div class="form-grid">
        <mat-form-field class="span-2">
          <mat-label>Display name</mat-label>
          <input matInput formControlName="displayName" maxlength="100" autocomplete="name" />
          @if (form.controls.displayName.invalid) {
            <mat-error>Please enter a name (max. 100 characters).</mat-error>
          }
        </mat-form-field>

        <mat-form-field class="span-2">
          <mat-label>Time zone</mat-label>
          <input matInput formControlName="timezone" [matAutocomplete]="tz" />
          <mat-hint>Used for reminder times</mat-hint>
          <mat-autocomplete #tz="matAutocomplete">
            @for (zone of timezoneOptions(); track zone) {
              <mat-option [value]="zone">{{ zone }}</mat-option>
            }
          </mat-autocomplete>
          @if (form.controls.timezone.hasError('timezone')) {
            <mat-error>Pick a time zone from the list.</mat-error>
          }
        </mat-form-field>

        <mat-slide-toggle class="span-2 toggle" formControlName="emailNotifications">
          Email me reminders (vaccinations, medications, vet visits)
        </mat-slide-toggle>
      </div>

      <div class="form-actions">
        <span class="since">Member since {{ session.user()?.createdAt | date: 'longDate' }}</span>
        <button mat-button type="button" (click)="reset()" [disabled]="form.pristine || saving()">Discard</button>
        <button mat-flat-button type="submit" [disabled]="form.invalid || form.pristine || saving()">
          {{ saving() ? 'Saving…' : 'Save changes' }}
        </button>
      </div>
    </form>
  `,
  styles: `
    .profile-form {
      max-width: 640px;
    }
    .toggle {
      margin: 8px 0 16px;
    }
    .form-actions {
      align-items: center;
    }
    .since {
      margin-right: auto;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }
  `,
})
export class ProfilePage {
  protected readonly session = inject(SessionStore);
  private readonly me = inject(MeService);
  private readonly notify = inject(NotifyService);

  protected readonly saving = signal(false);
  protected readonly form = inject(NonNullableFormBuilder).group({
    displayName: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]],
    timezone: ['', [(control: { value: string }) => (control.value && !TIMEZONES.includes(control.value) ? { timezone: true } : null)]],
    emailNotifications: [true],
  });

  private readonly timezoneQuery = toSignal(
    this.form.controls.timezone.valueChanges.pipe(
      startWith(''),
      map((value) => value.toLowerCase()),
    ),
    { initialValue: '' },
  );
  protected readonly timezoneOptions = computed(() => {
    const query = this.timezoneQuery();
    return TIMEZONES.filter((zone) => zone.toLowerCase().includes(query)).slice(0, 50);
  });

  constructor() {
    this.reset();
  }

  protected reset(): void {
    const user = this.session.user();
    this.form.reset({
      displayName: user?.displayName ?? '',
      timezone: user?.timezone ?? '',
      emailNotifications: user?.emailNotifications ?? true,
    });
  }

  protected save(): void {
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    const request: UpdateUserRequest = {
      displayName: value.displayName.trim(),
      emailNotifications: value.emailNotifications,
      ...(value.timezone ? { timezone: value.timezone } : {}),
    };
    this.saving.set(true);
    this.me
      .update(request)
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (user) => {
          this.session.user.set(user);
          this.reset();
          this.notify.success('Profile saved');
        },
        error: (error: unknown) => this.notify.error(error),
      });
  }
}
