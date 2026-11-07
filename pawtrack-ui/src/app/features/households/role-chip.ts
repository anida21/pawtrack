import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { HouseholdRole } from '../../core/api/models';

const LABELS: Record<HouseholdRole, string> = { OWNER: 'Owner', MEMBER: 'Member', SITTER: 'Pet-sitter' };

@Component({
  selector: 'app-role-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="role-chip" [class.owner]="role() === 'OWNER'" [class.sitter]="role() === 'SITTER'">{{ label() }}</span>`,
})
export class RoleChip {
  readonly role = input<HouseholdRole | undefined>();
  protected readonly label = computed(() => {
    const role = this.role();
    return role ? LABELS[role] : '';
  });
}
