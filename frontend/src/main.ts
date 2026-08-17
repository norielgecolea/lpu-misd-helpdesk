import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

/**
 * MSAL loginPopup redirects the popup back to this origin with #code=... in the hash.
 * If Angular boots and the router navigates to /login, that hash is lost and the
 * opener never finishes sign-in. When we are the popup callback, stay on a blank
 * page so MSAL in the opener can read the URL and close the window.
 */
function isMsalPopupCallback(): boolean {
  if (typeof window === 'undefined') {
    return false;
  }
  if (!window.opener) {
    return false;
  }
  const hash = window.location.hash ?? '';
  const search = window.location.search ?? '';
  return (
    hash.includes('code=') ||
    hash.includes('error=') ||
    hash.includes('client_info=') ||
    search.includes('code=') ||
    search.includes('error=')
  );
}

if (isMsalPopupCallback()) {
  document.body.innerHTML =
    '<p style="font-family:system-ui,sans-serif;display:grid;place-items:center;height:100vh;margin:0;color:#52525b">Signing you in…</p>';
} else {
  bootstrapApplication(App, appConfig).catch((err) => console.error(err));
}
