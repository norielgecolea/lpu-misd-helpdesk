import { ApplicationConfig, provideAppInitializer, provideBrowserGlobalErrorListeners, inject } from '@angular/core';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthService } from './core/auth/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    provideAppInitializer(() => {
      const auth = inject(AuthService);
      return auth.completeMicrosoftRedirectIfNeeded().catch((err: unknown) => {
        console.error('Microsoft redirect sign-in failed', err);
      });
    }),
  ],
};
