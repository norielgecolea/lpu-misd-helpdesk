import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnDestroy,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { environment } from '../../../environments/environment';
import { ThemeService } from '../../core/theme/theme.service';

interface TurnstileApi {
  render(container: HTMLElement | string, options: Record<string, unknown>): string;
  reset(widgetId: string): void;
  remove(widgetId: string): void;
  getResponse(widgetId: string): string | undefined;
  ready(callback: () => void): void;
}

declare global {
  interface Window {
    turnstile?: TurnstileApi;
  }
}

@Component({
  selector: 'app-turnstile-widget',
  template: `<div #host class="flex min-h-[65px] w-full justify-center overflow-hidden"></div>`,
})
export class TurnstileWidget implements AfterViewInit, OnDestroy {
  @Input({ required: true }) action = '';
  /** When set, ignores the app theme (e.g. always-light login pages). */
  @Input() colorTheme?: 'light' | 'dark';
  @ViewChild('host', { static: true }) private readonly host?: ElementRef<HTMLElement>;

  readonly solved = signal(false);

  private readonly theme = inject(ThemeService);
  private widgetId: string | null = null;
  private token = '';
  private destroyed = false;

  currentToken(): string {
    if (this.widgetId && window.turnstile) {
      return window.turnstile.getResponse(this.widgetId) ?? this.token;
    }
    return this.token;
  }

  reset(): void {
    this.token = '';
    this.solved.set(false);
    if (this.widgetId && window.turnstile) {
      window.turnstile.reset(this.widgetId);
    }
  }

  ngAfterViewInit(): void {
    this.whenReady(() => this.render());
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    if (this.widgetId && window.turnstile) {
      window.turnstile.remove(this.widgetId);
    }
    this.widgetId = null;
  }

  private render(): void {
    if (this.destroyed || !this.host || this.widgetId || !window.turnstile) {
      return;
    }
    this.widgetId = window.turnstile.render(this.host.nativeElement, {
      sitekey: environment.turnstile.siteKey,
      action: this.action,
      theme: this.colorTheme ?? (this.theme.isDark() ? 'dark' : 'light'),
      size: 'flexible',
      callback: (value: string) => {
        this.token = value ?? '';
        this.solved.set(this.token.length > 0);
      },
      'error-callback': () => {
        this.token = '';
        this.solved.set(false);
      },
      'expired-callback': () => {
        this.token = '';
        this.solved.set(false);
      },
    });
  }

  private whenReady(callback: () => void): void {
    if (window.turnstile?.ready) {
      window.turnstile.ready(callback);
      return;
    }
    const started = Date.now();
    const timer = setInterval(() => {
      if (this.destroyed) {
        clearInterval(timer);
        return;
      }
      if (window.turnstile?.ready) {
        clearInterval(timer);
        window.turnstile.ready(callback);
      } else if (Date.now() - started > 15_000) {
        clearInterval(timer);
      }
    }, 50);
  }
}
