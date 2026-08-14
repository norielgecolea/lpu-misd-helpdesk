export interface MicrosoftLoginRequest {
  idToken: string;
}

export interface OtpRequestRequest {
  email: string;
}

export interface OtpRequestResponse {
  /** How long the code stays valid, so the UI can show a countdown. */
  expiresInMs: number;
}

export interface OtpVerifyRequest {
  email: string;
  code: string;
}

export interface AdminLoginRequest {
  login: string;
  password: string;
  rememberMe?: boolean;
}

export interface LoginResponse {
  id: number;
  token: string;
  tokenType: string;
  email: string;
  name: string;
  role: string;
  expiresInMs: number;
}

export type AppRole = 'USER' | 'ADMIN' | 'SUPER_ADMIN' | 'MONITORING';

export interface AuthUser {
  id: number;
  email: string;
  name: string;
  role: AppRole;
}
