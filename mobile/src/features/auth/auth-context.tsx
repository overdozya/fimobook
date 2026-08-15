import { createContext, type PropsWithChildren, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import { getCurrentUser, login as loginRequest, logoutSession, refreshSession, register as registerRequest } from './api';
import { loadToken, removeToken, saveToken } from './token-storage';
import type { AuthUser, Credentials } from './types';

interface AuthContextValue {
  isReady: boolean;
  login(credentials: Credentials): Promise<void>;
  logout(): Promise<void>;
  register(credentials: Credentials): Promise<void>;
  token: string | null;
  user: AuthUser | null;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren) {
  const [token, setToken] = useState<string | null>(null);
  const [refreshToken, setRefreshToken] = useState<string | null>(null);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isReady, setIsReady] = useState(false);

  useEffect(() => {
    let active = true;
    loadToken()
      .then(async (storedSession) => {
        if (!storedSession) return;
        try {
          let activeSession = storedSession;
          let currentUser;
          try {
            currentUser = await getCurrentUser(activeSession.token);
          } catch {
            const refreshed = await refreshSession(activeSession.refreshToken);
            activeSession = { refreshToken: refreshed.refreshToken, token: refreshed.token };
            await saveToken(activeSession);
            currentUser = refreshed;
          }
          if (!active) return;
          setToken(activeSession.token);
          setRefreshToken(activeSession.refreshToken);
          setUser(currentUser);
        } catch {
          await removeToken();
        }
      })
      .finally(() => {
        if (active) setIsReady(true);
      });
    return () => {
      active = false;
    };
  }, []);

  const acceptSession = useCallback(async (response: Awaited<ReturnType<typeof loginRequest>>) => {
    await saveToken({ refreshToken: response.refreshToken, token: response.token });
    setToken(response.token);
    setRefreshToken(response.refreshToken);
    setUser({ userId: response.userId, email: response.email, displayName: response.displayName });
  }, []);

  const login = useCallback(async (credentials: Credentials) => {
    await acceptSession(await loginRequest(credentials));
  }, [acceptSession]);

  const register = useCallback(async (credentials: Credentials) => {
    await acceptSession(await registerRequest(credentials));
  }, [acceptSession]);

  const logout = useCallback(async () => {
    if (refreshToken) await logoutSession(refreshToken).catch(() => undefined);
    await removeToken();
    setToken(null);
    setRefreshToken(null);
    setUser(null);
  }, [refreshToken]);

  const value = useMemo(
    () => ({ isReady, login, logout, register, token, user }),
    [isReady, login, logout, register, token, user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
