import { Component, input, output, signal, effect, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { DirectoryProfile, DirectoryService } from '../../core/directory/directory.service';
import { Ticket, displayRequesterEmail, isPendingRequesterEmail, needsDirectoryLink } from '../../core/tickets/ticket.models';
import { environment } from '../../../environments/environment';

export interface TicketSummaryRow {
  label: string;
  value: string;
  /** When set, show a copy-to-clipboard control for this row. */
  copyValue?: string | null;
  multiline?: boolean;
}

export interface TicketSummarySection {
  title: string;
  rows: TicketSummaryRow[];
}

@Component({
  selector: 'app-ticket-summary-dialog',
  imports: [FormsModule],
  templateUrl: './ticket-summary-dialog.html',
})
export class TicketSummaryDialog {
  private readonly directoryService = inject(DirectoryService);

  /** When set, the dialog is open for this ticket. */
  readonly ticket = input<Ticket | null>(null);
  readonly closed = output<void>();
  readonly viewHistory = output<Ticket>();
  readonly directoryLinked = output<Ticket>();

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly profile = signal<DirectoryProfile | null>(null);
  protected readonly sections = signal<TicketSummarySection[]>([]);
  protected readonly copiedKey = signal<string | null>(null);
  protected readonly linkPersonType = signal('');
  protected readonly linkPersonNo = signal('');
  protected readonly linking = signal(false);
  protected readonly linkError = signal<string | null>(null);

  private copyResetTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    effect(() => {
      const ticket = this.ticket();
      if (ticket) {
        this.sections.set(this.buildSections(ticket, null));
        void this.load(ticket);
      } else {
        this.profile.set(null);
        this.sections.set([]);
        this.error.set(null);
        this.loading.set(false);
        this.copiedKey.set(null);
        this.resetLinkForm();
      }
    });
  }

  protected canLinkDirectory(ticket: Ticket): boolean {
    return needsDirectoryLink(ticket);
  }

  protected async onLinkDirectory(ticket: Ticket): Promise<void> {
    this.linkError.set(null);
    const personNo = this.linkPersonNo().trim();
    if (!personNo) {
      this.linkError.set('Enter a student or employee number.');
      return;
    }
    const email = ticket.requesterEmail?.trim().toLowerCase() ?? '';
    const domain = environment.allowedEmailDomain.toLowerCase();
    if (!email.endsWith(`@${domain}`)) {
      this.linkError.set(`This ticket does not have an @${domain} address to encode.`);
      return;
    }

    this.linking.set(true);
    try {
      const personType = this.linkPersonType().trim().toUpperCase();
      await firstValueFrom(
        this.directoryService.encodeLpuEmail({
          email,
          ticketId: ticket.id,
          personType: personType || null,
          personNo,
        }),
      );
      this.resetLinkForm();
      this.directoryLinked.emit(ticket);
      this.close();
    } catch (err) {
      const message =
        err && typeof err === 'object' && 'error' in err
          ? ((err as { error?: { message?: string } }).error?.message ?? null)
          : null;
      this.linkError.set(message ?? 'Could not link this email to the local record.');
    } finally {
      this.linking.set(false);
    }
  }

  private resetLinkForm(): void {
    this.linkPersonType.set('');
    this.linkPersonNo.set('');
    this.linkError.set(null);
    this.linking.set(false);
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
    this.sections.set(this.buildSections(ticket, null));
    try {
      const profile = await firstValueFrom(
        this.directoryService.lookupProfile({
          email: isPendingRequesterEmail(ticket.requesterEmail, ticket.pendingEmail)
            ? null
            : ticket.requesterEmail,
          personType: ticket.requesterPersonType,
          personNo: ticket.requesterPersonNo,
        }),
      );
      this.profile.set(profile);
      this.sections.set(this.buildSections(ticket, profile));
    } catch {
      this.error.set('Could not load directory details.');
      this.sections.set(this.buildSections(ticket, null));
    } finally {
      this.loading.set(false);
    }
  }

  private buildSections(ticket: Ticket, profile: DirectoryProfile | null): TicketSummarySection[] {
    return [
      { title: 'Ticket', rows: this.buildTicketRows(ticket) },
      { title: this.personSectionTitle(ticket, profile), rows: this.buildPersonRows(ticket, profile) },
    ];
  }

  private buildTicketRows(ticket: Ticket): TicketSummaryRow[] {
    const rows: TicketSummaryRow[] = [
      {
        label: 'Ticket ID',
        value: ticket.ticketNumber || '—',
        copyValue: ticket.ticketNumber || null,
      },
      { label: 'Title', value: ticket.subject?.trim() || '—', multiline: true },
      { label: 'Description', value: ticket.description?.trim() || '—', multiline: true },
      { label: 'Category', value: ticket.categoryLabel || ticket.category || '—' },
      { label: 'Status', value: this.statusLabel(ticket.status) },
      { label: 'Channel', value: ticket.channel === 'ONSITE_RFID' ? 'Onsite' : 'Online' },
      { label: 'Assignee', value: ticket.assignedAdminName?.trim() || 'Unassigned' },
    ];
    if (ticket.queueNumber != null) {
      rows.push({ label: 'Queue number', value: `#${ticket.queueNumber}` });
    }
    rows.push(
      { label: 'Date submitted', value: this.formatWhen(ticket.createdAt) },
      { label: 'Last updated', value: this.formatWhen(ticket.updatedAt) },
    );
    if (ticket.resolvedAt) {
      rows.push({ label: 'Resolved', value: this.formatWhen(ticket.resolvedAt) });
    }
    rows.push({ label: 'ID photo', value: ticket.hasIdPhoto ? 'Yes' : 'No' });
    return rows;
  }

  private buildPersonRows(ticket: Ticket, profile: DirectoryProfile | null): TicketSummaryRow[] {
    const type = (profile?.found ? profile.personType : ticket.requesterPersonType)?.toUpperCase() ?? null;
    const name = (profile?.found && profile.name) || ticket.requesterName || '—';
    const emailRaw = (profile?.found && profile.email) || ticket.requesterEmail || '';
    const email = displayRequesterEmail(emailRaw, ticket.pendingEmail && !(profile?.found && profile.email));
    const personNoRaw = (profile?.found && profile.personNo) || ticket.requesterPersonNo || '';
    const personNo = personNoRaw || '—';
    const department = (profile?.found && profile.department) || '—';
    const course = (profile?.found && profile.course) || '—';
    const position = (profile?.found && profile.position) || '—';

    const emailRow: TicketSummaryRow = {
      label: 'LPU Email',
      value: email,
      copyValue: isPendingRequesterEmail(emailRaw, ticket.pendingEmail && !(profile?.found && profile.email))
        ? null
        : emailRaw || null,
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
        { label: 'Position', value: position },
      ];
    }

    const rows: TicketSummaryRow[] = [
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
    if (type && type !== 'STUDENT') {
      rows.splice(2, 0, { label: 'Record type', value: type });
    }
    return rows;
  }

  private personSectionTitle(ticket: Ticket, profile: DirectoryProfile | null): string {
    const type = (profile?.found ? profile.personType : ticket.requesterPersonType)?.toUpperCase();
    if (type === 'EMPLOYEE') {
      return 'Employee';
    }
    if (type === 'STUDENT') {
      return 'Student';
    }
    return 'Requester';
  }

  private statusLabel(status: Ticket['status']): string {
    switch (status) {
      case 'OPEN':
        return 'Open';
      case 'IN_PROGRESS':
        return 'In progress';
      case 'RESOLVED':
        return 'Resolved';
      case 'CLOSED':
        return 'Closed';
      default:
        return status;
    }
  }

  private formatWhen(value: string | null | undefined): string {
    if (!value) {
      return '—';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return '—';
    }
    return new Intl.DateTimeFormat('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    }).format(date);
  }
}
