import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { AdminService } from '../../../core/admin/admin.service';
import { AdminCategory } from '../../../core/admin/admin.models';

type FormKind = 'main' | 'problem';

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
  protected readonly formKind = signal<FormKind>('main');
  protected readonly formParent = signal<AdminCategory | null>(null);
  protected readonly editing = signal<AdminCategory | null>(null);
  protected readonly deleting = signal<AdminCategory | null>(null);
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly label = signal('');
  protected readonly showOnKiosk = signal(true);
  protected readonly showOnline = signal(true);
  protected readonly requiresDetail = signal(false);
  protected readonly active = signal(true);

  async ngOnInit(): Promise<void> {
    await this.load();
  }

  protected childrenOf(category: AdminCategory): AdminCategory[] {
    return category.children ?? [];
  }

  protected openCreateMain(): void {
    this.editing.set(null);
    this.formParent.set(null);
    this.formKind.set('main');
    this.formError.set(null);
    this.label.set('');
    this.showOnKiosk.set(true);
    this.showOnline.set(true);
    this.requiresDetail.set(false);
    this.active.set(true);
    this.showForm.set(true);
  }

  protected openCreateProblem(parent: AdminCategory): void {
    this.editing.set(null);
    this.formParent.set(parent);
    this.formKind.set('problem');
    this.formError.set(null);
    this.label.set('');
    this.requiresDetail.set(false);
    this.active.set(true);
    this.showForm.set(true);
  }

  protected openEdit(category: AdminCategory, parent: AdminCategory | null = null): void {
    this.editing.set(category);
    this.formParent.set(parent);
    this.formKind.set(parent || category.parentId ? 'problem' : 'main');
    this.formError.set(null);
    this.label.set(category.label);
    this.showOnKiosk.set(category.showOnKiosk);
    this.showOnline.set(category.showOnline);
    this.requiresDetail.set(category.requiresDetail);
    this.active.set(category.active);
    this.showForm.set(true);
  }

  protected closeForm(): void {
    this.showForm.set(false);
    this.editing.set(null);
    this.formParent.set(null);
  }

  protected formTitle(): string {
    const editing = this.editing();
    if (this.formKind() === 'problem') {
      const parentLabel = this.formParent()?.label ?? 'category';
      return editing ? `Edit problem · ${parentLabel}` : `Add problem · ${parentLabel}`;
    }
    return editing ? 'Edit category' : 'New category';
  }

  protected askDelete(category: AdminCategory): void {
    this.deleting.set(category);
    this.error.set(null);
  }

  protected cancelDelete(): void {
    this.deleting.set(null);
  }

  protected async confirmDelete(): Promise<void> {
    const category = this.deleting();
    if (!category || this.isBusy(category.id)) {
      return;
    }
    this.setBusy(category.id, true);
    this.error.set(null);
    try {
      await firstValueFrom(this.adminService.deleteCategory(category.id));
      this.deleting.set(null);
      if (this.editing()?.id === category.id) {
        this.closeForm();
      }
      await this.load();
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.setBusy(category.id, false);
    }
  }

  protected async submitForm(): Promise<void> {
    this.formError.set(null);
    const label = this.label().trim();
    if (!label) {
      this.formError.set('Name is required.');
      return;
    }

    this.submitting.set(true);
    try {
      const editing = this.editing();
      if (editing) {
        await firstValueFrom(
          this.adminService.updateCategory(editing.id, {
            label,
            sortOrder: editing.sortOrder,
            active: this.active(),
            showOnKiosk: this.formKind() === 'main' ? this.showOnKiosk() : editing.showOnKiosk,
            showOnline: this.formKind() === 'main' ? this.showOnline() : editing.showOnline,
            requiresDetail: this.formKind() === 'problem' ? this.requiresDetail() : false,
          }),
        );
      } else if (this.formKind() === 'problem') {
        const parent = this.formParent();
        if (!parent) {
          this.formError.set('Pick a category first.');
          this.submitting.set(false);
          return;
        }
        await firstValueFrom(
          this.adminService.createCategory({
            label,
            parentId: parent.id,
            requiresDetail: this.requiresDetail(),
          }),
        );
      } else {
        await firstValueFrom(
          this.adminService.createCategory({
            label,
            showOnKiosk: this.showOnKiosk(),
            showOnline: this.showOnline(),
          }),
        );
      }
      this.showForm.set(false);
      this.editing.set(null);
      this.formParent.set(null);
      await this.load();
    } catch (err) {
      this.formError.set(this.describeError(err));
    } finally {
      this.submitting.set(false);
    }
  }

  protected async toggleActive(category: AdminCategory): Promise<void> {
    await this.patch(category, { active: !category.active });
  }

  protected async toggleKiosk(category: AdminCategory): Promise<void> {
    await this.patch(category, { showOnKiosk: !category.showOnKiosk });
  }

  protected async move(category: AdminCategory, direction: -1 | 1, parent: AdminCategory | null = null): Promise<void> {
    const siblings = parent ? this.childrenOf(parent) : this.categories();
    const index = siblings.findIndex((item) => item.id === category.id);
    const swapWith = siblings[index + direction];
    if (!swapWith || this.isBusy(category.id) || this.isBusy(swapWith.id)) {
      return;
    }
    const firstOrder = category.sortOrder;
    const secondOrder = swapWith.sortOrder === firstOrder ? firstOrder + direction * 10 : swapWith.sortOrder;
    this.setBusy(category.id, true);
    this.setBusy(swapWith.id, true);
    this.error.set(null);
    try {
      await firstValueFrom(
        this.adminService.updateCategory(category.id, this.updatePayload(category, { sortOrder: secondOrder })),
      );
      await firstValueFrom(
        this.adminService.updateCategory(swapWith.id, this.updatePayload(swapWith, { sortOrder: firstOrder })),
      );
      await this.load();
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.setBusy(category.id, false);
      this.setBusy(swapWith.id, false);
    }
  }

  protected canMove(category: AdminCategory, direction: -1 | 1, parent: AdminCategory | null = null): boolean {
    const siblings = parent ? this.childrenOf(parent) : this.categories();
    const index = siblings.findIndex((item) => item.id === category.id);
    return index + direction >= 0 && index + direction < siblings.length;
  }

  protected isBusy(id: number): boolean {
    return this.busyIds().has(id);
  }

  private async patch(category: AdminCategory, changes: Partial<AdminCategory>): Promise<void> {
    this.setBusy(category.id, true);
    this.error.set(null);
    try {
      await firstValueFrom(this.adminService.updateCategory(category.id, this.updatePayload(category, changes)));
      await this.load();
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.setBusy(category.id, false);
    }
  }

  private updatePayload(category: AdminCategory, changes: Partial<AdminCategory>) {
    return {
      label: changes.label ?? category.label,
      sortOrder: changes.sortOrder ?? category.sortOrder,
      active: changes.active ?? category.active,
      showOnKiosk: changes.showOnKiosk ?? category.showOnKiosk,
      showOnline: changes.showOnline ?? category.showOnline,
      requiresDetail: changes.requiresDetail ?? category.requiresDetail,
    };
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.categories.set(await firstValueFrom(this.adminService.listCategories()));
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
