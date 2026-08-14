import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CreateTicketRequest,
  PendingCsm,
  SubmitCsmRequest,
  Ticket,
  TicketCategoryOption,
  TicketMessage,
} from './ticket.models';

@Injectable({ providedIn: 'root' })
export class TicketService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/tickets`;

  getCategories(): Observable<TicketCategoryOption[]> {
    return this.http.get<TicketCategoryOption[]>(`${this.base}/categories`);
  }

  getMyTickets(): Observable<Ticket[]> {
    return this.http.get<Ticket[]>(`${this.base}/mine`);
  }

  getPendingCsm(): Observable<PendingCsm | null> {
    return this.http
      .get<PendingCsm>(`${this.base}/pending-csm`, { observe: 'response' })
      .pipe(map((res: HttpResponse<PendingCsm>) => (res.status === 204 ? null : res.body)));
  }

  submitCsm(ticketId: number, request: SubmitCsmRequest): Observable<PendingCsm> {
    return this.http.post<PendingCsm>(`${this.base}/${ticketId}/csm`, request);
  }

  getTicket(id: number): Observable<Ticket> {
    return this.http.get<Ticket>(`${this.base}/${id}`);
  }

  createTicket(request: CreateTicketRequest): Observable<Ticket> {
    const form = new FormData();
    form.append('category', request.category);
    form.append('subject', request.subject);
    form.append('description', request.description);
    form.append('idPhoto', request.idPhoto, request.idPhoto.name);
    for (const file of request.attachments ?? []) {
      form.append('attachments', file, file.name);
    }
    return this.http.post<Ticket>(this.base, form);
  }

  getIdPhoto(ticketId: number): Observable<Blob> {
    return this.http.get(`${this.base}/${ticketId}/id-photo`, {
      responseType: 'blob',
    });
  }

  listMessages(ticketId: number): Observable<TicketMessage[]> {
    return this.http.get<TicketMessage[]>(`${this.base}/${ticketId}/messages`);
  }

  postMessage(ticketId: number, body: string, attachment?: File | null): Observable<TicketMessage> {
    const form = new FormData();
    if (body.trim()) {
      form.append('body', body.trim());
    }
    if (attachment) {
      form.append('attachment', attachment, attachment.name);
    }
    return this.http.post<TicketMessage>(`${this.base}/${ticketId}/messages`, form);
  }

  getMessageAttachment(ticketId: number, messageId: number): Observable<Blob> {
    return this.http.get(`${this.base}/${ticketId}/messages/${messageId}/attachment`, {
      responseType: 'blob',
    });
  }
}
