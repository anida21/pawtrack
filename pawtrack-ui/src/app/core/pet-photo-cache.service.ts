import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, shareReplay } from 'rxjs';
import { PetService } from './api/pet.service';

/**
 * Object URLs for pet photos, cached per pet and version (`updatedAt`), so lists don't
 * download the same photo twice and a new upload invalidates the old image.
 */
@Injectable({ providedIn: 'root' })
export class PetPhotoCache {
  private readonly pets = inject(PetService);
  private readonly entries = new Map<string, { version: string; url$: Observable<string | null> }>();
  private readonly objectUrls = new Map<string, string>();

  url(petId: string, version: string): Observable<string | null> {
    const cached = this.entries.get(petId);
    if (cached?.version === version) {
      return cached.url$;
    }
    this.revoke(petId);
    const url$ = this.pets.photo(petId).pipe(
      map((blob) => {
        const url = URL.createObjectURL(blob);
        this.objectUrls.set(petId, url);
        return url;
      }),
      catchError(() => of(null)),
      shareReplay(1),
    );
    this.entries.set(petId, { version, url$ });
    return url$;
  }

  evict(petId: string): void {
    this.revoke(petId);
    this.entries.delete(petId);
  }

  private revoke(petId: string): void {
    const url = this.objectUrls.get(petId);
    if (url) {
      URL.revokeObjectURL(url);
      this.objectUrls.delete(petId);
    }
  }
}
