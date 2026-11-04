import {
  AfterViewInit,
  Component,
  ElementRef,
  Injector,
  OnDestroy,
  OnInit,
  ViewChild,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  Filler,
  Legend,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
} from 'chart.js';
import { firstValueFrom } from 'rxjs';
import { AdminService } from '../../../core/admin/admin.service';
import { AnalyticsDayVolume, AnalyticsSummary } from '../../../core/admin/admin.models';

Chart.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  LineController,
  BarElement,
  BarController,
  Tooltip,
  Legend,
  Filler,
);

const MAROON = '#8d2546';
const EMERALD = '#10b981';
const MONTH_LABELS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

type DashboardPeriod = 'today' | '7d' | '30d' | 'year';

@Component({
  selector: 'app-admin-dashboard',
  imports: [RouterLink],
  templateUrl: './admin-dashboard.html',
})
export class AdminDashboard implements OnInit, AfterViewInit, OnDestroy {
  private readonly adminService = inject(AdminService);
  private readonly injector = inject(Injector);

  @ViewChild('volumeCanvas') private volumeCanvas?: ElementRef<HTMLCanvasElement>;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly summary = signal<AnalyticsSummary | null>(null);
  protected readonly period = signal<DashboardPeriod>('30d');
  protected readonly periods: { id: DashboardPeriod; label: string }[] = [
    { id: 'today', label: 'Today' },
    { id: '7d', label: '7 days' },
    { id: '30d', label: '30 days' },
    { id: 'year', label: 'This year' },
  ];

  protected readonly periodLabel = computed(() => {
    switch (this.period()) {
      case 'today':
        return 'Today';
      case '7d':
        return 'Last 7 days';
      case '30d':
        return 'Last 30 days';
      case 'year':
        return `Year ${new Date().getFullYear()}`;
    }
  });

  protected readonly volumeTitle = computed(() =>
    this.period() === 'year' ? 'Monthly volume' : 'Daily volume',
  );

  protected readonly closeRate = computed(() => {
    const data = this.summary();
    if (!data || data.totals.created === 0) {
      return null;
    }
    return Math.round((data.totals.closed * 1000) / data.totals.created) / 10;
  });

  protected readonly activeNow = computed(() => {
    const data = this.summary();
    if (!data) {
      return 0;
    }
    return data.totals.open + data.totals.inProgress;
  });

  private charts: Chart[] = [];
  private viewReady = false;
  private loadSeq = 0;

  ngOnInit(): void {
    void this.load();
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
    this.scheduleRenderCharts();
  }

  ngOnDestroy(): void {
    this.destroyCharts();
  }

  protected setPeriod(period: DashboardPeriod): void {
    if (period === this.period()) {
      return;
    }
    this.period.set(period);
    void this.load();
  }

  protected formatHours(value: number | null | undefined): string {
    if (value == null) {
      return '—';
    }
    return `${value.toFixed(1)} h`;
  }

  protected formatPercent(value: number | null | undefined): string {
    if (value == null) {
      return '—';
    }
    return `${value}%`;
  }

  private async load(): Promise<void> {
    const seq = ++this.loadSeq;
    this.loading.set(true);
    this.error.set(null);
    try {
      const { from, to } = this.periodBounds();
      const data = await firstValueFrom(this.adminService.getAnalyticsSummary(from, to));
      if (seq !== this.loadSeq) {
        return;
      }
      this.summary.set(data);
      this.scheduleRenderCharts();
    } catch (err) {
      if (seq !== this.loadSeq) {
        return;
      }
      this.summary.set(null);
      this.error.set(this.describeError(err));
      this.destroyCharts();
    } finally {
      if (seq === this.loadSeq) {
        this.loading.set(false);
      }
    }
  }

  private scheduleRenderCharts(): void {
    afterNextRender(() => this.renderCharts(), { injector: this.injector });
    setTimeout(() => this.renderCharts());
  }

  private renderCharts(): void {
    if (!this.viewReady) {
      return;
    }
    const data = this.summary();
    if (!data || !this.volumeCanvas) {
      return;
    }
    this.destroyCharts();

    const yearly = this.period() === 'year';
    const monthly = yearly ? this.aggregateByMonth(data.volumeByDay) : [];
    this.charts.push(
      new Chart(this.volumeCanvas.nativeElement, {
        type: yearly ? 'bar' : 'line',
        data: {
          labels: yearly ? MONTH_LABELS : data.volumeByDay.map((d) => d.date.slice(5)),
          datasets: [
            {
              label: 'Created',
              data: yearly ? monthly.map((m) => m.created) : data.volumeByDay.map((d) => d.created),
              borderColor: MAROON,
              backgroundColor: yearly ? MAROON : 'rgba(141, 37, 70, 0.12)',
              fill: !yearly,
              tension: 0.3,
            },
            {
              label: 'Closed',
              data: yearly ? monthly.map((m) => m.closed) : data.volumeByDay.map((d) => d.closed),
              borderColor: EMERALD,
              backgroundColor: yearly ? EMERALD : 'rgba(16, 185, 129, 0.08)',
              fill: !yearly,
              tension: 0.3,
            },
          ],
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: {
              labels: { boxWidth: 12, font: { size: 11 } },
            },
          },
          scales: {
            x: {
              ticks: { font: { size: 10 }, maxRotation: 0 },
              grid: { display: false },
            },
            y: {
              beginAtZero: true,
              ticks: { font: { size: 10 }, precision: 0 },
              grid: { color: 'rgba(24, 24, 27, 0.06)' },
            },
          },
        },
      }),
    );
  }

  private aggregateByMonth(days: AnalyticsDayVolume[]): { created: number; closed: number }[] {
    const buckets = Array.from({ length: 12 }, () => ({ created: 0, closed: 0 }));
    for (const day of days) {
      const monthIndex = Number(day.date.slice(5, 7)) - 1;
      if (monthIndex >= 0 && monthIndex < 12) {
        buckets[monthIndex].created += day.created;
        buckets[monthIndex].closed += day.closed;
      }
    }
    return buckets;
  }

  private destroyCharts(): void {
    for (const chart of this.charts) {
      chart.destroy();
    }
    this.charts = [];
  }

  private periodBounds(): { from: string; to: string } {
    const to = this.todayIso();
    switch (this.period()) {
      case 'today':
        return { from: to, to };
      case '7d':
        return { from: this.addDays(to, -6), to };
      case '30d':
        return { from: this.addDays(to, -29), to };
      case 'year': {
        const year = new Date().getFullYear();
        return { from: `${year}-01-01`, to: `${year}-12-31` };
      }
    }
  }

  private todayIso(): string {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  }

  private addDays(iso: string, days: number): string {
    const [year, month, day] = iso.split('-').map(Number);
    const date = new Date(year, month - 1, day + days);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }

  private describeError(err: unknown): string {
    const message =
      err && typeof err === 'object' && 'error' in err
        ? ((err as { error?: { message?: string } }).error?.message ?? null)
        : null;
    return message ?? 'Could not load dashboard. Please try again.';
  }
}
