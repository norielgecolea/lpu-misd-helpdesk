import { bootstrapApplication } from '@angular/platform-browser';
import { PublicClientApplication } from '@azure/msal-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { environment } from './environments/environment';

/** Must match AuthService storage keys. */
const TOKEN_KEY = 'lpu_helpdesk_token';
const USER_KEY = 'lpu_helpdesk_user';
const MSAL_ERROR_KEY = 'lpu_helpdesk_msal_error';

function hasMicrosoftAuthResponse(): boolean {
  const hash = window.location.hash ?? '';
  const search = window.location.search ?? '';
  const combined = `${search}${hash}`;
  return (
    /(?:^|[?#&])code=/.test(combined) ||
    /(?:^|[?#&])error=/.test(combined) ||
    hash.includes('client_info=')
  );
}

/**
 * Finish Microsoft loginRedirect before Angular boots.
 * Waiting until APP_INITIALIZER/router can lose the auth hash or race the guest guard.
 */
async function completeMicrosoftRedirectBeforeBootstrap(): Promise<void> {
  if (typeof window === 'undefined' || !hasMicrosoftAuthResponse()) {
    return;
  }

  try {
    const msal = new PublicClientApplication({
      auth: {
        clientId: environment.msal.clientId,
        authority: `https://login.microsoftonline.com/${environment.msal.tenantId}`,
        redirectUri: environment.msal.redirectUri,
      },
      cache: {
        cacheLocation: 'sessionStorage',
      },
    });
    await msal.initialize();
    const result = await msal.handleRedirectPromise();
    if (!result?.idToken) {
      const errDesc = new URLSearchParams(
        (window.location.hash || window.location.search).replace(/^[#?]/, ''),
      ).get('error_description');
      sessionStorage.setItem(
        MSAL_ERROR_KEY,
        errDesc || 'Microsoft sign-in did not return a token. Please try again.',
      );
      return;
    }

    const response = await fetch(`${environment.apiBaseUrl}/auth/microsoft`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ idToken: result.idToken }),
    });

    if (!response.ok) {
      let message = 'Microsoft sign-in failed on the server.';
      try {
        const body = (await response.json()) as { message?: string };
        if (body.message) {
          message = body.message;
        }
      } catch {
        // keep default
      }
      sessionStorage.setItem(MSAL_ERROR_KEY, message);
      return;
    }

    const data = (await response.json()) as {
      id: number;
      token: string;
      email: string;
      name: string;
      role: string;
    };

    sessionStorage.removeItem(MSAL_ERROR_KEY);
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    sessionStorage.setItem(TOKEN_KEY, data.token);
    sessionStorage.setItem(
      USER_KEY,
      JSON.stringify({
        id: data.id,
        email: data.email,
        name: data.name,
        role: data.role,
      }),
    );

    // Drop the auth hash so a refresh does not re-run the exchange.
    window.history.replaceState({}, document.title, `${window.location.pathname}${window.location.search}`);
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Microsoft sign-in failed.';
    sessionStorage.setItem(MSAL_ERROR_KEY, message);
  }
}

completeMicrosoftRedirectBeforeBootstrap()
  .catch((err) => console.error(err))
  .finally(() => {
    bootstrapApplication(App, appConfig).catch((err) => console.error(err));
  });
