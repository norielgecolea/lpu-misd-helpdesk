import { Component, ElementRef, OnDestroy, OnInit, ViewChild, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService, InvalidEmailDomainError } from '../../core/auth/auth.service';

const RESEND_COOLDOWN_SECONDS = 60;
const OTP_LENGTH = 6;

type Step = 'email' | 'otp';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.html',
  styles: `
    @keyframes login-rise {
      from {
        opacity: 0;
        transform: translateY(18px) scale(0.985);
      }
      to {
        opacity: 1;
        transform: translateY(0) scale(1);
      }
    }

    @keyframes login-float {
      0%,
      100% {
        transform: translate3d(0, 0, 0);
      }
      50% {
        transform: translate3d(0, -14px, 0);
      }
    }

    .animate-rise {
      animation: login-rise 0.55s cubic-bezier(0.22, 1, 0.36, 1) both;
    }

    .animate-float {
      animation: login-float 7s ease-in-out infinite;
    }

    @media (prefers-reduced-motion: reduce) {
      .animate-rise,
      .animate-float {
        animation: none;
      }
    }
  `,
})
export class Login implements OnInit, OnDestroy {
  @ViewChild('otpInput') private readonly otpInput?: ElementRef<HTMLInputElement>;

  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);

  protected readonly heroImages = [
    { src: '/lpu-building.webp', alt: 'LPU Laguna campus' },
    { src: '/background.webp', alt: 'LPU Laguna building' },
  ];
  protected readonly activeImage = signal(0);
  private readonly slideshowTimer = setInterval(
    () => this.activeImage.update((i) => (i + 1) % this.heroImages.length),
    8000,
  );

  protected readonly otpLength = OTP_LENGTH;
  protected readonly step = signal<Step>('email');
  protected readonly email = signal('');
  protected readonly otp = signal('');
  protected readonly sendingOtp = signal(false);
  protected readonly verifyingOtp = signal(false);
  protected readonly resendCooldown = signal(0);
  private cooldownTimer: ReturnType<typeof setInterval> | null = null;

  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  ngOnInit(): void {
    const msalError = this.auth.consumeMicrosoftRedirectError();
    if (msalError) {
      this.error.set(msalError);
    }
  }

  ngOnDestroy(): void {
    clearInterval(this.slideshowTimer);
    if (this.cooldownTimer) {
      clearInterval(this.cooldownTimer);
    }
  }

  protected async onSignInWithMicrosoft(): Promise<void> {
    this.error.set(null);
    this.loading.set(true);
    try {
      // Full-page redirect — this promise does not resolve on success.
      await this.auth.loginWithMicrosoft();
    } catch (err: unknown) {
      this.error.set(this.describeError(err));
      this.loading.set(false);
    }
  }

  protected async onSendOtp(): Promise<void> {
    this.error.set(null);
    const email = this.email().trim();
    if (!email) {
      this.error.set('Please enter your LPU Laguna email address.');
      return;
    }

    this.sendingOtp.set(true);
    try {
      await this.auth.requestOtp(email);
      this.email.set(email.toLowerCase());
      this.otp.set('');
      this.step.set('otp');
      this.startResendCooldown();
      queueMicrotask(() => this.otpInput?.nativeElement.focus());
    } catch (err: unknown) {
      this.error.set(this.describeError(err));
    } finally {
      this.sendingOtp.set(false);
    }
  }

  protected async onResendOtp(): Promise<void> {
    if (this.resendCooldown() > 0) {
      return;
    }
    await this.onSendOtp();
  }

  protected async onVerifyOtp(): Promise<void> {
    this.error.set(null);
    const code = this.otp().trim();
    if (code.length !== this.otpLength) {
      this.error.set(`Enter the ${this.otpLength}-digit code sent to your email.`);
      return;
    }

    this.verifyingOtp.set(true);
    try {
      await this.auth.verifyOtp(this.email(), code);
      await this.router.navigateByUrl(this.auth.homeRoute());
    } catch (err: unknown) {
      this.error.set(this.describeError(err));
    } finally {
      this.verifyingOtp.set(false);
    }
  }

  protected onChangeEmail(): void {
    this.error.set(null);
    this.otp.set('');
    this.step.set('email');
    if (this.cooldownTimer) {
      clearInterval(this.cooldownTimer);
      this.cooldownTimer = null;
    }
    this.resendCooldown.set(0);
  }

  private startResendCooldown(): void {
    if (this.cooldownTimer) {
      clearInterval(this.cooldownTimer);
    }
    this.resendCooldown.set(RESEND_COOLDOWN_SECONDS);
    this.cooldownTimer = setInterval(() => {
      const next = this.resendCooldown() - 1;
      if (next <= 0) {
        this.resendCooldown.set(0);
        clearInterval(this.cooldownTimer!);
        this.cooldownTimer = null;
      } else {
        this.resendCooldown.set(next);
      }
    }, 1000);
  }

  private describeError(err: unknown): string {
    if (err instanceof InvalidEmailDomainError) {
      return err.message;
    }
    // MSAL throws when the user closes the popup — not a real failure.
    if (err && typeof err === 'object' && 'errorCode' in err) {
      const code = (err as { errorCode?: string }).errorCode;
      if (code === 'user_cancelled' || code === 'popup_window_error') {
        return 'Sign-in was cancelled.';
      }
    }
    const message =
      err && typeof err === 'object' && 'error' in err
        ? ((err as { error?: { message?: string } }).error?.message ?? null)
        : null;
    return message ?? 'Something went wrong. Please try again.';
  }
}
