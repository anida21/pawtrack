import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { of, switchMap } from 'rxjs';
import { Pet } from '../core/api/models';
import { PetPhotoCache } from '../core/pet-photo-cache.service';
import { SpeciesPipe } from './species';

/** Pet photo with a species-icon placeholder while loading or when there is none. */
@Component({
  selector: 'app-pet-photo',
  imports: [MatIconModule, SpeciesPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'pet-photo' },
  template: `
    @if (src(); as url) {
      <img [src]="url" [alt]="'Photo of ' + pet().name" />
    } @else {
      <div class="placeholder" [attr.aria-label]="pet().name + ' has no photo'">
        <mat-icon>{{ pet().species | species: 'icon' }}</mat-icon>
      </div>
    }
  `,
  styles: `
    :host {
      display: block;
      overflow: hidden;
      background: var(--mat-sys-secondary-container);
    }
    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
    }
    .placeholder {
      width: 100%;
      height: 100%;
      display: grid;
      place-items: center;
      color: var(--mat-sys-on-secondary-container);
    }
    .placeholder mat-icon {
      width: 40%;
      height: auto;
      aspect-ratio: 1;
      font-size: min(64px, 12vw);
      display: grid;
      place-items: center;
      opacity: 0.6;
    }
  `,
})
export class PetPhoto {
  private readonly cache = inject(PetPhotoCache);
  readonly pet = input.required<Pet>();

  private readonly key = computed(() => {
    const pet = this.pet();
    return pet.hasPhoto ? { id: pet.id, version: pet.updatedAt ?? pet.createdAt } : null;
  });

  protected readonly src = toSignal(
    toObservable(this.key).pipe(switchMap((key) => (key ? this.cache.url(key.id, key.version) : of(null)))),
    { initialValue: null },
  );
}
