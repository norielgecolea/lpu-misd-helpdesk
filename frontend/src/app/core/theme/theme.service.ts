import { Injectable, computed, signal } from '@angular/core';

const STORAGE_KEY = 'lpu-helpdesk-theme';

export type ThemeMode = 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly mode = signal<ThemeMode>(this.readStored());

  readonly current = this.mode.asReadonly();
  readonly isDark = computed(() => this.mode() === 'dark');

  constructor() {
    const mode = this.mode();
    this.apply(this.isForcedLightPath() ? 'light' : mode);
    // Persist light when unset so the app never falls back to OS appearance.
    if (this.readRaw() == null) {
      this.persist(mode);
    }
  }

  toggle(): void {
    this.setMode(this.mode() === 'dark' ? 'light' : 'dark');
  }

  setMode(mode: ThemeMode): void {
    this.mode.set(mode);
    this.apply(mode);
    this.persist(mode);
  }

  /** Paint a theme on the document without changing the stored preference. */
  applyAppearance(mode: ThemeMode): void {
    this.apply(mode);
  }

  /** Re-apply the stored theme preference, unless this page is forced light. */
  applyStored(): void {
    this.apply(this.isForcedLightPath() ? 'light' : this.mode());
  }

  private readStored(): ThemeMode {
    return this.readRaw() === 'dark' ? 'dark' : 'light';
  }

  private readRaw(): string | null {
    try {
      return localStorage.getItem(STORAGE_KEY);
    } catch {
      return null;
    }
  }

  private persist(mode: ThemeMode): void {
    try {
      localStorage.setItem(STORAGE_KEY, mode);
    } catch {
      // ignore quota / private mode
    }
  }

  private apply(mode: ThemeMode): void {
    if (typeof document === 'undefined') {
      return;
    }
    document.documentElement.classList.toggle('dark', mode === 'dark');
    document.documentElement.style.colorScheme = mode === 'dark' ? 'dark' : 'only light';
  }

  private isForcedLightPath(): boolean {
    if (typeof location === 'undefined') {
      return false;
    }
    const path = location.pathname.replace(/\/+$/, '') || '/';
    return path === '/admin' || path === '/admin/forgot-password' || path === '/admin/reset-password';
  }
}
