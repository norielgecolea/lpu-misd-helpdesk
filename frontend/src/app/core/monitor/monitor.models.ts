import { NowServingEntry } from '../admin/admin.models';
import { Ticket } from '../tickets/ticket.models';

export interface MonitorSnapshot {
  nowServing: NowServingEntry[];
  waiting: Ticket[];
  recentTickets: Ticket[];
}
