import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-admin-login',
  imports: [FormsModule, RouterLink],
  templateUrl: './admin-login.html',
})
export class AdminLogin {
  protected readonly login = signal('');
  protected readonly password = signal('');
  protected readonly rememberMe = signal(false);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected async onSubmit(): Promise<void> {
    this.error.set(null);
    const login = this.login().trim();
    const password = this.password();

    if (!login || !password) {
      this.error.set('Please enter your username/email and password.');
      return;
    }

    this.loading.set(true);
    try {
      await this.auth.loginWithPassword(login, password, this.rememberMe());
      await this.router.navigateByUrl(this.auth.homeRoute());
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
    return message ?? 'Invalid username/email or password.';
  }
}
