import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, ViewChild, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { APP_NAME, APP_VERSION } from '../../core/app-info';
import { AuthService } from '../../core/auth/auth.service';
import { ThemeService } from '../../core/theme/theme.service';
import { TurnstileWidget } from '../../shared/turnstile-widget/turnstile-widget';

@Component({
  selector: 'app-admin-login',
  imports: [FormsModule, RouterLink, TurnstileWidget],
  templateUrl: './admin-login.html',
})
export class AdminLogin implements OnInit, OnDestroy {
  @ViewChild(TurnstileWidget) protected readonly turnstile?: TurnstileWidget;

  protected readonly login = signal('');
  protected readonly password = signal('');
  protected readonly rememberMe = signal(false);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly appName = APP_NAME;
  protected readonly appVersion = APP_VERSION;

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly theme = inject(ThemeService);

  ngOnInit(): void {
    this.theme.applyAppearance('light');
  }

  ngOnDestroy(): void {
    this.theme.applyStored();
  }

  protected async onSubmit(): Promise<void> {
    this.error.set(null);
    const login = this.login().trim();
    const password = this.password();

    if (!login || !password) {
      this.error.set('Please enter your username/email and password.');
      return;
    }
    const turnstileToken = this.turnstile?.currentToken() ?? '';
    if (!turnstileToken) {
      this.error.set('Please complete the verification check.');
      return;
    }

    this.loading.set(true);
    try {
      await this.auth.loginWithPassword(login, password, this.rememberMe(), turnstileToken);
      await this.router.navigateByUrl(this.auth.homeRoute());
    } catch (err: unknown) {
      this.error.set(this.describeError(err));
    } finally {
      this.loading.set(false);
      this.turnstile?.reset();
    }
  }

  private describeError(err: unknown): string {
    if (err instanceof HttpErrorResponse && err.status === 403) {
      return 'Verification failed. Refresh the check and try again.';
    }
    const message =
      err && typeof err === 'object' && 'error' in err
        ? ((err as { error?: { message?: string } }).error?.message ?? null)
        : null;
    return message ?? 'Invalid username/email or password.';
  }
}
