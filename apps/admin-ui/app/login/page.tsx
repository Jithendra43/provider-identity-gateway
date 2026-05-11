'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/lib/auth';
import { Button, Input, Select, Spinner } from '@/components/ui';


type Account = {
  username: string;
  orgId: string | null;
  nodeId: string | null;
  roles: string | null;
  displayName: string | null;
};

export default function LoginPage() {
  const { login } = useAuth();
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [username, setUsername] = useState('admin@local');
  const [password, setPassword] = useState('tefca-admin');
  const [orgId, setOrgId] = useState('ORG-QHIN-001');
  const [nodeId, setNodeId] = useState('NODE-CW-001');
  const [roles, setRoles] = useState('QHIN_ADMIN');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    fetch('/api/admin/auth/accounts', { credentials: 'include' })
      .then((r) => (r.ok ? r.json() : []))
      .then((data: Account[]) => setAccounts(Array.isArray(data) ? data : []))
      .catch(() => setAccounts([]));
  }, []);

  const pickAccount = (acc: Account) => {
    setUsername(acc.username);
    if (acc.orgId) setOrgId(acc.orgId);
    if (acc.nodeId) setNodeId(acc.nodeId);
    if (acc.roles) setRoles(acc.roles.split(',')[0].trim());
    // Convenience: pre-fill the matching dev password.
    const pw = DEV_PASSWORDS[acc.username];
    if (pw) setPassword(pw);
    setError(null);
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    const res = await login({ username, password, orgId, nodeId, roles });
    setBusy(false);
    if (!res.ok) setError(res.error === 'invalid_credentials' ? 'Invalid username or password.' : res.error || 'Login failed');
  };

  return (
    <div className="flex min-h-screen flex-col bg-gradient-to-br from-sky-50 via-white to-sky-100">
      <header className="sticky top-0 z-30 border-b border-slate-200/70 bg-white/85 backdrop-blur-md">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
          <Link href="/" className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-white p-1 ring-1 ring-slate-200 shadow-sm">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src="/admin/chit-logo.png" alt="C-HIT" className="h-full w-full object-contain" />
            </div>
            <div className="leading-tight">
              <div className="text-sm font-semibold tracking-tight text-slate-900">C-HIT Provider</div>
              <div className="text-sm font-semibold tracking-tight text-slate-900">Identity Gateway</div>
            </div>
          </Link>
          <Link
            href="/"
            className="inline-flex items-center gap-1.5 rounded-md px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 hover:text-slate-900"
          >
            Back to home
          </Link>
        </div>
      </header>

      <div className="relative flex flex-1 items-center justify-center overflow-hidden px-4 py-10">
        <div className="pointer-events-none absolute -left-24 -top-24 h-96 w-96 rounded-full bg-sky-300/30 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-32 -right-24 h-[28rem] w-[28rem] rounded-full bg-blue-400/20 blur-3xl" />

        <div className="relative w-full max-w-md animate-fade-in">
          <div className="rounded-2xl border border-slate-200 bg-white p-8 shadow-xl">
            <h1 className="text-xl font-bold text-slate-900">Sign in</h1>
            <p className="mt-1 text-sm text-slate-600">Operator credentials for the gateway control plane.</p>

          <form className="mt-6 space-y-4" onSubmit={submit}>
            <Field label="Operator">
              <Input value={username} onChange={(e) => setUsername(e.target.value)} required autoFocus autoComplete="username" />
            </Field>
            <Field label="Password">
              <Input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                autoComplete="current-password"
                placeholder="••••••••"
              />
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Org ID"><Input value={orgId} onChange={(e) => setOrgId(e.target.value)} /></Field>
              <Field label="Node ID"><Input value={nodeId} onChange={(e) => setNodeId(e.target.value)} /></Field>
            </div>
            <Field label="Role">
              <Select value={roles} onChange={(e) => setRoles(e.target.value)}>
                <option value="QHIN_ADMIN">QHIN_ADMIN — full control</option>
                <option value="QHIN_OPERATOR">QHIN_OPERATOR — read + write</option>
                <option value="QHIN_AUDITOR">QHIN_AUDITOR — read only</option>
              </Select>
            </Field>

            {error && <div className="rounded-lg border border-red-200 bg-dangerLight px-3 py-2 text-xs text-danger">{error}</div>}

            <Button type="submit" size="lg" className="w-full" disabled={busy}>
              {busy ? (<><Spinner size="sm" className="border-white border-t-transparent" /> Signing in…</>) : 'Sign in'}
            </Button>

            {accounts.length > 0 && (
              <div className="rounded-lg border border-blue-200 bg-blue-50 px-3 py-3 text-[11px] leading-relaxed text-accentDark">
                <div className="mb-2 font-semibold uppercase tracking-wider text-[10px] text-accent">Dev quick-pick</div>
                <div className="flex flex-wrap gap-1.5">
                  {accounts.map((a) => (
                    <button
                      type="button"
                      key={a.username}
                      onClick={() => pickAccount(a)}
                      className="rounded-md border border-blue-300 bg-white px-2 py-1 text-[11px] font-medium text-accentDark transition hover:bg-accentLight hover:shadow-soft"
                    >
                      {a.username}
                      {a.roles && <span className="ml-1 text-[10px] text-muted">({a.roles})</span>}
                    </button>
                  ))}
                </div>
                <div className="mt-2 text-[10px] text-muted">
                  Default password: <code className="rounded bg-white px-1 font-mono text-accentDark">tefca-admin</code> / <code className="rounded bg-white px-1 font-mono text-accentDark">tefca-operator</code> / <code className="rounded bg-white px-1 font-mono text-accentDark">tefca-auditor</code>
                </div>
              </div>
            )}
          </form>
        </div>

          <p className="mt-6 text-center text-xs text-slate-500">© C-HIT Provider Identity Gateway · Operational console</p>
        </div>
      </div>
    </div>
  );
}

const DEV_PASSWORDS: Record<string, string> = {
  'admin@local': 'tefca-admin',
  'operator@local': 'tefca-operator',
  'auditor@local': 'tefca-auditor',
};

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="mb-1 block text-[11px] font-medium uppercase tracking-wider text-muted">{label}</label>
      {children}
    </div>
  );
}
