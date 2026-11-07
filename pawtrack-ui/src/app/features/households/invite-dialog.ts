import { Clipboard } from '@angular/cdk/clipboard';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { finalize } from 'rxjs';
import { HouseholdService } from '../../core/api/household.service';
import { Invitation, InviteRole } from '../../core/api/models';
import { ApiError, toApiError } from '../../core/errors/api-error';

export interface InviteDialogData {
  householdId: string;
  householdName: string;
}

/** Creates an invitation and shows the resulting link (the notification service also emails it). */
@Component({
  selector: 'app-invite-dialog',
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatRadioModule,
    MatIconModule,
    ReactiveFormsModule,
    DatePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Invite to {{ data.householdName }}</h2>
    @if (invitation(); as invite) {
      <mat-dialog-content>
        <p>
          Invitation for <strong>{{ invite.email }}</strong> created. They will get an email; you can also share
          this link. It works once and expires {{ invite.expiresAt | date: 'medium' }}.
        </p>
        <div class="link-box">
          <code>{{ link() }}</code>
          <button mat-icon-button (click)="copy()" aria-label="Copy invitation link">
            <mat-icon>{{ copied() ? 'check' : 'content_copy' }}</mat-icon>
          </button>
        </div>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-flat-button [mat-dialog-close]="true">Done</button>
      </mat-dialog-actions>
    } @else {
      <form [formGroup]="form" (ngSubmit)="send()">
        <mat-dialog-content>
          <mat-form-field class="full-width">
            <mat-label>Email</mat-label>
            <input matInput type="email" formControlName="email" autocomplete="email" cdkFocusInitial />
            <mat-hint>The invitation can only be accepted with this email address.</mat-hint>
            @if (form.controls.email.invalid) {
              <mat-error>Please enter a valid email address.</mat-error>
            }
          </mat-form-field>
          <mat-radio-group class="roles" formControlName="role" aria-label="Role">
            <mat-radio-button value="MEMBER">
              <strong>Member</strong> – can add and edit pets
            </mat-radio-button>
            <mat-radio-button value="SITTER">
              <strong>Pet-sitter</strong> – can view pets, not change them
            </mat-radio-button>
          </mat-radio-group>
          @if (error(); as err) {
            <p class="error" role="alert">{{ err.message }}</p>
          }
        </mat-dialog-content>
        <mat-dialog-actions align="end">
          <button mat-button type="button" [mat-dialog-close]="false">Cancel</button>
          <button mat-flat-button type="submit" [disabled]="form.invalid || sending()">
            {{ sending() ? 'Sending…' : 'Send invitation' }}
          </button>
        </mat-dialog-actions>
      </form>
    }
  `,
  styles: `
    .roles {
      display: flex;
      flex-direction: column;
      gap: 4px;
      margin-top: 16px;
    }
    .link-box {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 8px 8px 12px;
      border-radius: 12px;
      background: var(--mat-sys-surface-container-high);
      code {
        flex: 1;
        overflow-wrap: anywhere;
      }
    }
    .error {
      color: var(--mat-sys-error);
    }
  `,
})
export class InviteDialog {
  protected readonly data = inject<InviteDialogData>(MAT_DIALOG_DATA);
  private readonly households = inject(HouseholdService);
  private readonly clipboard = inject(Clipboard);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    role: ['MEMBER' as InviteRole, Validators.required],
  });
  protected readonly sending = signal(false);
  protected readonly error = signal<ApiError | null>(null);
  protected readonly invitation = signal<Invitation | null>(null);
  protected readonly link = signal('');
  protected readonly copied = signal(false);

  protected send(): void {
    if (this.form.invalid) return;
    const { email, role } = this.form.getRawValue();
    this.sending.set(true);
    this.error.set(null);
    this.households
      .invite(this.data.householdId, { email: email.trim(), role })
      .pipe(finalize(() => this.sending.set(false)))
      .subscribe({
        next: (invitation) => {
          this.invitation.set(invitation);
          this.link.set(`${window.location.origin}/invite/${encodeURIComponent(invitation.code ?? '')}`);
        },
        error: (error: unknown) =>
          this.error.set(toApiError(error, { 403: 'Only owners can invite people to this household.' })),
      });
  }

  protected copy(): void {
    this.copied.set(this.clipboard.copy(this.link()));
  }
}
