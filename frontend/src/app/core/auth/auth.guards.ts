import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Signed-in users get redirected away from the login page. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return router.createUrlTree([auth.homeRoute()]);
  }
  return true;
};

/** Anonymous users get sent back to the login page. Online helpdesk only. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/']);
  }
  if (auth.isStaffRole(auth.user()?.role)) {
    return router.createUrlTree([auth.homeRoute()]);
  }
  return true;
};

/** Signed-in admins get redirected away from the /admin login page. */
export const adminGuestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return router.createUrlTree([auth.homeRoute()]);
  }
  return true;
};

/** Only ADMIN/SUPER_ADMIN sessions may reach the admin portal. */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated() && auth.isAdminRole(auth.user()?.role)) {
    return true;
  }
  if (auth.isAuthenticated()) {
    return router.createUrlTree([auth.homeRoute()]);
  }
  return router.createUrlTree(['/admin']);
};

/** MONITORING (and admins) may reach the live display board. */
export const monitoringGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (
    auth.isAuthenticated() &&
    (auth.isMonitoringRole(auth.user()?.role) || auth.isAdminRole(auth.user()?.role))
  ) {
    return true;
  }
  return router.createUrlTree(['/admin']);
};

/** Account management is Super Admin only. */
export const superAdminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated() && auth.user()?.role === 'SUPER_ADMIN') {
    return true;
  }
  if (auth.isAuthenticated() && auth.isAdminRole(auth.user()?.role)) {
    return router.createUrlTree(['/admin/tickets']);
  }
  return router.createUrlTree(['/admin']);
};
