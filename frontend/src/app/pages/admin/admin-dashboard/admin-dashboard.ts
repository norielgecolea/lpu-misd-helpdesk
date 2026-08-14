import {
  AfterViewInit,
  Component,
  ElementRef,
  Injector,
  OnDestroy,
  OnInit,
  ViewChild,
  afterNextRender,
  inject,
  signal,
} from '@angular/core';
import {
  ArcElement,
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  DoughnutController,
  Filler,
  Legend,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
} from 'chart.js';
import { firstValueFrom } from 'rxjs';
import { Router } from '@angular/router';
import { AdminService } from '../../../core/admin/admin.service';
import {
  AnalyticsAssigneeLoad,
  AnalyticsCsmRating,
  AnalyticsDayVolume,
  AnalyticsSummary,
  AnalyticsTicketList,
  AnalyticsTicketListItem,
} from '../../../core/admin/admin.models';
import { AnalyticsTicketListDialog } from '../../../shared/analytics-ticket-list-dialog/analytics-ticket-list-dialog';

Chart.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  LineController,
  BarElement,
  BarController,
  ArcElement,
  DoughnutController,
  Tooltip,
  Legend,
  Filler,
);

const MAROON = '#8d2546';
const SKY = '#0ea5e9';
const AMBER = '#f59e0b';
const EMERALD = '#10b981';
const ZINC = '#71717a';

const MONTH_LABELS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

@Component({
  selector: 'app-admin-dashboard',
  imports: [AnalyticsTicketListDialog],
  templateUrl: './admin-dashboard.html',
})
export class AdminDashboard implements OnInit, AfterViewInit, OnDestroy {
  private readonly adminService = inject(AdminService);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);

  @ViewChild('volumeCanvas') private volumeCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('statusCanvas') private statusCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('channelCanvas') private channelCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('csmCanvas') private csmCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('categoryCanvas') private categoryCanvas?: ElementRef<HTMLCanvasElement>;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly summary = signal<AnalyticsSummary | null>(null);
  protected readonly year = this.defaultYear();

  protected readonly ticketListOpen = signal(false);
  protected readonly ticketListLoading = signal(false);
  protected readonly ticketListError = signal<string | null>(null);
  protected readonly ticketList = signal<AnalyticsTicketList | null>(null);

  private charts: Chart[] = [];
  private viewReady = false;

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

  protected async openAssigneeTickets(row: AnalyticsAssigneeLoad): Promise<void> {
    const from = `${this.year}-01-01`;
    const to = `${this.year}-12-31`;
    this.ticketListOpen.set(true);
    this.ticketListLoading.set(true);
    this.ticketListError.set(null);
    this.ticketList.set(null);
    try {
      const list = await firstValueFrom(this.adminService.getAssigneeTickets(row.adminId, from, to));
      this.ticketList.set(list);
    } catch (err) {
      this.ticketListError.set(this.describeError(err));
    } finally {
      this.ticketListLoading.set(false);
    }
  }

  protected async openCsmTickets(rating: AnalyticsCsmRating): Promise<void> {
    const from = `${this.year}-01-01`;
    const to = `${this.year}-12-31`;
    this.ticketListOpen.set(true);
    this.ticketListLoading.set(true);
    this.ticketListError.set(null);
    this.ticketList.set(null);
    try {
      const list = await firstValueFrom(this.adminService.getCsmTickets(rating, from, to));
      this.ticketList.set(list);
    } catch (err) {
      this.ticketListError.set(this.describeError(err));
    } finally {
      this.ticketListLoading.set(false);
    }
  }

  protected closeTicketList(): void {
    this.ticketListOpen.set(false);
    this.ticketList.set(null);
    this.ticketListError.set(null);
  }

  protected onTicketSelected(item: AnalyticsTicketListItem): void {
    this.closeTicketList();
    void this.router.navigate(['/admin/tickets'], {
      queryParams: { ticket: item.id },
    });
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const y = this.year;
      const data = await firstValueFrom(
        this.adminService.getAnalyticsSummary(`${y}-01-01`, `${y}-12-31`),
      );
      this.summary.set(data);
      this.scheduleRenderCharts();
    } catch (err) {
      this.summary.set(null);
      this.error.set(this.describeError(err));
      this.destroyCharts();
    } finally {
      this.loading.set(false);
    }
  }

  private scheduleRenderCharts(): void {
    afterNextRender(() => this.renderCharts(), { injector: this.injector });
    // Fallback: ViewChild canvases may not exist until after the @if block paints.
    setTimeout(() => this.renderCharts());
  }

  private renderCharts(): void {
    if (!this.viewReady) {
      return;
    }
    const data = this.summary();
    if (!data) {
      return;
    }
    this.destroyCharts();

    const monthly = this.aggregateByMonth(data.volumeByDay);

    if (this.volumeCanvas) {
      this.charts.push(
        new Chart(this.volumeCanvas.nativeElement, {
          type: 'bar',
          data: {
            labels: MONTH_LABELS,
            datasets: [
              {
                label: 'Created',
                data: monthly.map((m) => m.created),
                backgroundColor: MAROON,
              },
              {
                label: 'Closed',
                data: monthly.map((m) => m.closed),
                backgroundColor: EMERALD,
              },
            ],
          },
          options: this.baseOptions('Monthly volume'),
        }),
      );
    }

    if (this.statusCanvas) {
      this.charts.push(
        new Chart(this.statusCanvas.nativeElement, {
          type: 'doughnut',
          data: {
            labels: data.byStatus.map((s) => s.label),
            datasets: [
              {
                data: data.byStatus.map((s) => s.count),
                backgroundColor: [AMBER, SKY, EMERALD, ZINC],
              },
            ],
          },
          options: this.doughnutOptions('Status mix'),
        }),
      );
    }

    if (this.channelCanvas) {
      this.charts.push(
        new Chart(this.channelCanvas.nativeElement, {
          type: 'doughnut',
          data: {
            labels: data.byChannel.map((c) => c.label),
            datasets: [
              {
                data: data.byChannel.map((c) => c.count),
                backgroundColor: [MAROON, SKY],
              },
            ],
          },
          options: this.doughnutOptions('Channel mix (created)'),
        }),
      );
    }

    if (this.csmCanvas) {
      const ratings = data.totals.csmByRating;
      const ratingKeys: AnalyticsCsmRating[] = ['SAD', 'NEUTRAL', 'HAPPY'];
      this.charts.push(
        new Chart(this.csmCanvas.nativeElement, {
          type: 'bar',
          data: {
            labels: ['Sad', 'Neh', 'Happy'],
            datasets: [
              {
                label: 'CSM ratings',
                data: [ratings['SAD'] ?? 0, ratings['NEUTRAL'] ?? 0, ratings['HAPPY'] ?? 0],
                backgroundColor: ['#ef4444', AMBER, EMERALD],
              },
            ],
          },
          options: {
            ...this.baseOptions('CSM faces'),
            onClick: (_event, elements) => {
              if (!elements.length) {
                return;
              }
              const index = elements[0].index;
              const rating = ratingKeys[index];
              if (rating) {
                void this.openCsmTickets(rating);
              }
            },
            onHover: (event, elements) => {
              const target = event.native?.target as HTMLElement | undefined;
              if (target) {
                target.style.cursor = elements.length ? 'pointer' : 'default';
              }
            },
            plugins: {
              ...this.baseOptions('CSM faces').plugins,
              legend: { display: false },
            },
          },
        }),
      );
    }

    if (this.categoryCanvas) {
      const cats = [...data.byCategory].slice(0, 8).reverse();
      this.charts.push(
        new Chart(this.categoryCanvas.nativeElement, {
          type: 'bar',
          data: {
            labels: cats.map((c) => c.label),
            datasets: [
              {
                label: 'Tickets',
                data: cats.map((c) => c.count),
                backgroundColor: MAROON,
              },
            ],
          },
          options: {
            indexAxis: 'y',
            ...this.baseOptions('Top categories'),
            plugins: {
              ...this.baseOptions('Top categories').plugins,
              legend: { display: false },
            },
          },
        }),
      );
    }
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

  private baseOptions(title: string) {
    return {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          labels: { boxWidth: 12, font: { size: 11 } },
        },
        title: { display: false, text: title },
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
    };
  }

  private doughnutOptions(title: string) {
    return {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          position: 'bottom' as const,
          labels: { boxWidth: 10, font: { size: 11 } },
        },
        title: { display: false, text: title },
      },
    };
  }

  private destroyCharts(): void {
    for (const chart of this.charts) {
      chart.destroy();
    }
    this.charts = [];
  }

  private defaultYear(): number {
    return new Date().getUTCFullYear();
  }

  private describeError(err: unknown): string {
    const message =
      err && typeof err === 'object' && 'error' in err
        ? ((err as { error?: { message?: string } }).error?.message ?? null)
        : null;
    return message ?? 'Could not load dashboard. Please try again.';
  }
}
