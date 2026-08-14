import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-admin-forgot-password',
  imports: [FormsModule, RouterLink],
  templateUrl: './admin-forgot-password.html',
})
export class AdminForgotPassword {
  protected readonly login = signal('');
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);

  private readonly auth = inject(AuthService);

  protected async onSubmit(): Promise<void> {
    this.error.set(null);
    this.success.set(null);
    const login = this.login().trim();
    if (!login) {
      this.error.set('Enter your username or email.');
      return;
    }
    this.loading.set(true);
    try {
      const message = await this.auth.forgotPassword(login);
      this.success.set(message);
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
    return message ?? 'Could not send reset email. Please try again.';
  }
}
