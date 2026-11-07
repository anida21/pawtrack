import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatRippleModule } from '@angular/material/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { PetFilter, PetService } from '../../core/api/pet.service';
import { PetPage } from '../../core/api/models';
import { ApiError, toApiError } from '../../core/errors/api-error';
import { SessionStore } from '../../core/session.store';
import { ErrorState } from '../../shared/error-state';
import { PetPhoto } from '../../shared/pet-photo';
import { AgePipe, SPECIES, SpeciesPipe, isSpecies } from '../../shared/species';

export const PAGE_SIZES = [12, 24, 48];

type LoadState =
  | { status: 'loading'; page?: PetPage }
  | { status: 'ok'; page: PetPage }
  | { status: 'error'; error: ApiError };

@Component({
  selector: 'app-pet-list-page',
  imports: [
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatRippleModule,
    ErrorState,
    PetPhoto,
    SpeciesPipe,
    AgePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pet-list.page.html',
  styleUrl: './pet-list.page.scss',
})
export class PetListPage {
  private readonly pets = inject(PetService);
  private readonly router = inject(Router);
  protected readonly session = inject(SessionStore);
  protected readonly speciesOptions = SPECIES;
  protected readonly pageSizes = PAGE_SIZES;

  // Query parameters (component input binding) are the single source of truth for filters and paging,
  // so filtered views can be bookmarked and the back button works.
  readonly householdId = input<string>();
  readonly species = input<string>();
  readonly page = input<string>();
  readonly size = input<string>();

  protected readonly filter = computed<Required<Pick<PetFilter, 'page' | 'size'>> & PetFilter>(() => {
    const species = this.species();
    return {
      householdId: this.householdId() || undefined,
      species: isSpecies(species) ? species : undefined,
      page: toInt(this.page(), 0, 0),
      size: PAGE_SIZES.includes(toInt(this.size(), 12, 1)) ? toInt(this.size(), 12, 1) : PAGE_SIZES[0],
    };
  });

  protected readonly state = toSignal(
    toObservable(this.filter).pipe(
      switchMap((filter) =>
        this.pets.list(filter).pipe(
          map((page): LoadState => ({ status: 'ok', page })),
          catchError((error: unknown) => of<LoadState>({ status: 'error', error: toApiError(error) })),
          startWith<LoadState>({ status: 'loading' }),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as LoadState },
  );

  protected readonly canAdd = computed(() => this.session.writableHouseholds().length > 0);
  protected readonly hasFilter = computed(() => !!this.filter().householdId || !!this.filter().species);
  protected readonly showHousehold = computed(() => this.session.households().length > 1);

  protected setFilter(change: Partial<Pick<PetFilter, 'householdId' | 'species'>>): void {
    // Changing a filter always goes back to the first page.
    this.navigate({ ...change, page: undefined });
  }

  protected onPage(event: PageEvent): void {
    this.navigate({
      page: event.pageIndex || undefined,
      size: event.pageSize === PAGE_SIZES[0] ? undefined : event.pageSize,
    });
  }

  protected clearFilters(): void {
    this.navigate({ householdId: undefined, species: undefined, page: undefined });
  }

  private navigate(params: Partial<PetFilter>): void {
    const queryParams: Record<string, string | number | null> = {};
    for (const [key, value] of Object.entries(params)) {
      queryParams[key] = value === undefined || value === '' ? null : value;
    }
    void this.router.navigate([], { queryParams, queryParamsHandling: 'merge' });
  }
}

function toInt(value: string | undefined, fallback: number, min: number): number {
  const parsed = Number.parseInt(value ?? '', 10);
  return Number.isFinite(parsed) && parsed >= min ? parsed : fallback;
}
