import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AdminService } from '../../core/admin/admin.service';
import { Ticket, TicketStatus, isPendingRequesterEmail } from '../../core/tickets/ticket.models';

const PAGE_SIZE = 8;

@Component({
  selector: 'app-ticket-history-dialog',
  imports: [DatePipe],
  templateUrl: './ticket-history-dialog.html',
})
export class TicketHistoryDialog {
  private readonly adminService = inject(AdminService);

  /** When set, the dialog is open for this person's identity (from a ticket). */
  readonly ticket = input<Ticket | null>(null);
  readonly closed = output<void>();
  /** Emitted when a history row is chosen so the parent can open that ticket. */
  readonly ticketSelected = output<Ticket>();

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly tickets = signal<Ticket[]>([]);
  protected readonly pageIndex = signal(0);

  protected readonly totalCount = computed(() => this.tickets().length);
  protected readonly totalPages = computed(() => Math.max(1, Math.ceil(this.totalCount() / PAGE_SIZE)));

  protected readonly pagedTickets = computed(() => {
    const start = this.pageIndex() * PAGE_SIZE;
    return this.tickets().slice(start, start + PAGE_SIZE);
  });

  protected readonly rangeLabel = computed(() => {
    const total = this.totalCount();
    if (total === 0) {
      return '0 tickets';
    }
    const start = this.pageIndex() * PAGE_SIZE + 1;
    const end = Math.min(total, start + PAGE_SIZE - 1);
    return `${start}–${end} of ${total}`;
  });

  protected readonly canGoPrev = computed(() => this.pageIndex() > 0);
  protected readonly canGoNext = computed(() => this.pageIndex() < this.totalPages() - 1);

  constructor() {
    effect(() => {
      const ticket = this.ticket();
      if (ticket) {
        void this.load(ticket);
      } else {
        this.tickets.set([]);
        this.pageIndex.set(0);
        this.error.set(null);
        this.loading.set(false);
      }
    });
  }

  protected close(): void {
    this.closed.emit();
  }

  protected onBackdropClick(): void {
    this.close();
  }

  protected onRowClick(item: Ticket): void {
    this.ticketSelected.emit(item);
  }

  protected goPrev(): void {
    if (this.canGoPrev()) {
      this.pageIndex.update((p) => p - 1);
    }
  }

  protected goNext(): void {
    if (this.canGoNext()) {
      this.pageIndex.update((p) => p + 1);
    }
  }

  protected statusLabel(status: TicketStatus): string {
    switch (status) {
      case 'OPEN':
        return 'Open';
      case 'IN_PROGRESS':
        return 'In Progress';
      case 'RESOLVED':
        return 'Resolved';
      case 'CLOSED':
        return 'Closed';
    }
  }

  protected statusClass(status: TicketStatus): string {
    switch (status) {
      case 'OPEN':
        return 'bg-sky-50 text-sky-800 ring-1 ring-inset ring-sky-200';
      case 'IN_PROGRESS':
        return 'bg-amber-50 text-amber-900 ring-1 ring-inset ring-amber-200';
      case 'RESOLVED':
        return 'bg-emerald-50 text-emerald-800 ring-1 ring-inset ring-emerald-200';
      case 'CLOSED':
        return 'bg-zinc-100 text-zinc-600 ring-1 ring-inset ring-zinc-200';
    }
  }

  protected channelLabel(channel: Ticket['channel']): string {
    return channel === 'ONSITE_RFID' ? 'Onsite' : 'Online';
  }

  protected displayEmail(ticket: Ticket): string {
    if (isPendingRequesterEmail(ticket.requesterEmail, ticket.pendingEmail)) {
      return '';
    }
    return ticket.requesterEmail;
  }

  private async load(ticket: Ticket): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    this.tickets.set([]);
    this.pageIndex.set(0);
    try {
      const history = await firstValueFrom(
        this.adminService.listTicketHistory({
          email: isPendingRequesterEmail(ticket.requesterEmail, ticket.pendingEmail)
            ? null
            : ticket.requesterEmail,
          personType: ticket.requesterPersonType,
          personNo: ticket.requesterPersonNo,
        }),
      );
      this.tickets.set(history);
    } catch {
      this.error.set('Could not load ticket history.');
    } finally {
      this.loading.set(false);
    }
  }
}
