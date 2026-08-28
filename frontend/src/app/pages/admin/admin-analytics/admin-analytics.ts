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
import { Router, RouterLink } from '@angular/router';
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
import { AdminService } from '../../../core/admin/admin.service';
import {
  AdminCategory,
  AnalyticsAssigneeCsm,
  AnalyticsAssigneeLoad,
  AnalyticsConcernCount,
  AnalyticsCsmRating,
  AnalyticsDayVolume,
  AnalyticsSummary,
  AnalyticsTicketList,
  AnalyticsTicketListItem,
} from '../../../core/admin/admin.models';
import { CSM_CHART_LABELS, CSM_LABEL } from '../../../core/csm/csm-labels';
import {
  adminTicketsPathForChannel,
  formatResolveDuration,
  ticketResolveHours,
} from '../../../core/tickets/ticket.models';
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
const ALL_TIME_FROM = '2020-01-01';

type DashboardPeriod = 'today' | '7d' | '30d' | 'year';
type ReportMode = 'all' | 'range';
type ReportKind = 'analytics' | 'tickets';

@Component({
  selector: 'app-admin-analytics',
  imports: [FormsModule, AnalyticsTicketListDialog, RouterLink],
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

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly summary = signal<AnalyticsSummary | null>(null);
  protected readonly resolvedTickets = signal<AnalyticsTicketListItem[]>([]);
  protected readonly period = signal<DashboardPeriod>('30d');
  protected readonly periods: { id: DashboardPeriod; label: string }[] = [
    { id: 'today', label: 'Today' },
    { id: '7d', label: '7 days' },
    { id: '30d', label: '30 days' },
    { id: 'year', label: 'This year' },
  ];

  protected readonly ticketListOpen = signal(false);
  protected readonly ticketListLoading = signal(false);
  protected readonly ticketListError = signal<string | null>(null);
  protected readonly ticketList = signal<AnalyticsTicketList | null>(null);
  protected readonly csmLabels = CSM_LABEL;

  protected readonly reportOpen = signal(false);
  protected readonly reportKind = signal<ReportKind>('analytics');
  protected readonly reportMode = signal<ReportMode>('range');
  protected readonly reportFrom = signal(this.periodBounds().from);
  protected readonly reportTo = signal(this.periodBounds().to);
  protected readonly reportLoading = signal(false);
  protected readonly reportError = signal<string | null>(null);

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

  protected readonly topConcerns = computed(() => (this.summary()?.byConcern ?? []).slice(0, 10));

  protected readonly maxConcernCount = computed(() => {
    const first = this.topConcerns()[0];
    return first?.count || 1;
  });

  protected readonly maxAssigneeActive = computed(() => {
    const rows = this.summary()?.byAssignee ?? [];
    return Math.max(1, ...rows.map((row) => row.open + row.inProgress));
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

  protected shareOfCreated(count: number): string {
    const created = this.summary()?.totals.created ?? 0;
    if (!created) {
      return '—';
    }
    return `${Math.round((count * 1000) / created) / 10}%`;
  }

  protected assigneeActive(row: AnalyticsAssigneeLoad): number {
    return row.open + row.inProgress;
  }

  protected concernBarWidth(row: AnalyticsConcernCount): string {
    return `${Math.max(4, Math.round((row.count * 100) / this.maxConcernCount()))}%`;
  }

  protected assigneeBarWidth(row: AnalyticsAssigneeLoad): string {
    return `${Math.max(4, Math.round((this.assigneeActive(row) * 100) / this.maxAssigneeActive()))}%`;
  }

  protected concernTrack(row: AnalyticsConcernCount): string {
    return `${row.categoryKey}:${row.concernKey}`;
  }

  protected resolveDuration(item: AnalyticsTicketListItem): string {
    return formatResolveDuration(ticketResolveHours(item));
  }

  protected async openAssigneeTickets(row: AnalyticsAssigneeLoad): Promise<void> {
    const { from, to } = this.periodBounds();
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
    const { from, to } = this.periodBounds();
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

  protected openResolvedTicket(item: AnalyticsTicketListItem): void {
    void this.router.navigate([adminTicketsPathForChannel(item.channel)], {
      queryParams: { ticket: item.id },
    });
  }

  protected openAnalyticsReportDialog(): void {
    this.openReportDialog('analytics');
  }

  protected openTicketReportDialog(): void {
    this.openReportDialog('tickets');
  }

  private openReportDialog(kind: ReportKind): void {
    const bounds = this.periodBounds();
    this.reportKind.set(kind);
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
    const range = this.resolveReportRange();
    if (!range) {
      return;
    }
    const { from, to, periodLabel, mode } = range;
    const kind = this.reportKind();

    this.reportLoading.set(true);
    this.reportError.set(null);
    try {
      if (kind === 'tickets') {
        const [summary, tickets] = await Promise.all([
          firstValueFrom(this.adminService.getAnalyticsSummary(from, to)),
          firstValueFrom(this.adminService.getCreatedTickets(from, to, 20000)),
        ]);
        const csv = this.buildTicketCsv(
          { ...summary, byConcern: summary.byConcern ?? [] },
          tickets,
          periodLabel,
          mode,
        );
        this.downloadCsv(csv, this.reportFilename('tickets', mode, from, to));
      } else {
        const [summary, csmByAssignee, categories] = await Promise.all([
          firstValueFrom(this.adminService.getAnalyticsSummary(from, to)),
          firstValueFrom(this.adminService.getCsmByAssignee(from, to)),
          firstValueFrom(this.adminService.listCategories()),
        ]);
        const csv = this.buildAnalyticsCsv(
          { ...summary, byConcern: summary.byConcern ?? [] },
          csmByAssignee.byAssignee ?? [],
          categories,
          periodLabel,
          mode,
        );
        this.downloadCsv(csv, this.reportFilename('analytics', mode, from, to));
      }
      this.reportOpen.set(false);
    } catch (err) {
      this.reportError.set(this.describeError(err));
    } finally {
      this.reportLoading.set(false);
    }
  }

  private resolveReportRange(): { from: string; to: string; periodLabel: string; mode: ReportMode } | null {
    const mode = this.reportMode();
    if (mode === 'all') {
      return { from: ALL_TIME_FROM, to: this.todayIso(), periodLabel: 'All time', mode };
    }
    const from = this.reportFrom().trim();
    const to = this.reportTo().trim();
    if (!/^\d{4}-\d{2}-\d{2}$/.test(from) || !/^\d{4}-\d{2}-\d{2}$/.test(to)) {
      this.reportError.set('Choose a valid from and to date.');
      return null;
    }
    if (to < from) {
      this.reportError.set('“To” must be on or after “From”.');
      return null;
    }
    return { from, to, periodLabel: `${from} to ${to}`, mode };
  }

  private buildAnalyticsCsv(
    summary: AnalyticsSummary,
    csmByAdmin: AnalyticsAssigneeCsm[],
    categories: AdminCategory[],
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

    push('SECTION', 'Concerns per tickets');
    push('Rank', 'Category', 'Concern', 'Tickets', '% of tickets');
    const created = summary.totals.created || 0;
    this.allConcernRows(categories, summary.byConcern ?? []).forEach((row, index) => {
      const share = created > 0 ? Math.round((row.count * 1000) / created) / 10 : '';
      push(index + 1, row.categoryLabel, row.concernLabel, row.count, share);
    });
    blank();

    push('SECTION', 'CSM overall');
    push('Metric', 'Value');
    push('Total ratings', summary.totals.csmCount);
    push(CSM_LABEL.SAD, summary.totals.csmByRating['SAD'] ?? 0);
    push(CSM_LABEL.NEUTRAL, summary.totals.csmByRating['NEUTRAL'] ?? 0);
    push(CSM_LABEL.HAPPY, summary.totals.csmByRating['HAPPY'] ?? 0);
    push(`${CSM_LABEL.HAPPY} %`, summary.totals.csmHappyPercent ?? '');
    blank();

    push('SECTION', 'CSM per admin');
    push('Admin', CSM_LABEL.SAD, CSM_LABEL.NEUTRAL, CSM_LABEL.HAPPY, 'Total', `${CSM_LABEL.HAPPY} %`);
    for (const row of csmByAdmin) {
      const happyPct = row.total > 0 ? Math.round((row.happy * 1000) / row.total) / 10 : '';
      push(row.name, row.sad, row.neutral, row.happy, row.total, happyPct);
    }

    return `\uFEFF${lines.join('\r\n')}\r\n`;
  }

  private buildTicketCsv(
    summary: AnalyticsSummary,
    tickets: AnalyticsTicketList,
    periodLabel: string,
    mode: ReportMode,
  ): string {
    const lines: string[] = [];
    const push = (...cells: Array<string | number | null | undefined>) => {
      lines.push(cells.map((c) => this.csvCell(c)).join(','));
    };
    const blank = () => lines.push('');
    const items = tickets.items ?? [];

    push('LPU MISD Helpdesk — Ticket Report');
    push('Period', periodLabel);
    push('Mode', mode === 'all' ? 'All time' : 'Date span');
    push('From', summary.from);
    push('To', summary.to);
    push('Generated at', new Date().toISOString());
    blank();

    push('SECTION', 'Summary');
    push('Metric', 'Value');
    push('Tickets in report', items.length);
    push('Tickets created in period', summary.totals.created);
    push('Average resolve hours', summary.totals.avgResolveHours ?? '');
    if (tickets.truncated) {
      push('Note', `List truncated at ${tickets.limit} tickets`);
    }
    blank();

    push('SECTION', 'Tickets');
    push(
      'Ticket',
      'Subject',
      'Description',
      'Status',
      'Channel',
      'Category',
      'Requester',
      'Email',
      'Assignee',
      'Created',
      'Resolved',
      'Resolve hours',
    );
    for (const item of items) {
      push(
        item.ticketNumber,
        item.subject,
        item.description ?? '',
        item.status,
        item.channel === 'ONSITE_RFID' ? 'Onsite' : item.channel === 'ONLINE' ? 'Online' : item.channel,
        item.categoryPath || item.categoryLabel,
        item.requesterName,
        item.requesterEmail,
        item.assignedAdminName ?? 'Unassigned',
        item.createdAt,
        item.resolvedAt ?? '',
        ticketResolveHours(item) ?? '',
      );
    }

    return `\uFEFF${lines.join('\r\n')}\r\n`;
  }

  private allConcernRows(
    categories: AdminCategory[],
    byConcern: AnalyticsConcernCount[],
  ): AnalyticsConcernCount[] {
    const countByKey = new Map<string, number>();
    for (const row of byConcern) {
      countByKey.set(`${row.categoryKey}::${row.concernKey}`, row.count);
    }
    const rows: AnalyticsConcernCount[] = [];
    const seen = new Set<string>();
    for (const parent of categories) {
      for (const child of parent.children ?? []) {
        const key = `${parent.code}::${child.code}`;
        seen.add(key);
        rows.push({
          categoryKey: parent.code,
          categoryLabel: parent.label,
          concernKey: child.code,
          concernLabel: child.label,
          count: countByKey.get(key) ?? 0,
        });
      }
    }
    for (const row of byConcern) {
      const key = `${row.categoryKey}::${row.concernKey}`;
      if (!seen.has(key)) {
        rows.push(row);
      }
    }
    return rows;
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

  private reportFilename(kind: ReportKind, mode: ReportMode, from: string, to: string): string {
    const prefix = kind === 'tickets' ? 'helpdesk-tickets' : 'helpdesk-analytics';
    if (mode === 'all') {
      return `${prefix}-all-time-${this.todayIso()}.csv`;
    }
    return `${prefix}-${from}_to_${to}.csv`;
  }

  private async load(): Promise<void> {
    const seq = ++this.loadSeq;
    this.loading.set(true);
    this.error.set(null);
    try {
      const { from, to } = this.periodBounds();
      const [data, resolved] = await Promise.all([
        firstValueFrom(this.adminService.getAnalyticsSummary(from, to)),
        firstValueFrom(this.adminService.getResolvedTickets(from, to)),
      ]);
      if (seq !== this.loadSeq) {
        return;
      }
      this.summary.set({
        ...data,
        byConcern: data.byConcern ?? [],
      });
      this.resolvedTickets.set(resolved.items ?? []);
      this.scheduleRenderCharts();
    } catch (err) {
      if (seq !== this.loadSeq) {
        return;
      }
      this.summary.set(null);
      this.resolvedTickets.set([]);
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
    if (!data) {
      return;
    }
    this.destroyCharts();

    if (this.volumeCanvas) {
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
          options: this.baseOptions(this.volumeTitle()),
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
          options: this.doughnutOptions('Channel mix'),
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
            labels: [...CSM_CHART_LABELS],
            datasets: [
              {
                label: 'CSM ratings',
                data: [ratings['SAD'] ?? 0, ratings['NEUTRAL'] ?? 0, ratings['HAPPY'] ?? 0],
                backgroundColor: ['#ef4444', AMBER, EMERALD],
              },
            ],
          },
          options: {
            ...this.baseOptions('CSM ratings'),
            onClick: (_event, elements) => {
              if (!elements.length) {
                return;
              }
              const rating = ratingKeys[elements[0].index];
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
              ...this.baseOptions('CSM ratings').plugins,
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
      cutout: '62%',
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
    return message ?? 'Could not load analytics. Please try again.';
  }
}
