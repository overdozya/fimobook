import { requestJson } from '@/services/api';

import type { AuthResponse, AuthUser, Credentials } from './types';

export function login(credentials: Credentials) {
  return requestJson<AuthResponse>('/api/auth/login', {
    body: credentials,
    method: 'POST',
  });
}

export function register(credentials: Credentials) {
  return requestJson<AuthResponse>('/api/auth/register', {
    body: credentials,
    method: 'POST',
  });
}

export function getCurrentUser(token: string) {
  return requestJson<AuthUser>('/api/auth/me', { token });
}

export function refreshSession(refreshToken: string) {
  return requestJson<AuthResponse>('/api/auth/refresh', { body: { refreshToken }, method: 'POST' });
}

export function logoutSession(refreshToken: string) {
  return requestJson<void>('/api/auth/logout', { body: { refreshToken }, method: 'POST' });
}
