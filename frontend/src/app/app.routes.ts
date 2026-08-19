import { Routes } from '@angular/router';
import { Login } from './pages/login/login';
import { Dashboard } from './pages/dashboard/dashboard';
import { AdminLogin } from './pages/admin-login/admin-login';
import { AdminForgotPassword } from './pages/admin-forgot-password/admin-forgot-password';
import { AdminResetPassword } from './pages/admin-reset-password/admin-reset-password';
import { AdminShell } from './pages/admin/admin-shell/admin-shell';
import { AdminTickets } from './pages/admin/admin-tickets/admin-tickets';
import { AdminQueue } from './pages/admin/admin-queue/admin-queue';
import { AdminAccounts } from './pages/admin/admin-accounts/admin-accounts';
import { AdminKioskChoices } from './pages/admin/admin-kiosk-choices/admin-kiosk-choices';
import { AdminCsm } from './pages/admin/admin-csm/admin-csm';
import { AdminAnalytics } from './pages/admin/admin-analytics/admin-analytics';
import { AdminDashboard } from './pages/admin/admin-dashboard/admin-dashboard';
import { AdminStudents } from './pages/admin/admin-students/admin-students';
import { AdminEmployees } from './pages/admin/admin-employees/admin-employees';
import { Kiosk } from './pages/kiosk/kiosk';
import { Monitor } from './pages/monitor/monitor';
import {
  adminGuard,
  adminGuestGuard,
  authGuard,
  guestGuard,
  monitoringGuard,
  superAdminGuard,
} from './core/auth/auth.guards';

export const routes: Routes = [
  { path: '', component: Login, canActivate: [guestGuard], pathMatch: 'full' },
  { path: 'login', redirectTo: '' },
  { path: 'dashboard', component: Dashboard, canActivate: [authGuard] },

  { path: 'kiosk', component: Kiosk },
  { path: 'tap', redirectTo: 'kiosk' },

  { path: 'admin', component: AdminLogin, canActivate: [adminGuestGuard], pathMatch: 'full' },
  {
    path: 'admin/forgot-password',
    component: AdminForgotPassword,
    canActivate: [adminGuestGuard],
  },
  {
    path: 'admin/reset-password',
    component: AdminResetPassword,
    canActivate: [adminGuestGuard],
  },
  {
    path: 'admin',
    component: AdminShell,
    canActivate: [adminGuard],
    children: [
      { path: '', redirectTo: 'tickets', pathMatch: 'full' },
      { path: 'tickets', component: AdminTickets, data: { scope: 'all', channel: 'ONLINE' } },
      { path: 'onsite-tickets', component: AdminTickets, data: { scope: 'all', channel: 'ONSITE_RFID' } },
      { path: 'my-tickets', component: AdminTickets, data: { scope: 'mine' } },
      { path: 'queue', component: AdminQueue },
      { path: 'dashboard', component: AdminDashboard },
      { path: 'analytics', component: AdminAnalytics },
      { path: 'csm', component: AdminCsm },
      { path: 'students', component: AdminStudents },
      { path: 'employees', component: AdminEmployees },
      { path: 'kiosk-choices', component: AdminKioskChoices },
      { path: 'accounts', component: AdminAccounts, canActivate: [superAdminGuard] },
    ],
  },

  { path: 'monitor', component: Monitor, canActivate: [monitoringGuard] },

  { path: '**', redirectTo: '' },
];
