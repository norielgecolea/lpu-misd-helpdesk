import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AdminService } from '../../../core/admin/admin.service';
import { AuthService, isAllowedUserEmail, allowedUserEmailLabel } from '../../../core/auth/auth.service';
import {
  AdminSummary,
  NowServingEntry,
  QueueSnapshot,
  QueueTransferRequest,
} from '../../../core/admin/admin.models';
import { Ticket, TicketCategoryOption, canEncodeLpuEmail, displayRequesterEmail } from '../../../core/tickets/ticket.models';
import { TicketService } from '../../../core/tickets/ticket.service';
import { DirectoryService } from '../../../core/directory/directory.service';
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
  private readonly directory = inject(DirectoryService);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  protected readonly waiting = signal<Ticket[]>([]);
  protected readonly nowServing = signal<NowServingEntry[]>([]);
  protected readonly pendingTransfers = signal<QueueTransferRequest[]>([]);
  protected readonly assignees = signal<AdminSummary[]>([]);
  protected readonly categories = signal<TicketCategoryOption[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly callingNext = signal(false);
  protected readonly busyTicketIds = signal<Set<number>>(new Set());
  protected readonly busyTransferIds = signal<Set<number>>(new Set());

  protected readonly showWalkInForm = signal(false);
  protected readonly submittingWalkIn = signal(false);
  protected readonly walkInError = signal<string | null>(null);
  protected readonly walkInName = signal('');
  protected readonly walkInEmail = signal('');
  protected readonly walkInCategory = signal('');
  protected readonly walkInSubject = signal('');
  protected readonly summaryTicket = signal<Ticket | null>(null);
  protected readonly historyTicket = signal<Ticket | null>(null);
  protected readonly encodeEmail = signal('');
  protected readonly encodingEmail = signal(false);
  protected readonly encodeError = signal<string | null>(null);

  protected readonly myServing = computed(() =>
    this.nowServing().find((entry) => entry.adminId === this.auth.userId()),
  );

  protected readonly incomingTransfers = computed(() => {
    const myId = this.auth.userId();
    return this.pendingTransfers().filter((t) => t.toAdminId === myId);
  });

  protected readonly transferTargets = computed(() => {
    const myId = this.auth.userId();
    const busyIds = new Set(this.nowServing().map((e) => e.adminId));
    return this.assignees().filter((a) => a.id !== myId && !busyIds.has(a.id));
  });

  protected readonly controllerStatus = computed(() => {
    const mine = this.myServing();
    if (mine) {
      return `Serving Q-${mine.ticket.queueNumber}`;
    }
    if (this.incomingTransfers().length > 0) {
      return 'Transfer pending';
    }
    if (this.waiting().length === 0) {
      return 'Queue empty';
    }
    return 'Idle — ready to call';
  });

  protected readonly nextWaiting = computed(() => this.waiting()[0] ?? null);

  private refreshTimer: ReturnType<typeof setInterval> | null = null;
  private pollInFlight = false;

  async ngOnInit(): Promise<void> {
    await Promise.all([this.loadSnapshot(), this.loadCategories(), this.loadAssignees()]);
    this.refreshTimer = setInterval(() => void this.loadSnapshot(true), REFRESH_INTERVAL_MS);
  }

  ngOnDestroy(): void {
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer);
    }
  }

  protected pendingForTicket(ticketId: number): QueueTransferRequest | undefined {
    return this.pendingTransfers().find((t) => t.ticketId === ticketId);
  }

  protected waitingPosition(index: number): number {
    return index + 1;
  }

  protected async onCallNext(): Promise<void> {
    this.error.set(null);
    this.success.set(null);
    this.callingNext.set(true);
    try {
      await firstValueFrom(this.adminService.callNext());
      await this.loadSnapshot();
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.callingNext.set(false);
    }
  }

  protected async onServe(ticketId: number): Promise<void> {
    this.error.set(null);
    this.success.set(null);
    this.setBusy(ticketId, true);
    try {
      await firstValueFrom(this.adminService.claimQueueTicket(ticketId));
      await this.loadSnapshot();
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.setBusy(ticketId, false);
    }
  }

  protected async onTransfer(ticketId: number, adminIdRaw: string): Promise<void> {
    if (!adminIdRaw) {
      return;
    }
    const adminId = Number(adminIdRaw);
    this.error.set(null);
    this.success.set(null);
    this.setBusy(ticketId, true);
    try {
      const transfer = await firstValueFrom(this.adminService.transferQueueTicket(ticketId, adminId));
      this.success.set(`Transfer requested — waiting for ${transfer.toAdminName} to approve.`);
      await this.loadSnapshot();
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.setBusy(ticketId, false);
    }
  }

  protected async onApproveTransfer(transferId: number): Promise<void> {
    this.error.set(null);
    this.success.set(null);
    this.setTransferBusy(transferId, true);
    try {
      await firstValueFrom(this.adminService.approveQueueTransfer(transferId));
      this.success.set('Transfer approved. You are now serving this ticket.');
      await this.loadSnapshot();
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.setTransferBusy(transferId, false);
    }
  }

  protected async onRejectTransfer(transferId: number): Promise<void> {
    this.error.set(null);
    this.success.set(null);
    this.setTransferBusy(transferId, true);
    try {
      await firstValueFrom(this.adminService.rejectQueueTransfer(transferId));
      this.success.set('Transfer declined.');
      await this.loadSnapshot();
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.setTransferBusy(transferId, false);
    }
  }

  protected async onCancelTransfer(transferId: number): Promise<void> {
    this.error.set(null);
    this.success.set(null);
    this.setTransferBusy(transferId, true);
    try {
      await firstValueFrom(this.adminService.cancelQueueTransfer(transferId));
      this.success.set('Transfer request cancelled.');
      await this.loadSnapshot();
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.setTransferBusy(transferId, false);
    }
  }

  protected async onComplete(ticketId: number): Promise<void> {
    this.setBusy(ticketId, true);
    try {
      await firstValueFrom(this.adminService.completeServing(ticketId));
      await this.loadSnapshot();
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.setBusy(ticketId, false);
    }
  }

  protected async onRequeue(ticketId: number): Promise<void> {
    this.setBusy(ticketId, true);
    try {
      await firstValueFrom(this.adminService.requeue(ticketId));
      await this.loadSnapshot();
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.setBusy(ticketId, false);
    }
  }

  protected async onHold(ticketId: number): Promise<void> {
    this.error.set(null);
    this.success.set(null);
    this.setBusy(ticketId, true);
    try {
      await firstValueFrom(this.adminService.holdServing(ticketId));
      this.success.set('Removed from the queue. Ticket is In Progress so it can be finished later.');
      await this.loadSnapshot();
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.setBusy(ticketId, false);
    }
  }

  protected openWalkInForm(): void {
    this.walkInError.set(null);
    this.walkInName.set('');
    this.walkInEmail.set('');
    this.walkInCategory.set(this.categories()[0]?.value ?? '');
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
    const subject = this.walkInSubject().trim();

    if (!name || !email || !category || !subject) {
      this.walkInError.set('Please fill in all fields.');
      return;
    }

    this.submittingWalkIn.set(true);
    try {
      await firstValueFrom(this.adminService.createWalkIn({ name, email, category, subject }));
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

  protected isTransferBusy(transferId: number): boolean {
    return this.busyTransferIds().has(transferId);
  }

  protected personId(ticket: Ticket): string | null {
    return ticket.requesterPersonNo || null;
  }

  protected requesterEmailLabel(ticket: Ticket): string {
    return displayRequesterEmail(ticket.requesterEmail, ticket.pendingEmail);
  }

  protected canEncode(ticket: Ticket): boolean {
    return canEncodeLpuEmail(ticket);
  }

  protected async onEncodeEmail(ticket: Ticket): Promise<void> {
    this.encodeError.set(null);
    this.error.set(null);
    this.success.set(null);
    const email = this.encodeEmail().trim().toLowerCase();
    if (!isAllowedUserEmail(email)) {
      this.encodeError.set(`Use an ${allowedUserEmailLabel()} address.`);
      return;
    }

    this.encodingEmail.set(true);
    try {
      const result = await firstValueFrom(
        this.directory.encodeLpuEmail({
          email,
          ticketId: ticket.id,
          personType: ticket.requesterPersonType,
          personNo: ticket.requesterPersonNo,
        }),
      );
      this.encodeEmail.set('');
      this.success.set(
        `Encoded ${result.email}. ${result.ticketsLinked} ticket${result.ticketsLinked === 1 ? '' : 's'} linked.`,
      );
      await this.loadSnapshot();
    } catch (err) {
      this.encodeError.set(this.describeError(err));
    } finally {
      this.encodingEmail.set(false);
    }
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
    void this.router.navigate(['/admin/tickets'], {
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
      this.error.set(null);
    }
    this.pollInFlight = true;
    try {
      const snapshot: QueueSnapshot = await firstValueFrom(this.adminService.getQueueSnapshot());
      this.waiting.set(snapshot.waiting);
      this.nowServing.set(snapshot.nowServing);
      this.pendingTransfers.set(snapshot.pendingTransfers ?? []);
    } catch (err) {
      if (!silent) {
        this.error.set(this.describeError(err));
      }
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

  private async loadAssignees(): Promise<void> {
    try {
      this.assignees.set(await firstValueFrom(this.adminService.listAssignees()));
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

  private setTransferBusy(transferId: number, busy: boolean): void {
    this.busyTransferIds.update((current) => {
      const next = new Set(current);
      if (busy) {
        next.add(transferId);
      } else {
        next.delete(transferId);
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
