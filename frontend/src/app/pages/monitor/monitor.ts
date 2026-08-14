import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  armAutoUnlock,
  playNewTicketCue,
  playNowServingCue,
} from '../../core/audio/cue-sounds';
import { NowServingEntry } from '../../core/admin/admin.models';
import { AuthService } from '../../core/auth/auth.service';
import { MonitorService } from '../../core/monitor/monitor.service';
import { Ticket } from '../../core/tickets/ticket.models';

const REFRESH_MS = 1_000;

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

    @keyframes counter-call-in {
      0% {
        opacity: 0;
        transform: translateY(8px) scale(0.96);
        box-shadow: 0 0 0 0 rgb(141 37 70 / 0);
      }
      40% {
        opacity: 1;
        transform: translateY(0) scale(1.02);
        box-shadow: 0 0 0 3px rgb(141 37 70 / 0.4);
      }
      100% {
        opacity: 1;
        transform: scale(1);
        box-shadow: 0 0 0 0 rgb(141 37 70 / 0);
      }
    }

    .counter-call-in {
      animation: counter-call-in 0.85s cubic-bezier(0.22, 1, 0.36, 1) both;
    }

    @keyframes unassigned-blink {
      0%,
      100% {
        background-color: rgb(141 37 70 / 0.08);
        box-shadow: inset 3px 0 0 0 rgb(141 37 70 / 0.55);
      }
      50% {
        background-color: rgb(141 37 70 / 0.28);
        box-shadow: inset 3px 0 0 0 rgb(232 160 180 / 0.95);
      }
    }

    .unassigned-blink {
      animation: unassigned-blink 1.4s ease-in-out infinite;
    }

    @media (prefers-reduced-motion: reduce) {
      .counter-call-in {
        animation: none;
      }

      .unassigned-blink {
        animation: none;
        background-color: rgb(141 37 70 / 0.18);
        box-shadow: inset 3px 0 0 0 rgb(141 37 70 / 0.8);
      }
    }
  `,
})
export class Monitor implements OnDestroy, OnInit {
  private readonly monitorService = inject(MonitorService);
  protected readonly auth = inject(AuthService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly nowServing = signal<NowServingEntry[]>([]);
  protected readonly waiting = signal<Ticket[]>([]);
  protected readonly recentTickets = signal<Ticket[]>([]);
  protected readonly clock = signal<Date>(new Date());
  /** Serving keys that just received a called ticket (for call-in animation). */
  protected readonly calledKeys = signal<Set<string>>(new Set());

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
  private callAnimTimer: ReturnType<typeof setTimeout> | null = null;
  private disarmAudio: (() => void) | null = null;
  private primed = false;
  private pollInFlight = false;
  private knownTicketIds = new Set<number>();
  private knownServingKeys = new Set<string>();

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
    if (this.callAnimTimer) {
      clearTimeout(this.callAnimTimer);
    }
    this.disarmAudio?.();
    this.disarmAudio = null;
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

  protected personTypeLabel(ticket: Ticket): string {
    if (ticket.requesterPersonType === 'STUDENT') {
      return 'Student ID';
    }
    if (ticket.requesterPersonType === 'EMPLOYEE') {
      return 'Employee ID';
    }
    return 'Requester';
  }

  protected isUnassigned(ticket: Ticket): boolean {
    return ticket.assignedAdminId == null;
  }

  protected isJustCalled(entry: NowServingEntry): boolean {
    return this.calledKeys().has(this.servingKey(entry));
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
      this.nowServing.set(snap.nowServing ?? []);
      this.waiting.set(snap.waiting ?? []);
      this.recentTickets.set(snap.recentTickets ?? []);
      this.error.set(null);

      if (!this.primed) {
        this.knownTicketIds = new Set((snap.recentTickets ?? []).map((t) => t.id));
        this.knownServingKeys = new Set((snap.nowServing ?? []).map((e) => this.servingKey(e)));
        this.primed = true;
      } else {
        this.detectAndCue(snap.nowServing ?? [], snap.recentTickets ?? []);
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

  private detectAndCue(serving: NowServingEntry[], recent: Ticket[]): void {
    let newTicket = false;

    for (const ticket of recent) {
      if (!this.knownTicketIds.has(ticket.id)) {
        newTicket = true;
        break;
      }
    }

    const nextServingKeys = new Set(serving.map((e) => this.servingKey(e)));
    const newlyCalled: string[] = [];
    for (const key of nextServingKeys) {
      if (!this.knownServingKeys.has(key)) {
        newlyCalled.push(key);
      }
    }

    this.knownTicketIds = new Set(recent.map((t) => t.id));
    this.knownServingKeys = nextServingKeys;

    if (newlyCalled.length > 0) {
      playNowServingCue();
      this.flashCalledKeys(newlyCalled);
    } else if (newTicket) {
      playNewTicketCue();
    }
  }

  private flashCalledKeys(keys: string[]): void {
    this.calledKeys.set(new Set(keys));
    if (this.callAnimTimer) {
      clearTimeout(this.callAnimTimer);
    }
    this.callAnimTimer = setTimeout(() => {
      this.calledKeys.set(new Set());
      this.callAnimTimer = null;
    }, 900);
  }

  private servingKey(entry: NowServingEntry): string {
    return `${entry.adminId}:${entry.ticket?.id ?? 'none'}`;
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
