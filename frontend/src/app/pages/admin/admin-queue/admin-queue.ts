import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AdminService } from '../../../core/admin/admin.service';
import { AuthService } from '../../../core/auth/auth.service';
import { NowServingEntry, QueueSnapshot } from '../../../core/admin/admin.models';
import { Ticket, TicketCategoryOption, adminTicketsPathForChannel, displayRequesterEmail } from '../../../core/tickets/ticket.models';
import { TicketService } from '../../../core/tickets/ticket.service';
import { TicketSummaryDialog } from '../../../shared/ticket-summary-dialog/ticket-summary-dialog';
import { TicketHistoryDialog } from '../../../shared/ticket-history-dialog/ticket-history-dialog';

const REFRESH_INTERVAL_MS = 2_000;

@Component({
  selector: 'app-admin-queue',
  imports: [FormsModule, TicketSummaryDialog, TicketHistoryDialog],
  templateUrl: './admin-queue.html',
})
export class AdminQueue implements OnInit, OnDestroy {
  private readonly adminService = inject(AdminService);
  private readonly ticketService = inject(TicketService);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  protected readonly waiting = signal<Ticket[]>([]);
  protected readonly nowServing = signal<NowServingEntry[]>([]);
  protected readonly categories = signal<TicketCategoryOption[]>([]);
  protected readonly loading = signal(true);
  protected readonly busyTicketIds = signal<Set<number>>(new Set());

  protected readonly showWalkInForm = signal(false);
  protected readonly submittingWalkIn = signal(false);
  protected readonly walkInError = signal<string | null>(null);
  protected readonly walkInName = signal('');
  protected readonly walkInEmail = signal('');
  protected readonly walkInCategory = signal('');
  protected readonly walkInSubcategory = signal('');
  protected readonly walkInSubject = signal('');
  protected readonly summaryTicket = signal<Ticket | null>(null);
  protected readonly historyTicket = signal<Ticket | null>(null);

  protected readonly myAssigned = computed(() =>
    this.nowServing().filter((entry) => entry.adminId === this.auth.userId()),
  );

  protected readonly walkInProblems = computed(
    () => this.categories().find((option) => option.value === this.walkInCategory())?.children ?? [],
  );

  protected onWalkInCategoryChange(value: string): void {
    this.walkInCategory.set(value);
    this.walkInSubcategory.set(
      this.categories().find((option) => option.value === value)?.children?.[0]?.value ?? '',
    );
  }

  private refreshTimer: ReturnType<typeof setInterval> | null = null;
  private pollInFlight = false;

  async ngOnInit(): Promise<void> {
    await Promise.all([this.loadSnapshot(), this.loadCategories()]);
    this.refreshTimer = setInterval(() => void this.loadSnapshot(true), REFRESH_INTERVAL_MS);
  }

  ngOnDestroy(): void {
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer);
    }
  }

  protected async onMarkInProgress(ticketId: number): Promise<void> {
    this.setBusy(ticketId, true);
    try {
      await firstValueFrom(this.adminService.claimQueueTicket(ticketId));
      await this.loadSnapshot();
    } catch {
    } finally {
      this.setBusy(ticketId, false);
    }
  }

  protected async onComplete(ticketId: number): Promise<void> {
    this.setBusy(ticketId, true);
    try {
      await firstValueFrom(this.adminService.completeServing(ticketId));
      await this.loadSnapshot();
    } catch {
    } finally {
      this.setBusy(ticketId, false);
    }
  }

  protected async onRequeue(ticketId: number): Promise<void> {
    this.setBusy(ticketId, true);
    try {
      await firstValueFrom(this.adminService.requeue(ticketId));
      await this.loadSnapshot();
    } catch {
    } finally {
      this.setBusy(ticketId, false);
    }
  }

  protected openWalkInForm(): void {
    this.walkInError.set(null);
    this.walkInName.set('');
    this.walkInEmail.set('');
    const first = this.categories()[0];
    this.walkInCategory.set(first?.value ?? '');
    this.walkInSubcategory.set(first?.children?.[0]?.value ?? '');
    this.walkInSubject.set('');
    this.showWalkInForm.set(true);
  }

  protected closeWalkInForm(): void {
    this.showWalkInForm.set(false);
  }

  protected async submitWalkIn(): Promise<void> {
    this.walkInError.set(null);
    const name = this.walkInName().trim();
    const email = this.walkInEmail().trim();
    const category = this.walkInCategory();
    const subcategory = this.walkInSubcategory();
    const subject = this.walkInSubject().trim();

    if (!name || !email || !category || !subcategory || !subject) {
      this.walkInError.set('Please fill in all fields.');
      return;
    }

    this.submittingWalkIn.set(true);
    try {
      await firstValueFrom(this.adminService.createWalkIn({ name, email, category, subcategory, subject }));
      this.showWalkInForm.set(false);
      await this.loadSnapshot();
    } catch (err) {
      this.walkInError.set(this.describeError(err));
    } finally {
      this.submittingWalkIn.set(false);
    }
  }

  protected isBusy(ticketId: number): boolean {
    return this.busyTicketIds().has(ticketId);
  }

  protected personId(ticket: Ticket): string | null {
    return ticket.requesterPersonNo || null;
  }

  protected requesterEmailLabel(ticket: Ticket): string {
    return displayRequesterEmail(ticket.requesterEmail, ticket.pendingEmail);
  }

  protected openSummary(ticket: Ticket): void {
    this.summaryTicket.set(ticket);
  }

  protected closeSummary(): void {
    this.summaryTicket.set(null);
  }

  protected async onDirectoryLinked(): Promise<void> {
    this.closeSummary();
    await this.loadSnapshot();
  }

  protected openHistory(ticket: Ticket): void {
    this.summaryTicket.set(null);
    this.historyTicket.set(ticket);
  }

  protected closeHistory(): void {
    this.historyTicket.set(null);
  }

  protected onHistoryTicketSelected(ticket: Ticket): void {
    this.historyTicket.set(null);
    this.summaryTicket.set(null);
    void this.router.navigate([adminTicketsPathForChannel(ticket.channel)], {
      queryParams: { ticket: ticket.id },
      state: { focusTicket: ticket },
    });
  }

  private async loadSnapshot(silent = false): Promise<void> {
    if (this.pollInFlight) {
      return;
    }
    if (silent && typeof document !== 'undefined' && document.visibilityState === 'hidden') {
      return;
    }
    if (!silent) {
      this.loading.set(true);
    }
    this.pollInFlight = true;
    try {
      const snapshot: QueueSnapshot = await firstValueFrom(this.adminService.getQueueSnapshot());
      this.waiting.set(snapshot.waiting);
      this.nowServing.set(snapshot.nowServing);
    } catch {
    } finally {
      this.pollInFlight = false;
      this.loading.set(false);
    }
  }

  private async loadCategories(): Promise<void> {
    try {
      this.categories.set(await firstValueFrom(this.ticketService.getCategories()));
    } catch {
      // Non-fatal
    }
  }

  private setBusy(ticketId: number, busy: boolean): void {
    this.busyTicketIds.update((current) => {
      const next = new Set(current);
      if (busy) {
        next.add(ticketId);
      } else {
        next.delete(ticketId);
      }
      return next;
    });
  }

  private describeError(err: unknown): string {
    const message =
      err && typeof err === 'object' && 'error' in err
        ? ((err as { error?: { message?: string } }).error?.message ?? null)
        : null;
    return message ?? 'Something went wrong. Please try again.';
  }
}
