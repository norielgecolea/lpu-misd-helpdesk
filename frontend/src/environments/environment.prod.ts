const contextPath = '/lpu-helpdesk';

function msalRedirectUri(): string {
  if (typeof window === 'undefined') {
    return '';
  }
  return `${window.location.origin}/auth-redirect.html`;
}

export const environment = {
  production: true,
  contextPath,
  apiBaseUrl: `${contextPath}/api`,
  msal: {
    clientId: '0c648cfe-768d-4abf-852d-8ffe1dc22c31',
    tenantId: '173859cd-235f-4bb4-bb4d-7faa54164776',
    redirectUri: msalRedirectUri(),
  },
  allowedEmailDomain: 'lpulaguna.edu.ph',
};
