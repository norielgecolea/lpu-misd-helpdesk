import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  armAutoUnlock,
  playNewTicketCue,
} from '../../core/audio/cue-sounds';
import { AuthService } from '../../core/auth/auth.service';
import { MonitorService } from '../../core/monitor/monitor.service';
import { Ticket } from '../../core/tickets/ticket.models';

const REFRESH_MS = 1_000;
const ALERT_MS = 6_000;

interface MonitorAlert {
  title: string;
  name: string;
  detail: string;
  extraCount: number;
}

@Component({
  selector: 'app-monitor',
  imports: [DatePipe],
  templateUrl: './monitor.html',
  styles: `
    .monitor-scroll {
      scrollbar-width: thin;
      scrollbar-color: transparent transparent;
    }

    .monitor-scroll:hover,
    .monitor-scroll:focus-within {
      scrollbar-color: rgb(63 63 70 / 0.85) transparent;
    }

    .monitor-scroll::-webkit-scrollbar {
      width: 6px;
      height: 6px;
    }

    .monitor-scroll::-webkit-scrollbar-track {
      background: transparent;
    }

    .monitor-scroll::-webkit-scrollbar-thumb {
      border-radius: 9999px;
      background: transparent;
    }

    .monitor-scroll:hover::-webkit-scrollbar-thumb,
    .monitor-scroll:focus-within::-webkit-scrollbar-thumb {
      background: rgb(63 63 70 / 0.85);
    }

    .unassigned-blink {
      animation: unassigned-blink 1.4s ease-in-out infinite;
    }

    @keyframes unassigned-blink {
      0%,
      100% {
        background-color: transparent;
        box-shadow: none;
      }
      50% {
        background-color: rgb(141 37 70 / 0.18);
        box-shadow: inset 3px 0 0 0 rgb(141 37 70 / 0.8);
      }
    }

    .monitor-alert-enter {
      animation: monitor-alert-enter 0.35s ease-out both;
    }

    @keyframes monitor-alert-enter {
      from {
        opacity: 0;
        transform: scale(0.92);
      }
      to {
        opacity: 1;
        transform: scale(1);
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .unassigned-blink {
        animation: none;
        background-color: rgb(141 37 70 / 0.18);
        box-shadow: inset 3px 0 0 0 rgb(141 37 70 / 0.8);
      }

      .monitor-alert-enter {
        animation: none;
      }
    }
  `,
})
export class Monitor implements OnDestroy, OnInit {
  private readonly monitorService = inject(MonitorService);
  protected readonly auth = inject(AuthService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly waiting = signal<Ticket[]>([]);
  protected readonly recentTickets = signal<Ticket[]>([]);
  protected readonly clock = signal<Date>(new Date());
  protected readonly alert = signal<MonitorAlert | null>(null);

  protected readonly recentOnline = computed(() => {
    const open = this.recentTickets().filter(
      (t) => t.status !== 'CLOSED' && t.status !== 'RESOLVED',
    );
    const byNewest = (a: Ticket, b: Ticket) =>
      new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
    const unassigned = open.filter((t) => t.assignedAdminId == null).sort(byNewest);
    const assigned = open.filter((t) => t.assignedAdminId != null).sort(byNewest);
    return [...unassigned, ...assigned].slice(0, 20);
  });

  private refreshTimer: ReturnType<typeof setInterval> | null = null;
  private clockTimer: ReturnType<typeof setInterval> | null = null;
  private disarmAudio: (() => void) | null = null;
  private primed = false;
  private pollInFlight = false;
  private knownTicketIds = new Set<number>();
  private knownWaitingIds = new Set<number>();
  private alertTimer: ReturnType<typeof setTimeout> | null = null;

  async ngOnInit(): Promise<void> {
    this.disarmAudio = armAutoUnlock();
    await this.loadSnapshot(true);
    this.refreshTimer = setInterval(() => void this.loadSnapshot(false), REFRESH_MS);
    this.clockTimer = setInterval(() => this.clock.set(new Date()), 1_000);
  }

  ngOnDestroy(): void {
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer);
    }
    if (this.clockTimer) {
      clearInterval(this.clockTimer);
    }
    this.disarmAudio?.();
    this.disarmAudio = null;
    if (this.alertTimer) {
      clearTimeout(this.alertTimer);
      this.alertTimer = null;
    }
  }

  protected async logout(): Promise<void> {
    await this.auth.logout('/admin');
  }

  protected personLabel(ticket: Ticket): string {
    if (ticket.requesterPersonNo) {
      return ticket.requesterPersonNo;
    }
    return ticket.requesterName;
  }

  protected isUnassigned(ticket: Ticket): boolean {
    return ticket.assignedAdminId == null;
  }

  private async loadSnapshot(initial: boolean): Promise<void> {
    if (this.pollInFlight) {
      return;
    }
    if (!initial && typeof document !== 'undefined' && document.visibilityState === 'hidden') {
      return;
    }

    this.pollInFlight = true;
    try {
      const snap = await firstValueFrom(this.monitorService.snapshot(40));
      this.waiting.set(snap.waiting ?? []);
      this.recentTickets.set(snap.recentTickets ?? []);
      this.error.set(null);

      if (!this.primed) {
        this.knownTicketIds = new Set((snap.recentTickets ?? []).map((t) => t.id));
        this.knownWaitingIds = new Set((snap.waiting ?? []).map((t) => t.id));
        this.primed = true;
      } else {
        this.detectAndCue(snap.waiting ?? [], snap.recentTickets ?? []);
      }
    } catch (err) {
      if (initial) {
        this.error.set(this.describeError(err));
      }
    } finally {
      this.pollInFlight = false;
      this.loading.set(false);
    }
  }

  private detectAndCue(waiting: Ticket[], recent: Ticket[]): void {
    const incoming = [
      ...waiting.filter((ticket) => !this.knownWaitingIds.has(ticket.id)),
      ...recent.filter((ticket) => !this.knownTicketIds.has(ticket.id)),
    ];

    this.knownWaitingIds = new Set(waiting.map((t) => t.id));
    this.knownTicketIds = new Set(recent.map((t) => t.id));

    if (incoming.length === 0) {
      return;
    }
    playNewTicketCue();
    this.showAlert(incoming);
  }

  private showAlert(tickets: Ticket[]): void {
    const first = tickets[0];
    const walkIn = first.channel === 'ONSITE_RFID';
    this.alert.set({
      title: tickets.length > 1 ? `${tickets.length} new tickets` : walkIn ? 'New walk-in' : 'New online ticket',
      name: first.requesterName,
      detail: [first.ticketNumber, first.categoryPath || first.categoryLabel].filter(Boolean).join(' · '),
      extraCount: Math.max(0, tickets.length - 1),
    });
    if (this.alertTimer) {
      clearTimeout(this.alertTimer);
    }
    this.alertTimer = setTimeout(() => {
      this.alert.set(null);
      this.alertTimer = null;
    }, ALERT_MS);
  }

  private describeError(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 401 || err.status === 403) {
        return 'Session expired or not allowed. Please sign in again.';
      }
      const body = err.error;
      if (body && typeof body === 'object' && 'message' in body && typeof body.message === 'string') {
        return body.message;
      }
      return err.message || 'Unable to load live board.';
    }
    if (err instanceof Error) {
      return err.message;
    }
    return 'Unable to load live board.';
  }
}
