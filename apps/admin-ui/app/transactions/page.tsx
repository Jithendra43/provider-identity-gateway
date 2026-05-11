'use client';
import { AppShell } from '@/components/AppShell';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { Card, PageHeader, Badge, Button, Input } from '@/components/ui';

/**
 * "Transactions" view: groups policy_audit_entries by correlation ID so
 * operators can see the full evaluation chain for a single TEFCA call.
 */
export default function TransactionsPage() {
  const [page, setPage] = useState(0);
  const [data, setData] = useState<any>(null);
  const [filter, setFilter] = useState('');
  const [open, setOpen] = useState<string | null>(null);
  const [openEntries, setOpenEntries] = useState<any[]>([]);

  const load = async (p = 0) => {
    const r = await api.policyAuditEntries({ page: p, size: 200, correlationId: filter });
    setData(r);
    setPage(p);
  };
  useEffect(() => { load(0); /* eslint-disable-next-line */ }, []);

  // Group by correlationId.
  const groups: Record<string, any[]> = {};
  for (const row of data?.content || []) {
    (groups[row.correlationId] = groups[row.correlationId] || []).push(row);
  }
  const txs = Object.entries(groups).map(([cid, rows]) => {
    const last = rows[0];
    const finalDecision = rows.find((r) => r.decision === 'DENY')?.decision || rows[0].decision;
    return {
      correlationId: cid,
      operation: last.operation,
      requesterOrg: last.requesterOrgId,
      decisions: rows.length,
      finalDecision,
      first: rows[rows.length - 1].evaluatedAt,
      last: rows[0].evaluatedAt,
    };
  });

  const inspect = async (cid: string) => {
    setOpen(cid);
    setOpenEntries(groups[cid] || []);
  };

  return (
    <AppShell>
      <PageHeader title="Transactions" description="TEFCA exchange transactions, grouped by correlation ID." />
      <Card className="mb-4">
        <div className="flex gap-2">
          <Input placeholder="Filter by Correlation ID" value={filter} onChange={(e) => setFilter(e.target.value)} />
          <Button onClick={() => load(0)}>Search</Button>
        </div>
      </Card>
      <Card className="p-0 overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="border-b border-border bg-slate-50 text-xs uppercase text-muted">
            <tr>
              <th className="px-3 py-2 text-left">Correlation ID</th>
              <th className="px-3 py-2 text-left">Operation</th>
              <th className="px-3 py-2 text-left">Requester</th>
              <th className="px-3 py-2 text-left">Decisions</th>
              <th className="px-3 py-2 text-left">Result</th>
              <th className="px-3 py-2 text-left">Started</th>
            </tr>
          </thead>
          <tbody>
            {txs.map((t) => (
              <tr key={t.correlationId} className="cursor-pointer border-b border-border/40 hover:bg-slate-50" onClick={() => inspect(t.correlationId)}>
                <td className="px-3 py-2 font-mono text-xs">{t.correlationId}</td>
                <td className="px-3 py-2">{t.operation}</td>
                <td className="px-3 py-2 text-xs">{t.requesterOrg}</td>
                <td className="px-3 py-2 text-xs">{t.decisions}</td>
                <td className="px-3 py-2"><Badge tone={t.finalDecision === 'PERMIT' ? 'green' : 'red'}>{t.finalDecision}</Badge></td>
                <td className="px-3 py-2 font-mono text-xs">{new Date(t.first).toLocaleString()}</td>
              </tr>
            ))}
            {txs.length === 0 && <tr><td colSpan={6} className="px-3 py-6 text-center text-muted">No transactions.</td></tr>}
          </tbody>
        </table>
      </Card>
      {data && (
        <div className="mt-3 flex items-center justify-between text-xs text-muted">
          <div>{data.totalElements} raw decision rows across {txs.length} transactions</div>
          <div className="flex gap-2">
            <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => load(page - 1)}>Prev page</Button>
            <Button size="sm" variant="secondary" disabled={(page + 1) * 200 >= data.totalElements} onClick={() => load(page + 1)}>Next page</Button>
          </div>
        </div>
      )}

      {open && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-6" onClick={() => setOpen(null)}>
          <Card className="max-h-[85vh] w-full max-w-4xl overflow-auto" >
            <div className="flex items-start justify-between" onClick={(e) => e.stopPropagation()}>
              <div>
                <h3 className="text-sm font-semibold text-fg">Transaction</h3>
                <div className="mt-1 font-mono text-xs text-muted">{open}</div>
              </div>
              <Button variant="ghost" onClick={() => setOpen(null)}>Close</Button>
            </div>
            <div className="mt-3 space-y-2" onClick={(e) => e.stopPropagation()}>
              {openEntries.map((d: any) => (
                <Card key={d.id} className="!p-3">
                  <div className="flex items-center justify-between">
                    <div className="text-xs">
                      <Badge tone={d.decision === 'PERMIT' ? 'green' : 'red'}>{d.decision}</Badge>
                      <span className="ml-2 text-muted">{d.operation} • rule {d.ruleId || '(combined)'}</span>
                    </div>
                    <div className="font-mono text-xs text-muted">{new Date(d.evaluatedAt).toLocaleTimeString()}</div>
                  </div>
                  <div className="mt-2 text-xs text-muted">{d.reason}</div>
                  {d.context && (
                    <pre className="mt-2 max-h-40 overflow-auto rounded-md border border-border bg-slate-50 p-2 text-xs">
                      {JSON.stringify(d.context, null, 2)}
                    </pre>
                  )}
                </Card>
              ))}
            </div>
          </Card>
        </div>
      )}
    </AppShell>
  );
}
