import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { API, NotifyMock, createNotifyMock, makePet, testProviders } from '../../../testing/fixtures';
import { Reminder } from '../../core/api/models';
import { ReminderService } from '../../core/api/reminder.service';
import { groupReminders, mergeByDueAt } from './reminder-groups';
import { RemindersPage } from './reminders.page';

const PET = makePet({ id: 'p1', name: 'Luna' });
const reminder = (id: string, dueAt: string, status: Reminder['status'] = 'PENDING'): Reminder => ({
  id,
  petId: 'p1',
  title: `Reminder ${id}`,
  dueAt,
  status,
  source: 'CUSTOM',
});

describe('reminder grouping', () => {
  const now = new Date(2026, 10, 7, 12, 0); // local time

  it('groups open reminders into overdue / today / next 7 days / later', () => {
    const at = (d: number, h: number) => new Date(2026, 10, d, h, 0).toISOString();
    const groups = groupReminders(
      [reminder('a', at(6, 9)), reminder('b', at(7, 18)), reminder('c', at(10, 9)), reminder('d', at(30, 9))],
      'open',
      now,
    );
    expect(groups.map((g) => [g.key, g.reminders.map((r) => r.id)])).toEqual([
      ['overdue', ['a']],
      ['today', ['b']],
      ['week', ['c']],
      ['later', ['d']],
    ]);
  });

  it('shows closed reminders newest first in one group', () => {
    const groups = groupReminders([reminder('a', '2026-01-01T09:00:00Z'), reminder('b', '2026-02-01T09:00:00Z')], 'DONE', now);
    expect(groups).toHaveLength(1);
    expect(groups[0].reminders.map((r) => r.id)).toEqual(['b', 'a']);
  });

  it('merges PENDING and SENT lists by dueAt', () => {
    const merged = mergeByDueAt([reminder('b', '2026-02-01T09:00:00Z')], [reminder('a', '2026-01-01T09:00:00Z', 'SENT')]);
    expect(merged.map((r) => r.id)).toEqual(['a', 'b']);
  });
});

describe('ReminderService', () => {
  it('maps list, create, done and cancel to the contract', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), ...testProviders()] });
    const http = TestBed.inject(HttpTestingController);
    const api = TestBed.inject(ReminderService);
    api.list({ petId: 'p1', status: 'SENT' }).subscribe();
    api.create({ petId: 'p1', title: 'Walk', dueAt: '2026-11-08T08:00:00Z' }).subscribe();
    api.done('r1').subscribe();
    api.cancel('r1').subscribe();

    const list = http.expectOne((r) => r.url === `${API}/api/v1/reminders` && r.method === 'GET');
    expect(list.request.params.get('petId')).toBe('p1');
    expect(list.request.params.get('status')).toBe('SENT');
    expect(list.request.params.has('from')).toBe(false);
    http.expectOne({ method: 'POST', url: `${API}/api/v1/reminders` });
    http.expectOne({ method: 'POST', url: `${API}/api/v1/reminders/r1/done` });
    http.expectOne({ method: 'DELETE', url: `${API}/api/v1/reminders/r1` });
    http.verify();
  });
});

describe('RemindersPage', () => {
  let fixture: ComponentFixture<RemindersPage>;
  let http: HttpTestingController;
  let notify: NotifyMock;
  const url = `${API}/api/v1/reminders`;
  const future = new Date(Date.now() + 3 * 86_400_000).toISOString();

  beforeEach(async () => {
    notify = createNotifyMock();
    TestBed.configureTestingModule({
      imports: [RemindersPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), ...testProviders(notify)],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RemindersPage);
    await fixture.whenStable();
    http.expectOne((r) => r.url === `${API}/api/v1/pets`).flush({ content: [PET], totalElements: 1 });
  });

  const flushOpen = async (pending: Reminder[], sent: Reminder[] = []) => {
    http.expectOne((r) => r.url === url && r.params.get('status') === 'PENDING').flush(pending);
    http.expectOne((r) => r.url === url && r.params.get('status') === 'SENT').flush(sent);
    await fixture.whenStable();
  };

  it('shows open reminders (pending + sent) with the pet name', async () => {
    await flushOpen([reminder('r1', future)], [reminder('r2', future, 'SENT')]);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Reminder r1');
    expect(text).toContain('email sent');
    expect(text).toContain('Luna');
  });

  it('loads closed reminders with a single status filter', async () => {
    await flushOpen([]);
    fixture.componentRef.setInput('view', 'DONE');
    fixture.componentRef.setInput('petId', 'p1');
    await fixture.whenStable();
    const req = http.expectOne((r) => r.url === url);
    expect(req.request.params.get('status')).toBe('DONE');
    expect(req.request.params.get('petId')).toBe('p1');
    req.flush([]);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Nothing here');
  });

  it('marks a reminder done and removes it from the open list', async () => {
    await flushOpen([reminder('r1', future)]);
    (fixture.nativeElement.querySelector('[aria-label="Mark Reminder r1 as done"]') as HTMLButtonElement).click();
    http.expectOne({ method: 'POST', url: `${url}/r1/done` }).flush(reminder('r1', future, 'DONE'));
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).not.toContain('Reminder r1');
    expect(notify.success).toHaveBeenCalledWith('Marked as done');
  });

  it('explains a 409 and reloads', async () => {
    await flushOpen([reminder('r1', future)]);
    (fixture.nativeElement.querySelector('[aria-label="Cancel Reminder r1"]') as HTMLButtonElement).click();
    http.expectOne({ method: 'DELETE', url: `${url}/r1` }).flush({ status: 409 }, { status: 409, statusText: 'Conflict' });
    expect(notify.error).toHaveBeenCalledWith(expect.anything(), expect.objectContaining({ 409: expect.any(String) }));
    await flushOpen([]);
  });

  it('switches views through the URL', async () => {
    await flushOpen([]);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    (fixture.componentInstance as unknown as { navigate(p: object): void }).navigate({ view: 'open', petId: '' });
    expect(navigate).toHaveBeenCalledWith([], { queryParams: { view: null, petId: null }, queryParamsHandling: 'merge' });
  });
});
