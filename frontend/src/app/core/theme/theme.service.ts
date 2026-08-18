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
    this.apply(mode);
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
}
