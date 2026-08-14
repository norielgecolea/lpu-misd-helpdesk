import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { AdminService } from '../../../core/admin/admin.service';
import { AdminCategory } from '../../../core/admin/admin.models';

@Component({
  selector: 'app-admin-kiosk-choices',
  imports: [FormsModule],
  templateUrl: './admin-kiosk-choices.html',
})
export class AdminKioskChoices implements OnInit {
  private readonly adminService = inject(AdminService);

  protected readonly categories = signal<AdminCategory[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly busyIds = signal<Set<number>>(new Set());

  protected readonly showForm = signal(false);
  protected readonly editing = signal<AdminCategory | null>(null);
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly code = signal('');
  protected readonly label = signal('');
  protected readonly sortOrder = signal(0);
  protected readonly showOnKiosk = signal(true);
  protected readonly showOnline = signal(true);
  protected readonly requiresDetail = signal(false);
  protected readonly active = signal(true);

  async ngOnInit(): Promise<void> {
    await this.load();
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.formError.set(null);
    this.code.set('');
    this.label.set('');
    this.sortOrder.set((this.categories().at(-1)?.sortOrder ?? 0) + 10);
    this.showOnKiosk.set(true);
    this.showOnline.set(true);
    this.requiresDetail.set(false);
    this.active.set(true);
    this.showForm.set(true);
  }

  protected openEdit(category: AdminCategory): void {
    this.editing.set(category);
    this.formError.set(null);
    this.code.set(category.code);
    this.label.set(category.label);
    this.sortOrder.set(category.sortOrder);
    this.showOnKiosk.set(category.showOnKiosk);
    this.showOnline.set(category.showOnline);
    this.requiresDetail.set(category.requiresDetail);
    this.active.set(category.active);
    this.showForm.set(true);
  }

  protected closeForm(): void {
    this.showForm.set(false);
    this.editing.set(null);
  }

  protected async submitForm(): Promise<void> {
    this.formError.set(null);
    const label = this.label().trim();
    if (!label) {
      this.formError.set('Label is required.');
      return;
    }

    this.submitting.set(true);
    try {
      const editing = this.editing();
      if (editing) {
        const updated = await firstValueFrom(
          this.adminService.updateCategory(editing.id, {
            label,
            sortOrder: this.sortOrder(),
            active: this.active(),
            showOnKiosk: this.showOnKiosk(),
            showOnline: this.showOnline(),
            requiresDetail: this.requiresDetail(),
          }),
        );
        this.categories.update((list) =>
          list.map((c) => (c.id === updated.id ? updated : c)).sort((a, b) => a.sortOrder - b.sortOrder),
        );
      } else {
        const code = this.code().trim();
        if (!code) {
          this.formError.set('Code is required.');
          this.submitting.set(false);
          return;
        }
        const created = await firstValueFrom(
          this.adminService.createCategory({
            code,
            label,
            sortOrder: this.sortOrder(),
            showOnKiosk: this.showOnKiosk(),
            showOnline: this.showOnline(),
            requiresDetail: this.requiresDetail(),
          }),
        );
        this.categories.update((list) => [...list, created].sort((a, b) => a.sortOrder - b.sortOrder));
      }
      this.showForm.set(false);
      this.editing.set(null);
    } catch (err) {
      this.formError.set(this.describeError(err));
    } finally {
      this.submitting.set(false);
    }
  }

  protected async toggleActive(category: AdminCategory): Promise<void> {
    this.setBusy(category.id, true);
    try {
      const updated = await firstValueFrom(
        this.adminService.updateCategory(category.id, {
          label: category.label,
          sortOrder: category.sortOrder,
          active: !category.active,
          showOnKiosk: category.showOnKiosk,
          showOnline: category.showOnline,
          requiresDetail: category.requiresDetail,
        }),
      );
      this.categories.update((list) => list.map((c) => (c.id === updated.id ? updated : c)));
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.setBusy(category.id, false);
    }
  }

  protected async toggleKiosk(category: AdminCategory): Promise<void> {
    this.setBusy(category.id, true);
    try {
      const updated = await firstValueFrom(
        this.adminService.updateCategory(category.id, {
          label: category.label,
          sortOrder: category.sortOrder,
          active: category.active,
          showOnKiosk: !category.showOnKiosk,
          showOnline: category.showOnline,
          requiresDetail: category.requiresDetail,
        }),
      );
      this.categories.update((list) => list.map((c) => (c.id === updated.id ? updated : c)));
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.setBusy(category.id, false);
    }
  }

  protected isBusy(id: number): boolean {
    return this.busyIds().has(id);
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const list = await firstValueFrom(this.adminService.listCategories());
      this.categories.set(list);
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.loading.set(false);
    }
  }

  private setBusy(id: number, busy: boolean): void {
    this.busyIds.update((current) => {
      const next = new Set(current);
      if (busy) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }

  private describeError(err: unknown): string {
    const message =
      err && typeof err === 'object' && 'error' in err
        ? ((err as { error?: { message?: string } }).error?.message ?? null)
        : null;
    return message ?? 'Something went wrong. Please try again.';
  }
}
