import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

const TOKEN_KEY = 'fimobook.auth.session';

export interface StoredSession {
  refreshToken: string;
  token: string;
}

export async function loadToken() {
  const value = Platform.OS === 'web'
    ? globalThis.localStorage?.getItem(TOKEN_KEY) ?? null
    : await SecureStore.getItemAsync(TOKEN_KEY);
  if (!value) return null;
  try { return JSON.parse(value) as StoredSession; } catch { return null; }
}

export async function saveToken(session: StoredSession) {
  const value = JSON.stringify(session);
  if (Platform.OS === 'web') {
    globalThis.localStorage?.setItem(TOKEN_KEY, value);
    return;
  }
  await SecureStore.setItemAsync(TOKEN_KEY, value, {
    keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
  });
}

export async function removeToken() {
  if (Platform.OS === 'web') {
    globalThis.localStorage?.removeItem(TOKEN_KEY);
    return;
  }
  await SecureStore.deleteItemAsync(TOKEN_KEY);
}
