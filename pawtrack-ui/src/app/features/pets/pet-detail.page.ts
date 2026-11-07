import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import { filter, finalize, switchMap } from 'rxjs';
import { Pet, canWrite } from '../../core/api/models';
import { PetService } from '../../core/api/pet.service';
import { ApiError, toApiError } from '../../core/errors/api-error';
import { NotifyService } from '../../core/notify.service';
import { PetPhotoCache } from '../../core/pet-photo-cache.service';
import { SessionStore } from '../../core/session.store';
import { confirm } from '../../shared/confirm-dialog';
import { ErrorState } from '../../shared/error-state';
import { PetPhoto } from '../../shared/pet-photo';
import { AgePipe, SEX_LABELS, SpeciesPipe } from '../../shared/species';

export const PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
export const PHOTO_MAX_BYTES = 5 * 1024 * 1024;

/** Client-side pre-check; the server sniffs the real format and enforces the limit too (415/413). */
export function photoProblem(file: File): string | null {
  if (!PHOTO_TYPES.includes(file.type)) return 'Please choose a JPEG, PNG or WebP image.';
  if (file.size > PHOTO_MAX_BYTES) return 'The photo must be 5 MB or smaller.';
  return null;
}

@Component({
  selector: 'app-pet-detail-page',
  imports: [
    RouterLink,
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    ErrorState,
    PetPhoto,
    SpeciesPipe,
    AgePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pet-detail.page.html',
  styleUrl: './pet-detail.page.scss',
})
export class PetDetailPage {
  private readonly pets = inject(PetService);
  private readonly photos = inject(PetPhotoCache);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);
  protected readonly session = inject(SessionStore);

  readonly petId = input.required<string>();

  protected readonly pet = signal<Pet | null>(null);
  protected readonly error = signal<ApiError | null>(null);
  protected readonly uploading = signal(false);
  protected readonly sexLabels = SEX_LABELS;
  protected readonly accept = PHOTO_TYPES.join(',');

  protected readonly canEdit = computed(() => canWrite(this.session.roleIn(this.pet()?.householdId)));

  constructor() {
    effect(() => {
      const id = this.petId();
      untracked(() => this.load(id));
    });
  }

  protected load(id = this.petId()): void {
    this.error.set(null);
    this.pet.set(null);
    this.pets.get(id).subscribe({
      next: (pet) => this.pet.set(pet),
      error: (error: unknown) =>
        this.error.set(
          toApiError(error, {
            403: 'This pet belongs to a household you are not a member of.',
            404: 'This pet does not exist or was deleted.',
          }),
        ),
    });
  }

  protected onFile(event: Event, pet: Pet): void {
    const inputEl = event.target as HTMLInputElement;
    const file = inputEl.files?.[0];
    inputEl.value = '';
    if (!file) return;
    const problem = photoProblem(file);
    if (problem) {
      this.notify.warn(problem);
      return;
    }
    this.uploading.set(true);
    this.pets
      .uploadPhoto(pet.id, file)
      .pipe(finalize(() => this.uploading.set(false)))
      .subscribe({
        next: (updated) => {
          this.photos.evict(updated.id);
          this.pet.set(updated);
          this.notify.success('Photo updated');
        },
        error: (error: unknown) =>
          this.notify.error(error, {
            403: 'Pet-sitters cannot change photos.',
            413: 'The photo must be 5 MB or smaller.',
            415: 'Please choose a JPEG, PNG or WebP image.',
          }),
      });
  }

  protected delete(pet: Pet): void {
    confirm(this.dialog, {
      title: `Delete ${pet.name}?`,
      message: `${pet.name} will be removed for everyone in the household.`,
      confirmLabel: 'Delete',
      danger: true,
    })
      .pipe(
        filter(Boolean),
        switchMap(() => this.pets.delete(pet.id)),
      )
      .subscribe({
        next: () => {
          this.photos.evict(pet.id);
          this.notify.success(`${pet.name} was deleted`);
          void this.router.navigate(['/pets']);
        },
        error: (error: unknown) => this.notify.error(error),
      });
  }
}
