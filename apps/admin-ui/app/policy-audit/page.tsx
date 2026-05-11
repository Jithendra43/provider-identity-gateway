'use client';
import { AppShell } from '@/components/AppShell';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { Card, PageHeader, Badge, Input, Select, Button } from '@/components/ui';

export default function PolicyAuditPage() {
  const [page, setPage] = useState(0);
  const [size] = useState(50);
  const [data, setData] = useState<any>(null);
  const [filters, setFilters] = useState({ correlationId: '', decision: '', requesterOrgId: '', operation: '', since: '' });
  const [open, setOpen] = useState<any | null>(null);

  const load = async (p = page) => {
    const r = await api.policyAuditEntries({ page: p, size, ...filters });
    setData(r);
    setPage(p);
  };
  useEffect(() => { load(0); /* eslint-disable-next-line */ }, []);

  return (
    <AppShell>
      <PageHeader title="Policy Decision Log" description="Every PERMIT / DENY / OBLIGATE evaluated by the policy engine." />
      <Card className="mb-4">
        <div className="grid grid-cols-6 gap-2">
          <Input placeholder="Correlation ID" value={filters.correlationId} onChange={(e) => setFilters({ ...filters, correlationId: e.target.value })} />
          <Select value={filters.decision} onChange={(e) => setFilters({ ...filters, decision: e.target.value })}>
            <option value="">Any decision</option><option>PERMIT</option><option>DENY</option><option>OBLIGATE</option>
          </Select>
          <Input placeholder="Requester Org" value={filters.requesterOrgId} onChange={(e) => setFilters({ ...filters, requesterOrgId: e.target.value })} />
          <Select value={filters.operation} onChange={(e) => setFilters({ ...filters, operation: e.target.value })}>
            <option value="">Any operation</option>
            <optgroup label="TEFCA Core">
              {['PATIENT_DISCOVERY', 'DOCUMENT_QUERY', 'DOCUMENT_RETRIEVE', 'MESSAGE_DELIVERY'].map((o) => <option key={o}>{o}</option>)}
            </optgroup>
            <optgroup label="Prior Authorization">
              <option value="PRIOR_AUTHORIZATION">PRIOR_AUTHORIZATION (any PA)</option>
            </optgroup>
          </Select>
          <Input type="datetime-local" value={filters.since} onChange={(e) => setFilters({ ...filters, since: e.target.value })} />
          <Button onClick={() => load(0)}>Apply</Button>
        </div>
      </Card>

      <Card className="p-0 overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="border-b border-border bg-slate-50 text-xs uppercase text-muted">
            <tr>
              <th className="px-3 py-2 text-left">Time</th>
              <th className="px-3 py-2 text-left">Operation</th>
              <th className="px-3 py-2 text-left">Requester</th>
              <th className="px-3 py-2 text-left">Decision</th>
              <th className="px-3 py-2 text-left">Reason</th>
              <th className="px-3 py-2 text-left">Correlation</th>
            </tr>
          </thead>
          <tbody>
            {data?.content?.map((d: any) => (
              <tr key={d.id} className="cursor-pointer border-b border-border/40 hover:bg-slate-50" onClick={() => setOpen(d)}>
                <td className="px-3 py-2 font-mono text-xs">{new Date(d.evaluatedAt).toLocaleString()}</td>
                <td className="px-3 py-2">{d.operation}</td>
                <td className="px-3 py-2">{d.requesterOrgId}</td>
                <td className="px-3 py-2"><Badge tone={d.decision === 'PERMIT' ? 'green' : d.decision === 'DENY' ? 'red' : 'amber'}>{d.decision}</Badge></td>
                <td className="px-3 py-2 truncate text-xs text-muted" style={{ maxWidth: 280 }}>{d.reason}</td>
                <td className="px-3 py-2 font-mono text-xs text-muted">{d.correlationId}</td>
              </tr>
            ))}
            {!data?.content?.length && <tr><td colSpan={6} className="px-3 py-6 text-center text-muted">No entries.</td></tr>}
          </tbody>
        </table>
      </Card>
      {data && (
        <div className="mt-3 flex items-center justify-between text-xs text-muted">
          <div>Total: {data.totalElements}</div>
          <div className="flex gap-2">
            <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => load(page - 1)}>Prev</Button>
            <span>Page {page + 1} of {Math.max(1, Math.ceil(data.totalElements / size))}</span>
            <Button size="sm" variant="secondary" disabled={(page + 1) * size >= data.totalElements} onClick={() => load(page + 1)}>Next</Button>
          </div>
        </div>
      )}

      {open && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-6" onClick={() => setOpen(null)}>
          <Card className="max-h-[80vh] w-full max-w-3xl overflow-auto" >
            <div className="flex items-start justify-between" onClick={(e) => e.stopPropagation()}>
              <div>
                <h3 className="text-sm font-semibold text-fg">Decision detail</h3>
                <div className="mt-1 font-mono text-xs text-muted">{open.correlationId}</div>
              </div>
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
