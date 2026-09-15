export interface MicrosoftLoginRequest {
  idToken: string;
}

export interface GoogleLoginRequest {
  idToken: string;
  nonce?: string;
}

export interface GoogleLoginConfig {
  configured: boolean;
  clientId: string;
}

export interface OtpRequestRequest {
  email: string;
  'cf-turnstile-response': string;
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
  'cf-turnstile-response': string;
}

export interface LoginResponse {
  id: number;
  token: string;
  tokenType: string;
  email: string;
  name: string;
  role: string;
  expiresInMs: number;
  needsStudentInfo?: boolean;
  declaredStudentName?: string | null;
  declaredStudentNo?: string | null;
  declaredPersonType?: string | null;
  declaredLpuEmail?: string | null;
}

export type AppRole = 'USER' | 'ADMIN' | 'SUPER_ADMIN' | 'MONITORING';

export interface AuthUser {
  id: number;
  email: string;
  name: string;
  username?: string | null;
  role: AppRole;
  needsStudentInfo?: boolean;
  declaredStudentName?: string | null;
  declaredStudentNo?: string | null;
  declaredPersonType?: string | null;
  declaredLpuEmail?: string | null;
}
