import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, firstValueFrom } from 'rxjs';
import { AdminService } from '../../../core/admin/admin.service';
import { AuditLogEntry, AuditResourceType } from '../../../core/admin/admin.models';

const PAGE_SIZE = 50;

interface ResourceFilter {
  id: AuditResourceType | '';
  label: string;
}

@Component({
  selector: 'app-admin-audit-trail',
  imports: [FormsModule, DatePipe],
  templateUrl: './admin-audit-trail.html',
  host: { class: 'flex min-h-0 flex-1 flex-col' },
})
export class AdminAuditTrail implements OnInit {
  private readonly adminService = inject(AdminService);

  protected readonly resourceFilters: ResourceFilter[] = [
    { id: '', label: 'All activity' },
    { id: 'AUTH', label: 'Sign-in & passwords' },
    { id: 'ACCOUNT', label: 'Staff accounts' },
    { id: 'TICKET', label: 'Tickets' },
    { id: 'QUEUE', label: 'Queue' },
    { id: 'CATEGORY', label: 'Kiosk choices' },
    { id: 'DIRECTORY', label: 'Directory' },
  ];

  protected readonly logs = signal<AuditLogEntry[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(true);
  protected readonly loadingMore = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly search = signal('');
  protected readonly resourceType = signal<AuditResourceType | ''>('');
  protected readonly from = signal('');
  protected readonly to = signal('');

  private readonly searchChanges = new Subject<string>();
  private offset = 0;

  protected readonly hasMore = computed(() => this.logs().length < this.total());

  constructor() {
    this.searchChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => void this.reload());
  }

  async ngOnInit(): Promise<void> {
    await this.reload();
  }

  protected onSearchChange(term: string): void {
    this.search.set(term);
    this.searchChanges.next(term.trim());
  }

  protected onResourceTypeChange(value: AuditResourceType | ''): void {
    this.resourceType.set(value);
    void this.reload();
  }

  protected onFromChange(value: string): void {
    this.from.set(value);
    void this.reload();
  }

  protected onToChange(value: string): void {
    this.to.set(value);
    void this.reload();
  }

  protected async onTableScroll(event: Event): Promise<void> {
    const el = event.target as HTMLElement;
    if (el.scrollTop + el.clientHeight < el.scrollHeight - 200) {
      return;
    }
    if (!this.hasMore() || this.loadingMore() || this.loading()) {
      return;
    }
    await this.loadMore();
  }

  protected actorLabel(log: AuditLogEntry): string {
    return log.actorName?.trim() || log.actorEmail?.trim() || 'System';
  }

  protected roleLabel(role: string | null): string {
    switch (role) {
      case 'SUPER_ADMIN':
        return 'Super Admin';
      case 'MONITORING':
        return 'Monitoring';
      case 'ADMIN':
        return 'Admin';
      case 'SYSTEM':
        return 'System';
      default:
        return role || '';
    }
  }

  protected resourceLabel(type: AuditResourceType): string {
    switch (type) {
      case 'AUTH':
        return 'Sign-in';
      case 'ACCOUNT':
        return 'Account';
      case 'TICKET':
        return 'Ticket';
      case 'QUEUE':
        return 'Queue';
      case 'CATEGORY':
        return 'Kiosk';
      case 'DIRECTORY':
        return 'Directory';
      default:
        return type;
    }
  }

  private async reload(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    this.offset = 0;
    try {
      const page = await firstValueFrom(this.adminService.listAuditLogs(this.query(0)));
      this.logs.set(page.items ?? []);
      this.total.set(page.total ?? 0);
      this.offset = this.logs().length;
    } catch (err) {
      this.error.set(this.describeError(err));
      this.logs.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  private async loadMore(): Promise<void> {
    this.loadingMore.set(true);
    try {
      const page = await firstValueFrom(this.adminService.listAuditLogs(this.query(this.offset)));
      const incoming = page.items ?? [];
      this.logs.update((current) => [...current, ...incoming]);
      this.total.set(page.total ?? this.total());
      this.offset = this.logs().length;
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.loadingMore.set(false);
    }
  }

  private query(offset: number) {
    return {
      q: this.search().trim() || undefined,
      resourceType: this.resourceType() || undefined,
      from: this.from() || undefined,
      to: this.to() || undefined,
      offset,
      limit: PAGE_SIZE,
    };
  }

  private describeError(err: unknown): string {
    const message =
      err && typeof err === 'object' && 'error' in err
        ? ((err as { error?: { message?: string } }).error?.message ?? null)
        : null;
    return message ?? 'Could not load the audit trail.';
  }
}
