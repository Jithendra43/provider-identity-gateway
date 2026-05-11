'use client';

import { createContext, useCallback, useContext, useEffect, useState } from 'react';

export type Operator = {
  subject: string;
  orgId: string | null;
  nodeId: string | null;
  roles: string[] | null;
  scope: string | null;
};

type AuthContextValue = {
  operator: Operator | null;
  loading: boolean;
  refresh: () => Promise<void>;
  login: (params: LoginParams) => Promise<{ ok: boolean; error?: string }>;
  logout: () => Promise<void>;
};

export type LoginParams = {
  username: string;
  password: string;
  orgId: string;
  nodeId: string;
  roles: string;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [operator, setOperator] = useState<Operator | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      const r = await fetch('/api/admin/auth/me', { credentials: 'include' });
      if (r.ok) {
        const data = await r.json();
        setOperator({
          subject: data.subject,
          orgId: data.orgId,
          nodeId: data.nodeId,
          roles: data.roles,
          scope: data.scope,
        });
      } else {
        setOperator(null);
      }
    } catch {
      setOperator(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  // Route guard: redirect unauthenticated users to the public welcome page
  // (which renders the "Sign in securely" CTA that triggers the Cognito OIDC
  // round-trip). The legacy /admin/login/ form is still served for the
  // non-OIDC (mock) profile, but it must NEVER be the auto-redirect target
  // in production because the security filter chain treats /admin/login/ as
  // authenticated() and the bearer-token AccessDeniedHandler returns 403,
  // producing a refresh loop (welcome -> login -> 403 -> welcome -> ...).
  //
  // We use a hard navigation (window.location) because Next.js' client router
  // is unreliable when crossing pages of a static export with `trailingSlash`
  // and `basePath`. The basePath is prepended manually so the URL is always
  // /admin/welcome/ or /admin/dashboard/ regardless of how the SPA was reached.
  useEffect(() => {
    if (loading) return;
    if (typeof window === 'undefined') return;
    const here = window.location.pathname.replace(/\/+$/, '');
    // Public pages: marketing landing (/admin, /admin/welcome) and the
    // local-profile login form. Authenticated app routes live under
    // /admin/dashboard, etc. /admin/welcome MUST be public because Spring
    // Security redirects unauthenticated requests for /admin/ to it; if the
    // SPA also tries to bounce away from /admin/welcome, the two redirects
    // form an infinite reload loop.
    const isPublic =
      here === '' ||
      here === '/admin' ||
      here === '/admin/welcome' ||
      here.endsWith('/login');
    if (!operator && !isPublic) {
      window.location.replace('/admin/welcome/');
    } else if (operator && isPublic && !here.endsWith('/login')) {
      window.location.replace('/admin/dashboard/');
    }
  }, [loading, operator]);

  const login = async (params: LoginParams) => {
    const r = await fetch('/api/admin/auth/login', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(params),
    });
    if (!r.ok) {
      const body = await r.json().catch(() => ({}));
      return { ok: false, error: body.error || `HTTP ${r.status}` };
    }
    await refresh();
    // Hard-navigate to the dashboard so any deep state (sidebar, AppShell)
    // boots cleanly under its own static-exported bundle.
    if (typeof window !== 'undefined') {
      window.location.assign('/admin/dashboard/');
    }
    return { ok: true };
  };

  const logout = async () => {
    await fetch('/api/admin/auth/logout', { method: 'POST', credentials: 'include' });
    setOperator(null);
    if (typeof window !== 'undefined') {
      window.location.assign('/admin/login/');
    }
  };

  return (
    <AuthContext.Provider value={{ operator, loading, refresh, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
