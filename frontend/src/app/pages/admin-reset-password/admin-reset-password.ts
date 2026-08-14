import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-admin-reset-password',
  imports: [FormsModule, RouterLink],
  templateUrl: './admin-reset-password.html',
})
export class AdminResetPassword implements OnInit {
  protected readonly token = signal('');
  protected readonly newPassword = signal('');
  protected readonly confirmPassword = signal('');
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);

  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token') ?? '';
    this.token.set(token);
    if (!token) {
      this.error.set('This reset link is missing a token. Request a new one from the forgot password page.');
    }
  }

  protected async onSubmit(): Promise<void> {
    this.error.set(null);
    this.success.set(null);
    const token = this.token().trim();
    const password = this.newPassword();
    const confirm = this.confirmPassword();
    if (!token) {
      this.error.set('Reset token is missing.');
      return;
    }
    if (password.length < 8) {
      this.error.set('Password must be at least 8 characters.');
      return;
    }
    if (password !== confirm) {
      this.error.set('Passwords do not match.');
      return;
    }

    this.loading.set(true);
    try {
      const message = await this.auth.resetPassword(token, password);
      this.success.set(message);
      setTimeout(() => void this.router.navigateByUrl('/admin'), 1600);
    } catch (err: unknown) {
      this.error.set(this.describeError(err));
    } finally {
      this.loading.set(false);
    }
  }

  private describeError(err: unknown): string {
    const message =
      err && typeof err === 'object' && 'error' in err
        ? ((err as { error?: { message?: string } }).error?.message ?? null)
        : null;
    return message ?? 'Could not reset password. The link may have expired.';
  }
}
