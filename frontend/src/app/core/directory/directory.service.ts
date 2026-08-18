import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Student {
  id: number;
  name: string;
  studentNo: string;
  photo: string | null;
  rfid: string | null;
  birthdate: string | null;
  department: string;
  course: string;
  school: string;
  financeTagged: boolean;
}

export interface Employee {
  id: number;
  name: string;
  employeeNo: string;
  photo: string | null;
  rfid: string | null;
  birthdate: string | null;
  department: string | null;
  position: string | null;
}

export interface PageResponse<T> {
  items: T[];
  total: number;
}

export interface DirectoryProfile {
  found: boolean;
  personType: 'STUDENT' | 'EMPLOYEE' | string | null;
  name: string | null;
  email: string | null;
  personNo: string | null;
  department: string | null;
  course: string | null;
  position: string | null;
}

export interface EncodeLpuEmailResponse {
  email: string;
  personType: string;
  personNo: string;
  name?: string | null;
  ticketsLinked: number;
}

@Injectable({ providedIn: 'root' })
export class DirectoryService {
  private readonly http = inject(HttpClient);

  pageStudents(search = '', offset = 0, limit = 50): Observable<PageResponse<Student>> {
    const params = new HttpParams()
      .set('search', search)
      .set('offset', offset)
      .set('limit', limit);
    return this.http.get<PageResponse<Student>>(`${environment.apiBaseUrl}/admin/students`, { params });
  }

  pageEmployees(search = '', offset = 0, limit = 50): Observable<PageResponse<Employee>> {
    const params = new HttpParams()
      .set('search', search)
      .set('offset', offset)
      .set('limit', limit);
    return this.http.get<PageResponse<Employee>>(`${environment.apiBaseUrl}/admin/employees`, { params });
  }

  lookupProfile(opts: {
    email?: string | null;
    personType?: string | null;
    personNo?: string | null;
  }): Observable<DirectoryProfile> {
    let params = new HttpParams();
    if (opts.email) {
      params = params.set('email', opts.email);
    }
    if (opts.personType) {
      params = params.set('personType', opts.personType);
    }
    if (opts.personNo) {
      params = params.set('personNo', opts.personNo);
    }
    return this.http.get<DirectoryProfile>(`${environment.apiBaseUrl}/admin/directory/profile`, { params });
  }

  encodeLpuEmail(request: {
    email: string;
    ticketId?: number | null;
    personType?: string | null;
    personNo?: string | null;
  }): Observable<EncodeLpuEmailResponse> {
    return this.http.post<EncodeLpuEmailResponse>(`${environment.apiBaseUrl}/admin/directory/encode-email`, {
      email: request.email,
      ticketId: request.ticketId ?? undefined,
      personType: request.personType ?? undefined,
      personNo: request.personNo ?? undefined,
    });
  }
}
