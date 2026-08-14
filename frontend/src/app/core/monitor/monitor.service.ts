import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MonitorSnapshot } from './monitor.models';

@Injectable({ providedIn: 'root' })
export class MonitorService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/monitor`;

  snapshot(recentLimit = 20): Observable<MonitorSnapshot> {
    return this.http.get<MonitorSnapshot>(`${this.base}/snapshot`, {
      params: { recentLimit },
    });
  }
}
