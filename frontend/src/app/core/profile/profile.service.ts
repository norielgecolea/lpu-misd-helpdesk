import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { StudentInfoRequest, UserProfile } from './profile.models';

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);

  getProfile(): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${environment.apiBaseUrl}/me/profile`);
  }

  saveStudentInfo(request: StudentInfoRequest): Observable<UserProfile> {
    return this.http.post<UserProfile>(`${environment.apiBaseUrl}/me/student-info`, request);
  }
}
