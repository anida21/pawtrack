import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { Weight } from '../../core/api/models';

const W = 640;
const H = 240;
const PAD = { top: 16, right: 16, bottom: 28, left: 44 };

export interface ChartPoint {
  x: number;
  y: number;
  weight: Weight;
}

/** Rounded y-axis ticks around the data, at least 4 steps. */
export function niceTicks(min: number, max: number, count = 4): number[] {
  if (min === max) {
    const pad = Math.max(Math.abs(min) * 0.1, 0.5);
    min -= pad;
    max += pad;
  }
  const raw = (max - min) / count;
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const step = [1, 2, 2.5, 5, 10].map((m) => m * magnitude).find((s) => s >= raw) ?? raw;
  const start = Math.floor(min / step) * step;
  const end = Math.ceil(max / step) * step;
  const ticks: number[] = [];
  for (let v = start; v <= end + step / 2; v += step) ticks.push(Number(v.toFixed(6)));
  return ticks;
}

/**
 * Single-series weight line (oldest → newest). Hand-drawn SVG: 2px line, 8px markers with a surface
 * ring, recessive grid, crosshair + tooltip on hover/focus. The list next to it is the table view.
 */
@Component({
  selector: 'app-weight-chart',
  imports: [DatePipe, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <figure class="chart" [attr.aria-label]="summary()">
      <svg
        [attr.viewBox]="'0 0 ' + width + ' ' + height"
        role="img"
        [attr.aria-label]="summary()"
        (pointerleave)="active.set(null)"
      >
        @for (tick of ticks(); track tick) {
          <line class="grid" [attr.x1]="pad.left" [attr.x2]="width - pad.right" [attr.y1]="yOf(tick)" [attr.y2]="yOf(tick)" />
          <text class="axis" [attr.x]="pad.left - 8" [attr.y]="yOf(tick)" text-anchor="end" dominant-baseline="middle">
            {{ tick | number: '1.0-2' }}
          </text>
        }
        @for (label of xLabels(); track label.x) {
          <text class="axis" [attr.x]="label.x" [attr.y]="height - 8" [attr.text-anchor]="label.anchor">
            {{ label.date | date: 'MMM d, y' }}
          </text>
        }
        @if (active(); as a) {
          <line class="crosshair" [attr.x1]="a.x" [attr.x2]="a.x" [attr.y1]="pad.top" [attr.y2]="height - pad.bottom" />
        }
        @if (points().length > 1) {
          <path class="line" [attr.d]="path()" />
        }
        @for (p of points(); track p.weight.id ?? $index) {
          <circle
            class="marker"
            [class.active]="active() === p"
            [attr.cx]="p.x"
            [attr.cy]="p.y"
            r="4"
            tabindex="0"
            [attr.aria-label]="(p.weight.weightKg | number: '1.0-3') + ' kg on ' + (p.weight.measuredOn | date: 'mediumDate')"
            (focus)="active.set(p)"
            (blur)="active.set(null)"
          />
        }
        <!-- Hit area larger than the marks: nearest point by x. -->
        <rect
          class="hit"
          [attr.x]="pad.left"
          [attr.y]="pad.top"
          [attr.width]="width - pad.left - pad.right"
          [attr.height]="height - pad.top - pad.bottom"
          (pointermove)="onMove($event)"
        />
      </svg>
      @if (active(); as a) {
        <div
          class="tooltip"
          role="status"
          [style.left.%]="(a.x / width) * 100"
          [style.top.%]="(a.y / height) * 100"
          [class.flip]="a.x > width * 0.7"
        >
          <strong>{{ a.weight.weightKg | number: '1.0-3' }} kg</strong>
          <span>{{ a.weight.measuredOn | date: 'mediumDate' }}</span>
        </div>
      }
    </figure>
  `,
  styles: `
    .chart {
      position: relative;
      margin: 0;
    }
    svg {
      display: block;
      width: 100%;
      height: auto;
      overflow: visible;
    }
    .grid {
      stroke: var(--mat-sys-outline-variant);
      stroke-width: 1;
    }
    .axis {
      fill: var(--mat-sys-on-surface-variant);
      font: 11px var(--mat-sys-body-small-font, sans-serif);
    }
    .line {
      fill: none;
      stroke: var(--mat-sys-primary);
      stroke-width: 2;
      stroke-linejoin: round;
      stroke-linecap: round;
    }
    .marker {
      fill: var(--mat-sys-primary);
      stroke: var(--mat-sys-surface-container-low);
      stroke-width: 2;
      outline: none;
      &.active,
      &:focus-visible {
        r: 6;
      }
    }
    .crosshair {
      stroke: var(--mat-sys-outline);
      stroke-width: 1;
      stroke-dasharray: 3 3;
    }
    .hit {
      fill: transparent;
      cursor: crosshair;
    }
    .tooltip {
      position: absolute;
      transform: translate(12px, -50%);
      display: flex;
      flex-direction: column;
      padding: 6px 10px;
      border-radius: 8px;
      background: var(--mat-sys-inverse-surface);
      color: var(--mat-sys-inverse-on-surface);
      font: var(--mat-sys-body-small);
      white-space: nowrap;
      pointer-events: none;
      &.flip {
        transform: translate(calc(-100% - 12px), -50%);
      }
    }
  `,
})
export class WeightChart {
  readonly weights = input.required<Weight[]>();

  protected readonly width = W;
  protected readonly height = H;
  protected readonly pad = PAD;
  protected readonly active = signal<ChartPoint | null>(null);

  private readonly times = computed(() => this.weights().map((w) => Date.parse(w.measuredOn)));
  protected readonly ticks = computed(() => {
    const kg = this.weights().map((w) => w.weightKg);
    return kg.length ? niceTicks(Math.min(...kg), Math.max(...kg)) : [0, 1];
  });

  private xScale(t: number): number {
    const times = this.times();
    const min = Math.min(...times);
    const max = Math.max(...times);
    const span = W - PAD.left - PAD.right;
    return max === min ? PAD.left + span / 2 : PAD.left + ((t - min) / (max - min)) * span;
  }

  protected yOf(kg: number): number {
    const ticks = this.ticks();
    const lo = ticks[0];
    const hi = ticks[ticks.length - 1];
    return PAD.top + (1 - (kg - lo) / (hi - lo)) * (H - PAD.top - PAD.bottom);
  }

  protected readonly points = computed<ChartPoint[]>(() =>
    this.weights().map((weight, i) => ({ x: this.xScale(this.times()[i]), y: this.yOf(weight.weightKg), weight })),
  );

  protected readonly path = computed(() =>
    this.points()
      .map((p, i) => `${i ? 'L' : 'M'}${p.x.toFixed(1)},${p.y.toFixed(1)}`)
      .join(' '),
  );

  protected readonly xLabels = computed(() => {
    const points = this.points();
    if (points.length === 0) return [];
    const first = points[0];
    const last = points[points.length - 1];
    if (points.length === 1) return [{ x: first.x, date: first.weight.measuredOn, anchor: 'middle' }];
    return [
      { x: first.x, date: first.weight.measuredOn, anchor: 'start' },
      { x: last.x, date: last.weight.measuredOn, anchor: 'end' },
    ];
  });

  protected readonly summary = computed(() => {
    const w = this.weights();
    if (!w.length) return 'No weight measurements';
    const first = w[0];
    const last = w[w.length - 1];
    return `Weight over time: ${w.length} measurements, from ${first.weightKg} kg on ${first.measuredOn} to ${last.weightKg} kg on ${last.measuredOn}`;
  });

  protected onMove(event: PointerEvent): void {
    const svg = (event.target as SVGElement).ownerSVGElement;
    if (!svg) return;
    const rect = svg.getBoundingClientRect();
    const x = ((event.clientX - rect.left) / rect.width) * W;
    let nearest: ChartPoint | null = null;
    for (const p of this.points()) {
      if (!nearest || Math.abs(p.x - x) < Math.abs(nearest.x - x)) nearest = p;
    }
    this.active.set(nearest);
  }
}
