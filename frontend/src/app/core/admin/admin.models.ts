import { Ticket, TicketStatus } from '../tickets/ticket.models';

export type AdminRole = 'ADMIN' | 'SUPER_ADMIN' | 'MONITORING';

export interface AdminAccount {
  id: number;
  email: string;
  username: string | null;
  name: string;
  role: AdminRole;
  active: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface CreateAdminRequest {
  email: string;
  username: string;
  name: string;
  password: string;
  role?: AdminRole;
}

/** Lean admin summary used for the ticket "assign to" dropdown. */
export interface AdminSummary {
  id: number;
  name: string;
  email: string;
  role: AdminRole;
}

export interface AssignTicketRequest {
  adminId: number | null;
}

export interface UpdateTicketStatusRequest {
  status: TicketStatus;
}

export interface WalkInTicketRequest {
  name: string;
  email: string;
  category: string;
  subject: string;
  description?: string;
}

export interface NowServingEntry {
  adminId: number;
  adminName: string;
  ticket: Ticket;
}

export interface QueueTransferRequest {
  id: number;
  ticketId: number;
  ticketNumber: string | null;
  queueNumber: number | null;
  requesterName: string | null;
  requesterPersonNo: string | null;
  categoryLabel: string | null;
  fromAdminId: number;
  fromAdminName: string;
  toAdminId: number;
  toAdminName: string;
  status: string;
  createdAt: string;
}

export interface QueueSnapshot {
  waiting: Ticket[];
  nowServing: NowServingEntry[];
  pendingTransfers: QueueTransferRequest[];
}

export interface AdminCategory {
  id: number;
  code: string;
  label: string;
  sortOrder: number;
  active: boolean;
  showOnKiosk: boolean;
  showOnline: boolean;
  requiresDetail: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCategoryRequest {
  code: string;
  label: string;
  sortOrder?: number;
  showOnKiosk?: boolean;
  showOnline?: boolean;
  requiresDetail?: boolean;
}

export interface UpdateCategoryRequest {
  label: string;
  sortOrder?: number;
  active?: boolean;
  showOnKiosk?: boolean;
  showOnline?: boolean;
  requiresDetail?: boolean;
}

export interface AnalyticsNamedCount {
  key: string;
  label: string;
  count: number;
}

export interface AnalyticsDayVolume {
  date: string;
  created: number;
  closed: number;
}

export interface AnalyticsDayCsm {
  date: string;
  sad: number;
  neutral: number;
  happy: number;
}

export interface AnalyticsAssigneeLoad {
  adminId: number;
  name: string;
  open: number;
  inProgress: number;
  closed: number;
}

export interface AnalyticsTotals {
  created: number;
  closed: number;
  open: number;
  inProgress: number;
  unassignedOpen: number;
  avgResolveHours: number | null;
  csmCount: number;
  csmByRating: Record<string, number>;
  csmHappyPercent: number | null;
}

export interface AnalyticsSummary {
  from: string;
  to: string;
  totals: AnalyticsTotals;
  byStatus: AnalyticsNamedCount[];
  byChannel: AnalyticsNamedCount[];
  byCategory: AnalyticsNamedCount[];
  volumeByDay: AnalyticsDayVolume[];
  csmByDay: AnalyticsDayCsm[];
  byAssignee: AnalyticsAssigneeLoad[];
  queueToday: { waiting: number; serving: number };
}

export type AnalyticsCsmRating = 'SAD' | 'NEUTRAL' | 'HAPPY';

export interface AnalyticsTicketListItem {
  id: number;
  ticketNumber: string;
  subject: string;
  status: string;
  category: string;
  categoryLabel: string;
  requesterName: string;
  requesterEmail: string;
  channel: string;
  assignedAdminId: number | null;
  assignedAdminName: string | null;
  createdAt: string;
  resolvedAt: string | null;
  csmRating: AnalyticsCsmRating | null;
  csmComment: string | null;
  csmSubmittedAt: string | null;
}

export interface AnalyticsTicketList {
  title: string;
  truncated: boolean;
  limit: number;
  items: AnalyticsTicketListItem[];
}

export interface AnalyticsAssigneeCsm {
  adminId: number;
  name: string;
  sad: number;
  neutral: number;
  happy: number;
  total: number;
}

export interface AnalyticsCsmByAssignee {
  byAssignee: AnalyticsAssigneeCsm[];
}
