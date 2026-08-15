import Constants from 'expo-constants';
import { Platform } from 'react-native';

function trimTrailingSlash(value: string) {
  return value.replace(/\/+$/, '');
}

function inferDevelopmentApiUrl() {
  const expoHost = Constants.expoConfig?.hostUri?.split(':')[0];
  if (expoHost) return `http://${expoHost}:8080`;
  if (Platform.OS === 'android') return 'http://10.0.2.2:8080';
  return 'http://localhost:8080';
}

export const apiBaseUrl = trimTrailingSlash(
  process.env.EXPO_PUBLIC_API_URL?.trim() || inferDevelopmentApiUrl(),
);

export function resolveApiResourceUrl(url: string | null | undefined) {
  if (!url) return undefined;
  return url.startsWith('/') ? `${apiBaseUrl}${url}` : url;
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

interface RequestOptions {
  body?: unknown;
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  signal?: AbortSignal;
  token?: string | null;
}

function isAbortSignal(value: RequestOptions | AbortSignal): value is AbortSignal {
  return 'aborted' in value && 'addEventListener' in value;
}

export async function requestJson<T>(path: string, options: RequestOptions | AbortSignal = {}): Promise<T> {
  const normalizedOptions: RequestOptions =
    isAbortSignal(options)
      ? { signal: options }
      : options;
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (normalizedOptions.body !== undefined) headers['Content-Type'] = 'application/json';
  if (normalizedOptions.token) headers.Authorization = `Bearer ${normalizedOptions.token}`;

  const response = await fetch(`${apiBaseUrl}${path}`, {
    body: normalizedOptions.body === undefined ? undefined : JSON.stringify(normalizedOptions.body),
    headers,
    method: normalizedOptions.method ?? 'GET',
    signal: normalizedOptions.signal,
  });

  if (!response.ok) {
    let message = `API 요청에 실패했습니다. (${response.status})`;
    try {
      const body = (await response.json()) as { detail?: string; message?: string };
      if (body.message || body.detail) message = body.message ?? body.detail ?? message;
    } catch {
      // The status code is enough when an upstream proxy returns a non-JSON body.
    }
    throw new ApiError(message, response.status);
  }

  if (response.status === 204) return undefined as T;
  const responseText = await response.text();
  if (!responseText) return undefined as T;
  return JSON.parse(responseText) as T;
}
