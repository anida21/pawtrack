import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink } from '@angular/router';
import { filter, switchMap } from 'rxjs';
import { HouseholdService } from '../../core/api/household.service';
import { Household, Member } from '../../core/api/models';
import { ApiError, toApiError } from '../../core/errors/api-error';
import { NotifyService } from '../../core/notify.service';
import { SessionStore } from '../../core/session.store';
import { initialsOf } from '../../layout/shell';
import { confirm } from '../../shared/confirm-dialog';
import { ErrorState } from '../../shared/error-state';
import { NameDialog, NameDialogData } from '../../shared/name-dialog';
import { InviteDialog, InviteDialogData } from './invite-dialog';
import { RoleChip } from './role-chip';

@Component({
  selector: 'app-household-detail-page',
  imports: [
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    RouterLink,
    DatePipe,
    ErrorState,
    RoleChip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './household-detail.page.html',
  styleUrl: './household-detail.page.scss',
})
export class HouseholdDetailPage {
  private readonly api = inject(HouseholdService);
  private readonly session = inject(SessionStore);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);

  /** Route parameter (component input binding). */
  readonly householdId = input.required<string>();

  protected readonly household = signal<Household | null>(null);
  protected readonly error = signal<ApiError | null>(null);
  protected readonly busy = signal(false);

  protected readonly myId = computed(() => this.session.user()?.id);
  protected readonly myRole = computed(() => this.household()?.members.find((m) => m.userId === this.myId())?.role);
  protected readonly isOwner = computed(() => this.myRole() === 'OWNER');
  protected readonly members = computed(() =>
    [...(this.household()?.members ?? [])].sort(
      (a, b) => roleRank(a) - roleRank(b) || (a.displayName ?? '').localeCompare(b.displayName ?? ''),
    ),
  );

  constructor() {
    effect(() => this.load(this.householdId()));
  }

  protected load(id = this.householdId()): void {
    this.error.set(null);
    this.household.set(null);
    this.api.get(id).subscribe({
      next: (household) => this.household.set(household),
      error: (error: unknown) =>
        this.error.set(
          toApiError(error, {
            403: 'You are not a member of this household.',
            404: 'This household does not exist (anymore).',
          }),
        ),
    });
  }

  protected initials(member: Member): string {
    return initialsOf(member.displayName ?? member.email ?? '');
  }

  protected rename(household: Household): void {
    this.dialog
      .open<NameDialog, NameDialogData, string>(NameDialog, {
        data: { title: 'Rename household', label: 'Name', initial: household.name, confirmLabel: 'Rename' },
        width: '420px',
      })
      .afterClosed()
      .pipe(
        filter((name): name is string => !!name && name !== household.name),
        switchMap((name) => this.api.rename(household.id, name)),
      )
      .subscribe({
        next: (updated) => {
          this.household.set(updated);
          this.notify.success('Household renamed');
          this.session.reloadHouseholds().subscribe();
        },
        error: (error: unknown) => this.notify.error(error),
      });
  }

  protected invite(household: Household): void {
    this.dialog.open<InviteDialog, InviteDialogData>(InviteDialog, {
      data: { householdId: household.id, householdName: household.name },
      width: '480px',
    });
  }

  protected remove(household: Household, member: Member): void {
    const self = member.userId === this.myId();
    confirm(this.dialog, {
      title: self ? 'Leave household?' : `Remove ${member.displayName}?`,
      message: self
        ? `You will lose access to the pets of “${household.name}”.`
        : `${member.displayName} will lose access to the pets of “${household.name}”.`,
      confirmLabel: self ? 'Leave' : 'Remove',
      danger: true,
    })
      .pipe(
        filter(Boolean),
        switchMap(() => {
          this.busy.set(true);
          return this.api.removeMember(household.id, member.userId ?? '');
        }),
      )
      .subscribe({
        next: () => {
          this.busy.set(false);
          if (self) {
            this.notify.success(`You left “${household.name}”`);
            this.session.reloadHouseholds().subscribe();
            void this.router.navigate(['/households']);
          } else {
            this.notify.success(`${member.displayName} was removed`);
            this.load(household.id);
          }
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.notify.error(error, {
            409: 'A household needs at least one owner. Make someone else owner or delete the household instead.',
          });
        },
      });
  }

  protected delete(household: Household): void {
    confirm(this.dialog, {
      title: `Delete “${household.name}”?`,
      message: 'All members lose access and its pets are removed. This cannot be undone.',
      confirmLabel: 'Delete household',
      danger: true,
    })
      .pipe(
        filter(Boolean),
        switchMap(() => this.api.delete(household.id)),
      )
      .subscribe({
        next: () => {
          this.notify.success(`Household “${household.name}” deleted`);
          this.session.reloadHouseholds().subscribe();
          void this.router.navigate(['/households']);
        },
        error: (error: unknown) => this.notify.error(error),
      });
  }
}

const ROLE_ORDER = { OWNER: 0, MEMBER: 1, SITTER: 2 } as const;
const roleRank = (member: Member) => (member.role ? ROLE_ORDER[member.role] : 3);
