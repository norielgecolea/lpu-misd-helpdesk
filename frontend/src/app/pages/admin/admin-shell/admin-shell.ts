import { Component, HostListener, OnDestroy, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, firstValueFrom } from 'rxjs';
import { AdminService } from '../../../core/admin/admin.service';
import { StaffNotification } from '../../../core/admin/admin.models';
import { AuthService } from '../../../core/auth/auth.service';
import { ThemeService } from '../../../core/theme/theme.service';
import { adminTicketsPathForChannel } from '../../../core/tickets/ticket.models';

interface NavItem {
  label: string;
  route: string;
  icon:
    | 'tickets'
    | 'onsite'
    | 'my-tickets'
    | 'queue'
    | 'dashboard'
    | 'analytics'
    | 'csm'
    | 'students'
    | 'employees'
    | 'accounts'
    | 'audit'
    | 'kiosk';
  superAdminOnly?: boolean;
}

interface NavSection {
  label: string | null;
  items: NavItem[];
}

@Component({
  selector: 'app-admin-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule],
  templateUrl: './admin-shell.html',
  styles: `
    .sidebar-nav {
      scrollbar-width: thin;
      scrollbar-color: color-mix(in oklch, var(--sidebar-border) 80%, transparent) transparent;
    }

    .sidebar-nav::-webkit-scrollbar {
      width: 4px;
    }

    .sidebar-nav::-webkit-scrollbar-thumb {
      border-radius: 9999px;
      background: color-mix(in oklch, var(--sidebar-border) 80%, transparent);
    }

    /* Routed page fills the main column (sibling of router-outlet). */
    :host ::ng-deep .page-fill > *:not(router-outlet) {
      display: flex;
      flex: 1 1 0%;
      min-height: 0;
      flex-direction: column;
      overflow: hidden;
    }
  `,
})
export class AdminShell implements OnDestroy {
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  private readonly router = inject(Router);
  private readonly adminApi = inject(AdminService);

  protected readonly sidebarOpen = signal(true);
  protected readonly mobileNavOpen = signal(false);
  protected readonly loggingOut = signal(false);
  protected readonly accountMenuOpen = signal(false);
  protected readonly notificationsOpen = signal(false);
  protected readonly notifications = signal<StaffNotification[]>([]);
  protected readonly unreadCount = signal(0);
  protected readonly notificationsLoading = signal(false);
  protected readonly accountSettingsOpen = signal(false);
  protected readonly accountSettingsLoading = signal(false);
  protected readonly accountSettingsSaving = signal(false);
  protected readonly accountSettingsError = signal<string | null>(null);
  protected readonly accountSettingsSuccess = signal<string | null>(null);
  protected readonly settingsName = signal('');
  protected readonly settingsUsername = signal('');
  protected readonly settingsEmail = signal('');
  protected readonly changePasswordOpen = signal(false);
  protected readonly changePasswordLoading = signal(false);
  protected readonly changePasswordError = signal<string | null>(null);
  protected readonly changePasswordSuccess = signal<string | null>(null);
  protected readonly currentPassword = signal('');
  protected readonly newPassword = signal('');
  protected readonly confirmPassword = signal('');
  private readonly currentUrl = signal(this.router.url);
  private notifTimer: ReturnType<typeof setInterval> | null = null;
  private notifInFlight = false;

  private readonly allNavSections: NavSection[] = [
    {
      label: 'Operations',
      items: [
        { label: 'Dashboard', route: '/admin/dashboard', icon: 'dashboard' },
        { label: 'My tickets', route: '/admin/my-tickets', icon: 'my-tickets' },
        { label: 'Online Tickets', route: '/admin/tickets', icon: 'tickets' },
        { label: 'Onsite tickets', route: '/admin/onsite-tickets', icon: 'onsite' },
        { label: 'Queue', route: '/admin/queue', icon: 'queue' },
        { label: 'Analytics Recap', route: '/admin/analytics', icon: 'analytics' },
        { label: 'CSM by admin', route: '/admin/csm', icon: 'csm' },
      ],
    },
    {
      label: 'Directory',
      items: [
        { label: 'Students', route: '/admin/students', icon: 'students' },
        { label: 'Employees', route: '/admin/employees', icon: 'employees' },
      ],
    },
    {
      label: 'Administration',
      items: [
        { label: 'Kiosk choices', route: '/admin/kiosk-choices', icon: 'kiosk' },
        { label: 'Accounts', route: '/admin/accounts', icon: 'accounts', superAdminOnly: true },
        { label: 'Audit trail', route: '/admin/audit', icon: 'audit', superAdminOnly: true },
      ],
    },
  ];

  protected readonly navSections = computed(() => {
    const isSuperAdmin = this.auth.user()?.role === 'SUPER_ADMIN';
    return this.allNavSections
      .map((section) => ({
        ...section,
        items: section.items.filter((item) => !item.superAdminOnly || isSuperAdmin),
      }))
      .filter((section) => section.items.length > 0);
  });

  protected readonly pageTitle = computed(() => {
    const url = this.currentUrl().split('?')[0];
    const items = this.navSections().flatMap((section) => section.items);
    const match = items.find((item) => url === item.route || url.startsWith(`${item.route}/`));
    return match?.label ?? 'Admin Portal';
  });

  /** Pages that lock to viewport height with internal scroll (like the sidebar). */
  protected readonly isFillHeightPage = computed(() => {
    const url = this.currentUrl().split('?')[0];
    return (
      url.startsWith('/admin/my-tickets')
      || url.startsWith('/admin/tickets')
      || url.startsWith('/admin/onsite-tickets')
      || url.startsWith('/admin/queue')
      || url.startsWith('/admin/students')
      || url.startsWith('/admin/employees')
      || url.startsWith('/admin/audit')
    );
  });

  /** Tickets tables should use the full main column instead of the default page cap. */
  protected readonly isWidePage = computed(() => {
    const url = this.currentUrl().split('?')[0];
    return (
      url.startsWith('/admin/my-tickets')
      || url.startsWith('/admin/tickets')
      || url.startsWith('/admin/onsite-tickets')
      || url.startsWith('/admin/queue')
    );
  });

  protected readonly roleLabel = computed(() =>
    this.auth.user()?.role === 'SUPER_ADMIN' ? 'Super Admin' : 'Admin',
  );

  constructor() {
    this.router.events
      .pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        this.currentUrl.set(this.router.url);
        this.mobileNavOpen.set(false);
      });
    void this.refreshNotifications(true);
    this.notifTimer = setInterval(() => void this.refreshNotifications(false), 8_000);
  }

  ngOnDestroy(): void {
    if (this.notifTimer) {
      clearInterval(this.notifTimer);
      this.notifTimer = null;
    }
  }

  protected showSidebarLabels(): boolean {
    return this.sidebarOpen() || this.mobileNavOpen();
  }

  protected toggleSidebar(): void {
    if (typeof window !== 'undefined' && window.matchMedia('(max-width: 767px)').matches) {
      this.mobileNavOpen.update((open) => !open);
      return;
    }
    this.sidebarOpen.update((open) => !open);
  }

  protected closeMobileNav(): void {
    this.mobileNavOpen.set(false);
  }

  protected isActive(route: string): boolean {
    const url = this.currentUrl().split('?')[0];
    return url === route || url.startsWith(`${route}/`);
  }

  @HostListener('document:click')
  protected onDocumentClick(): void {
    this.accountMenuOpen.set(false);
    this.notificationsOpen.set(false);
  }

  @HostListener('document:visibilitychange')
  protected onVisibilityChange(): void {
    if (typeof document !== 'undefined' && document.visibilityState === 'visible') {
      void this.refreshNotifications(false);
    }
  }

  protected toggleAccountMenu(event: Event): void {
    event.stopPropagation();
    this.notificationsOpen.set(false);
    this.accountMenuOpen.update((open) => !open);
  }

  protected toggleNotifications(event: Event): void {
    event.stopPropagation();
    this.accountMenuOpen.set(false);
    this.notificationsOpen.update((open) => !open);
    if (this.notificationsOpen()) {
      void this.refreshNotifications(true);
    }
  }

  protected unreadLabel(): string {
    const count = this.unreadCount();
    if (count > 9) {
      return '9+';
    }
    return String(count);
  }

  protected relativeTime(iso: string): string {
    const then = new Date(iso).getTime();
    if (Number.isNaN(then)) {
      return '';
    }
    const minutes = Math.floor(Math.max(0, Date.now() - then) / 60_000);
    if (minutes < 1) {
      return 'Just now';
    }
    if (minutes < 60) {
      return `${minutes}m ago`;
    }
    const hours = Math.floor(minutes / 60);
    if (hours < 24) {
      return `${hours}h ago`;
    }
    return `${Math.floor(hours / 24)}d ago`;
  }

  protected async openNotification(notification: StaffNotification): Promise<void> {
    this.notificationsOpen.set(false);
    if (!notification.read) {
      try {
        await firstValueFrom(this.adminApi.markNotificationRead(notification.id));
        this.notifications.update((items) =>
          items.map((item) => (item.id === notification.id ? { ...item, read: true } : item)),
        );
        this.unreadCount.update((count) => Math.max(0, count - 1));
      } catch {
        // Still open the ticket even if the read update fails.
      }
    }
    if (notification.ticketId == null) {
      return;
    }
    const path = adminTicketsPathForChannel(notification.ticketChannel);
    await this.router.navigate([path], {
      queryParams: { ticket: notification.ticketId },
    });
  }

  protected async markAllNotificationsRead(event: Event): Promise<void> {
    event.stopPropagation();
    try {
      await firstValueFrom(this.adminApi.markAllNotificationsRead());
      this.notifications.update((items) => items.map((item) => ({ ...item, read: true })));
      this.unreadCount.set(0);
    } catch {
      // Keep the current list if the request fails.
    }
  }

  protected async clearNotifications(event: Event): Promise<void> {
    event.stopPropagation();
    try {
      await firstValueFrom(this.adminApi.clearNotifications());
      this.notifications.set([]);
      this.unreadCount.set(0);
    } catch {
      // Keep the current list if the request fails.
    }
  }

  private async refreshNotifications(showSpinner: boolean): Promise<void> {
    if (this.notifInFlight) {
      return;
    }
    if (!showSpinner && typeof document !== 'undefined' && document.visibilityState === 'hidden') {
      return;
    }
    this.notifInFlight = true;
    if (showSpinner && this.notifications().length === 0) {
      this.notificationsLoading.set(true);
    }
    try {
      const data = await firstValueFrom(this.adminApi.listNotifications());
      this.notifications.set(data.items ?? []);
      this.unreadCount.set(data.unreadCount ?? 0);
    } catch {
      // Leave the last successful snapshot in place.
    } finally {
      this.notifInFlight = false;
      this.notificationsLoading.set(false);
    }
  }

  protected async openAccountSettings(): Promise<void> {
    this.accountMenuOpen.set(false);
    this.accountSettingsError.set(null);
    this.accountSettingsSuccess.set(null);
    this.currentPassword.set('');
    this.newPassword.set('');
    this.confirmPassword.set('');
    this.accountSettingsOpen.set(true);
    this.accountSettingsLoading.set(true);
    try {
      const profile = await this.auth.getStaffProfile();
      this.settingsName.set(profile.name);
      this.settingsUsername.set(profile.username ?? '');
      this.settingsEmail.set(profile.email);
    } catch (err: unknown) {
      const user = this.auth.user();
      this.settingsName.set(user?.name ?? '');
      this.settingsUsername.set(user?.username ?? '');
      this.settingsEmail.set(user?.email ?? '');
      this.accountSettingsError.set(this.describeAuthError(err) ?? 'Could not load account details.');
    } finally {
      this.accountSettingsLoading.set(false);
    }
  }

  protected closeAccountSettings(): void {
    if (this.accountSettingsSaving()) {
      return;
    }
    this.accountSettingsOpen.set(false);
  }

  protected async submitAccountSettings(): Promise<void> {
    this.accountSettingsError.set(null);
    this.accountSettingsSuccess.set(null);
    const name = this.settingsName().trim();
    const username = this.settingsUsername().trim().toLowerCase();
    const email = this.settingsEmail().trim();
    const current = this.currentPassword();
    const next = this.newPassword();
    const confirm = this.confirmPassword();

    if (!name || !username || !email) {
      this.accountSettingsError.set('Please fill in name, username, and email.');
      return;
    }
    if (username.length < 3) {
      this.accountSettingsError.set('Username must be at least 3 characters.');
      return;
    }
    if (!/^[a-zA-Z0-9._-]+$/.test(username)) {
      this.accountSettingsError.set('Username may only contain letters, numbers, dots, underscores, and hyphens.');
      return;
    }
    if (next || confirm || current) {
      if (!current || !next) {
        this.accountSettingsError.set('Enter your current and new password to change it.');
        return;
      }
      if (next.length < 8) {
        this.accountSettingsError.set('New password must be at least 8 characters.');
        return;
      }
      if (next !== confirm) {
        this.accountSettingsError.set('New passwords do not match.');
        return;
      }
    }

    this.accountSettingsSaving.set(true);
    try {
      await this.auth.updateStaffProfile({
        name,
        username,
        email,
        currentPassword: next ? current : undefined,
        newPassword: next || undefined,
      });
      this.accountSettingsSuccess.set('Account settings saved.');
      this.currentPassword.set('');
      this.newPassword.set('');
      this.confirmPassword.set('');
      setTimeout(() => this.accountSettingsOpen.set(false), 1200);
    } catch (err: unknown) {
      this.accountSettingsError.set(this.describeAuthError(err) ?? 'Could not save account settings.');
    } finally {
      this.accountSettingsSaving.set(false);
    }
  }

  protected openChangePassword(): void {
    this.currentPassword.set('');
    this.newPassword.set('');
    this.confirmPassword.set('');
    this.changePasswordError.set(null);
    this.changePasswordSuccess.set(null);
    this.changePasswordOpen.set(true);
  }

  protected closeChangePassword(): void {
    if (this.changePasswordLoading()) {
      return;
    }
    this.changePasswordOpen.set(false);
  }

  protected async submitChangePassword(): Promise<void> {
    this.changePasswordError.set(null);
    this.changePasswordSuccess.set(null);
    const current = this.currentPassword();
    const next = this.newPassword();
    const confirm = this.confirmPassword();
    if (!current || !next) {
      this.changePasswordError.set('Enter your current and new password.');
      return;
    }
    if (next.length < 8) {
      this.changePasswordError.set('New password must be at least 8 characters.');
      return;
    }
    if (next !== confirm) {
      this.changePasswordError.set('New passwords do not match.');
      return;
    }
    this.changePasswordLoading.set(true);
    try {
      const message = await this.auth.changePassword(current, next);
      this.changePasswordSuccess.set(message);
      this.currentPassword.set('');
      this.newPassword.set('');
      this.confirmPassword.set('');
      setTimeout(() => this.changePasswordOpen.set(false), 1200);
    } catch (err: unknown) {
      const message =
        err && typeof err === 'object' && 'error' in err
          ? ((err as { error?: { message?: string } }).error?.message ?? null)
          : null;
      this.changePasswordError.set(message ?? 'Could not change password.');
    } finally {
      this.changePasswordLoading.set(false);
    }
  }

  protected signOut(): void {
    this.loggingOut.set(true);
    void this.auth.logout('/admin').finally(() => this.loggingOut.set(false));
  }

  private describeAuthError(err: unknown): string | null {
    if (err && typeof err === 'object' && 'error' in err) {
      return (err as { error?: { message?: string } }).error?.message ?? null;
    }
    return null;
  }
}
