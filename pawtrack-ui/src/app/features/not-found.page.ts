import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ErrorState } from '../shared/error-state';

@Component({
  selector: 'app-not-found-page',
  imports: [ErrorState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-error-state [error]="error" backLink="/pets" backLabel="Go to my pets" />`,
})
export class NotFoundPage {
  protected readonly error = { status: 404, title: 'Page not found', message: 'There is nothing at this address.' };
}
