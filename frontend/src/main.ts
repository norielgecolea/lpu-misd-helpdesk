import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

/**
 * MSAL loginPopup redirects the popup back to this origin with #code=... / error
 * in the URL. Do not boot Angular in that case — the router would clear the hash
 * before the opener's MSAL instance can read it and finish loginPopup().
 *
 * Do not require window.opener: after the Microsoft hop, browsers often null it
 * (COOP / privacy), even though this window is still the MSAL popup.
 */
function isMsalAuthCallback(): boolean {
  if (typeof window === 'undefined') {
    return false;
  }
  const hash = window.location.hash ?? '';
  const search = window.location.search ?? '';
  return (
    /(?:^|[&#])code=/.test(hash) ||
    /(?:^|[&#])error=/.test(hash) ||
    /(?:^|[&#])client_info=/.test(hash) ||
    /(?:^|[?&])code=/.test(search) ||
    /(?:^|[?&])error=/.test(search)
  );
}

if (isMsalAuthCallback()) {
  document.body.innerHTML =
    '<p style="font-family:system-ui,sans-serif;display:grid;place-items:center;height:100vh;margin:0;color:#52525b">Signing you in…</p>';
} else {
  bootstrapApplication(App, appConfig).catch((err) => console.error(err));
}
