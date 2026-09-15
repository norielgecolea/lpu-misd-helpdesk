import { bootstrapApplication } from '@angular/platform-browser';
import { PublicClientApplication } from '@azure/msal-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { environment } from './environments/environment';

/** Must match AuthService storage keys. */
const TOKEN_KEY = 'lpu_helpdesk_token';
const USER_KEY = 'lpu_helpdesk_user';
const MSAL_ERROR_KEY = 'lpu_helpdesk_msal_error';
const GOOGLE_ERROR_KEY = 'lpu_helpdesk_google_error';
const GOOGLE_PENDING_KEY = 'lpu_helpdesk_google_pending';
const GOOGLE_STATE_KEY = 'lpu_helpdesk_google_state';
const GOOGLE_NONCE_KEY = 'lpu_helpdesk_google_nonce';

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

function hasGoogleAuthResponse(): boolean {
  if (sessionStorage.getItem(GOOGLE_PENDING_KEY) === '1') {
    return true;
  }
  const hash = window.location.hash ?? '';
  return hash.includes('id_token=');
}

function hashParams(): URLSearchParams {
  return new URLSearchParams((window.location.hash || '').replace(/^#/, ''));
}

function apiErrorMessage(body: { message?: string; detail?: string }, fallback: string): string {
  return body.detail || body.message || fallback;
}

function persistLoginSession(data: {
  id: number;
  token: string;
  email: string;
  name: string;
  role: string;
  needsStudentInfo?: boolean;
  declaredStudentName?: string | null;
  declaredStudentNo?: string | null;
  declaredPersonType?: string | null;
  declaredLpuEmail?: string | null;
}): void {
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
      needsStudentInfo: data.needsStudentInfo ?? false,
      declaredStudentName: data.declaredStudentName ?? null,
      declaredStudentNo: data.declaredStudentNo ?? null,
      declaredPersonType: data.declaredPersonType ?? null,
      declaredLpuEmail: data.declaredLpuEmail ?? null,
    }),
  );
}

function clearAuthHash(): void {
  window.history.replaceState({}, document.title, `${window.location.pathname}${window.location.search}`);
}

function clearGoogleRedirectKeys(): void {
  sessionStorage.removeItem(GOOGLE_PENDING_KEY);
  sessionStorage.removeItem(GOOGLE_STATE_KEY);
  sessionStorage.removeItem(GOOGLE_NONCE_KEY);
}

/**
 * Finish Google OpenID redirect before Angular boots.
 * Waiting until APP_INITIALIZER/router can lose the auth hash or race the guest guard.
 */
async function completeGoogleRedirectBeforeBootstrap(): Promise<void> {
  if (typeof window === 'undefined' || !hasGoogleAuthResponse()) {
    return;
  }

  const params = hashParams();
  const expectedState = sessionStorage.getItem(GOOGLE_STATE_KEY);
  const nonce = sessionStorage.getItem(GOOGLE_NONCE_KEY);
  clearGoogleRedirectKeys();

  try {
    const oauthError = params.get('error');
    if (oauthError) {
      sessionStorage.setItem(
        GOOGLE_ERROR_KEY,
        oauthError === 'access_denied'
          ? 'Sign-in was cancelled.'
          : params.get('error_description') || 'Google sign-in was cancelled.',
      );
      clearAuthHash();
      return;
    }

    const idToken = params.get('id_token');
    const state = params.get('state');
    if (!idToken) {
      sessionStorage.setItem(GOOGLE_ERROR_KEY, 'Google sign-in did not return a token. Please try again.');
      clearAuthHash();
      return;
    }
    if (!expectedState || state !== expectedState) {
      sessionStorage.setItem(GOOGLE_ERROR_KEY, 'Google sign-in could not be verified. Please try again.');
      clearAuthHash();
      return;
    }

    const response = await fetch(`${environment.apiBaseUrl}/auth/google`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ idToken, nonce }),
    });

    if (!response.ok) {
      let message = 'Google sign-in failed on the server.';
      try {
        const body = (await response.json()) as { message?: string; detail?: string };
        message = apiErrorMessage(body, message);
      } catch {
        // keep default
      }
      sessionStorage.setItem(GOOGLE_ERROR_KEY, message);
      clearAuthHash();
      return;
    }

    const data = (await response.json()) as Parameters<typeof persistLoginSession>[0];
    sessionStorage.removeItem(GOOGLE_ERROR_KEY);
    persistLoginSession(data);
    clearAuthHash();
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Google sign-in failed.';
    sessionStorage.setItem(GOOGLE_ERROR_KEY, message);
    clearAuthHash();
  }
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
        const body = (await response.json()) as { message?: string; detail?: string };
        message = apiErrorMessage(body, message);
      } catch {
        // keep default
      }
      sessionStorage.setItem(MSAL_ERROR_KEY, message);
      return;
    }

    const data = (await response.json()) as Parameters<typeof persistLoginSession>[0];
    sessionStorage.removeItem(MSAL_ERROR_KEY);
    persistLoginSession(data);

    // Drop the auth hash so a refresh does not re-run the exchange.
    clearAuthHash();
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Microsoft sign-in failed.';
    sessionStorage.setItem(MSAL_ERROR_KEY, message);
  }
}

async function completeSsoBeforeBootstrap(): Promise<void> {
  if (typeof window === 'undefined') {
    return;
  }
  if (hasGoogleAuthResponse()) {
    await completeGoogleRedirectBeforeBootstrap();
    return;
  }
  await completeMicrosoftRedirectBeforeBootstrap();
}

completeSsoBeforeBootstrap()
  .catch((err) => console.error(err))
  .finally(() => {
    bootstrapApplication(App, appConfig).catch((err) => console.error(err));
  });
