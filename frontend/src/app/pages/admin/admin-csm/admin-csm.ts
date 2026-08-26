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
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
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
  AnalyticsAssigneeCsm,
  AnalyticsSummary,
  AnalyticsTicketListItem,
} from '../../../core/admin/admin.models';
import { adminTicketsPathForChannel } from '../../../core/tickets/ticket.models';
import { CSM_LABEL } from '../../../core/csm/csm-labels';

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

const AMBER = '#f59e0b';
const EMERALD = '#10b981';
const RED = '#ef4444';

type CsmDetailTab = 'assigned' | 'happy' | 'neutral' | 'sad';

@Component({
  selector: 'app-admin-csm',
  imports: [FormsModule, DatePipe],
  templateUrl: './admin-csm.html',
})
export class AdminCsm implements OnInit, AfterViewInit, OnDestroy {
  private readonly adminService = inject(AdminService);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);

  @ViewChild('trendCanvas') private trendCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('mixCanvas') private mixCanvas?: ElementRef<HTMLCanvasElement>;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly summary = signal<AnalyticsSummary | null>(null);
  protected readonly byAssignee = signal<AnalyticsAssigneeCsm[]>([]);
  protected readonly month = signal(this.defaultMonth());
  protected readonly monthLabel = computed(() => this.formatMonthLabel(this.month()));
  protected readonly selectedAdminId = signal<number | null>(null);
  protected readonly detailTab = signal<CsmDetailTab>('sad');

  protected readonly assignedTickets = signal<AnalyticsTicketListItem[]>([]);
  protected readonly happyTickets = signal<AnalyticsTicketListItem[]>([]);
  protected readonly neutralTickets = signal<AnalyticsTicketListItem[]>([]);
  protected readonly sadTickets = signal<AnalyticsTicketListItem[]>([]);
  protected readonly detailLoading = signal(false);
  protected readonly detailError = signal<string | null>(null);
  protected readonly csmLabels = CSM_LABEL;

  protected readonly rankedAdmins = computed(() =>
    [...this.byAssignee()].sort((a, b) => {
      const pct = this.happyPercent(b) - this.happyPercent(a);
      if (pct !== 0) {
        return pct;
      }
      return b.total - a.total;
    }),
  );

  protected readonly selectedAdmin = computed(() => {
    const id = this.selectedAdminId();
    if (id == null) {
      return null;
    }
    return this.byAssignee().find((a) => a.adminId === id) ?? null;
  });

  protected readonly sadComments = computed(() =>
    this.sadTickets().filter((item) => !!item.csmComment?.trim()),
  );

  protected readonly detailTickets = computed(() => {
    switch (this.detailTab()) {
      case 'sad':
        return this.sadTickets();
      case 'neutral':
        return this.neutralTickets();
      case 'happy':
        return this.happyTickets();
      default:
        return this.assignedTickets();
    }
  });

  protected readonly coveragePercent = computed(() => {
    const data = this.summary();
    if (!data || data.totals.closed === 0) {
      return null;
    }
    return Math.round((data.totals.csmCount * 1000) / data.totals.closed) / 10;
  });

  private charts: Chart[] = [];
  private viewReady = false;
  private loadSeq = 0;
  private detailSeq = 0;

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

  protected onAdminChange(raw: string): void {
    const id = raw ? Number(raw) : null;
    this.selectedAdminId.set(Number.isFinite(id) ? id : null);
    this.detailTab.set('sad');
    void this.loadAdminDetails();
  }

  protected selectAdmin(adminId: number): void {
    this.selectedAdminId.set(adminId);
    this.detailTab.set('sad');
    void this.loadAdminDetails();
  }

  protected setDetailTab(tab: CsmDetailTab): void {
    this.detailTab.set(tab);
  }

  protected openTicket(item: AnalyticsTicketListItem): void {
    void this.router.navigate([adminTicketsPathForChannel(item.channel)], {
      queryParams: { ticket: item.id },
    });
  }

  protected statusLabel(status: string): string {
    switch (status) {
      case 'OPEN':
        return 'Open';
      case 'IN_PROGRESS':
        return 'In Progress';
      case 'RESOLVED':
        return 'Resolved';
      case 'CLOSED':
        return 'Closed';
      default:
        return status;
    }
  }

  protected happyPercent(row: AnalyticsAssigneeCsm): number {
    if (row.total === 0) {
      return 0;
    }
    return Math.round((row.happy * 1000) / row.total) / 10;
  }

  protected formatPercent(value: number | null | undefined): string {
    if (value == null) {
      return '—';
    }
    return `${value}%`;
  }

  protected ratingShare(key: 'SAD' | 'NEUTRAL' | 'HAPPY'): string {
    const data = this.summary();
    const total = data?.totals.csmCount ?? 0;
    if (!total) {
      return '0%';
    }
    const count = data?.totals.csmByRating[key] ?? 0;
    return `${Math.round((count * 1000) / total) / 10}%`;
  }

  protected stackWidth(count: number, total: number): string {
    if (!total) {
      return '0%';
    }
    return `${Math.max(count > 0 ? 4 : 0, Math.round((count * 100) / total))}%`;
  }

  private async load(month: string): Promise<void> {
    const seq = ++this.loadSeq;
    this.loading.set(true);
    this.error.set(null);
    try {
      const { from, to } = this.monthBounds(month);
      const [summary, csm] = await Promise.all([
        firstValueFrom(this.adminService.getAnalyticsSummary(from, to)),
        firstValueFrom(this.adminService.getCsmByAssignee(from, to)),
      ]);
      if (seq !== this.loadSeq) {
        return;
      }
      this.summary.set(summary);
      this.byAssignee.set(csm.byAssignee ?? []);
      const selected = this.selectedAdminId();
      if (selected != null && !csm.byAssignee.some((a) => a.adminId === selected)) {
        this.selectedAdminId.set(csm.byAssignee[0]?.adminId ?? null);
      } else if (selected == null && csm.byAssignee.length > 0) {
        this.selectedAdminId.set(csm.byAssignee[0].adminId);
      }
      this.scheduleRenderCharts();
      await this.loadAdminDetails();
    } catch (err) {
      if (seq !== this.loadSeq) {
        return;
      }
      this.summary.set(null);
      this.byAssignee.set([]);
      this.error.set(this.describeError(err));
      this.destroyCharts();
    } finally {
      if (seq === this.loadSeq) {
        this.loading.set(false);
      }
    }
  }

  private async loadAdminDetails(): Promise<void> {
    const adminId = this.selectedAdminId();
    if (adminId == null) {
      this.assignedTickets.set([]);
      this.happyTickets.set([]);
      this.neutralTickets.set([]);
      this.sadTickets.set([]);
      return;
    }
    const seq = ++this.detailSeq;
    this.detailLoading.set(true);
    this.detailError.set(null);
    const { from, to } = this.monthBounds(this.month());
    try {
      const [assigned, happy, neutral, sad] = await Promise.all([
        firstValueFrom(this.adminService.getAssigneeTickets(adminId, from, to)),
        firstValueFrom(this.adminService.getCsmTickets('HAPPY', from, to, adminId)),
        firstValueFrom(this.adminService.getCsmTickets('NEUTRAL', from, to, adminId)),
        firstValueFrom(this.adminService.getCsmTickets('SAD', from, to, adminId)),
      ]);
      if (seq !== this.detailSeq) {
        return;
      }
      this.assignedTickets.set(assigned.items ?? []);
      this.happyTickets.set(happy.items ?? []);
      this.neutralTickets.set(neutral.items ?? []);
      this.sadTickets.set(sad.items ?? []);
    } catch (err) {
      if (seq !== this.detailSeq) {
        return;
      }
      this.detailError.set(this.describeError(err));
      this.assignedTickets.set([]);
      this.happyTickets.set([]);
      this.neutralTickets.set([]);
      this.sadTickets.set([]);
    } finally {
      if (seq === this.detailSeq) {
        this.detailLoading.set(false);
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
    this.destroyCharts();
    const summary = this.summary();

    if (this.trendCanvas && summary) {
      this.charts.push(
        new Chart(this.trendCanvas.nativeElement, {
          type: 'line',
          data: {
            labels: summary.csmByDay.map((d) => d.date.slice(5)),
            datasets: [
              {
                label: CSM_LABEL.SAD,
                data: summary.csmByDay.map((d) => d.sad),
                borderColor: RED,
                backgroundColor: 'rgba(239, 68, 68, 0.08)',
                fill: true,
                tension: 0.3,
              },
              {
                label: CSM_LABEL.NEUTRAL,
                data: summary.csmByDay.map((d) => d.neutral),
                borderColor: AMBER,
                backgroundColor: 'rgba(245, 158, 11, 0.08)',
                fill: true,
                tension: 0.3,
              },
              {
                label: CSM_LABEL.HAPPY,
                data: summary.csmByDay.map((d) => d.happy),
                borderColor: EMERALD,
                backgroundColor: 'rgba(16, 185, 129, 0.08)',
                fill: true,
                tension: 0.3,
              },
            ],
          },
          options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
              legend: { position: 'bottom', labels: { boxWidth: 12, font: { size: 11 } } },
            },
            scales: {
              x: { ticks: { font: { size: 10 }, maxRotation: 0 }, grid: { display: false } },
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

    if (this.mixCanvas && summary) {
      const ratings = summary.totals.csmByRating;
      this.charts.push(
        new Chart(this.mixCanvas.nativeElement, {
          type: 'doughnut',
          data: {
            labels: [CSM_LABEL.SAD, CSM_LABEL.NEUTRAL, CSM_LABEL.HAPPY],
            datasets: [
              {
                data: [ratings['SAD'] ?? 0, ratings['NEUTRAL'] ?? 0, ratings['HAPPY'] ?? 0],
                backgroundColor: [RED, AMBER, EMERALD],
              },
            ],
          },
          options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '64%',
            plugins: {
              legend: { position: 'bottom', labels: { boxWidth: 10, font: { size: 11 } } },
            },
          },
        }),
      );
    }
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
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
  }

  private monthBounds(month: string): { from: string; to: string } {
    const match = /^(\d{4})-(\d{2})$/.exec(month.trim());
    if (!match) {
      return this.monthBounds(this.defaultMonth());
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
    return message ?? 'Could not load CSM summary. Please try again.';
  }
}
