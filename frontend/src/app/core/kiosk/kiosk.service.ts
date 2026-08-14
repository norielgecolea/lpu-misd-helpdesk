import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PendingCsm, SubmitCsmRequest, Ticket, TicketCategoryOption } from '../tickets/ticket.models';
import { KioskLookupRequest, KioskPerson, KioskTicketRequest, ServerTime } from './kiosk.models';

@Injectable({ providedIn: 'root' })
export class KioskService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/kiosk`;

  serverTime(): Observable<ServerTime> {
    return this.http.get<ServerTime>(`${this.base}/time`);
  }

  lookup(identifier: string): Observable<KioskPerson> {
    return this.http.post<KioskPerson>(`${this.base}/lookup`, {
      identifier,
    } satisfies KioskLookupRequest);
  }

  categories(): Observable<TicketCategoryOption[]> {
    return this.http.get<TicketCategoryOption[]>(`${this.base}/categories`);
  }

  getPendingCsm(identifier: string): Observable<PendingCsm | null> {
    return this.http
      .post<PendingCsm>(
        `${this.base}/pending-csm`,
        { identifier } satisfies KioskLookupRequest,
        { observe: 'response' },
      )
      .pipe(map((res: HttpResponse<PendingCsm>) => (res.status === 204 ? null : res.body)));
  }

  submitCsm(identifier: string, ticketId: number, request: SubmitCsmRequest): Observable<PendingCsm> {
    return this.http.post<PendingCsm>(`${this.base}/csm`, {
      identifier,
      ticketId,
      rating: request.rating,
      comment: request.comment,
    });
  }

  createTicket(request: KioskTicketRequest): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.base}/tickets`, request);
  }
}
