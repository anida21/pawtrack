import { Reminder, ReminderSource } from '../../core/api/models';

export type ReminderView = 'open' | 'DONE' | 'CANCELLED';

export interface ReminderGroup {
  key: 'overdue' | 'today' | 'week' | 'later' | 'closed';
  label: string;
  reminders: Reminder[];
}

export const SOURCE_ICONS: Record<ReminderSource, string> = {
  VACCINATION: 'vaccines',
  MEDICATION: 'medication',
  CUSTOM: 'notifications',
};

export const isOpen = (r: Reminder) => r.status === 'PENDING' || r.status === 'SENT';

/** Merges lists (e.g. PENDING + SENT) by id and sorts by dueAt, like the API does. */
export function mergeByDueAt(...lists: Reminder[][]): Reminder[] {
  const byId = new Map<string, Reminder>();
  for (const list of lists) for (const r of list) byId.set(r.id ?? `${r.title}-${r.dueAt}`, r);
  return [...byId.values()].sort((a, b) => a.dueAt.localeCompare(b.dueAt) || (a.id ?? '').localeCompare(b.id ?? ''));
}

/**
 * Open reminders grouped by when they are due (local time): overdue, today, next 7 days, later.
 * Closed views (done/cancelled) are one group, newest first.
 */
export function groupReminders(reminders: Reminder[], view: ReminderView, now = new Date()): ReminderGroup[] {
  if (view !== 'open') {
    const label = view === 'DONE' ? 'Done' : 'Cancelled';
    return reminders.length ? [{ key: 'closed', label, reminders: [...reminders].reverse() }] : [];
  }
  const startOfTomorrow = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
  const inAWeek = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 8);
  const groups: ReminderGroup[] = [
    { key: 'overdue', label: 'Overdue', reminders: [] },
    { key: 'today', label: 'Today', reminders: [] },
    { key: 'week', label: 'Next 7 days', reminders: [] },
    { key: 'later', label: 'Later', reminders: [] },
  ];
  for (const r of reminders) {
    const due = new Date(r.dueAt);
    const group =
      due < now ? groups[0] : due < startOfTomorrow ? groups[1] : due < inAWeek ? groups[2] : groups[3];
    group.reminders.push(r);
  }
  return groups.filter((g) => g.reminders.length > 0);
}
