'use client';
import { AppShell } from '@/components/AppShell';
import { useEffect, useState, Suspense } from 'react';
import { api } from '@/lib/api';
import { Button, Card, PageHeader, Badge, Textarea, Input, Select } from '@/components/ui';
import { useSearchParams, useRouter } from 'next/navigation';

const EMPTY_RULE = {
  ruleId: '',
  ruleName: '',
  category: 'OPERATIONAL',
  description: '',
  ruleExpression: '',
  priority: 100,
  active: true,
};

export default function PoliciesPage() {
  return (
    <Suspense fallback={<div className="p-6 text-muted">Loading…</div>}>
      <PoliciesPageInner />
    </Suspense>
  );
}

function PoliciesPageInner() {
  const params = useSearchParams();
  const router = useRouter();
  const selected = params.get('id');

  const [rules, setRules] = useState<any[]>([]);
  const [includeInactive, setIncludeInactive] = useState(true);
  const [detail, setDetail] = useState<any | null>(null);
  const [history, setHistory] = useState<any[]>([]);
  const [editing, setEditing] = useState<any | null>(null);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const reload = async () => {
    setError(null);
    try {
      const r = await api.policyRules({ includeInactive });
      setRules(r);
    } catch (e: any) {
      setError(`Failed to load rules: ${JSON.stringify(e.body)}`);
    }
  };

  useEffect(() => { reload(); }, [includeInactive]);

  useEffect(() => {
    if (!selected) { setDetail(null); setHistory([]); return; }
    (async () => {
      try {
        const [d, h] = await Promise.all([
          api.policyRule(selected),
          api.policyRuleHistory(selected).catch(() => []),
        ]);
        setDetail(d);
        setHistory(h);
      } catch (e: any) {
        setError(`Failed to load rule ${selected}: ${JSON.stringify(e.body)}`);
      }
    })();
  }, [selected]);

  const beginEdit = () => {
    if (!detail) return;
    setEditing({ ...detail });
    setCreating(false);
  };
  const beginCreate = () => {
    setEditing({ ...EMPTY_RULE });
    setCreating(true);
  };
  const cancelEdit = () => { setEditing(null); setCreating(false); };

  const save = async () => {
    if (!editing) return;
    setBusy(true);
    setError(null);
    try {
      const dto = { ...editing };
      if (creating) {
        const created = await api.createPolicyRule(dto);
        setEditing(null);
        setCreating(false);
        await reload();
        router.push(`/policies?id=${created.ruleId}`);
      } else {
        await api.updatePolicyRule(dto.ruleId, dto);
        setEditing(null);
        const d = await api.policyRule(dto.ruleId);
        setDetail(d);
        await reload();
      }
    } catch (e: any) {
      setError(`Save failed: ${e.status} ${JSON.stringify(e.body)}`);
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    if (!detail) return;
    if (!confirm(`Delete rule ${detail.ruleId}?`)) return;
    setBusy(true);
    try {
      await api.deletePolicyRule(detail.ruleId);
      router.push('/policies');
      setDetail(null);
      await reload();
    } catch (e: any) {
      setError(`Delete failed: ${e.status} ${JSON.stringify(e.body)}`);
    } finally {
      setBusy(false);
    }
  };

  return (
    <AppShell>
      <PageHeader
        title="Policy Rules"
        description="Author, version, and disable policies enforced by the policy engine."
        actions={
          <div className="flex items-center gap-3">
            <label className="flex items-center gap-2 text-xs text-muted">
              <input
                type="checkbox"
                checked={includeInactive}
                onChange={(e) => setIncludeInactive(e.target.checked)}
              />
              Show inactive
            </label>
            <Button variant="secondary" onClick={reload}>Refresh</Button>
            <Button onClick={beginCreate}>+ New rule</Button>
          </div>
        }
      />
      {error && <Card className="mb-4 border-red-800 bg-red-900/20 text-red-200 text-sm">{error}</Card>}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
        <div className="lg:col-span-7">
          <Card className="overflow-hidden p-0">
            <div className="overflow-x-auto">
              <table className="w-full table-fixed text-sm min-w-[640px]">
                <colgroup>
                  <col className="w-[28%]" />
                  <col className="w-[34%]" />
                  <col className="w-[18%]" />
                  <col className="w-[10%]" />
                  <col className="w-[10%]" />
                </colgroup>
                <thead className="border-b border-border bg-slate-50 text-xs uppercase text-muted">
                  <tr>
                    <th className="px-3 py-2 text-left">Rule ID</th>
                    <th className="px-3 py-2 text-left">Name</th>
                    <th className="px-3 py-2 text-left">Category</th>
                    <th className="px-3 py-2 text-left">Pri</th>
                    <th className="px-3 py-2"></th>
                  </tr>
                </thead>
                <tbody>
                  {rules.map((r) => (
                    <tr
                      key={r.ruleId}
                      className={`cursor-pointer border-b border-border/40 hover:bg-slate-50 ${selected === r.ruleId ? 'bg-slate-50' : ''} ${!r.active ? 'opacity-60' : ''}`}
                      onClick={() => router.push(`/policies?id=${r.ruleId}`)}
                    >
                      <td className="px-3 py-2 font-mono text-xs">
                        <span className="block truncate" title={r.ruleId}>{r.ruleId}</span>
                      </td>
                      <td className="px-3 py-2 text-xs">
                        <span className="block truncate" title={r.ruleName}>{r.ruleName}</span>
                      </td>
                      <td className="px-3 py-2">
                        <span className="block truncate" title={r.category}>
                          <Badge tone="blue">{r.category}</Badge>
                        </span>
                      </td>
                      <td className="px-3 py-2 text-xs font-mono text-muted">{r.priority}</td>
                      <td className="px-3 py-2">
                        <Badge tone={r.active ? 'green' : 'gray'}>{r.active ? 'on' : 'off'}</Badge>
                      </td>
                    </tr>
                  ))}
                  {rules.length === 0 && (
                    <tr><td colSpan={5} className="px-3 py-6 text-center text-muted">No rules.</td></tr>
                  )}
                </tbody>
              </table>
            </div>
          </Card>
        </div>

        <div className="lg:col-span-5">
          {!editing && !detail && (
            <Card className="text-center text-muted">Select a rule on the left or create a new one.</Card>
          )}
          {editing && (
            <Card>
              <h3 className="mb-3 text-sm font-semibold text-fg">{creating ? 'Create rule' : `Edit ${editing.ruleId}`}</h3>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="mb-1 block text-xs uppercase text-muted">Rule ID</label>
                  <Input value={editing.ruleId} disabled={!creating} onChange={(e) => setEditing({ ...editing, ruleId: e.target.value })} />
                </div>
                <div>
                  <label className="mb-1 block text-xs uppercase text-muted">Name</label>
                  <Input value={editing.ruleName || ''} onChange={(e) => setEditing({ ...editing, ruleName: e.target.value })} />
                </div>
                <div>
                  <label className="mb-1 block text-xs uppercase text-muted">Category</label>
                  <Select value={editing.category} onChange={(e) => setEditing({ ...editing, category: e.target.value })}>
                    {['HIPAA_PRIVACY_RULE','HIPAA_SECURITY_RULE','CFR_PART_2','TEFCA_COMMON_AGREEMENT','TEFCA_BREAKGLASS','PHI_SAFEGUARD','OPERATIONAL','DEFAULT'].map((o) => <option key={o}>{o}</option>)}
                  </Select>
                </div>
                <div>
                  <label className="mb-1 block text-xs uppercase text-muted">Priority</label>
                  <Input type="number" value={editing.priority} onChange={(e) => setEditing({ ...editing, priority: parseInt(e.target.value || '0') })} />
                </div>
              </div>
              <div className="mt-3">
                <label className="mb-1 block text-xs uppercase text-muted">Description / regulatory citation</label>
                <Textarea rows={3} value={editing.description || ''} onChange={(e) => setEditing({ ...editing, description: e.target.value })} />
              </div>
              <div className="mt-3">
                <label className="mb-1 block text-xs uppercase text-muted">Rule expression</label>
                <Textarea rows={6} value={editing.ruleExpression || ''} onChange={(e) => setEditing({ ...editing, ruleExpression: e.target.value })} />
              </div>
              <div className="mt-3 flex items-center gap-3">
                <label className="flex items-center gap-2 text-xs">
                  <input type="checkbox" checked={!!editing.active} onChange={(e) => setEditing({ ...editing, active: e.target.checked })} />
                  Active
                </label>
                <div className="flex-1" />
                <Button variant="secondary" onClick={cancelEdit}>Cancel</Button>
                <Button onClick={save} disabled={busy}>{busy ? 'Saving…' : 'Save'}</Button>
              </div>
            </Card>
          )}
          {!editing && detail && (
            <Card>
              <div className="flex items-start justify-between">
                <div>
                  <h3 className="text-sm font-semibold text-fg">{detail.ruleName || detail.ruleId}</h3>
                  <div className="mt-1 text-xs font-mono text-muted">{detail.ruleId}</div>
                </div>
                <div className="flex gap-2">
                  <Button
                    variant="secondary"
                    onClick={async () => {
                      try {
                        await api.setPolicyRuleActive(detail.ruleId, !detail.active);
                        const d = await api.policyRule(detail.ruleId);
                        setDetail(d);
                        await reload();
                      } catch (e: any) {
                        setError(`Toggle failed: ${e.status} ${JSON.stringify(e.body)}`);
                      }
                    }}
                  >
                    {detail.active ? 'Deactivate' : 'Activate'}
                  </Button>
                  <Button variant="secondary" onClick={beginEdit}>Edit</Button>
                  <Button variant="danger" onClick={remove}>Delete</Button>
                </div>
              </div>
              <div className="mt-4 grid grid-cols-3 gap-3 text-xs">
                <Field label="Category"><Badge tone="blue">{detail.category}</Badge></Field>
                <Field label="Priority" value={detail.priority} />
                <Field label="Active"><Badge tone={detail.active ? 'green' : 'gray'}>{String(detail.active)}</Badge></Field>
                <Field label="Created" value={detail.createdAt ? new Date(detail.createdAt).toLocaleString() : '—'} />
                <Field label="Updated" value={detail.updatedAt ? new Date(detail.updatedAt).toLocaleString() : '—'} />
              </div>
              <div className="mt-4">
                <div className="text-xs uppercase tracking-wider text-muted">Description</div>
                <div className="mt-1 whitespace-pre-wrap rounded-md border border-border bg-slate-50 p-3 text-xs text-fg">{detail.description || '—'}</div>
              </div>
              <div className="mt-4">
                <div className="text-xs uppercase tracking-wider text-muted">Rule expression</div>
                <pre className="mt-1 max-h-72 overflow-auto rounded-md border border-border bg-slate-50 p-3 text-xs">{detail.ruleExpression || '—'}</pre>
              </div>
              <div className="mt-4">
                <div className="text-xs uppercase tracking-wider text-muted">Version History</div>
                {history.length === 0 ? (
                  <div className="mt-1 text-xs text-muted">No prior versions.</div>
                ) : (
                  <table className="mt-2 w-full text-xs">
                    <thead className="text-muted"><tr><th className="text-left">Version</th><th className="text-left">Changed by</th><th className="text-left">Reason</th><th className="text-left">When</th></tr></thead>
                    <tbody>
                      {history.map((h: any) => (
                        <tr key={h.versionId} className="border-t border-border/40">
                          <td className="py-1">v{h.versionNumber}</td>
                          <td className="py-1">{h.changedBy || '—'}</td>
                          <td className="py-1">{h.changeReason || '—'}</td>
                          <td className="py-1 font-mono">{h.createdAt ? new Date(h.createdAt).toLocaleString() : '—'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </Card>
          )}
        </div>
      </div>
    </AppShell>
  );
}

function Field({ label, value, children }: { label: string; value?: any; children?: React.ReactNode }) {
  return (
    <div>
      <div className="text-[10px] uppercase tracking-wider text-muted">{label}</div>
      <div className="mt-0.5 text-fg">{children ?? String(value ?? '')}</div>
    </div>
  );
}
