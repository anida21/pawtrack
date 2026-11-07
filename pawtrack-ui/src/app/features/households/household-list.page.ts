import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { Router, RouterLink } from '@angular/router';
import { filter, switchMap, tap } from 'rxjs';
import { HouseholdService } from '../../core/api/household.service';
import { NotifyService } from '../../core/notify.service';
import { SessionStore } from '../../core/session.store';
import { NameDialog, NameDialogData } from '../../shared/name-dialog';
import { RoleChip } from './role-chip';

@Component({
  selector: 'app-household-list-page',
  imports: [MatButtonModule, MatIconModule, MatRippleModule, RouterLink, RoleChip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="page-header">
      <h1>Households</h1>
      <button mat-flat-button (click)="create()"><mat-icon>add</mat-icon> New household</button>
      <p class="subtitle">Pets belong to a household. Invite your partner or pet-sitter to share them.</p>
    </header>

    @if (session.households().length === 0) {
      <div class="state-card">
        <mat-icon class="state-icon">home</mat-icon>
        <h2>No households yet</h2>
        <p>Create one to start adding pets.</p>
      </div>
    } @else {
      <ul class="grid" role="list">
        @for (household of session.households(); track household.householdId) {
          <li>
            <a class="household-card" matRipple [routerLink]="['/households', household.householdId]">
              <span class="icon"><mat-icon>home</mat-icon></span>
              <span class="name">{{ household.householdName }}</span>
              <app-role-chip [role]="household.role" />
              <mat-icon class="chevron">chevron_right</mat-icon>
            </a>
          </li>
        }
      </ul>
    }
  `,
  styles: `
    .grid {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: 16px;
    }
    .household-card {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 16px;
      border-radius: 20px;
      text-decoration: none;
      color: inherit;
      background: var(--mat-sys-surface-container-low);
      border: 1px solid var(--mat-sys-outline-variant);
      transition: background 0.15s, box-shadow 0.15s;
      &:hover,
      &:focus-visible {
        background: var(--mat-sys-surface-container);
        box-shadow: var(--mat-sys-level1);
      }
    }
    .icon {
      display: grid;
      place-items: center;
      width: 44px;
      height: 44px;
      border-radius: 14px;
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
    }
    .name {
      flex: 1;
      font: var(--mat-sys-title-medium);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .chevron {
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class HouseholdListPage {
  protected readonly session = inject(SessionStore);
  private readonly households = inject(HouseholdService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);

  protected create(): void {
    this.dialog
      .open<NameDialog, NameDialogData, string>(NameDialog, {
        data: { title: 'New household', label: 'Name', confirmLabel: 'Create' },
        width: '420px',
      })
      .afterClosed()
      .pipe(
        filter((name): name is string => !!name),
        switchMap((name) => this.households.create(name)),
        tap((household) => {
          this.notify.success(`Household “${household.name}” created`);
          void this.router.navigate(['/households', household.id]);
        }),
        switchMap(() => this.session.reloadHouseholds()),
      )
      .subscribe({ error: (error: unknown) => this.notify.error(error) });
  }
}
