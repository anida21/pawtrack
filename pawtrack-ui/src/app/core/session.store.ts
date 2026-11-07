import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, forkJoin, tap } from 'rxjs';
import { MeService } from './api/me.service';
import { HouseholdMembership, HouseholdRole, User, canWrite } from './api/models';

/**
 * The signed-in user and the households they belong to. Loaded once by the shell,
 * refreshed after anything that changes memberships (create/delete/leave/accept).
 */
@Injectable({ providedIn: 'root' })
export class SessionStore {
  private readonly me = inject(MeService);

  readonly user = signal<User | null>(null);
  readonly households = signal<HouseholdMembership[]>([]);
  readonly loaded = signal(false);

  readonly writableHouseholds = computed(() => this.households().filter((h) => canWrite(h.role)));

  /** GET /me first: it provisions the user and the default household on the very first login. */
  load(): Observable<unknown> {
    return forkJoin([this.me.get(), this.me.households()]).pipe(
      tap(([user, households]) => {
        this.user.set(user);
        this.households.set(sortByName(households));
        this.loaded.set(true);
      }),
    );
  }

  reloadHouseholds(): Observable<HouseholdMembership[]> {
    return this.me.households().pipe(tap((households) => this.households.set(sortByName(households))));
  }

  roleIn(householdId: string | undefined): HouseholdRole | undefined {
    return this.households().find((h) => h.householdId === householdId)?.role;
  }

  householdName(householdId: string | undefined): string {
    return this.households().find((h) => h.householdId === householdId)?.householdName ?? '';
  }
}

const sortByName = (list: HouseholdMembership[]) =>
  [...list].sort((a, b) => a.householdName.localeCompare(b.householdName));
