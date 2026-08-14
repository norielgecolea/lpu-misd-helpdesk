import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, firstValueFrom } from 'rxjs';
import { DirectoryService, Student } from '../../../core/directory/directory.service';

type SortKey = 'name' | 'rfid' | 'department' | 'course' | 'school';
type SortDir = 'asc' | 'desc';

const PAGE_SIZE = 50;

@Component({
  selector: 'app-admin-students',
  imports: [FormsModule],
  templateUrl: './admin-students.html',
  host: { class: 'flex min-h-0 flex-1 flex-col' },
})
export class AdminStudents implements OnInit {
  private readonly directory = inject(DirectoryService);

  protected readonly students = signal<Student[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(true);
  protected readonly loadingMore = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly search = signal('');
  protected readonly sortKey = signal<SortKey>('name');
  protected readonly sortDir = signal<SortDir>('asc');

  private readonly searchChanges = new Subject<string>();
  private offset = 0;

  protected readonly hasMore = computed(() => this.students().length < this.total());

  protected readonly sortedStudents = computed(() => {
    const key = this.sortKey();
    const dir = this.sortDir() === 'asc' ? 1 : -1;
    return [...this.students()].sort((a, b) => {
      const av = (a[key] ?? '').toString().toLowerCase();
      const bv = (b[key] ?? '').toString().toLowerCase();
      return av.localeCompare(bv) * dir;
    });
  });

  constructor() {
    this.searchChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((term) => void this.reload(term));
  }

  async ngOnInit(): Promise<void> {
    await this.reload('');
  }

  protected onSearchChange(term: string): void {
    this.search.set(term);
    this.searchChanges.next(term.trim());
  }

  protected toggleSort(key: SortKey): void {
    if (this.sortKey() === key) {
      this.sortDir.update((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      this.sortKey.set(key);
      this.sortDir.set('asc');
    }
  }

  protected sortIcon(key: SortKey): string {
    if (this.sortKey() !== key) return '↕';
    return this.sortDir() === 'asc' ? '↑' : '↓';
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

  protected initials(name: string): string {
    const parts = name.replace(',', '').trim().split(/\s+/);
    return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase();
  }

  private async reload(search: string): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    this.offset = 0;
    try {
      const page = await firstValueFrom(this.directory.pageStudents(search, 0, PAGE_SIZE));
      this.students.set(page.items);
      this.total.set(page.total);
      this.offset = page.items.length;
    } catch (err) {
      this.students.set([]);
      this.total.set(0);
      this.error.set(this.describeError(err));
    } finally {
      this.loading.set(false);
    }
  }

  private async loadMore(): Promise<void> {
    this.loadingMore.set(true);
    try {
      const page = await firstValueFrom(
        this.directory.pageStudents(this.search().trim(), this.offset, PAGE_SIZE),
      );
      this.students.update((current) => [...current, ...page.items]);
      this.total.set(page.total);
      this.offset += page.items.length;
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.loadingMore.set(false);
    }
  }

  private describeError(err: unknown): string {
    const message =
      err && typeof err === 'object' && 'error' in err
        ? ((err as { error?: { message?: string } }).error?.message ?? null)
        : null;
    return message ?? 'Unable to load students. Check the gate attendance database connection.';
  }
}
