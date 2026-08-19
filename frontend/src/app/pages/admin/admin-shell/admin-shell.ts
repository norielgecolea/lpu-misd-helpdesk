import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ThemeService } from '../../../core/theme/theme.service';

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
export class AdminShell {
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  private readonly router = inject(Router);

  protected readonly sidebarOpen = signal(true);
  protected readonly mobileNavOpen = signal(false);
  protected readonly loggingOut = signal(false);
  protected readonly changePasswordOpen = signal(false);
  protected readonly changePasswordLoading = signal(false);
  protected readonly changePasswordError = signal<string | null>(null);
  protected readonly changePasswordSuccess = signal<string | null>(null);
  protected readonly currentPassword = signal('');
  protected readonly newPassword = signal('');
  protected readonly confirmPassword = signal('');
  private readonly currentUrl = signal(this.router.url);

  private readonly allNavSections: NavSection[] = [
    {
      label: 'Operations',
      items: [
        { label: 'Dashboard', route: '/admin/dashboard', icon: 'dashboard' },
        { label: 'My tickets', route: '/admin/my-tickets', icon: 'my-tickets' },
        { label: 'Tickets', route: '/admin/tickets', icon: 'tickets' },
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
    );
  });

  /** Tickets tables should use the full main column instead of the default page cap. */
  protected readonly isWidePage = computed(() => {
    const url = this.currentUrl().split('?')[0];
    return (
      url.startsWith('/admin/my-tickets')
      || url.startsWith('/admin/tickets')
      || url.startsWith('/admin/onsite-tickets')
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
}
