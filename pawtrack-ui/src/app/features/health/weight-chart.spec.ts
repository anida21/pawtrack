import { TestBed } from '@angular/core/testing';
import { WeightChart } from './weight-chart';

describe('WeightChart', () => {
  it('draws one marker per measurement and a line between them', async () => {
    const fixture = TestBed.createComponent(WeightChart);
    fixture.componentRef.setInput('weights', [
      { id: 'a', measuredOn: '2026-01-01', weightKg: 10 },
      { id: 'b', measuredOn: '2026-03-01', weightKg: 11.5 },
      { id: 'c', measuredOn: '2026-06-01', weightKg: 11 },
    ]);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('circle.marker').length).toBe(3);
    expect(el.querySelector('path.line')?.getAttribute('d')).toMatch(/^M[\d.]+,[\d.]+ L/);
    expect(el.querySelector('svg')?.getAttribute('aria-label')).toContain('3 measurements');
  });

  it('shows a tooltip when a marker gets focus', async () => {
    const fixture = TestBed.createComponent(WeightChart);
    fixture.componentRef.setInput('weights', [{ id: 'a', measuredOn: '2026-01-01', weightKg: 9.25 }]);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('path.line')).toBeNull();
    el.querySelector('circle.marker')?.dispatchEvent(new FocusEvent('focus'));
    await fixture.whenStable();
    expect(el.querySelector('.tooltip')?.textContent).toContain('9.25 kg');
  });
});
