import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Ticket, TicketStatus } from '../tickets/ticket.models';
import {
  AdminAccount,
  AdminCategory,
  AdminSummary,
  AnalyticsCsmRating,
  AnalyticsSummary,
  AnalyticsTicketList,
  AnalyticsCsmByAssignee,
  CreateAdminRequest,
  CreateCategoryRequest,
  QueueSnapshot,
  QueueTransferRequest,
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

  listTickets(status?: TicketStatus | ''): Observable<Ticket[]> {
    const url = status
      ? `${environment.apiBaseUrl}/admin/tickets?status=${encodeURIComponent(status)}`
      : `${environment.apiBaseUrl}/admin/tickets`;
    return this.http.get<Ticket[]>(url);
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

  // --- Onsite queue ---

  getQueueSnapshot(): Observable<QueueSnapshot> {
    return this.http.get<QueueSnapshot>(`${environment.apiBaseUrl}/admin/queue`);
  }

  createWalkIn(request: WalkInTicketRequest): Observable<Ticket> {
    return this.http.post<Ticket>(`${environment.apiBaseUrl}/admin/queue/walk-in`, request);
  }

  callNext(): Observable<Ticket> {
    return this.http.post<Ticket>(`${environment.apiBaseUrl}/admin/queue/call-next`, {});
  }

  claimQueueTicket(ticketId: number): Observable<Ticket> {
    return this.http.post<Ticket>(`${environment.apiBaseUrl}/admin/queue/${ticketId}/claim`, {});
  }

  transferQueueTicket(ticketId: number, adminId: number): Observable<QueueTransferRequest> {
    return this.http.post<QueueTransferRequest>(`${environment.apiBaseUrl}/admin/queue/${ticketId}/transfer`, {
      adminId,
    });
  }

  approveQueueTransfer(transferId: number): Observable<Ticket> {
    return this.http.post<Ticket>(`${environment.apiBaseUrl}/admin/queue/transfers/${transferId}/approve`, {});
  }

  rejectQueueTransfer(transferId: number): Observable<QueueTransferRequest> {
    return this.http.post<QueueTransferRequest>(
      `${environment.apiBaseUrl}/admin/queue/transfers/${transferId}/reject`,
      {}
    );
  }

  cancelQueueTransfer(transferId: number): Observable<QueueTransferRequest> {
    return this.http.post<QueueTransferRequest>(
      `${environment.apiBaseUrl}/admin/queue/transfers/${transferId}/cancel`,
      {}
    );
  }

  completeServing(ticketId: number): Observable<Ticket> {
    return this.http.post<Ticket>(`${environment.apiBaseUrl}/admin/queue/${ticketId}/complete`, {});
  }

  requeue(ticketId: number): Observable<Ticket> {
    return this.http.post<Ticket>(`${environment.apiBaseUrl}/admin/queue/${ticketId}/requeue`, {});
  }
}
