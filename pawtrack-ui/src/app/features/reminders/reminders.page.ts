import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink } from '@angular/router';
import { Observable, catchError, filter, forkJoin, map, of } from 'rxjs';
import { Pet, Reminder } from '../../core/api/models';
import { PetService } from '../../core/api/pet.service';
import { ReminderService } from '../../core/api/reminder.service';
import { ApiError, toApiError } from '../../core/errors/api-error';
import { NotifyService } from '../../core/notify.service';
import { ErrorState } from '../../shared/error-state';
import { ReminderDialog, ReminderDialogData } from './reminder-dialog';
import { ReminderView, SOURCE_ICONS, groupReminders, isOpen, mergeByDueAt } from './reminder-groups';

const VIEWS: ReminderView[] = ['open', 'DONE', 'CANCELLED'];

@Component({
  selector: 'app-reminders-page',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTooltipModule,
    ErrorState,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './reminders.page.html',
  styleUrl: './reminders.page.scss',
})
export class RemindersPage {
  private readonly api = inject(ReminderService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);

  /** Query parameters (component input binding). */
  readonly petId = input<string>();
  readonly view = input<string>();

  protected readonly sourceIcons = SOURCE_ICONS;
  protected readonly isOpen = isOpen;
  protected readonly currentView = computed<ReminderView>(() => {
    const view = this.view() as ReminderView;
    return VIEWS.includes(view) ? view : 'open';
  });

  /** All pets of the caller (names for the list, choices for the filter and the dialog). */
  protected readonly pets = toSignal(
    inject(PetService)
      .list({ size: 100 })
      .pipe(
        map((page) => page.content ?? []),
        catchError(() => of([] as Pet[])),
      ),
    { initialValue: [] as Pet[] },
  );
  private readonly petNames = computed(() => new Map(this.pets().map((p) => [p.id, p.name])));

  protected readonly reminders = signal<Reminder[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<ApiError | null>(null);
  protected readonly busyId = signal<string | null>(null);
  protected readonly groups = computed(() => groupReminders(this.reminders(), this.currentView()));

  constructor() {
    effect(() => {
      const petId = this.petId();
      const view = this.currentView();
      untracked(() => this.load(petId, view));
    });
  }

  protected load(petId = this.petId(), view = this.currentView()): void {
    this.loading.set(true);
    this.error.set(null);
    const request: Observable<Reminder[]> =
      view === 'open'
        ? forkJoin([this.api.list({ petId, status: 'PENDING' }), this.api.list({ petId, status: 'SENT' })]).pipe(
            map(([pending, sent]) => mergeByDueAt(pending, sent)),
          )
        : this.api.list({ petId, status: view });
    request.subscribe({
      next: (reminders) => {
        this.reminders.set(reminders);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(toApiError(error));
        this.loading.set(false);
      },
    });
  }

  protected petName(id: string): string {
    return this.petNames().get(id) ?? 'Pet';
  }

  protected navigate(params: { petId?: string | null; view?: ReminderView | null }): void {
    const queryParams = { ...params, view: params.view === 'open' ? null : params.view };
    if (queryParams.petId === '') queryParams.petId = null;
    void this.router.navigate([], { queryParams, queryParamsHandling: 'merge' });
  }

  protected create(): void {
    this.dialog
      .open<ReminderDialog, ReminderDialogData, Reminder>(ReminderDialog, {
        data: { pets: this.pets(), petId: this.petId() },
        width: '520px',
        maxWidth: '95vw',
      })
      .afterClosed()
      .pipe(filter(Boolean))
      .subscribe(() => {
        this.notify.success('Reminder created');
        this.load();
      });
  }

  protected markDone(reminder: Reminder): void {
    this.change(reminder, this.api.done(reminder.id ?? ''), 'Marked as done');
  }

  protected cancel(reminder: Reminder): void {
    this.change(reminder, this.api.cancel(reminder.id ?? ''), 'Reminder cancelled');
  }

  private change(reminder: Reminder, request: Observable<unknown>, message: string): void {
    this.busyId.set(reminder.id ?? null);
    request.subscribe({
      next: () => {
        this.busyId.set(null);
        // It leaves the open view; remove it right away instead of reloading everything.
        this.reminders.update((list) => list.filter((r) => r.id !== reminder.id));
        this.notify.success(message);
      },
      error: (error: unknown) => {
        this.busyId.set(null);
        this.notify.error(error, { 409: 'This reminder was already closed in another way. The list has been refreshed.' });
        this.load();
      },
    });
  }
}
