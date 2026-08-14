import { Component, input, output, signal, effect, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { DirectoryProfile, DirectoryService } from '../../core/directory/directory.service';
import { Ticket } from '../../core/tickets/ticket.models';

export interface TicketSummaryRow {
  label: string;
  value: string;
  /** When set, show a copy-to-clipboard control for this row. */
  copyValue?: string | null;
}

@Component({
  selector: 'app-ticket-summary-dialog',
  templateUrl: './ticket-summary-dialog.html',
})
export class TicketSummaryDialog {
  private readonly directoryService = inject(DirectoryService);

  /** When set, the dialog is open for this ticket. */
  readonly ticket = input<Ticket | null>(null);
  readonly closed = output<void>();
  readonly viewHistory = output<Ticket>();

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly profile = signal<DirectoryProfile | null>(null);
  protected readonly rows = signal<TicketSummaryRow[]>([]);
  protected readonly copiedKey = signal<string | null>(null);

  private copyResetTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    effect(() => {
      const ticket = this.ticket();
      if (ticket) {
        void this.load(ticket);
      } else {
        this.profile.set(null);
        this.rows.set([]);
        this.error.set(null);
        this.loading.set(false);
        this.copiedKey.set(null);
      }
    });
  }

  protected close(): void {
    this.closed.emit();
  }

  protected openHistory(): void {
    const ticket = this.ticket();
    if (!ticket) {
      return;
    }
    this.viewHistory.emit(ticket);
  }

  protected onBackdropClick(): void {
    this.close();
  }

  protected async copyValue(row: TicketSummaryRow): Promise<void> {
    const text = row.copyValue?.trim();
    if (!text) {
      return;
    }
    try {
      await navigator.clipboard.writeText(text);
      this.copiedKey.set(row.label);
      if (this.copyResetTimer) {
        clearTimeout(this.copyResetTimer);
      }
      this.copyResetTimer = setTimeout(() => {
        if (this.copiedKey() === row.label) {
          this.copiedKey.set(null);
        }
      }, 1600);
    } catch {
      // Clipboard may be unavailable (insecure context / denied permission).
    }
  }

  private async load(ticket: Ticket): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    this.profile.set(null);
    this.copiedKey.set(null);
    try {
      const profile = await firstValueFrom(
        this.directoryService.lookupProfile({
          email: ticket.requesterEmail,
          personType: ticket.requesterPersonType,
          personNo: ticket.requesterPersonNo,
        }),
      );
      this.profile.set(profile);
      this.rows.set(this.buildRows(ticket, profile));
    } catch {
      this.error.set('Could not load directory details.');
      this.rows.set(this.buildRows(ticket, null));
    } finally {
      this.loading.set(false);
    }
  }

  private buildRows(ticket: Ticket, profile: DirectoryProfile | null): TicketSummaryRow[] {
    const type = (profile?.found ? profile.personType : ticket.requesterPersonType)?.toUpperCase() ?? null;
    const name = (profile?.found && profile.name) || ticket.requesterName || '—';
    const emailRaw = (profile?.found && profile.email) || ticket.requesterEmail || '';
    const email = emailRaw || '—';
    const personNoRaw = (profile?.found && profile.personNo) || ticket.requesterPersonNo || '';
    const personNo = personNoRaw || '—';
    const department = (profile?.found && profile.department) || '—';
    const course = (profile?.found && profile.course) || '—';

    const emailRow: TicketSummaryRow = {
      label: 'LPU Email',
      value: email,
      copyValue: emailRaw || null,
    };

    if (type === 'EMPLOYEE') {
      return [
        { label: 'Name', value: name },
        emailRow,
        {
          label: 'Employee number',
          value: personNo,
          copyValue: personNoRaw || null,
        },
        { label: 'Department', value: department },
      ];
    }

    // Student (or unknown — show the fuller student-oriented set with ticket fallbacks)
    return [
      { label: 'Name', value: name },
      emailRow,
      { label: 'Course', value: course },
      { label: 'Department', value: department },
      {
        label: 'Student number',
        value: personNo,
        copyValue: personNoRaw || null,
      },
    ];
  }
}
