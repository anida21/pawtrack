import { api } from '../../helpers/api';
import { createHousehold, createPet, isoDate, vaccinationRequest } from '../../helpers/builders';
import type { HealthSummary, Medication, Vaccination, Weight } from '../../helpers/types';

describe('health: records of a pet', () => {
  let householdId: string;
  let petId: string;

  beforeAll(async () => {
    householdId = (await createHousehold('alice')).id;
    petId = (await createPet('alice', householdId)).id;
  });

  afterAll(async () => {
    await api('alice').delete(`/api/v1/households/${householdId}`);
  });

  it('records vaccinations, weights and medications and summarises them', async () => {
    const overdue = await api('alice').post<Vaccination>(`/api/v1/pets/${petId}/vaccinations`,
      vaccinationRequest({ vaccine: 'Rabies', administeredOn: isoDate(-400), nextDueDate: isoDate(-35) }));
    expect(overdue.status).toBe(201);
    expect((await api('alice').post(`/api/v1/pets/${petId}/vaccinations`,
      vaccinationRequest({ vaccine: 'DHPPi', nextDueDate: isoDate(20) }))).status).toBe(201);

    for (const [days, kg] of [[-60, 11.2], [-30, 11.8], [-1, 12.4]] as const) {
      expect((await api('alice').post<Weight>(`/api/v1/pets/${petId}/weights`, { measuredOn: isoDate(days), weightKg: kg })).status)
        .toBe(201);
    }
    const medication = await api('alice').post<Medication>(`/api/v1/pets/${petId}/medications`, {
      name: 'Bravecto', dosage: '1 tablet', startDate: isoDate(0), frequency: 'MONTHLY', timeOfDay: '08:00',
    });
    expect(medication.status).toBe(201);
    expect(medication.body.active).toBe(true);

    const summary = await api('alice').get<HealthSummary>(`/api/v1/pets/${petId}/summary`);

    expect(summary.status).toBe(200);
    expect(summary.body).toEqual(expect.objectContaining({
      latestWeightKg: 12.4,
      nextVaccinationDue: isoDate(20), // next upcoming one; the overdue one is counted separately

      overdueVaccinations: 1,
      activeMedications: 1,
    }));
  });

  it('returns weights in a date range, oldest first', async () => {
    const weights = await api('alice').get<Weight[]>(`/api/v1/pets/${petId}/weights?from=${isoDate(-45)}&to=${isoDate(0)}`);

    expect(weights.status).toBe(200);
    expect(weights.body.map((w) => w.weightKg)).toEqual([11.8, 12.4]);
  });

  it('rejects a vaccination given in the future', async () => {
    const res = await api('alice').post(`/api/v1/pets/${petId}/vaccinations`, vaccinationRequest({ administeredOn: isoDate(3) }));
    expect(res.status).toBe(400);
  });

  it('applies the pet access rules (checked through pet)', async () => {
    expect((await api('bob').get(`/api/v1/pets/${petId}/vaccinations`)).status).toBe(403);
    expect((await api('alice').get(`/api/v1/pets/00000000-0000-0000-0000-000000000000/vaccinations`)).status).toBe(404);
  });
});
