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
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  Legend,
  LinearScale,
  Tooltip,
} from 'chart.js';
import { firstValueFrom } from 'rxjs';
import { AdminService } from '../../../core/admin/admin.service';
import {
  AnalyticsAssigneeCsm,
  AnalyticsTicketListItem,
} from '../../../core/admin/admin.models';
import { adminTicketsPathForChannel } from '../../../core/tickets/ticket.models';

Chart.register(CategoryScale, LinearScale, BarElement, BarController, Tooltip, Legend);

const AMBER = '#f59e0b';
const EMERALD = '#10b981';
const RED = '#ef4444';

@Component({
  selector: 'app-admin-csm',
  imports: [FormsModule, DatePipe],
  templateUrl: './admin-csm.html',
})
export class AdminCsm implements OnInit, AfterViewInit, OnDestroy {
  private readonly adminService = inject(AdminService);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);

  @ViewChild('ratioCanvas') private ratioCanvas?: ElementRef<HTMLCanvasElement>;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly byAssignee = signal<AnalyticsAssigneeCsm[]>([]);
  protected readonly month = signal(this.defaultMonth());
  protected readonly monthLabel = computed(() => this.formatMonthLabel(this.month()));
  protected readonly selectedAdminId = signal<number | null>(null);

  protected readonly assignedTickets = signal<AnalyticsTicketListItem[]>([]);
  protected readonly happyTickets = signal<AnalyticsTicketListItem[]>([]);
  protected readonly neutralTickets = signal<AnalyticsTicketListItem[]>([]);
  protected readonly sadTickets = signal<AnalyticsTicketListItem[]>([]);
  protected readonly detailLoading = signal(false);
  protected readonly detailError = signal<string | null>(null);

  protected readonly selectedAdmin = computed(() => {
    const id = this.selectedAdminId();
    if (id == null) {
      return null;
    }
    return this.byAssignee().find((a) => a.adminId === id) ?? null;
  });

  private chart: Chart | null = null;
  private viewReady = false;
  private loadSeq = 0;
  private detailSeq = 0;

  ngOnInit(): void {
    void this.load(this.month());
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
    this.scheduleRenderChart();
  }

  ngOnDestroy(): void {
    this.destroyChart();
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
    void this.loadAdminDetails();
  }

  protected selectAdmin(adminId: number): void {
    this.selectedAdminId.set(adminId);
    void this.loadAdminDetails();
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

  private async load(month: string): Promise<void> {
    const seq = ++this.loadSeq;
    this.loading.set(true);
    this.error.set(null);
    try {
      const { from, to } = this.monthBounds(month);
      const data = await firstValueFrom(this.adminService.getCsmByAssignee(from, to));
      if (seq !== this.loadSeq) {
        return;
      }
      this.byAssignee.set(data.byAssignee ?? []);
      const selected = this.selectedAdminId();
      if (selected != null && !data.byAssignee.some((a) => a.adminId === selected)) {
        this.selectedAdminId.set(data.byAssignee[0]?.adminId ?? null);
      } else if (selected == null && data.byAssignee.length > 0) {
        this.selectedAdminId.set(data.byAssignee[0].adminId);
      }
      this.scheduleRenderChart();
      await this.loadAdminDetails();
    } catch (err) {
      if (seq !== this.loadSeq) {
        return;
      }
      this.byAssignee.set([]);
      this.error.set(this.describeError(err));
      this.destroyChart();
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

  private scheduleRenderChart(): void {
    afterNextRender(() => this.renderChart(), { injector: this.injector });
    setTimeout(() => this.renderChart());
  }

  private renderChart(): void {
    if (!this.viewReady || !this.ratioCanvas) {
      return;
    }
    const rows = this.byAssignee();
    this.destroyChart();
    if (rows.length === 0) {
      return;
    }

    this.chart = new Chart(this.ratioCanvas.nativeElement, {
      type: 'bar',
      data: {
        labels: rows.map((r) => r.name),
        datasets: [
          {
            label: 'Sad',
            data: rows.map((r) => r.sad),
            backgroundColor: RED,
            stack: 'csm',
          },
          {
            label: 'Neh',
            data: rows.map((r) => r.neutral),
            backgroundColor: AMBER,
            stack: 'csm',
          },
          {
            label: 'Happy',
            data: rows.map((r) => r.happy),
            backgroundColor: EMERALD,
            stack: 'csm',
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        onClick: (_event, elements) => {
          if (!elements.length) {
            return;
          }
          const index = elements[0].index;
          const row = rows[index];
          if (row) {
            this.selectAdmin(row.adminId);
          }
        },
        onHover: (event, elements) => {
          const target = event.native?.target as HTMLElement | undefined;
          if (target) {
            target.style.cursor = elements.length ? 'pointer' : 'default';
          }
        },
        plugins: {
          legend: {
            position: 'bottom',
            labels: { boxWidth: 12, font: { size: 11 } },
          },
          tooltip: {
            callbacks: {
              afterBody: (items) => {
                const index = items[0]?.dataIndex ?? 0;
                const row = rows[index];
                if (!row || row.total === 0) {
                  return '';
                }
                const happyPct = Math.round((row.happy * 1000) / row.total) / 10;
                return `Happy ratio: ${happyPct}%`;
              },
            },
          },
        },
        scales: {
          x: {
            stacked: true,
            ticks: { font: { size: 10 }, maxRotation: 45, minRotation: 0 },
            grid: { display: false },
          },
          y: {
            stacked: true,
            beginAtZero: true,
            ticks: { font: { size: 10 }, precision: 0 },
            grid: { color: 'rgba(24, 24, 27, 0.06)' },
          },
        },
      },
    });
  }

  private destroyChart(): void {
    this.chart?.destroy();
    this.chart = null;
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
