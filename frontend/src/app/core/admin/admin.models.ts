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

export interface UpdateAdminRequest {
  email: string;
  username: string;
  name: string;
  role?: AdminRole;
  password?: string;
}

export interface StaffProfile {
  id: number;
  email: string;
  username: string | null;
  name: string;
  role: AdminRole;
}

export interface UpdateOwnProfileRequest {
  email: string;
  username: string;
  name: string;
  currentPassword?: string;
  newPassword?: string;
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
  subcategory: string;
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
  parentId: number | null;
  code: string;
  label: string;
  sortOrder: number;
  active: boolean;
  showOnKiosk: boolean;
  showOnline: boolean;
  requiresDetail: boolean;
  createdAt: string;
  updatedAt: string;
  children?: AdminCategory[];
}

export interface CreateCategoryRequest {
  label: string;
  parentId?: number | null;
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

export interface AnalyticsConcernCount {
  categoryKey: string;
  categoryLabel: string;
  concernKey: string;
  concernLabel: string;
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
  byConcern: AnalyticsConcernCount[];
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
  description?: string | null;
  status: string;
  category: string;
  categoryLabel: string;
  categoryPath?: string | null;
  requesterName: string;
  requesterEmail: string;
  channel: string;
  assignedAdminId: number | null;
  assignedAdminName: string | null;
  createdAt: string;
  resolvedAt: string | null;
  resolveHours?: number | null;
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

export type StaffNotificationType = 'NEW_TICKET' | 'NEW_MESSAGE';

export interface StaffNotification {
  id: number;
  type: StaffNotificationType;
  title: string;
  body: string | null;
  ticketId: number | null;
  ticketChannel: string | null;
  read: boolean;
  createdAt: string;
}

export interface StaffNotificationList {
  items: StaffNotification[];
  unreadCount: number;
}
