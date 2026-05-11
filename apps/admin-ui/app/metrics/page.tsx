'use client';
import { AppShell } from '@/components/AppShell';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { Card, PageHeader, Badge } from '@/components/ui';
import { Sparkline } from '@/components/charts';

type ObsRow = Awaited<ReturnType<typeof api.observability>>[number];

export default function MetricsPage() {
  const [obs, setObs] = useState<ObsRow[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  useEffect(() => {
    let cancelled = false;
    const tick = async () => {
      try {
        const o = await api.observability();
        if (!cancelled) {
          setObs(o);
          setLastUpdated(new Date());
          setErr(null);
        }
      } catch (e: any) {
        if (!cancelled) setErr(String(e?.body?.error || e?.status || 'unable to fetch'));
      }
    };
    tick();
    const i = setInterval(tick, 15000);
    return () => {
      cancelled = true;
      clearInterval(i);
    };
  }, []);

  return (
    <AppShell>
      <PageHeader
        title="Metrics"
        description="Key performance indicators per service. Auto-refreshes every 15 seconds."
      />

      {err && (
        <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {err}
        </div>
      )}

      {obs.length === 0 && !err && (
        <Card>
          <div className="py-8 text-center text-sm text-muted">Collecting samples from services…</div>
        </Card>
      )}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        {obs.map((s) => {
          const latencyLine = s.series.map((p) => p.avgLatencyMs);
          const rpsLine = s.series.map((p) => p.rps);
          return (
            <Card key={s.service}>
              <div className="mb-3 flex items-center justify-between">
                <h3 className="text-base font-semibold capitalize text-fg">{s.service}</h3>
                <Badge tone={s.up ? 'green' : 'red'}>{s.up ? 'UP' : 'DOWN'}</Badge>
              </div>

              <div className="grid grid-cols-3 gap-3 text-center">
                <Kpi label="Throughput" value={`${s.rps.toFixed(2)}`} unit="req/s" />
                <Kpi label="Avg latency" value={`${s.avgLatencyMs.toFixed(0)}`} unit="ms"
                     warn={s.avgLatencyMs > 500} />
                <Kpi label="Error rate" value={`${(s.errorRate * 100).toFixed(2)}`} unit="%"
                     warn={s.errorRate > 0.01} danger={s.errorRate > 0.05} />
                <Kpi label="CPU" value={`${s.cpuPct.toFixed(1)}`} unit="%"
                     warn={s.cpuPct > 70} danger={s.cpuPct > 90} />
                <Kpi label="Heap" value={`${s.memoryUsedMb.toFixed(0)}`} unit="MB" />
                <Kpi label="DB pool" value={`${s.dbPoolActive}`} unit="active" />
              </div>

              <div className="mt-4">
                <div className="mb-1 flex items-baseline justify-between text-xs text-muted">
                  <span>Throughput (req/s)</span>
                  <span>last 30 min</span>
                </div>
                <Sparkline values={rpsLine} height={56} color="#0284c7" fill="rgba(2,132,199,0.10)" />
              </div>

              <div className="mt-3">
                <div className="mb-1 flex items-baseline justify-between text-xs text-muted">
                  <span>Latency (ms)</span>
                  <span>last 30 min</span>
                </div>
                <Sparkline values={latencyLine} height={56} color="#7c3aed" fill="rgba(124,58,237,0.10)" />
              </div>
            </Card>
          );
        })}
      </div>

      {lastUpdated && (
        <div className="mt-4 text-right text-xs text-muted">
          Last updated {lastUpdated.toLocaleTimeString()}
        </div>
      )}
    </AppShell>
  );
}

function Kpi({
  label,
  value,
  unit,
  warn,
  danger,
}: {
  label: string;
  value: string;
  unit: string;
  warn?: boolean;
  danger?: boolean;
}) {
  const tone = danger ? 'text-red-600' : warn ? 'text-amber-600' : 'text-slate-900';
  return (
    <div className="rounded-lg bg-slate-50 px-2 py-2">
      <div className="text-[10px] uppercase tracking-wide text-muted">{label}</div>
      <div className={`text-lg font-semibold tabular-nums ${tone}`}>
        {value} <span className="text-xs font-normal text-muted">{unit}</span>
      </div>
    </div>
  );
}
