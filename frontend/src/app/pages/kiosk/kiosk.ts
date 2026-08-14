import { HttpErrorResponse } from '@angular/common/http';
import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { playSuccessCue, unlockAudio } from '../../core/audio/cue-sounds';
import { KioskPerson } from '../../core/kiosk/kiosk.models';
import { KioskService } from '../../core/kiosk/kiosk.service';
import { CsmRating, PendingCsm, Ticket, TicketCategoryOption } from '../../core/tickets/ticket.models';

type KioskStep = 'idle' | 'csm' | 'form' | 'success';

const SUCCESS_RESET_MS = 12_000;
const FORM_IDLE_CANCEL_MS = 10_000;
const CLOCK_TICK_MS = 1_000;
const CLOCK_SYNC_MS = 60_000;
const DISPLAY_TIME_ZONE = 'Asia/Manila';

@Component({
  selector: 'app-kiosk',
  imports: [FormsModule],
  templateUrl: './kiosk.html',
  styles: `
    :host {
      --kiosk-maroon: #8d2546;
      --kiosk-maroon-deep: #6b1a34;
      --kiosk-ink: #14181f;
      --kiosk-font: 'Sora', 'Avenir Next', 'Segoe UI', sans-serif;
      font-family: var(--kiosk-font);
      display: block;
      min-height: 100dvh;
    }

    @keyframes kiosk-fade-up {
      from {
        opacity: 0;
        transform: translateY(22px);
      }
      to {
        opacity: 1;
        transform: translateY(0);
      }
    }

    @keyframes kiosk-pulse-ring {
      0% {
        transform: scale(0.9);
        opacity: 0.65;
      }
      70% {
        transform: scale(1.28);
        opacity: 0;
      }
      100% {
        transform: scale(1.28);
        opacity: 0;
      }
    }

    @keyframes kiosk-breathe {
      0%,
      100% {
        transform: scale(1);
      }
      50% {
        transform: scale(1.035);
      }
    }

    @keyframes kiosk-shimmer {
      0% {
        background-position: 0% 50%;
      }
      100% {
        background-position: 100% 50%;
      }
    }

    @keyframes kiosk-success-pop {
      0% {
        opacity: 0;
        transform: scale(0.9) translateY(12px);
      }
      100% {
        opacity: 1;
        transform: scale(1) translateY(0);
      }
    }

    @keyframes kiosk-number-in {
      0% {
        opacity: 0;
        transform: scale(0.7);
        filter: blur(6px);
      }
      100% {
        opacity: 1;
        transform: scale(1);
        filter: blur(0);
      }
    }

    .kiosk-enter {
      animation: kiosk-fade-up 0.5s cubic-bezier(0.22, 1, 0.36, 1) both;
    }

    .kiosk-enter-delay {
      animation: kiosk-fade-up 0.55s cubic-bezier(0.22, 1, 0.36, 1) 0.1s both;
    }

    .kiosk-pulse-ring {
      animation: kiosk-pulse-ring 2.4s ease-out infinite;
    }

    .kiosk-breathe {
      animation: kiosk-breathe 3.6s ease-in-out infinite;
    }

    .kiosk-success {
      animation: kiosk-success-pop 0.45s cubic-bezier(0.22, 1, 0.36, 1) both;
    }

    .kiosk-number {
      animation: kiosk-number-in 0.55s cubic-bezier(0.22, 1, 0.36, 1) 0.12s both;
    }

    .kiosk-scan-line {
      background: linear-gradient(
        90deg,
        transparent 0%,
        rgba(255, 255, 255, 0.55) 45%,
        rgba(255, 255, 255, 0.9) 50%,
        rgba(255, 255, 255, 0.55) 55%,
        transparent 100%
      );
      background-size: 220% 100%;
      animation: kiosk-shimmer 2.8s linear infinite;
    }
  `,
})
export class Kiosk implements OnInit, AfterViewInit, OnDestroy {
  private readonly kioskService = inject(KioskService);

  @ViewChild('rfidInput') private rfidInput?: ElementRef<HTMLInputElement>;

  protected readonly step = signal<KioskStep>('idle');
  protected readonly lookingUp = signal(false);
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly scanBuffer = signal('');
  protected readonly person = signal<KioskPerson | null>(null);
  protected readonly categories = signal<TicketCategoryOption[]>([]);
  protected readonly category = signal('');
  protected readonly concern = signal('');
  protected readonly createdTicket = signal<Ticket | null>(null);
  protected readonly pendingCsm = signal<PendingCsm | null>(null);
  protected readonly csmRating = signal<CsmRating | null>(null);
  protected readonly csmComment = signal('');
  protected readonly submittingCsm = signal(false);
  protected readonly clock = signal(new Date());
  protected readonly clockLabel = computed(() =>
    this.clock().toLocaleString('en-PH', {
      timeZone: DISPLAY_TIME_ZONE,
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
      second: '2-digit',
      hour12: true,
    }),
  );

  private scannedIdentifier = '';
  private resetTimer: ReturnType<typeof setTimeout> | null = null;
  private formIdleTimer: ReturnType<typeof setTimeout> | null = null;
  private focusTimer: ReturnType<typeof setInterval> | null = null;
  private clockTimer: ReturnType<typeof setInterval> | null = null;
  private clockSyncTimer: ReturnType<typeof setInterval> | null = null;
  /** serverEpoch - clientEpoch; applied so the displayed clock follows the server. */
  private serverOffsetMs = 0;

  async ngOnInit(): Promise<void> {
    this.ensureDisplayFont();
    await this.syncServerClock();
    this.clockTimer = setInterval(() => this.tickClock(), CLOCK_TICK_MS);
    this.clockSyncTimer = setInterval(() => void this.syncServerClock(), CLOCK_SYNC_MS);
    try {
      const options = await firstValueFrom(this.kioskService.categories());
      this.categories.set(options);
      if (options.length > 0) {
        this.category.set(options[0].value);
      }
    } catch {
      this.categories.set([
        { value: 'NETWORK_INTERNET', label: 'Network / Internet' },
        { value: 'HARDWARE_EQUIPMENT', label: 'Hardware / Equipment' },
        { value: 'ACCOUNT_PASSWORD', label: 'Account & Password' },
        { value: 'SOFTWARE_SYSTEM_ACCESS', label: 'Software / System Access' },
        { value: 'EMAIL_OUTLOOK', label: 'Email / Outlook' },
        { value: 'OTHERS', label: 'Others' },
      ]);
      this.category.set('NETWORK_INTERNET');
    }
  }

  ngAfterViewInit(): void {
    this.focusScanner();
    this.focusTimer = setInterval(() => this.focusScanner(), 1500);
  }

  ngOnDestroy(): void {
    this.clearResetTimer();
    this.clearFormIdleTimer();
    if (this.focusTimer) {
      clearInterval(this.focusTimer);
    }
    if (this.clockTimer) {
      clearInterval(this.clockTimer);
    }
    if (this.clockSyncTimer) {
      clearInterval(this.clockSyncTimer);
    }
  }

  protected get requiresDetail(): boolean {
    const selected = this.categories().find((c) => c.value === this.category());
    return selected?.requiresDetail === true || this.category() === 'OTHERS';
  }

  protected personTypeLabel(type: string | null | undefined): string {
    if (type === 'EMPLOYEE') {
      return 'Employee';
    }
    if (type === 'STUDENT') {
      return 'Student';
    }
    return type || 'Guest';
  }

  protected onPageActivate(): void {
    unlockAudio();
    if (this.step() === 'idle') {
      this.focusScanner();
    } else if (this.step() === 'form' || this.step() === 'csm') {
      this.bumpFormIdleTimer();
    }
  }

  protected selectCsmRating(rating: CsmRating): void {
    this.csmRating.set(rating);
    if (rating !== 'SAD') {
      this.csmComment.set('');
    }
    this.error.set(null);
    this.bumpFormIdleTimer();
  }

  protected onCsmCommentChange(value: string): void {
    this.csmComment.set(value);
    this.bumpFormIdleTimer();
  }

  protected async submitCsm(): Promise<void> {
    const pending = this.pendingCsm();
    const rating = this.csmRating();
    const identifier = this.scannedIdentifier;
    if (!pending || !rating || !identifier || this.submittingCsm()) {
      return;
    }

    this.error.set(null);
    this.clearFormIdleTimer();
    this.submittingCsm.set(true);
    try {
      await firstValueFrom(
        this.kioskService.submitCsm(identifier, pending.ticketId, {
          rating,
          comment: rating === 'SAD' ? this.csmComment().trim() || undefined : undefined,
        }),
      );
      const next = await firstValueFrom(this.kioskService.getPendingCsm(identifier));
      if (next) {
        this.pendingCsm.set(next);
        this.csmRating.set(null);
        this.csmComment.set('');
        this.step.set('csm');
        this.bumpFormIdleTimer();
      } else {
        this.pendingCsm.set(null);
        this.csmRating.set(null);
        this.csmComment.set('');
        this.step.set('form');
        this.bumpFormIdleTimer();
      }
    } catch (err) {
      this.error.set(this.describeError(err));
      this.bumpFormIdleTimer();
    } finally {
      this.submittingCsm.set(false);
    }
  }

  protected selectCategory(value: string): void {
    this.category.set(value);
    this.error.set(null);
    this.bumpFormIdleTimer();
  }

  protected onConcernChange(value: string): void {
    this.concern.set(value);
    this.bumpFormIdleTimer();
  }

  protected async onScanSubmit(event?: Event): Promise<void> {
    event?.preventDefault();
    unlockAudio();

    if (this.lookingUp() || this.submitting() || this.step() === 'success') {
      return;
    }

    const identifier = this.scanBuffer().trim();
    this.scanBuffer.set('');
    if (!identifier) {
      this.focusScanner();
      return;
    }

    this.error.set(null);
    this.lookingUp.set(true);
    try {
      const person = await firstValueFrom(this.kioskService.lookup(identifier));
      this.scannedIdentifier = identifier;
      this.person.set(person);
      this.concern.set('');
      this.csmRating.set(null);
      this.csmComment.set('');
      const cats = this.categories();
      if (cats.length > 0) {
        this.category.set(cats[0].value);
      }
      const pending = await firstValueFrom(this.kioskService.getPendingCsm(identifier));
      if (pending) {
        this.pendingCsm.set(pending);
        this.step.set('csm');
      } else {
        this.pendingCsm.set(null);
        this.step.set('form');
      }
      this.bumpFormIdleTimer();
    } catch (err) {
      this.person.set(null);
      this.pendingCsm.set(null);
      this.step.set('idle');
      this.error.set(this.describeError(err));
    } finally {
      this.lookingUp.set(false);
      this.focusScanner();
    }
  }

  protected async submitTicket(): Promise<void> {
    const person = this.person();
    if (!person) {
      return;
    }

    this.error.set(null);
    const category = this.category();
    if (!category) {
      this.error.set('Please select a concern type.');
      return;
    }
    if (this.requiresDetail && !this.concern().trim()) {
      this.error.set('Please type your concern.');
      return;
    }
    if (!person.email) {
      this.error.set('No LPU email on file. Please ask the MISD counter for help.');
      return;
    }

    const identifier = this.scannedIdentifier || person.rfid?.trim() || person.personNo;
    this.clearFormIdleTimer();
    this.submitting.set(true);
    try {
      const ticket = await firstValueFrom(
        this.kioskService.createTicket({
          identifier,
          category,
          concern: this.requiresDetail ? this.concern().trim() : undefined,
        }),
      );
      this.createdTicket.set(ticket);
      this.step.set('success');
      playSuccessCue();
      this.scheduleReset();
    } catch (err) {
      if (err instanceof HttpErrorResponse && err.status === 409) {
        try {
          const pending = await firstValueFrom(this.kioskService.getPendingCsm(identifier));
          if (pending) {
            this.pendingCsm.set(pending);
            this.csmRating.set(null);
            this.csmComment.set('');
            this.step.set('csm');
            this.error.set(null);
            this.bumpFormIdleTimer();
            return;
          }
        } catch {
          // fall through
        }
      }
      this.error.set(this.describeError(err));
      this.bumpFormIdleTimer();
    } finally {
      this.submitting.set(false);
      this.focusScanner();
    }
  }

  protected cancelForm(): void {
    this.clearResetTimer();
    this.clearFormIdleTimer();
    this.person.set(null);
    this.pendingCsm.set(null);
    this.csmRating.set(null);
    this.csmComment.set('');
    this.concern.set('');
    this.error.set(null);
    this.step.set('idle');
    this.focusScanner();
  }

  protected doneSuccess(): void {
    this.resetToIdle();
  }

  private scheduleReset(): void {
    this.clearResetTimer();
    this.resetTimer = setTimeout(() => this.resetToIdle(), SUCCESS_RESET_MS);
  }

  private resetToIdle(): void {
    this.clearResetTimer();
    this.clearFormIdleTimer();
    this.person.set(null);
    this.pendingCsm.set(null);
    this.csmRating.set(null);
    this.csmComment.set('');
    this.createdTicket.set(null);
    this.concern.set('');
    this.error.set(null);
    this.scanBuffer.set('');
    this.scannedIdentifier = '';
    this.step.set('idle');
    this.focusScanner();
  }

  private clearResetTimer(): void {
    if (this.resetTimer) {
      clearTimeout(this.resetTimer);
      this.resetTimer = null;
    }
  }

  /** Cancel the form/CSM if left idle for 10s; resets on any interaction. */
  private bumpFormIdleTimer(): void {
    this.clearFormIdleTimer();
    const step = this.step();
    if ((step !== 'form' && step !== 'csm') || this.submitting() || this.submittingCsm()) {
      return;
    }
    this.formIdleTimer = setTimeout(() => {
      const current = this.step();
      if ((current === 'form' || current === 'csm') && !this.submitting() && !this.submittingCsm()) {
        this.cancelForm();
      }
    }, FORM_IDLE_CANCEL_MS);
  }

  private clearFormIdleTimer(): void {
    if (this.formIdleTimer) {
      clearTimeout(this.formIdleTimer);
      this.formIdleTimer = null;
    }
  }

  private async syncServerClock(): Promise<void> {
    const clientAtRequest = Date.now();
    try {
      const server = await firstValueFrom(this.kioskService.serverTime());
      const clientAtResponse = Date.now();
      const roundTripMs = Math.max(0, clientAtResponse - clientAtRequest);
      this.serverOffsetMs = server.epochMillis + Math.floor(roundTripMs / 2) - clientAtResponse;
      this.tickClock();
    } catch {
      // Keep last known offset (or local time until the first successful sync).
      this.tickClock();
    }
  }

  private tickClock(): void {
    this.clock.set(new Date(Date.now() + this.serverOffsetMs));
  }

  private focusScanner(): void {
    if (this.step() === 'form' || this.step() === 'csm') {
      return;
    }
    const active = document.activeElement;
    if (
      active instanceof HTMLSelectElement
      || active instanceof HTMLTextAreaElement
      || active instanceof HTMLButtonElement
      || (active instanceof HTMLInputElement && active.id !== 'rfidScan')
    ) {
      return;
    }
    const el = this.rfidInput?.nativeElement;
    if (el && document.activeElement !== el) {
      el.focus({ preventScroll: true });
    }
  }

  private ensureDisplayFont(): void {
    if (typeof document === 'undefined') {
      return;
    }
    if (document.getElementById('kiosk-sora-font')) {
      return;
    }
    const link = document.createElement('link');
    link.id = 'kiosk-sora-font';
    link.rel = 'stylesheet';
    link.href =
      'https://fonts.googleapis.com/css2?family=Sora:wght@400;500;600;700;800&display=swap';
    document.head.appendChild(link);
  }

  private describeError(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      const body = err.error;
      if (typeof body === 'string' && body.trim()) {
        return body;
      }
      if (body && typeof body === 'object' && 'message' in body && typeof body.message === 'string') {
        return body.message;
      }
      if (err.status === 404) {
        return 'No student or employee found for that ID. Please try again or see the counter.';
      }
      if (err.status === 0) {
        return 'Cannot reach the helpdesk server. Please try again.';
      }
      return err.message || 'Something went wrong. Please try again.';
    }
    if (err instanceof Error) {
      return err.message;
    }
    return 'Something went wrong. Please try again.';
  }
}
