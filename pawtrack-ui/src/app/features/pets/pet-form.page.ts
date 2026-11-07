import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { Pet, PetRequest, Sex, Species, canWrite } from '../../core/api/models';
import { PetService } from '../../core/api/pet.service';
import { ApiError, toApiError } from '../../core/errors/api-error';
import { NotifyService } from '../../core/notify.service';
import { SessionStore } from '../../core/session.store';
import { PetPhotoCache } from '../../core/pet-photo-cache.service';
import { ErrorState } from '../../shared/error-state';
import { SEXES, SEX_LABELS, SPECIES, SpeciesPipe } from '../../shared/species';

/** Create (`/pets/new?householdId=`) and edit (`/pets/:petId/edit`) in one form. */
@Component({
  selector: 'app-pet-form-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    ErrorState,
    SpeciesPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pet-form.page.html',
  styles: `
    form {
      max-width: 760px;
    }
    .neutered {
      align-self: center;
      margin-bottom: 20px;
    }
  `,
})
export class PetFormPage {
  private readonly pets = inject(PetService);
  private readonly photos = inject(PetPhotoCache);
  private readonly router = inject(Router);
  private readonly notify = inject(NotifyService);
  protected readonly session = inject(SessionStore);

  /** Set when editing (route param). */
  readonly petId = input<string>();
  /** Preselected household when creating (query param). */
  readonly householdId = input<string>();

  protected readonly speciesOptions = SPECIES;
  protected readonly sexOptions = SEXES;
  protected readonly sexLabels = SEX_LABELS;
  protected readonly today = new Date().toISOString().slice(0, 10);

  protected readonly editing = computed(() => !!this.petId());
  protected readonly original = signal<Pet | null>(null);
  protected readonly loadError = signal<ApiError | null>(null);
  protected readonly saving = signal(false);
  protected readonly serverError = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    householdId: ['', Validators.required],
    name: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(60)]],
    species: ['' as Species | '', Validators.required],
    breed: ['', Validators.maxLength(80)],
    sex: ['' as Sex | ''],
    birthDate: [''],
    neutered: [false],
    microchipNumber: ['', Validators.pattern(/^[0-9]{15}$/)],
    color: ['', Validators.maxLength(60)],
    notes: ['', Validators.maxLength(2000)],
  });

  constructor() {
    effect(() => {
      const petId = this.petId();
      const householdId = this.householdId();
      untracked(() => (petId ? this.loadPet(petId) : this.prepareNew(householdId)));
    });
  }

  private prepareNew(householdId: string | undefined): void {
    const writable = this.session.writableHouseholds();
    const preselected = writable.find((h) => h.householdId === householdId) ?? (writable.length === 1 ? writable[0] : undefined);
    this.form.reset({ householdId: preselected?.householdId ?? '' });
    if (writable.length === 0) {
      this.loadError.set({
        status: 403,
        title: 'No access',
        message: 'You are a pet-sitter in all your households, so you cannot add pets.',
      });
    }
  }

  private loadPet(petId: string): void {
    this.loadError.set(null);
    this.pets.get(petId).subscribe({
      next: (pet) => {
        if (!canWrite(this.session.roleIn(pet.householdId))) {
          this.loadError.set({ status: 403, title: 'No access', message: 'Pet-sitters can view pets but not edit them.' });
          return;
        }
        this.original.set(pet);
        this.form.reset({
          householdId: pet.householdId,
          name: pet.name,
          species: pet.species,
          breed: pet.breed ?? '',
          sex: pet.sex ?? '',
          birthDate: pet.birthDate ?? '',
          neutered: pet.neutered ?? false,
          microchipNumber: pet.microchipNumber ?? '',
          color: pet.color ?? '',
          notes: pet.notes ?? '',
        });
      },
      error: (error: unknown) => this.loadError.set(toApiError(error)),
    });
  }

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const request = toRequest(this.form.getRawValue());
    const petId = this.petId();
    this.saving.set(true);
    this.serverError.set(null);
    (petId ? this.pets.update(petId, request) : this.pets.create(request))
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (pet) => {
          if (petId) this.photos.evict(pet.id);
          this.notify.success(petId ? `${pet.name} saved` : `${pet.name} added`);
          void this.router.navigate(['/pets', pet.id]);
        },
        error: (error: unknown) =>
          this.serverError.set(
            toApiError(error, {
              403: 'You can only add or edit pets in households where you are owner or member.',
            }).message,
          ),
      });
  }
}

type FormValue = ReturnType<PetFormPage['form']['getRawValue']>;

/** Maps the form to the contract. Empty optional fields are omitted (PUT clears them). */
export function toRequest(value: FormValue): PetRequest {
  const optional = (text: string) => (text.trim() ? text.trim() : undefined);
  const request: PetRequest = {
    householdId: value.householdId,
    name: value.name.trim(),
    species: value.species as Species,
    breed: optional(value.breed),
    sex: value.sex || undefined,
    birthDate: value.birthDate || undefined,
    neutered: value.neutered,
    microchipNumber: optional(value.microchipNumber),
    color: optional(value.color),
    notes: optional(value.notes),
  };
  return Object.fromEntries(Object.entries(request).filter(([, v]) => v !== undefined)) as PetRequest;
}
