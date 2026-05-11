'use client';
import { AppShell } from '@/components/AppShell';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { Card, PageHeader, StatTile, Badge } from '@/components/ui';
import { Sparkline, HBarChart, Donut } from '@/components/charts';

type Health = { status: string; components?: Record<string, any> };
type ObsRow = Awaited<ReturnType<typeof api.observability>>[number];

export default function DashboardPage() {
  const [health, setHealth] = useState<Record<string, Health | { error: string }>>({});
  const [policyCount, setPolicyCount] = useState<number | null>(null);
  const [auditCount, setAuditCount] = useState<number | null>(null);
  const [recentDecisions, setRecentDecisions] = useState<any[]>([]);
  const [obs, setObs] = useState<ObsRow[]>([]);

  useEffect(() => {
    let cancelled = false;
    const tickHealth = async () => {
      for (const svc of ['ingress', 'policy', 'routing', 'directory'] as const) {
        try {
          const h = await api.health(svc);
          if (!cancelled) setHealth((p) => ({ ...p, [svc]: h }));
        } catch (e: any) {
          if (!cancelled) {
            setHealth((p) => ({
              ...p,
              [svc]: { error: String(e?.body?.error || e?.status || 'down') },
            }));
          }
        }
      }
    };
    const tickPolicy = async () => {
      try {
        const p = await api.policyRules();
        if (!cancelled) setPolicyCount(p.length);
      } catch {}
      try {
        const a = await api.policyAuditEntries({ size: 10, page: 0 });
        if (!cancelled) {
          setAuditCount(a.totalElements);
          setRecentDecisions(a.content);
        }
      } catch {}
    };
    const tickObs = async () => {
      try {
        const o = await api.observability();
        if (!cancelled) setObs(o);
      } catch {}
    };
    tickHealth();
    tickPolicy();
    tickObs();
    const i1 = setInterval(tickObs, 15000);
    const i2 = setInterval(tickHealth, 30000);
    const i3 = setInterval(tickPolicy, 30000);
    return () => {
      cancelled = true;
      clearInterval(i1);
      clearInterval(i2);
      clearInterval(i3);
    };
  }, []);

  const upCount = Object.values(health).filter((h: any) => h?.status === 'UP').length;

  const totalRps = obs.reduce((acc, s) => acc + s.rps, 0);
  const avgLatency = obs.length ? obs.reduce((acc, s) => acc + s.avgLatencyMs, 0) / obs.length : 0;
  const errorRateAvg = obs.length
    ? obs.reduce((acc, s) => acc + s.errorRate, 0) / obs.length
    : 0;

  const ingressSeries = obs.find((s) => s.service === 'ingress')?.series ?? [];
  const rpsLine = ingressSeries.map((p) => p.rps);
  const latencyLine = ingressSeries.map((p) => p.avgLatencyMs);

  const denyCount = recentDecisions.filter((d) => d.decision === 'DENY').length;
  const permitCount = recentDecisions.filter((d) => d.decision === 'PERMIT').length;

  return (
    <AppShell>
      <PageHeader
        title="Dashboard"
        description="At-a-glance gateway health, throughput, and recent activity. Live samples every 15 seconds."
      />

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatTile label="Services Up" value={`${upCount} / 4`} tone={upCount === 4 ? 'green' : 'amber'} />
        <StatTile label="Throughput" value={`${totalRps.toFixed(1)} req/s`} hint="Across all services" tone="blue" />
        <StatTile label="Avg Latency" value={`${avgLatency.toFixed(0)} ms`} hint="Mean across services" tone={avgLatency > 500 ? 'amber' : 'blue'} />
        <StatTile label="Error Rate" value={`${(errorRateAvg * 100).toFixed(2)}%`} hint="HTTP 4xx + 5xx" tone={errorRateAvg > 0.05 ? 'red' : errorRateAvg > 0.01 ? 'amber' : 'green'} />
      </div>

      <div className="mt-6 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Card>
          <div className="mb-2 flex items-baseline justify-between">
            <h3 className="text-sm font-semibold text-slate-700">Ingress requests / second</h3>
            <span className="text-xs text-muted">last 30 min</span>
          </div>
          <Sparkline values={rpsLine} height={120} color="#0284c7" fill="rgba(2,132,199,0.10)" />
        </Card>
        <Card>
          <div className="mb-2 flex items-baseline justify-between">
            <h3 className="text-sm font-semibold text-slate-700">Ingress avg latency (ms)</h3>
            <span className="text-xs text-muted">last 30 min</span>
          </div>
          <Sparkline values={latencyLine} height={120} color="#7c3aed" fill="rgba(124,58,237,0.10)" />
        </Card>
      </div>

      <div className="mt-6 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Card>
          <h3 className="mb-3 text-sm font-semibold text-slate-700">Avg latency by service</h3>
          {obs.length === 0 ? (
            <div className="py-6 text-center text-xs text-muted">Collecting samples…</div>
          ) : (
            <HBarChart data={obs.map((s) => ({ label: s.service, value: s.avgLatencyMs }))} unit="ms" color="#0284c7" />
          )}
        </Card>
        <Card>
          <h3 className="mb-3 text-sm font-semibold text-slate-700">
            Recent policy decisions <span className="text-xs font-normal text-muted">({recentDecisions.length} shown · {auditCount ?? '…'} total · {policyCount ?? '…'} rules)</span>
          </h3>
          <Donut
            segments={[
              { label: 'Permit', value: permitCount, color: '#10b981' },
              { label: 'Deny', value: denyCount, color: '#ef4444' },
            ]}
          />
        </Card>
      </div>

      <h2 className="mb-2 mt-8 text-sm font-semibold uppercase tracking-wider text-muted">Service Health</h2>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {(['ingress', 'policy', 'routing', 'directory'] as const).map((svc) => {
          const h = health[svc] as any;
          const status = h?.status || (h?.error ? 'DOWN' : 'LOADING');
          const live = obs.find((o) => o.service === svc);
          return (
            <Card key={svc}>
              <div className="flex items-center justify-between">
                <div className="text-sm font-medium capitalize text-fg">{svc}</div>
                <Badge tone={status === 'UP' ? 'green' : status === 'LOADING' ? 'gray' : 'red'}>{status}</Badge>
              </div>
              <div className="mt-3 grid grid-cols-2 gap-x-2 gap-y-1 text-xs">
                <div className="text-muted">RPS</div>
                <div className="text-right font-mono tabular-nums">{(live?.rps ?? 0).toFixed(2)}</div>
                <div className="text-muted">Latency</div>
                <div className="text-right font-mono tabular-nums">{(live?.avgLatencyMs ?? 0).toFixed(0)} ms</div>
                <div className="text-muted">CPU</div>
                <div className="text-right font-mono tabular-nums">{(live?.cpuPct ?? 0).toFixed(1)}%</div>
                <div className="text-muted">Heap</div>
                <div className="text-right font-mono tabular-nums">{(live?.memoryUsedMb ?? 0).toFixed(0)} MB</div>
              </div>
            </Card>
          );
        })}
      </div>

      <h2 className="mb-2 mt-8 text-sm font-semibold uppercase tracking-wider text-muted">Recent Policy Decisions</h2>
      <Card className="overflow-x-auto p-0">
        <table className="w-full text-sm">
          <thead className="border-b border-border bg-slate-50 text-xs uppercase text-muted">
            <tr>
              <th className="px-3 py-2 text-left">Time</th>
              <th className="px-3 py-2 text-left">Operation</th>
              <th className="px-3 py-2 text-left">Requester Org</th>
              <th className="px-3 py-2 text-left">Decision</th>
              <th className="px-3 py-2 text-left">Correlation ID</th>
            </tr>
          </thead>
          <tbody>
            {recentDecisions.map((d: any) => (
              <tr key={d.id} className="border-b border-border/40">
                <td className="px-3 py-2 font-mono text-xs">{new Date(d.evaluatedAt).toLocaleString()}</td>
                <td className="px-3 py-2">{d.operation}</td>
                <td className="px-3 py-2">{d.requesterOrgId}</td>
                <td className="px-3 py-2">
                  <Badge tone={d.decision === 'PERMIT' ? 'green' : 'red'}>{d.decision}</Badge>
                </td>
                <td className="px-3 py-2 font-mono text-xs text-muted">{d.correlationId}</td>
              </tr>
            ))}
            {recentDecisions.length === 0 && (
              <tr><td colSpan={5} className="px-3 py-6 text-center text-muted">No decisions yet.</td></tr>
            )}
          </tbody>
        </table>
      </Card>
    </AppShell>
  );
}
