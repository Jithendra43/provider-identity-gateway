'use client';
import { AppShell } from '@/components/AppShell';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { Card, PageHeader, Badge, Button, Input, Select } from '@/components/ui';

export default function AuditPage() {
  const [page, setPage] = useState(0);
  const [size] = useState(50);
  const [data, setData] = useState<any>(null);
  const [filters, setFilters] = useState({ correlationId: '', eventType: '', outcome: '', requesterOrgId: '', since: '' });
  const [open, setOpen] = useState<any | null>(null);

  const load = async (p = page) => {
    const r = await api.auditEvents({ page: p, size, ...filters });
    setData(r); setPage(p);
  };
  useEffect(() => { load(0); /* eslint-disable-next-line */ }, []);

  return (
    <AppShell>
      <PageHeader title="Audit Trail" description="Append-only log of every TEFCA exchange and security event." />
      <Card className="mb-4">
        <div className="grid grid-cols-6 gap-2">
          <Input placeholder="Correlation ID" value={filters.correlationId} onChange={(e) => setFilters({ ...filters, correlationId: e.target.value })} />
          <Input placeholder="Event type" value={filters.eventType} onChange={(e) => setFilters({ ...filters, eventType: e.target.value })} />
          <Select value={filters.outcome} onChange={(e) => setFilters({ ...filters, outcome: e.target.value })}>
            <option value="">Any outcome</option>
            <option>SUCCESS</option><option>ERROR</option><option>DENIED</option>
          </Select>
          <Input placeholder="Requester Org" value={filters.requesterOrgId} onChange={(e) => setFilters({ ...filters, requesterOrgId: e.target.value })} />
          <Input type="datetime-local" value={filters.since} onChange={(e) => setFilters({ ...filters, since: e.target.value })} />
          <Button onClick={() => load(0)}>Apply</Button>
        </div>
      </Card>
      <Card className="p-0 overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="border-b border-border bg-slate-50 text-xs uppercase text-muted">
            <tr>
              <th className="px-3 py-2 text-left">Time</th>
              <th className="px-3 py-2 text-left">Event</th>
              <th className="px-3 py-2 text-left">Outcome</th>
              <th className="px-3 py-2 text-left">Requester</th>
              <th className="px-3 py-2 text-left">Target / Operation</th>
              <th className="px-3 py-2 text-left">Correlation</th>
            </tr>
          </thead>
          <tbody>
            {data?.content?.map((e: any) => (
              <tr key={e.eventId} className="cursor-pointer border-b border-border/40 hover:bg-slate-50" onClick={() => setOpen(e)}>
                <td className="px-3 py-2 font-mono text-xs">{e.createdAt ? new Date(e.createdAt).toLocaleString() : '—'}</td>
                <td className="px-3 py-2 text-xs">{e.eventType}</td>
                <td className="px-3 py-2"><Badge tone={e.outcome === 'SUCCESS' ? 'green' : e.outcome === 'DENIED' ? 'amber' : 'red'}>{e.outcome}</Badge></td>
                <td className="px-3 py-2 text-xs">{e.requesterOrgId || '—'}</td>
                <td className="px-3 py-2 text-xs text-muted"><span className="font-mono">{e.targetOrgId || '—'}</span> {e.operation && <span className="ml-2 text-[10px] uppercase tracking-wider">{e.operation}</span>}</td>
                <td className="px-3 py-2 font-mono text-xs text-muted">{e.correlationId}</td>
              </tr>
            ))}
            {!data?.content?.length && <tr><td colSpan={6} className="px-3 py-6 text-center text-muted">No events.</td></tr>}
          </tbody>
        </table>
      </Card>
      {data && (
        <div className="mt-3 flex items-center justify-between text-xs text-muted">
          <div>Total: {data.totalElements}</div>
          <div className="flex gap-2">
            <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => load(page - 1)}>Prev</Button>
            <span>Page {page + 1}</span>
            <Button size="sm" variant="secondary" disabled={(page + 1) * size >= data.totalElements} onClick={() => load(page + 1)}>Next</Button>
          </div>
        </div>
      )}
      {open && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-6" onClick={() => setOpen(null)}>
          <Card className="max-h-[80vh] w-full max-w-3xl overflow-auto">
            <div className="flex items-start justify-between" onClick={(e) => e.stopPropagation()}>
              <h3 className="text-sm font-semibold text-fg">Audit event {open.eventId}</h3>
              <Button variant="ghost" onClick={() => setOpen(null)}>Close</Button>
            </div>
            <pre className="mt-3 overflow-auto rounded-md border border-border bg-slate-50 p-3 text-xs" onClick={(e) => e.stopPropagation()}>
              {JSON.stringify(open, null, 2)}
            </pre>
          </Card>
        </div>
      )}
    </AppShell>
  );
}
