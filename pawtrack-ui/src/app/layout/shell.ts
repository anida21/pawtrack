import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { filter, map } from 'rxjs';
import { ApiError, toApiError } from '../core/errors/api-error';
import { SessionStore } from '../core/session.store';
import { ErrorState } from '../shared/error-state';

interface NavItem {
  link: string;
  label: string;
  icon: string;
}

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatProgressSpinnerModule,
    ErrorState,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  protected readonly session = inject(SessionStore);
  private readonly oidc = inject(OidcSecurityService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly nav: NavItem[] = [
    { link: '/pets', label: 'Pets', icon: 'pets' },
    { link: '/households', label: 'Households', icon: 'home' },
    { link: '/profile', label: 'Profile', icon: 'person' },
  ];

  protected readonly handset = toSignal(
    inject(BreakpointObserver)
      .observe([Breakpoints.XSmall, Breakpoints.Small])
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );
  protected readonly menuOpen = signal(false);
  protected readonly sidenavOpened = computed(() => !this.handset() || this.menuOpen());
  protected readonly loadError = signal<ApiError | null>(null);
  protected readonly initials = computed(() => initialsOf(this.session.user()?.displayName ?? ''));

  constructor() {
    // Close the drawer after navigating on small screens.
    inject(Router)
      .events.pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.menuOpen.set(false));
    this.load();
  }

  protected load(): void {
    this.loadError.set(null);
    this.session
      .load()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ error: (error: unknown) => this.loadError.set(toApiError(error)) });
  }

  protected logout(): void {
    this.oidc.logoff().subscribe();
  }
}

export function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  const first = parts[0][0];
  const last = parts.length > 1 ? parts[parts.length - 1][0] : '';
  return (first + last).toUpperCase();
}
