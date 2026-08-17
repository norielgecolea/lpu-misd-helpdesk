const contextPath = '/lpu-helpdesk';

export const environment = {
  production: false,
  contextPath,
  apiBaseUrl: `${contextPath}/api`,
  // From Azure Portal → App registrations → your app → Overview.
  // Must match APP_MSAL_* in repo .env (SPA reads these at build time, not from .env).
  msal: {
    clientId: '0c648cfe-768d-4abf-852d-8ffe1dc22c31',
    // Use the LPU Laguna Microsoft 365 tenant ID (single-tenant) so only
    // organization accounts can even reach the sign-in page.
    tenantId: '173859cd-235f-4bb4-bb4d-7faa54164776',
    redirectUri: typeof window !== 'undefined' ? window.location.origin : 'http://localhost:4200',
  },
  // Defense in depth: also checked client-side after sign-in, and must be
  // re-validated by the backend when it exchanges the Microsoft ID token.
  allowedEmailDomain: 'lpulaguna.edu.ph',
};
