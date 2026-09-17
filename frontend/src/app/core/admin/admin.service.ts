import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Ticket, TicketChannel, TicketPage, TicketStatus } from '../tickets/ticket.models';
import {
  AdminAccount,
  AdminCategory,
  AdminSummary,
  AnalyticsCsmRating,
  AnalyticsSummary,
  AnalyticsTicketList,
  AnalyticsCsmByAssignee,
  CreateAdminRequest,
  UpdateAdminRequest,
  CreateCategoryRequest,
  QueueSnapshot,
  StaffNotification,
  StaffNotificationList,
  UpdateCategoryRequest,
  WalkInTicketRequest,
} from './admin.models';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);

  // --- Accounts (Super Admin only) ---

  listAdmins(): Observable<AdminAccount[]> {
    return this.http.get<AdminAccount[]>(`${environment.apiBaseUrl}/admin/accounts`);
  }

  createAdmin(request: CreateAdminRequest): Observable<AdminAccount> {
    return this.http.post<AdminAccount>(`${environment.apiBaseUrl}/admin/accounts`, request);
  }

  updateAdmin(id: number, request: UpdateAdminRequest): Observable<AdminAccount> {
    return this.http.patch<AdminAccount>(`${environment.apiBaseUrl}/admin/accounts/${id}`, request);
  }

  setAdminActive(id: number, active: boolean): Observable<AdminAccount> {
    return this.http.patch<AdminAccount>(`${environment.apiBaseUrl}/admin/accounts/${id}/active`, { active });
  }

  // --- Analytics ---

  getAnalyticsSummary(from: string, to: string): Observable<AnalyticsSummary> {
    const params = new URLSearchParams({ from, to });
    return this.http.get<AnalyticsSummary>(
      `${environment.apiBaseUrl}/admin/analytics/summary?${params.toString()}`,
    );
  }

  getAssigneeTickets(adminId: number, from: string, to: string): Observable<AnalyticsTicketList> {
    const params = new URLSearchParams({
      adminId: String(adminId),
      from,
      to,
    });
    return this.http.get<AnalyticsTicketList>(
      `${environment.apiBaseUrl}/admin/analytics/assignee-tickets?${params.toString()}`,
    );
  }

  getCsmTickets(
    rating: AnalyticsCsmRating,
    from: string,
    to: string,
    adminId?: number | null,
  ): Observable<AnalyticsTicketList> {
    const params = new URLSearchParams({ rating, from, to });
    if (adminId != null) {
      params.set('adminId', String(adminId));
    }
    return this.http.get<AnalyticsTicketList>(
      `${environment.apiBaseUrl}/admin/analytics/csm-tickets?${params.toString()}`,
    );
  }

  getCsmByAssignee(from: string, to: string): Observable<AnalyticsCsmByAssignee> {
    const params = new URLSearchParams({ from, to });
    return this.http.get<AnalyticsCsmByAssignee>(
      `${environment.apiBaseUrl}/admin/analytics/csm-by-assignee?${params.toString()}`,
    );
  }

  getCreatedTickets(from: string, to: string, limit?: number): Observable<AnalyticsTicketList> {
    const params = new URLSearchParams({ from, to });
    if (limit != null) {
      params.set('limit', String(limit));
    }
    return this.http.get<AnalyticsTicketList>(
      `${environment.apiBaseUrl}/admin/analytics/tickets?${params.toString()}`,
    );
  }

  getResolvedTickets(from: string, to: string, limit?: number): Observable<AnalyticsTicketList> {
    const params = new URLSearchParams({ from, to });
    if (limit != null) {
      params.set('limit', String(limit));
    }
    return this.http.get<AnalyticsTicketList>(
      `${environment.apiBaseUrl}/admin/analytics/resolved-tickets?${params.toString()}`,
    );
  }

  // --- Ticket / kiosk categories ---

  listCategories(): Observable<AdminCategory[]> {
    return this.http.get<AdminCategory[]>(`${environment.apiBaseUrl}/admin/categories`);
  }

  createCategory(request: CreateCategoryRequest): Observable<AdminCategory> {
    return this.http.post<AdminCategory>(`${environment.apiBaseUrl}/admin/categories`, request);
  }

  updateCategory(id: number, request: UpdateCategoryRequest): Observable<AdminCategory> {
    return this.http.put<AdminCategory>(`${environment.apiBaseUrl}/admin/categories/${id}`, request);
  }

  deleteCategory(id: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiBaseUrl}/admin/categories/${id}`);
  }

  // --- Online tickets ---

  listTickets(params: {
    status?: TicketStatus | '';
    channel?: TicketChannel | null;
    scope?: string;
    category?: string;
    subcategory?: string;
    sort?: string;
    dir?: 'asc' | 'desc';
    offset?: number;
    limit?: number;
  } = {}): Observable<TicketPage> {
    let httpParams = new HttpParams()
      .set('offset', String(params.offset ?? 0))
      .set('limit', String(params.limit ?? 20))
      .set('sort', params.sort ?? 'updatedAt')
      .set('dir', params.dir ?? 'desc');
    if (params.status) {
      httpParams = httpParams.set('status', params.status);
    }
    if (params.channel) {
      httpParams = httpParams.set('channel', params.channel);
    }
    if (params.scope && params.scope !== 'all') {
      httpParams = httpParams.set('scope', params.scope);
    }
    if (params.category) {
      httpParams = httpParams.set('category', params.category);
    }
    if (params.subcategory) {
      httpParams = httpParams.set('subcategory', params.subcategory);
    }
    return this.http.get<TicketPage>(`${environment.apiBaseUrl}/admin/tickets`, { params: httpParams });
  }

  listTicketHistory(params: {
    email?: string | null;
    personType?: string | null;
    personNo?: string | null;
  }): Observable<Ticket[]> {
    const query = new URLSearchParams();
    if (params.email?.trim()) {
      query.set('email', params.email.trim());
    }
    if (params.personType?.trim()) {
      query.set('personType', params.personType.trim());
    }
    if (params.personNo?.trim()) {
      query.set('personNo', params.personNo.trim());
    }
    return this.http.get<Ticket[]>(`${environment.apiBaseUrl}/admin/tickets/history?${query.toString()}`);
  }

  listAssignees(): Observable<AdminSummary[]> {
    return this.http.get<AdminSummary[]>(`${environment.apiBaseUrl}/admin/tickets/assignees`);
  }

  assignTicket(ticketId: number, adminId: number | null): Observable<Ticket> {
    return this.http.patch<Ticket>(`${environment.apiBaseUrl}/admin/tickets/${ticketId}/assign`, { adminId });
  }

  updateTicketStatus(ticketId: number, status: TicketStatus): Observable<Ticket> {
    return this.http.patch<Ticket>(`${environment.apiBaseUrl}/admin/tickets/${ticketId}/status`, { status });
  }

  // --- Onsite tickets (queue page) ---

  getQueueSnapshot(): Observable<QueueSnapshot> {
    return this.http.get<QueueSnapshot>(`${environment.apiBaseUrl}/admin/queue`);
  }

  createWalkIn(request: WalkInTicketRequest): Observable<Ticket> {
    return this.http.post<Ticket>(`${environment.apiBaseUrl}/admin/queue/walk-in`, request);
  }

  claimQueueTicket(ticketId: number): Observable<Ticket> {
    return this.http.post<Ticket>(`${environment.apiBaseUrl}/admin/queue/${ticketId}/claim`, {});
  }

  completeServing(ticketId: number): Observable<Ticket> {
    return this.http.post<Ticket>(`${environment.apiBaseUrl}/admin/queue/${ticketId}/complete`, {});
  }

  requeue(ticketId: number): Observable<Ticket> {
    return this.http.post<Ticket>(`${environment.apiBaseUrl}/admin/queue/${ticketId}/requeue`, {});
  }

  listNotifications(): Observable<StaffNotificationList> {
    return this.http.get<StaffNotificationList>(`${environment.apiBaseUrl}/admin/notifications`);
  }

  markNotificationRead(id: number): Observable<StaffNotification> {
    return this.http.post<StaffNotification>(`${environment.apiBaseUrl}/admin/notifications/${id}/read`, {});
  }

  markAllNotificationsRead(): Observable<void> {
    return this.http.post<void>(`${environment.apiBaseUrl}/admin/notifications/read-all`, {});
  }

  clearNotifications(): Observable<void> {
    return this.http.delete<void>(`${environment.apiBaseUrl}/admin/notifications`);
  }
}
