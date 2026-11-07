import { api, eventually } from '../../helpers/api';
import { config } from '../../helpers/config';
import { addMember, createHousehold, createPet, isoDate, vaccinationRequest } from '../../helpers/builders';
import type { Reminder, Vaccination } from '../../helpers/types';

/** The full event chain: health → RabbitMQ → notification → reminders API (and Mailpit for email). */
describe('notification: reminders generated from events', () => {
  let householdId: string;
  let petId: string;

  beforeAll(async () => {
    householdId = (await createHousehold('alice')).id;
    petId = (await createPet('alice', householdId, { name: 'Reminder Rex' })).id;
  });

  afterAll(async () => {
    await api('alice').delete(`/api/v1/households/${householdId}`);
  });

  async function remindersOf(user: 'alice' | 'bob', sourceId: string) {
    const res = await api(user).get<Reminder[]>(`/api/v1/reminders?petId=${petId}`);
    return res.status === 200 ? res.body.filter((r) => r.sourceId === sourceId) : undefined;
  }

  it('creates two reminders for a vaccination due in 10 days and replaces them on update', async () => {
    const vaccination = await api('alice').post<Vaccination>(`/api/v1/pets/${petId}/vaccinations`,
      vaccinationRequest({ vaccine: 'Rabies', nextDueDate: isoDate(10) }));
    expect(vaccination.status).toBe(201);
    const id = vaccination.body.id!;

    const reminders = await eventually(async () => {
      const list = await remindersOf('alice', id);
      return list && list.length === 2 ? list : undefined;
    }, 20_000, 'two vaccination reminders');
    expect(reminders.map((r) => r.dueAt!.slice(0, 10)).sort()).toEqual([isoDate(3), isoDate(10)]);
    expect(reminders.every((r) => r.source === 'VACCINATION' && r.status === 'PENDING')).toBe(true);

    // No next due date any more → its pending reminders are cancelled.
    const { nextDueDate, ...withoutNextDue } = vaccinationRequest({ vaccine: 'Rabies' });
    expect((await api('alice').put(`/api/v1/pets/${petId}/vaccinations/${id}`, withoutNextDue)).status).toBe(200);
    await eventually(async () => {
      const list = await remindersOf('alice', id);
      return list && list.every((r) => r.status === 'CANCELLED') ? true : undefined;
    }, 20_000, 'reminders to be cancelled');
  });

  it('shares reminders with household members and lets them mark one done', async () => {
    await addMember('alice', householdId, 'bob', 'MEMBER');
    const custom = await api('alice').post<Reminder>('/api/v1/reminders', {
      petId, title: 'Vet check-up', dueAt: new Date(Date.now() + 2 * 86_400_000).toISOString(),
    });
    expect(custom.status).toBe(201);
    expect(custom.body.source).toBe('CUSTOM');

    const asBob = await eventually(async () => {
      const res = await api('bob').get<Reminder[]>(`/api/v1/reminders?petId=${petId}`);
      return res.status === 200 ? res.body.find((r) => r.id === custom.body.id) : undefined;
    }, 20_000, 'bob to see the reminder');
    expect(asBob.title).toBe('Vet check-up');

    const done = await api('bob').post<Reminder>(`/api/v1/reminders/${custom.body.id}/done`);
    expect(done.status).toBe(200);
    expect(done.body.status).toBe('DONE');
    expect((await api('alice').delete(`/api/v1/reminders/${custom.body.id}`)).status).toBe(409);
  });

  it('emails a due reminder to the household (Mailpit)', async () => {
    const title = `E2E due now ${Date.now()}`;
    const created = await api('alice').post<Reminder>('/api/v1/reminders', {
      petId, title, dueAt: new Date(Date.now() - 60_000).toISOString(),
    });
    expect(created.status).toBe(201);

    // The scheduler runs every minute and sends one email per member (alice, and bob who joined above).
    const recipients = await eventually(async () => {
      const res = await fetch(`${config.mailpitUrl}/api/v1/search?query=${encodeURIComponent(`subject:"${title}"`)}`);
      const body = (await res.json()) as { messages: { To: { Address: string }[] }[] };
      const to = body.messages.flatMap((m) => m.To.map((t) => t.Address));
      return to.length >= 2 ? to.sort() : undefined;
    }, 90_000, 'the reminder emails');
    expect(recipients).toEqual(['alice@pawtrack.local', 'bob@pawtrack.local']);
  }, 120_000);
});
