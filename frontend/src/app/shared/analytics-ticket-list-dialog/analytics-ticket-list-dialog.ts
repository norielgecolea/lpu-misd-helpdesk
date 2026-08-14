import { DatePipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { AnalyticsTicketList, AnalyticsTicketListItem } from '../../core/admin/admin.models';

@Component({
  selector: 'app-analytics-ticket-list-dialog',
  imports: [DatePipe],
  templateUrl: './analytics-ticket-list-dialog.html',
})
export class AnalyticsTicketListDialog {
  readonly list = input<AnalyticsTicketList | null>(null);
  readonly loading = input(false);
  readonly error = input<string | null>(null);
  readonly closed = output<void>();
  readonly ticketSelected = output<AnalyticsTicketListItem>();

  protected close(): void {
    this.closed.emit();
  }

  protected onBackdropClick(): void {
    this.close();
  }

  protected onRowClick(item: AnalyticsTicketListItem): void {
    this.ticketSelected.emit(item);
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

  protected csmLabel(rating: string | null): string {
    switch (rating) {
      case 'HAPPY':
        return 'Happy';
      case 'NEUTRAL':
        return 'Neutral';
      case 'SAD':
        return 'Sad';
      default:
        return '';
    }
  }
}
