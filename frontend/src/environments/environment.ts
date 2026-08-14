const contextPath = '/lpu-helpdesk';

export const environment = {
  production: false,
  contextPath,
  apiBaseUrl: `${contextPath}/api`,
  // From Azure Portal → App registrations → your app → Overview.
  msal: {
    clientId: 'REPLACE_WITH_AZURE_APP_CLIENT_ID',
    // Use the LPU Laguna Microsoft 365 tenant ID (single-tenant) so only
    // organization accounts can even reach the sign-in page.
    tenantId: 'REPLACE_WITH_AZURE_TENANT_ID',
    redirectUri: typeof window !== 'undefined' ? window.location.origin : 'http://localhost:4200',
  },
  // Defense in depth: also checked client-side after sign-in, and must be
  // re-validated by the backend when it exchanges the Microsoft ID token.
  allowedEmailDomain: 'lpulaguna.edu.ph',
};
