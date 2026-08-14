export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
export type TicketChannel = 'ONLINE' | 'ONSITE_RFID';

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
