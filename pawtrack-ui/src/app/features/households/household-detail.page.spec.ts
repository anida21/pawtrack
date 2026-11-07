import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import {
  ALICE_ID,
  API,
  BOB_ID,
  HOME_ID,
  NotifyMock,
  createNotifyMock,
  makeHousehold,
  memberships,
  signIn,
  testProviders,
} from '../../../testing/fixtures';
import { SessionStore } from '../../core/session.store';
import { HouseholdDetailPage } from './household-detail.page';

describe('HouseholdDetailPage', () => {
  let fixture: ComponentFixture<HouseholdDetailPage>;
  let http: HttpTestingController;
  let notify: NotifyMock;
  const dialog = { open: vi.fn() };

  beforeEach(async () => {
    notify = createNotifyMock();
    // Every dialog (confirm, rename) answers "yes".
    dialog.open.mockReset().mockReturnValue({ afterClosed: () => of(true) });
    TestBed.configureTestingModule({
      imports: [HouseholdDetailPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        ...testProviders(notify),
        { provide: MatDialog, useValue: dialog },
      ],
    });
    signIn(TestBed.inject(SessionStore));
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(HouseholdDetailPage);
    fixture.componentRef.setInput('householdId', HOME_ID);
    await fixture.whenStable();
  });

  const page = () => fixture.componentInstance as unknown as {
    remove(h: object, m: object): void;
    delete(h: object): void;
  };

  it('lists members, owner first, with owner actions', async () => {
    http.expectOne(`${API}/api/v1/households/${HOME_ID}`).flush(makeHousehold());
    await fixture.whenStable();
    const names = [...fixture.nativeElement.querySelectorAll('.member .name')].map((el: Element) => el.textContent?.trim());
    expect(names[0]).toContain('Alice Test');
    expect(names[0]).toContain('(you)');
    expect(fixture.nativeElement.textContent).toContain('Invite');
    expect(fixture.nativeElement.querySelector('[aria-label="Remove Bob Test"]')).not.toBeNull();
  });

  it('hides owner actions for members', async () => {
    const household = makeHousehold({
      members: [
        { userId: BOB_ID, displayName: 'Bob Test', role: 'OWNER' },
        { userId: ALICE_ID, displayName: 'Alice Test', role: 'MEMBER' },
      ],
    });
    http.expectOne(`${API}/api/v1/households/${HOME_ID}`).flush(household);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).not.toContain('Invite');
    expect(fixture.nativeElement.querySelector('[aria-label="Remove Bob Test"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[aria-label="Leave household"]')).not.toBeNull();
  });

  it('explains 409 when the last owner tries to leave', async () => {
    const household = makeHousehold();
    http.expectOne(`${API}/api/v1/households/${HOME_ID}`).flush(household);
    await fixture.whenStable();

    page().remove(household, household.members[0]);
    http
      .expectOne({ method: 'DELETE', url: `${API}/api/v1/households/${HOME_ID}/members/${ALICE_ID}` })
      .flush({ status: 409, title: 'Conflict' }, { status: 409, statusText: 'Conflict' });
    expect(notify.error).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ 409: expect.stringContaining('at least one owner') }),
    );
  });

  it('removes another member and reloads', async () => {
    const household = makeHousehold();
    http.expectOne(`${API}/api/v1/households/${HOME_ID}`).flush(household);
    await fixture.whenStable();

    page().remove(household, household.members[1]);
    http.expectOne({ method: 'DELETE', url: `${API}/api/v1/households/${HOME_ID}/members/${BOB_ID}` }).flush(null);
    http.expectOne(`${API}/api/v1/households/${HOME_ID}`).flush(makeHousehold({ members: [household.members[0]] }));
    expect(notify.success).toHaveBeenCalledWith('Bob Test was removed');
  });

  it('deletes the household and goes back to the list', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const household = makeHousehold();
    http.expectOne(`${API}/api/v1/households/${HOME_ID}`).flush(household);
    await fixture.whenStable();

    page().delete(household);
    http.expectOne({ method: 'DELETE', url: `${API}/api/v1/households/${HOME_ID}` }).flush(null);
    http.expectOne(`${API}/api/v1/me/households`).flush(memberships.slice(1));
    expect(navigate).toHaveBeenCalledWith(['/households']);
  });

  it('shows a 403 as an error state', async () => {
    http.expectOne(`${API}/api/v1/households/${HOME_ID}`).flush({ status: 403 }, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('app-error-state')?.textContent).toContain('not a member');
  });
});
