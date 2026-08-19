export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
export type TicketChannel = 'ONLINE' | 'ONSITE_RFID';

export function adminTicketsPathForChannel(channel: string | null | undefined): string {
  return channel === 'ONSITE_RFID' ? '/admin/onsite-tickets' : '/admin/tickets';
}

export interface TicketCategoryOption {
  value: string;
  label: string;
  requiresDetail?: boolean;
}

export interface Ticket {
  id: number;
  ticketNumber: string;
  requesterEmail: string;
  requesterName: string;
  requesterPersonType: string | null;
  requesterPersonNo: string | null;
  category: string;
  categoryLabel: string;
  subject: string;
  description: string;
  status: TicketStatus;
  channel: TicketChannel;
  assignedAdminId: number | null;
  assignedAdminName: string | null;
  queueNumber: number | null;
  hasIdPhoto: boolean;
  unreadCount: number;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
  pendingEmail?: boolean;
  /** True when the ticket has a real LPU email that is not on a student/employee record. */
  directoryUnlinked?: boolean;
}

export const LINK_LPU_EMAIL_CATEGORY = 'LINK_LPU_EMAIL';

export function isPendingRequesterEmail(
  email: string | null | undefined,
  pendingEmail?: boolean,
): boolean {
  if (pendingEmail === true) {
    return true;
  }
  if (!email || !email.trim()) {
    return true;
  }
  return email.trim().toLowerCase().endsWith('@pending.invalid');
}

export function displayRequesterEmail(
  email: string | null | undefined,
  pendingEmail?: boolean,
): string {
  if (isPendingRequesterEmail(email, pendingEmail)) {
    return 'No LPU email yet';
  }
  return email ?? '';
}

export function canEncodeLpuEmail(ticket: Pick<Ticket, 'requesterPersonType' | 'requesterPersonNo' | 'requesterEmail' | 'pendingEmail'>): boolean {
  if (!ticket.requesterPersonType || !ticket.requesterPersonNo) {
    return false;
  }
  return isPendingRequesterEmail(ticket.requesterEmail, ticket.pendingEmail);
}

export function needsDirectoryLink(
  ticket: Pick<Ticket, 'requesterPersonType' | 'requesterPersonNo' | 'requesterEmail' | 'pendingEmail' | 'directoryUnlinked'>,
): boolean {
  if (ticket.directoryUnlinked === true) {
    return true;
  }
  if (ticket.directoryUnlinked === false) {
    return false;
  }
  if (isPendingRequesterEmail(ticket.requesterEmail, ticket.pendingEmail)) {
    return false;
  }
  return !ticket.requesterPersonType?.trim() || !ticket.requesterPersonNo?.trim();
}

export interface CreateTicketRequest {
  category: string;
  subject: string;
  description: string;
  idPhoto: File;
  attachments?: File[];
}

export interface TicketMessage {
  id: number;
  ticketId: number;
  authorUserId: number | null;
  authorEmail: string;
  authorName: string;
  authorRole: string;
  body: string;
  hasAttachment: boolean;
  attachmentContentType: string | null;
  attachmentOriginalName: string | null;
  createdAt: string;
}

export function isRequesterMessage(message: Pick<TicketMessage, 'authorRole'>): boolean {
  return (message.authorRole ?? '').toUpperCase() === 'USER';
}

export function messageAuthorLabel(
  message: TicketMessage,
  opts: { isMine: boolean; requesterName?: string | null },
): string {
  if (opts.isMine) {
    return 'You';
  }
  const directoryName = opts.requesterName?.trim();
  if (directoryName && isRequesterMessage(message)) {
    return directoryName;
  }
  return message.authorName;
}

export type CsmRating = 'SAD' | 'NEUTRAL' | 'HAPPY';

export interface PendingCsm {
  ticketId: number;
  ticketNumber: string;
  subject: string;
  categoryLabel: string;
  channel: string;
  closedAt: string;
  createdAt: string;
}

export interface SubmitCsmRequest {
  rating: CsmRating;
  comment?: string;
}
