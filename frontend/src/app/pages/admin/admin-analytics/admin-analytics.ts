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
import { FormsModule } from '@angular/forms';
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
  AnalyticsAssigneeCsm,
  AnalyticsAssigneeLoad,
  AnalyticsCsmRating,
  AnalyticsSummary,
  AnalyticsTicketList,
  AnalyticsTicketListItem,
} from '../../../core/admin/admin.models';
import { adminTicketsPathForChannel } from '../../../core/tickets/ticket.models';
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
/** Inclusive start used when exporting “all time”. */
const ALL_TIME_FROM = '2020-01-01';

type ReportMode = 'all' | 'range';

@Component({
  selector: 'app-admin-analytics',
  imports: [FormsModule, AnalyticsTicketListDialog],
  templateUrl: './admin-analytics.html',
})
export class AdminAnalytics implements OnInit, AfterViewInit, OnDestroy {
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
  /** YYYY-MM for the month recap. */
  protected readonly month = signal(this.defaultMonth());
  protected readonly monthLabel = computed(() => this.formatMonthLabel(this.month()));

  protected readonly ticketListOpen = signal(false);
  protected readonly ticketListLoading = signal(false);
  protected readonly ticketListError = signal<string | null>(null);
  protected readonly ticketList = signal<AnalyticsTicketList | null>(null);

  protected readonly reportOpen = signal(false);
  protected readonly reportMode = signal<ReportMode>('range');
  protected readonly reportFrom = signal(this.monthBounds(this.defaultMonth()).from);
  protected readonly reportTo = signal(this.monthBounds(this.defaultMonth()).to);
  protected readonly reportLoading = signal(false);
  protected readonly reportError = signal<string | null>(null);

  private charts: Chart[] = [];
  private viewReady = false;
  private loadSeq = 0;

  ngOnInit(): void {
    void this.load(this.month());
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
    this.scheduleRenderCharts();
  }

  ngOnDestroy(): void {
    this.destroyCharts();
  }

  protected onMonthChange(value: string): void {
    if (!value || value === this.month()) {
      return;
    }
    this.month.set(value);
    void this.load(value);
  }

  protected shiftMonth(delta: number): void {
    const next = this.addMonths(this.month(), delta);
    const current = this.defaultMonth();
    if (delta > 0 && next > current) {
      return;
    }
    if (next === this.month()) {
      return;
    }
    this.month.set(next);
    void this.load(next);
  }

  protected isCurrentMonth(): boolean {
    return this.month() >= this.defaultMonth();
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
    const { from, to } = this.monthBounds(this.month());
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
    const { from, to } = this.monthBounds(this.month());
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
    void this.router.navigate([adminTicketsPathForChannel(item.channel)], {
      queryParams: { ticket: item.id },
    });
  }

  protected openReportDialog(): void {
    const bounds = this.monthBounds(this.month());
    this.reportMode.set('range');
    this.reportFrom.set(bounds.from);
    this.reportTo.set(bounds.to);
    this.reportError.set(null);
    this.reportOpen.set(true);
  }

  protected closeReportDialog(): void {
    if (this.reportLoading()) {
      return;
    }
    this.reportOpen.set(false);
    this.reportError.set(null);
  }

  protected async generateReport(): Promise<void> {
    const mode = this.reportMode();
    let from: string;
    let to: string;
    let periodLabel: string;

    if (mode === 'all') {
      from = ALL_TIME_FROM;
      to = this.todayIso();
      periodLabel = 'All time';
    } else {
      from = this.reportFrom().trim();
      to = this.reportTo().trim();
      if (!/^\d{4}-\d{2}-\d{2}$/.test(from) || !/^\d{4}-\d{2}-\d{2}$/.test(to)) {
        this.reportError.set('Choose a valid from and to date.');
        return;
      }
      if (to < from) {
        this.reportError.set('“To” must be on or after “From”.');
        return;
      }
      periodLabel = `${from} to ${to}`;
    }

    this.reportLoading.set(true);
    this.reportError.set(null);
    try {
      const [summary, csmByAssignee] = await Promise.all([
        firstValueFrom(this.adminService.getAnalyticsSummary(from, to)),
        firstValueFrom(this.adminService.getCsmByAssignee(from, to)),
      ]);
      const csv = this.buildAnalyticsCsv(summary, csmByAssignee.byAssignee ?? [], periodLabel, mode);
      this.downloadCsv(csv, this.reportFilename(mode, from, to));
      this.reportOpen.set(false);
    } catch (err) {
      this.reportError.set(this.describeError(err));
    } finally {
      this.reportLoading.set(false);
    }
  }

  private buildAnalyticsCsv(
    summary: AnalyticsSummary,
    csmByAdmin: AnalyticsAssigneeCsm[],
    periodLabel: string,
    mode: ReportMode,
  ): string {
    const lines: string[] = [];
    const push = (...cells: Array<string | number | null | undefined>) => {
      lines.push(cells.map((c) => this.csvCell(c)).join(','));
    };
    const blank = () => lines.push('');

    push('LPU MISD Helpdesk — Analytics Report');
    push('Period', periodLabel);
    push('Mode', mode === 'all' ? 'All time' : 'Date span');
    push('From', summary.from);
    push('To', summary.to);
    push('Generated at', new Date().toISOString());
    blank();

    push('SECTION', 'Tickets in range');
    push('Metric', 'Value');
    push('Tickets created', summary.totals.created);
    push('Tickets closed', summary.totals.closed);
    push('Avg resolve hours', summary.totals.avgResolveHours ?? '');
    for (const row of summary.byChannel) {
      push(`Channel — ${row.label}`, row.count);
    }
    blank();

    push('SECTION', 'Commonly submitted tickets');
    push('Rank', 'Category', 'Count');
    const categories = [...summary.byCategory].sort((a, b) => b.count - a.count);
    categories.forEach((row, index) => {
      push(index + 1, row.label, row.count);
    });
    blank();

    push('SECTION', 'CSM overall');
    push('Metric', 'Value');
    push('Total ratings', summary.totals.csmCount);
    push('Sad', summary.totals.csmByRating['SAD'] ?? 0);
    push('Neh', summary.totals.csmByRating['NEUTRAL'] ?? 0);
    push('Happy', summary.totals.csmByRating['HAPPY'] ?? 0);
    push('Happy %', summary.totals.csmHappyPercent ?? '');
    blank();

    push('SECTION', 'CSM per admin');
    push('Admin', 'Sad', 'Neh', 'Happy', 'Total', 'Happy %');
    for (const row of csmByAdmin) {
      const happyPct =
        row.total > 0 ? Math.round((row.happy * 1000) / row.total) / 10 : '';
      push(row.name, row.sad, row.neutral, row.happy, row.total, happyPct);
    }

    return `\uFEFF${lines.join('\r\n')}\r\n`;
  }

  private csvCell(value: string | number | null | undefined): string {
    if (value == null) {
      return '';
    }
    const raw = String(value);
    if (/[",\r\n]/.test(raw)) {
      return `"${raw.replace(/"/g, '""')}"`;
    }
    return raw;
  }

  private downloadCsv(content: string, filename: string): void {
    const blob = new Blob([content], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    anchor.rel = 'noopener';
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  }

  private reportFilename(mode: ReportMode, from: string, to: string): string {
    if (mode === 'all') {
      return `helpdesk-analytics-all-time-${this.todayIso()}.csv`;
    }
    return `helpdesk-analytics-${from}_to_${to}.csv`;
  }

  private todayIso(): string {
    return new Date().toISOString().slice(0, 10);
  }

  private async load(month: string): Promise<void> {
    const seq = ++this.loadSeq;
    this.loading.set(true);
    this.error.set(null);
    try {
      const { from, to } = this.monthBounds(month);
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

    if (this.volumeCanvas) {
      this.charts.push(
        new Chart(this.volumeCanvas.nativeElement, {
          type: 'line',
          data: {
            labels: data.volumeByDay.map((d) => d.date.slice(5)),
            datasets: [
              {
                label: 'Created',
                data: data.volumeByDay.map((d) => d.created),
                borderColor: MAROON,
                backgroundColor: 'rgba(141, 37, 70, 0.12)',
                fill: true,
                tension: 0.3,
              },
              {
                label: 'Closed',
                data: data.volumeByDay.map((d) => d.closed),
                borderColor: EMERALD,
                backgroundColor: 'rgba(16, 185, 129, 0.08)',
                fill: true,
                tension: 0.3,
              },
            ],
          },
          options: this.baseOptions('Daily volume'),
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

  private defaultMonth(): string {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    return `${year}-${month}`;
  }

  private formatMonthLabel(month: string): string {
    const match = /^(\d{4})-(\d{2})$/.exec(month.trim());
    if (!match) {
      return month;
    }
    const date = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, 1));
    return date.toLocaleString(undefined, { month: 'long', year: 'numeric', timeZone: 'UTC' });
  }

  private addMonths(month: string, delta: number): string {
    const match = /^(\d{4})-(\d{2})$/.exec(month.trim());
    if (!match) {
      return this.defaultMonth();
    }
    const year = Number(match[1]);
    const monthIndex = Number(match[2]) - 1 + delta;
    const date = new Date(year, monthIndex, 1);
    const nextYear = date.getFullYear();
    const nextMonth = String(date.getMonth() + 1).padStart(2, '0');
    return `${nextYear}-${nextMonth}`;
  }

  /** Inclusive calendar dates for the selected YYYY-MM month (UTC). */
  private monthBounds(month: string): { from: string; to: string } {
    const match = /^(\d{4})-(\d{2})$/.exec(month.trim());
    if (!match) {
      const fallback = this.defaultMonth();
      return this.monthBounds(fallback);
    }
    const year = Number(match[1]);
    const monthIndex = Number(match[2]) - 1;
    const from = new Date(Date.UTC(year, monthIndex, 1));
    const to = new Date(Date.UTC(year, monthIndex + 1, 0));
    return {
      from: from.toISOString().slice(0, 10),
      to: to.toISOString().slice(0, 10),
    };
  }

  private describeError(err: unknown): string {
    const message =
      err && typeof err === 'object' && 'error' in err
        ? ((err as { error?: { message?: string } }).error?.message ?? null)
        : null;
    return message ?? 'Could not load analytics. Please try again.';
  }
}
