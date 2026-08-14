import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Injectable, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthenticationResult, PublicClientApplication } from '@azure/msal-browser';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AdminLoginRequest,
  AppRole,
  AuthUser,
  LoginResponse,
  OtpRequestResponse,
} from './auth.models';

const TOKEN_KEY = 'lpu_helpdesk_token';
const USER_KEY = 'lpu_helpdesk_user';
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const ADMIN_ROLES: AppRole[] = ['ADMIN', 'SUPER_ADMIN'];
const STAFF_ROLES: AppRole[] = ['ADMIN', 'SUPER_ADMIN', 'MONITORING'];

/** Thrown when an account/email outside @lpulaguna.edu.ph tries to sign in. */
export class InvalidEmailDomainError extends Error {}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly platformId = inject(PLATFORM_ID);

  /** Lazily created — constructing MSAL on HTTP LAN hosts throws crypto_nonexistent. */
  private msalInstance: PublicClientApplication | null = null;
  private msalReady: Promise<void> | null = null;

  private readonly userSignal = signal<AuthUser | null>(this.readStoredUser());
  private readonly tokenSignal = signal<string | null>(this.readStoredToken());

  readonly user = this.userSignal.asReadonly();
  readonly token = this.tokenSignal.asReadonly();
  readonly isAuthenticated = computed(() => !!this.tokenSignal() && !!this.userSignal());

  userId(): number | undefined {
    return this.userSignal()?.id;
  }

  homeRoute(): string {
    if (!this.isAuthenticated()) {
      return '/';
    }
    const role = this.userSignal()?.role;
    if (role === 'MONITORING') {
      return '/monitor';
    }
    return this.isAdminRole(role) ? '/admin/tickets' : '/dashboard';
  }

  isAdminRole(role: AppRole | undefined | null): boolean {
    return !!role && ADMIN_ROLES.includes(role);
  }

  isMonitoringRole(role: AppRole | undefined | null): boolean {
    return role === 'MONITORING';
  }

  isStaffRole(role: AppRole | undefined | null): boolean {
    return !!role && STAFF_ROLES.includes(role);
  }

  /**
   * Opens the Microsoft account picker, rejects any account outside the LPU
   * Laguna domain, then exchanges the Microsoft ID token for an app session
   * via the backend (`POST /api/auth/microsoft`).
   */
  async loginWithMicrosoft(): Promise<AuthUser> {
    const msal = await this.ensureMsalInitialized();

    const result: AuthenticationResult = await msal.loginPopup({
      scopes: ['openid', 'profile', 'email', 'User.Read'],
      prompt: 'select_account',
    });

    const email = (result.account?.username ?? '').toLowerCase();
    if (!this.isAllowedEmail(email)) {
      await msal.logoutPopup({ account: result.account ?? undefined }).catch(() => undefined);
      throw new InvalidEmailDomainError(
        `Please sign in with a @${environment.allowedEmailDomain} account.`,
      );
    }

    const response = await firstValueFrom(
      this.http.post<LoginResponse>(`${environment.apiBaseUrl}/auth/microsoft`, {
        idToken: result.idToken,
      } satisfies { idToken: string }),
    );

    this.persistSession(response);
    return this.userSignal()!;
  }

  /**
   * Sends a one-time login code to the given LPU Laguna email via the
   * backend (`POST /api/auth/otp/request`). Rejects non-LPU-Laguna emails
   * before ever calling the backend.
   */
  async requestOtp(email: string): Promise<OtpRequestResponse> {
    const normalized = this.normalizeEmail(email);
    return firstValueFrom(
      this.http.post<OtpRequestResponse>(`${environment.apiBaseUrl}/auth/otp/request`, {
        email: normalized,
      }),
    );
  }

  /**
   * Verifies the one-time code for the given email via the backend
   * (`POST /api/auth/otp/verify`) and starts the app session on success.
   */
  async verifyOtp(email: string, code: string): Promise<AuthUser> {
    const normalized = this.normalizeEmail(email);
    const response = await firstValueFrom(
      this.http.post<LoginResponse>(`${environment.apiBaseUrl}/auth/otp/verify`, {
        email: normalized,
        code: code.trim(),
      }),
    );

    this.persistSession(response);
    return this.userSignal()!;
  }

  /**
   * Staff email/username + password sign-in via the backend
   * (`POST /api/admin/auth/login`). When `rememberMe` is true the session is
   * kept in `localStorage` so it survives browser restarts; otherwise it's
   * `sessionStorage`-only like the student flows.
   */
  async loginWithPassword(login: string, password: string, rememberMe: boolean): Promise<AuthUser> {
    const response = await firstValueFrom(
      this.http.post<LoginResponse>(`${environment.apiBaseUrl}/admin/auth/login`, {
        login: login.trim().toLowerCase(),
        password,
        rememberMe,
      } satisfies AdminLoginRequest),
    );

    this.persistSession(response, rememberMe);
    return this.userSignal()!;
  }

  async forgotPassword(login: string): Promise<string> {
    const response = await firstValueFrom(
      this.http.post<{ message: string }>(`${environment.apiBaseUrl}/admin/auth/forgot-password`, {
        login: login.trim().toLowerCase(),
      }),
    );
    return response.message;
  }

  async resetPassword(token: string, newPassword: string): Promise<string> {
    const response = await firstValueFrom(
      this.http.post<{ message: string }>(`${environment.apiBaseUrl}/admin/auth/reset-password`, {
        token,
        newPassword,
      }),
    );
    return response.message;
  }

  async changePassword(currentPassword: string, newPassword: string): Promise<string> {
    const response = await firstValueFrom(
      this.http.post<{ message: string }>(`${environment.apiBaseUrl}/admin/auth/change-password`, {
        currentPassword,
        newPassword,
      }),
    );
    return response.message;
  }

  /** Throws {@link InvalidEmailDomainError} for anything outside the LPU Laguna domain. */
  private normalizeEmail(email: string): string {
    const normalized = email.trim().toLowerCase();
    if (!EMAIL_PATTERN.test(normalized) || !this.isAllowedEmail(normalized)) {
      throw new InvalidEmailDomainError(
        `Please use a valid @${environment.allowedEmailDomain} email address.`,
      );
    }
    return normalized;
  }

  private isAllowedEmail(email: string): boolean {
    return email.toLowerCase().endsWith(`@${environment.allowedEmailDomain}`);
  }

  async logout(redirectTo = '/'): Promise<void> {
    this.clearSession();
    if (this.msalInstance) {
      await this.msalInstance.clearCache().catch(() => undefined);
    }
    await this.router.navigate([redirectTo]);
  }

  getAuthorizationHeader(): string | null {
    const token = this.tokenSignal();
    return token ? `Bearer ${token}` : null;
  }

  /** Updates the cached display name (e.g. after directory lookup). */
  updateDisplayName(name: string): void {
    const current = this.userSignal();
    if (!current || !name.trim()) {
      return;
    }
    const updated = { ...current, name: name.trim() };
    this.userSignal.set(updated);
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    const rawLocal = localStorage.getItem(USER_KEY);
    const store = rawLocal ? localStorage : sessionStorage;
    store.setItem(USER_KEY, JSON.stringify(updated));
  }

  private async ensureMsalInitialized(): Promise<PublicClientApplication> {
    if (!isPlatformBrowser(this.platformId)) {
      throw new Error('Microsoft sign-in is only available in the browser.');
    }
    if (!this.isSecureCryptoAvailable()) {
      throw new Error(
        'Microsoft sign-in needs a secure context (HTTPS or localhost). Use OTP login, or open this site over HTTPS.',
      );
    }

    this.msalInstance ??= new PublicClientApplication({
      auth: {
        clientId: environment.msal.clientId,
        authority: `https://login.microsoftonline.com/${environment.msal.tenantId}`,
        redirectUri: environment.msal.redirectUri,
      },
      cache: {
        cacheLocation: 'sessionStorage',
      },
    });
    this.msalReady ??= this.msalInstance.initialize();
    await this.msalReady;
    return this.msalInstance;
  }

  /** Web Crypto subtle is missing on plain HTTP except localhost — MSAL requires it. */
  private isSecureCryptoAvailable(): boolean {
    return typeof globalThis.crypto?.subtle !== 'undefined';
  }

  private persistSession(response: LoginResponse, remember = false): void {
    const user: AuthUser = {
      id: response.id,
      email: response.email,
      name: response.name,
      role: response.role as AppRole,
    };
    this.tokenSignal.set(response.token);
    this.userSignal.set(user);

    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    const store = remember ? localStorage : sessionStorage;
    const other = remember ? sessionStorage : localStorage;
    other.removeItem(TOKEN_KEY);
    other.removeItem(USER_KEY);
    store.setItem(TOKEN_KEY, response.token);
    store.setItem(USER_KEY, JSON.stringify(user));
  }

  private clearSession(): void {
    this.tokenSignal.set(null);
    this.userSignal.set(null);
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  private readStoredToken(): string | null {
    if (!isPlatformBrowser(this.platformId)) {
      return null;
    }
    return localStorage.getItem(TOKEN_KEY) ?? sessionStorage.getItem(TOKEN_KEY);
  }

  private readStoredUser(): AuthUser | null {
    if (!isPlatformBrowser(this.platformId)) {
      return null;
    }
    const raw = localStorage.getItem(USER_KEY) ?? sessionStorage.getItem(USER_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as AuthUser;
    } catch {
      return null;
    }
  }
}
