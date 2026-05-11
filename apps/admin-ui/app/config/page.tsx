'use client';
import { AppShell } from '@/components/AppShell';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { Card, PageHeader, Badge, Button, Input, Textarea, Select } from '@/components/ui';

type Tab = 'general' | 'partners' | 'loggers';

const SERVICES = ['ingress', 'policy', 'routing', 'directory'] as const;
type Service = (typeof SERVICES)[number];

export default function ConfigPage() {
  const [tab, setTab] = useState<Tab>('general');

  return (
    <AppShell>
      <PageHeader
        title="Configuration"
        description="Service settings, partner onboarding, and runtime knobs."
      />

      <div className="mb-4 flex gap-2 border-b border-border">
        <TabButton active={tab === 'general'} onClick={() => setTab('general')}>General</TabButton>
        <TabButton active={tab === 'partners'} onClick={() => setTab('partners')}>Partners</TabButton>
        <TabButton active={tab === 'loggers'} onClick={() => setTab('loggers')}>Loggers</TabButton>
      </div>

      {tab === 'general' && <GeneralTab />}
      {tab === 'partners' && <PartnersTab />}
      {tab === 'loggers' && <LoggersTab />}
    </AppShell>
  );
}

function TabButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium transition-colors ${
        active ? 'border-primary text-primary' : 'border-transparent text-muted hover:text-fg'
      }`}
    >
      {children}
    </button>
  );
}

// ───────────────────────── General ─────────────────────────

function GeneralTab() {
  const [info, setInfo] = useState<Record<string, any>>({});
  const [health, setHealth] = useState<Record<string, any>>({});

  useEffect(() => {
    (async () => {
      for (const svc of SERVICES) {
        try {
          const i = await api.info(svc);
          setInfo((p) => ({ ...p, [svc]: i }));
        } catch { /* ignore */ }
        try {
          const h = await api.health(svc);
          setHealth((p) => ({ ...p, [svc]: h }));
        } catch { /* ignore */ }
      }
    })();
  }, []);

  return (
    <div className="space-y-4">
      <Card>
        <h3 className="mb-3 text-sm font-semibold text-slate-700">Gateway services</h3>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="border-b border-border bg-slate-50 text-xs uppercase text-muted">
              <tr>
                <th className="px-3 py-2 text-left">Service</th>
                <th className="px-3 py-2 text-left">Status</th>
                <th className="px-3 py-2 text-left">Version</th>
                <th className="px-3 py-2 text-left">Build time</th>
              </tr>
            </thead>
            <tbody>
              {SERVICES.map((svc) => {
                const i = info[svc];
                const h = health[svc];
                const status = h?.status || 'UNKNOWN';
                return (
                  <tr key={svc} className="border-b border-border/40">
                    <td className="px-3 py-2 font-medium capitalize">{svc}</td>
                    <td className="px-3 py-2">
                      <Badge tone={status === 'UP' ? 'green' : status === 'UNKNOWN' ? 'gray' : 'red'}>{status}</Badge>
                    </td>
                    <td className="px-3 py-2 font-mono text-xs">
                      {i?.build?.version || i?.git?.build?.version || '—'}
                    </td>
                    <td className="px-3 py-2 font-mono text-xs text-muted">
                      {i?.build?.time || i?.git?.commit?.time || '—'}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Card>

      <Card>
        <h3 className="mb-3 text-sm font-semibold text-slate-700">Edge endpoints</h3>
        <dl className="grid grid-cols-1 gap-y-2 text-sm sm:grid-cols-[200px_1fr]">
          <dt className="text-muted">Admin UI</dt>
          <dd className="font-mono text-xs">{typeof window !== 'undefined' ? window.location.origin + '/admin/' : '/admin/'}</dd>
          <dt className="text-muted">Public mTLS gateway</dt>
          <dd className="font-mono text-xs">https://&lt;host&gt;:8443/api/v1/tefca/*</dd>
          <dt className="text-muted">Admin gateway (cookie auth)</dt>
          <dd className="font-mono text-xs">https://&lt;host&gt;:8444/api/admin/*</dd>
          <dt className="text-muted">Internal proxy prefix</dt>
          <dd className="font-mono text-xs">/api/admin/proxy/&#123;ingress|policy|routing|directory&#125;/**</dd>
        </dl>
        <p className="mt-3 text-xs text-muted">
          Public partner traffic terminates at the nginx mTLS edge on port 8443. The admin console
          uses a separate listener on 8444 with operator session cookies. Both forward to the
          ingress-auth service which proxies to downstream services on the internal network.
        </p>
      </Card>
    </div>
  );
}

// ───────────────────────── Partners ─────────────────────────

type Partner = { partnerId: string; orgId: string; name: string; status: string; environment: string };

function PartnersTab() {
  const [partners, setPartners] = useState<Partner[]>([]);
  const [loading, setLoading] = useState(false);
  const [showOnboard, setShowOnboard] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = async () => {
    setLoading(true);
    try {
      const list = await api.partners();
      setPartners(list);
      setError(null);
    } catch (e: any) {
      setError(String(e?.body?.message || e?.body?.error || e?.status || 'failed'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { refresh(); }, []);

  const onSuspend = async (p: Partner) => {
    const reason = window.prompt(`Offboard partner "${p.name}"?\nEnter reason (optional):`, '');
    if (reason === null) return;
    try {
      await api.suspendPartner(p.partnerId, reason || 'Offboarded by operator');
      await refresh();
    } catch (e: any) {
      alert('Failed: ' + (e?.body?.message || e?.status));
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-slate-700">
          Onboarded partners {partners.length > 0 && <span className="ml-2 text-xs font-normal text-muted">({partners.length})</span>}
        </h3>
        <Button onClick={() => setShowOnboard((v) => !v)}>
          {showOnboard ? 'Cancel' : '+ Onboard partner'}
        </Button>
      </div>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>
      )}

      {showOnboard && (
        <OnboardForm
          onDone={() => {
            setShowOnboard(false);
            refresh();
          }}
        />
      )}

      <Card className="overflow-x-auto p-0">
        <table className="w-full text-sm">
          <thead className="border-b border-border bg-slate-50 text-xs uppercase text-muted">
            <tr>
              <th className="px-3 py-2 text-left">Partner ID</th>
              <th className="px-3 py-2 text-left">Org ID</th>
              <th className="px-3 py-2 text-left">Name</th>
              <th className="px-3 py-2 text-left">Environment</th>
              <th className="px-3 py-2 text-left">Status</th>
              <th className="px-3 py-2 text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {partners.map((p) => (
              <tr key={p.partnerId} className="border-b border-border/40">
                <td className="px-3 py-2 font-mono text-xs">{p.partnerId}</td>
                <td className="px-3 py-2 font-mono text-xs">{p.orgId}</td>
                <td className="px-3 py-2">{p.name}</td>
                <td className="px-3 py-2">
                  <Badge tone={p.environment === 'PRODUCTION' ? 'blue' : 'gray'}>{p.environment}</Badge>
                </td>
                <td className="px-3 py-2">
                  <Badge tone={p.status === 'ACTIVE' ? 'green' : p.status === 'SUSPENDED' ? 'amber' : 'gray'}>
                    {p.status}
                  </Badge>
                </td>
                <td className="px-3 py-2 text-right">
                  {p.status === 'ACTIVE' && (
                    <button
                      className="text-xs text-red-600 hover:underline"
                      onClick={() => onSuspend(p)}
                    >
                      Offboard
                    </button>
                  )}
                </td>
              </tr>
            ))}
            {!loading && partners.length === 0 && (
              <tr><td colSpan={6} className="px-3 py-6 text-center text-muted">No partners onboarded yet.</td></tr>
            )}
            {loading && (
              <tr><td colSpan={6} className="px-3 py-6 text-center text-muted">Loading…</td></tr>
            )}
          </tbody>
        </table>
      </Card>
    </div>
  );
}

function OnboardForm({ onDone }: { onDone: () => void }) {
  const [orgId, setOrgId] = useState('');
  const [name, setName] = useState('');
  const [environment, setEnvironment] = useState('TEST');
  const [contactEmail, setContactEmail] = useState('');
  const [certificatePem, setCertificatePem] = useState('');
  const [requestsPerMinute, setRpm] = useState('60');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await api.onboardPartner({
        orgId: orgId.trim(),
        name: name.trim(),
        environment,
        contactEmail: contactEmail.trim(),
        certificatePem: certificatePem.trim(),
        requestsPerMinute: Number(requestsPerMinute) || undefined,
      });
      onDone();
    } catch (e: any) {
      setError(String(e?.body?.message || e?.body?.error || e?.status || 'failed'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Card>
      <h4 className="mb-3 text-sm font-semibold text-slate-700">Onboard a new partner</h4>
      <form onSubmit={submit} className="space-y-3">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Field label="Organization ID" required>
            <Input value={orgId} onChange={(e) => setOrgId(e.target.value)} placeholder="urn:oid:2.16.840.1.…" required />
          </Field>
          <Field label="Display name" required>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Acme Health Network" required />
          </Field>
          <Field label="Environment" required>
            <Select value={environment} onChange={(e) => setEnvironment(e.target.value)}>
              <option value="TEST">TEST</option>
              <option value="STAGING">STAGING</option>
              <option value="PRODUCTION">PRODUCTION</option>
            </Select>
          </Field>
          <Field label="Contact email" required>
            <Input type="email" value={contactEmail} onChange={(e) => setContactEmail(e.target.value)} placeholder="ops@acme.org" required />
          </Field>
          <Field label="Rate limit (req/min)">
            <Input type="number" min={1} value={requestsPerMinute} onChange={(e) => setRpm(e.target.value)} />
          </Field>
        </div>
        <Field label="Partner X.509 certificate (PEM)" required hint="Paste the public certificate the partner will present at the mTLS edge.">
          <Textarea
            value={certificatePem}
            onChange={(e) => setCertificatePem(e.target.value)}
            placeholder="-----BEGIN CERTIFICATE-----&#10;…&#10;-----END CERTIFICATE-----"
            rows={8}
            className="font-mono text-xs"
            required
          />
        </Field>
        {error && (
          <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>
        )}
        <div className="flex gap-2">
          <Button type="submit" disabled={submitting}>{submitting ? 'Onboarding…' : 'Onboard partner'}</Button>
        </div>
      </form>
    </Card>
  );
}

function Field({ label, hint, required, children }: { label: string; hint?: string; required?: boolean; children: React.ReactNode }) {
  return (
    <label className="block">
      <div className="mb-1 text-xs font-medium text-slate-700">
        {label} {required && <span className="text-red-600">*</span>}
      </div>
      {children}
      {hint && <div className="mt-1 text-xs text-muted">{hint}</div>}
    </label>
  );
}

// ───────────────────────── Loggers ─────────────────────────

const COMMON_LOGGERS = ['ROOT', 'chit.tefca'];
const LEVELS = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'OFF'];

function LoggersTab() {
  const [service, setService] = useState<Service>('ingress');
  const [levels, setLevels] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState<string | null>(null);

  const load = async () => {
    try {
      const data = await api.loggers(service);
      const m: Record<string, string> = {};
      for (const name of COMMON_LOGGERS) {
        const l = data?.loggers?.[name];
        if (l) m[name] = l.effectiveLevel || l.configuredLevel || 'INFO';
      }
      setLevels(m);
    } catch { /* ignore */ }
  };

  useEffect(() => { load(); }, [service]);

  const change = async (name: string, level: string) => {
    setBusy(name);
    try {
      await api.setLoggerLevel(service, name, level);
      setLevels((p) => ({ ...p, [name]: level }));
    } catch (e: any) {
      alert('Failed: ' + (e?.body?.message || e?.status));
    } finally {
      setBusy(null);
    }
  };

  return (
    <Card>
      <div className="mb-4 flex items-center gap-3">
        <span className="text-sm font-medium text-slate-700">Service:</span>
        <Select value={service} onChange={(e) => setService(e.target.value as Service)} className="w-48">
          {SERVICES.map((s) => <option key={s} value={s}>{s}</option>)}
        </Select>
      </div>

      <table className="w-full text-sm">
        <thead className="border-b border-border bg-slate-50 text-xs uppercase text-muted">
          <tr>
            <th className="px-3 py-2 text-left">Logger</th>
            <th className="px-3 py-2 text-left">Effective level</th>
          </tr>
        </thead>
        <tbody>
          {COMMON_LOGGERS.map((name) => (
            <tr key={name} className="border-b border-border/40">
              <td className="px-3 py-2 font-mono text-xs">{name}</td>
              <td className="px-3 py-2">
                <Select
                  value={levels[name] || 'INFO'}
                  onChange={(e) => change(name, e.target.value)}
                  disabled={busy === name}
                  className="w-32"
                >
                  {LEVELS.map((l) => <option key={l} value={l}>{l}</option>)}
                </Select>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="mt-3 text-xs text-muted">
        Runtime log level changes apply only until the service restarts. For permanent changes,
        update the service's <code className="font-mono text-xs">application.yml</code>.
      </p>
    </Card>
  );
}
