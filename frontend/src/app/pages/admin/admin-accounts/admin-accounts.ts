import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { AdminService } from '../../../core/admin/admin.service';
import { AdminAccount, AdminRole, CreateAdminRequest } from '../../../core/admin/admin.models';
import { AuthService } from '../../../core/auth/auth.service';

const USERNAME_PATTERN = /^[a-zA-Z0-9._-]+$/;

@Component({
  selector: 'app-admin-accounts',
  imports: [FormsModule, DatePipe],
  templateUrl: './admin-accounts.html',
})
export class AdminAccounts implements OnInit {
  private readonly adminService = inject(AdminService);
  protected readonly auth = inject(AuthService);

  protected readonly admins = signal<AdminAccount[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly busyIds = signal<Set<number>>(new Set());

  protected readonly showForm = signal(false);
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly name = signal('');
  protected readonly username = signal('');
  protected readonly email = signal('');
  protected readonly password = signal('');
  protected readonly role = signal<AdminRole>('ADMIN');

  async ngOnInit(): Promise<void> {
    await this.loadAdmins();
  }

  protected openForm(): void {
    this.formError.set(null);
    this.name.set('');
    this.username.set('');
    this.email.set('');
    this.password.set('');
    this.role.set('ADMIN');
    this.showForm.set(true);
  }

  protected closeForm(): void {
    this.showForm.set(false);
  }

  protected roleLabel(role: AdminRole): string {
    switch (role) {
      case 'SUPER_ADMIN':
        return 'Super Admin';
      case 'MONITORING':
        return 'Monitoring';
      default:
        return 'Admin';
    }
  }

  protected async submitForm(): Promise<void> {
    this.formError.set(null);
    const name = this.name().trim();
    const username = this.username().trim().toLowerCase();
    const email = this.email().trim();
    const password = this.password();

    if (!name || !username || !email || !password) {
      this.formError.set('Please fill in all fields.');
      return;
    }
    if (username.length < 3) {
      this.formError.set('Username must be at least 3 characters.');
      return;
    }
    if (!USERNAME_PATTERN.test(username)) {
      this.formError.set('Username may only contain letters, numbers, dots, underscores, and hyphens.');
      return;
    }
    if (password.length < 8) {
      this.formError.set('Password must be at least 8 characters.');
      return;
    }

    const request: CreateAdminRequest = { name, username, email, password, role: this.role() };
    this.submitting.set(true);
    try {
      const created = await firstValueFrom(this.adminService.createAdmin(request));
      this.admins.update((current) => [created, ...current]);
      this.showForm.set(false);
    } catch (err) {
      this.formError.set(this.describeError(err));
    } finally {
      this.submitting.set(false);
    }
  }

  protected async onToggleActive(admin: AdminAccount): Promise<void> {
    this.error.set(null);
    this.setBusy(admin.id, true);
    try {
      const updated = await firstValueFrom(this.adminService.setAdminActive(admin.id, !admin.active));
      this.admins.update((current) => current.map((a) => (a.id === updated.id ? updated : a)));
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.setBusy(admin.id, false);
    }
  }

  protected isBusy(id: number): boolean {
    return this.busyIds().has(id);
  }

  protected isSelf(admin: AdminAccount): boolean {
    return admin.id === this.auth.userId();
  }

  private async loadAdmins(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const admins = await firstValueFrom(this.adminService.listAdmins());
      this.admins.set(admins);
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
