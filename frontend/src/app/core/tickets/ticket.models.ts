export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
export type TicketChannel = 'ONLINE' | 'ONSITE_RFID';

export function adminTicketsPathForChannel(channel: string | null | undefined): string {
  return channel === 'ONSITE_RFID' ? '/admin/onsite-tickets' : '/admin/tickets';
}

export interface TicketCategoryOption {
  value: string;
  label: string;
  requiresDetail?: boolean;
  children?: TicketCategoryOption[];
}

export function ticketCategoryPath(ticket: {
  categoryPath?: string | null;
  categoryLabel?: string | null;
  subcategoryLabel?: string | null;
  category?: string | null;
}): string {
  if (ticket.categoryPath) {
    return ticket.categoryPath;
  }
  if (ticket.subcategoryLabel && ticket.categoryLabel) {
    return `${ticket.categoryLabel} / ${ticket.subcategoryLabel}`;
  }
  return ticket.categoryLabel || ticket.category || '';
}

export interface Ticket {
  id: number;
  ticketNumber: string;
  requesterEmail: string;
  requesterName: string;
  requesterPersonType: string | null;
  requesterPersonNo: string | null;
  /** Declared campus email for outside-sender tickets (visible under both accounts). */
  requesterLpuEmail?: string | null;
  category: string;
  categoryLabel: string;
  subcategory?: string | null;
  subcategoryLabel?: string | null;
  categoryPath?: string | null;
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
  /** Times the requester has reopened this ticket (max 2). */
  requesterReopenCount?: number;
  /** False when closed permanently after 2 reopens. */
  canReopen?: boolean;
}

export interface TicketPage {
  items: Ticket[];
  total: number;
  unreadTotal: number;
  openCount: number;
  inProgressCount: number;
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
  subcategory: string;
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

export interface TicketMessagesResponse {
  messages: TicketMessage[];
  requesterOnline: boolean;
  staffOnline: boolean;
}

export function isRequesterMessage(message: Pick<TicketMessage, 'authorRole'>): boolean {
  return (message.authorRole ?? '').toUpperCase() === 'USER';
}

export function isStaffMessage(message: Pick<TicketMessage, 'authorRole'>): boolean {
  const role = (message.authorRole ?? '').toUpperCase();
  return role === 'ADMIN' || role === 'SUPER_ADMIN' || role === 'MONITORING';
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

export function ticketResolveHours(ticket: {
  createdAt?: string | null;
  resolvedAt?: string | null;
  resolveHours?: number | null;
}): number | null {
  if (ticket.resolveHours != null && Number.isFinite(ticket.resolveHours)) {
    return ticket.resolveHours;
  }
  if (!ticket.createdAt || !ticket.resolvedAt) {
    return null;
  }
  const start = Date.parse(ticket.createdAt);
  const end = Date.parse(ticket.resolvedAt);
  if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) {
    return null;
  }
  return Math.round(((end - start) / 3_600_000) * 10) / 10;
}

export function formatResolveDuration(hours: number | null | undefined): string {
  if (hours == null || !Number.isFinite(hours)) {
    return '—';
  }
  if (hours < 1) {
    return `${Math.max(1, Math.round(hours * 60))} min`;
  }
  if (hours < 24) {
    return `${hours.toFixed(1)} h`;
  }
  return `${(Math.round((hours / 24) * 10) / 10).toFixed(1)} d`;
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
