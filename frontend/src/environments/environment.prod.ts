const contextPath = '/lpu-helpdesk';

export const environment = {
  production: true,
  contextPath,
  apiBaseUrl: `${contextPath}/api`,
  msal: {
    clientId: 'REPLACE_WITH_AZURE_APP_CLIENT_ID',
    tenantId: 'REPLACE_WITH_AZURE_TENANT_ID',
    redirectUri: typeof window !== 'undefined' ? window.location.origin : '',
  },
  allowedEmailDomain: 'lpulaguna.edu.ph',
};
