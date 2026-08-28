import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, ViewChild, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ThemeService } from '../../core/theme/theme.service';
import { TurnstileWidget } from '../../shared/turnstile-widget/turnstile-widget';

@Component({
  selector: 'app-admin-forgot-password',
  imports: [FormsModule, RouterLink, TurnstileWidget],
  templateUrl: './admin-forgot-password.html',
})
export class AdminForgotPassword implements OnInit, OnDestroy {
  @ViewChild(TurnstileWidget) protected readonly turnstile?: TurnstileWidget;

  protected readonly login = signal('');
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);

  private readonly auth = inject(AuthService);
  private readonly theme = inject(ThemeService);

  ngOnInit(): void {
    this.theme.applyAppearance('light');
  }

  ngOnDestroy(): void {
    this.theme.applyStored();
  }

  protected async onSubmit(): Promise<void> {
    this.error.set(null);
    this.success.set(null);
    const login = this.login().trim();
    if (!login) {
      this.error.set('Enter your username or email.');
      return;
    }
    const turnstileToken = this.turnstile?.currentToken() ?? '';
    if (!turnstileToken) {
      this.error.set('Please complete the verification check.');
      return;
    }
    this.loading.set(true);
    try {
      const message = await this.auth.forgotPassword(login, turnstileToken);
      this.success.set(message);
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
    return message ?? 'Could not send reset email. Please try again.';
  }
}
