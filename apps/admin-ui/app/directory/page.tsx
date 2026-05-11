'use client';
import { AppShell } from '@/components/AppShell';
import { Fragment, useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { Card, PageHeader, Badge, Button, Input } from '@/components/ui';

type Tab = 'orgs' | 'nodes' | 'sync';

export default function DirectoryPage() {
  const [tab, setTab] = useState<Tab>('orgs');

  return (
    <AppShell>
      <PageHeader title="Directory" description="Federated organization, node, and endpoint records cached locally." />
      <div className="mb-4 flex gap-2">
        {(['orgs', 'nodes', 'sync'] as Tab[]).map((t) => (
          <Button key={t} variant={tab === t ? 'primary' : 'secondary'} onClick={() => setTab(t)}>
            {t === 'orgs' ? 'Organizations' : t === 'nodes' ? 'Nodes' : 'Sync & Cache'}
          </Button>
        ))}
      </div>
      {tab === 'orgs' && <OrgsTab />}
      {tab === 'nodes' && <NodesTab />}
      {tab === 'sync' && <SyncTab />}
    </AppShell>
  );
}

function OrgsTab() {
  const [orgs, setOrgs] = useState<any[]>([]);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [endpointsByOrg, setEndpointsByOrg] = useState<Record<string, any[]>>({});
  const [nodesByOrg, setNodesByOrg] = useState<Record<string, any[]>>({});
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => { api.organizations().then(setOrgs).catch((e) => setErr(JSON.stringify(e.body))); }, []);

  const toggle = (orgId: string) => {
    if (expandedId === orgId) { setExpandedId(null); return; }
    setExpandedId(orgId);
    if (!endpointsByOrg[orgId]) {
      api.orgEndpoints(orgId).then((e) => setEndpointsByOrg((p) => ({ ...p, [orgId]: e }))).catch(() => setEndpointsByOrg((p) => ({ ...p, [orgId]: [] })));
    }
    if (!nodesByOrg[orgId]) {
      api.nodes({ orgId }).then((n) => setNodesByOrg((p) => ({ ...p, [orgId]: n }))).catch(() => setNodesByOrg((p) => ({ ...p, [orgId]: [] })));
    }
  };

  const replaceEndpoint = (orgId: string) => (updated: any) =>
    setEndpointsByOrg((p) => ({ ...p, [orgId]: (p[orgId] || []).map((x) => (x.endpointId === updated.endpointId ? { ...x, ...updated } : x)) }));
  const removeEndpoint = (orgId: string) => (id: string) =>
    setEndpointsByOrg((p) => ({ ...p, [orgId]: (p[orgId] || []).filter((x) => x.endpointId !== id) }));
  const addEndpoint = (orgId: string) => (created: any) =>
    setEndpointsByOrg((p) => ({ ...p, [orgId]: [...(p[orgId] || []), created] }));

  return (
    <Card className="p-0 overflow-hidden">
      <table className="w-full text-sm">
        <thead className="border-b border-border bg-slate-50 text-xs uppercase text-muted">
          <tr>
            <th className="w-8 px-2 py-2"></th>
            <th className="px-3 py-2 text-left">Org ID</th>
            <th className="px-3 py-2 text-left">Name</th>
            <th className="px-3 py-2 text-left">Type</th>
            <th className="px-3 py-2 text-left">Status</th>
            <th className="px-3 py-2 text-right text-[10px] normal-case tracking-normal text-muted">Click row to manage endpoints</th>
          </tr>
        </thead>
        <tbody>
          {orgs.map((o) => {
            const isOpen = expandedId === o.orgId;
            const eps = endpointsByOrg[o.orgId];
            const nodes = nodesByOrg[o.orgId] || [];
            return (
              <Fragment key={o.orgId}>
                <tr
                    className={`cursor-pointer border-b border-border/40 hover:bg-slate-50 ${isOpen ? 'bg-slate-50' : ''}`}
                    onClick={() => toggle(o.orgId)}>
                  <td className="px-2 py-2 text-center text-muted">{isOpen ? '▾' : '▸'}</td>
                  <td className="px-3 py-2 font-mono text-xs">{o.orgId}</td>
                  <td className="px-3 py-2 text-xs">{o.name}</td>
                  <td className="px-3 py-2"><Badge tone={o.orgType === 'QHIN' ? 'blue' : o.orgType === 'PUBLIC_HEALTH' ? 'amber' : 'gray'}>{o.orgType || '—'}</Badge></td>
                  <td className="px-3 py-2"><Badge tone={o.active ? 'green' : 'red'}>{o.active ? 'ACTIVE' : 'INACTIVE'}</Badge></td>
                  <td className="px-3 py-2 text-right text-[11px] text-blue-600">{isOpen ? 'Hide' : 'Manage endpoints →'}</td>
                </tr>
                {isOpen && (
                  <tr className="border-b border-border bg-slate-50/60">
                    <td colSpan={6} className="px-4 py-4">
                      <div className="grid grid-cols-3 gap-3 text-xs">
                        <Field label="OID" value={o.oid} />
                        <Field label="Home community" value={o.homeCommunityId} />
                        <Field label="Synced" value={o.lastSyncedAt ? new Date(o.lastSyncedAt).toLocaleString() : '—'} />
                      </div>
                      <h4 className="mt-4 text-xs uppercase tracking-wider text-muted">Endpoints</h4>
                      <div className="mt-2 space-y-2">
                        {eps === undefined && <div className="text-xs text-muted">Loading…</div>}
                        {eps && eps.length === 0 && <div className="text-xs text-muted">No endpoints registered.</div>}
                        {eps && eps.map((e: any) => (
                          <EndpointRow
                            key={e.endpointId}
                            endpoint={e}
                            orgId={o.orgId}
                            showNode
                            onSaved={replaceEndpoint(o.orgId)}
                            onDeleted={removeEndpoint(o.orgId)}
                          />
                        ))}
                        <NewEndpointForm nodes={nodes} onCreated={addEndpoint(o.orgId)} />
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            );
          })}
          {orgs.length === 0 && <tr><td colSpan={6} className="px-3 py-6 text-center text-muted">{err || 'No organizations.'}</td></tr>}
        </tbody>
      </table>
    </Card>
  );
}

function NodesTab() {
  const [nodes, setNodes] = useState<any[]>([]);
  const [selected, setSelected] = useState<any | null>(null);
  const [endpoints, setEndpoints] = useState<any[]>([]);
  const [delMsg, setDelMsg] = useState<string | null>(null);

  const reloadNodes = () => api.nodes().then(setNodes).catch(() => setNodes([]));
  useEffect(() => { reloadNodes(); }, []);
  useEffect(() => {
    if (!selected) return;
    api.nodeEndpoints(selected.nodeId).then(setEndpoints).catch(() => setEndpoints([]));
  }, [selected]);

  const replaceEndpoint = (updated: any) => setEndpoints((prev) => prev.map((x) => (x.endpointId === updated.endpointId ? { ...x, ...updated } : x)));
  const removeEndpoint = (id: string) => setEndpoints((prev) => prev.filter((x) => x.endpointId !== id));
  const addEndpoint = (created: any) => setEndpoints((prev) => [...prev, created]);

  const deleteNode = async () => {
    if (!selected) return;
    if (!confirm(`Delete node ${selected.nodeId}? This cannot be undone.`)) return;
    setDelMsg(null);
    try {
      await api.deleteNode(selected.nodeId);
      setSelected(null);
      setEndpoints([]);
      await reloadNodes();
    } catch (e: any) {
      const msg = typeof e.body === 'string' ? e.body : (e.body?.message || JSON.stringify(e.body));
      setDelMsg(`${e.status}: ${msg}`);
    }
  };

  return (
    <div className="grid grid-cols-12 gap-4">
      <div className="col-span-5">
        <Card className="p-0 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="border-b border-border bg-slate-50 text-xs uppercase text-muted">
              <tr><th className="px-3 py-2 text-left">Node ID</th><th className="px-3 py-2 text-left">Org</th><th className="px-3 py-2 text-left">Status</th></tr>
            </thead>
            <tbody>
              {nodes.map((n) => (
                <tr key={n.nodeId} className="cursor-pointer border-b border-border/40 hover:bg-slate-50" onClick={() => setSelected(n)}>
                  <td className="px-3 py-2 font-mono text-xs">{n.nodeId}</td>
                  <td className="px-3 py-2 text-xs">{n.orgId}</td>
                  <td className="px-3 py-2"><Badge tone={n.status === 'ACTIVE' ? 'green' : 'amber'}>{n.status}</Badge></td>
                </tr>
              ))}
              {nodes.length === 0 && <tr><td colSpan={3} className="px-3 py-6 text-center text-muted">No nodes.</td></tr>}
            </tbody>
          </table>
        </Card>
      </div>
      <div className="col-span-7">
        {selected ? (
          <Card>
            <div className="flex items-start justify-between">
              <div>
                <h3 className="text-sm font-semibold text-fg">{selected.name || selected.nodeId}</h3>
                <div className="mt-1 font-mono text-xs text-muted">{selected.nodeId}</div>
              </div>
              <Button size="sm" variant="danger" onClick={deleteNode} disabled={endpoints.length > 0}
                title={endpoints.length > 0 ? 'Delete endpoints first' : 'Delete this node'}>
                Delete node
              </Button>
            </div>
            <div className="mt-3 grid grid-cols-3 gap-3 text-xs">
              <Field label="Org" value={selected.orgId} />
              <Field label="Status" value={selected.status} />
              <Field label="Home community" value={selected.homeCommunityId} />
              <Field label="Capabilities" value={(selected.capabilities || []).join(', ')} />
            </div>
            {delMsg && <div className="mt-2 rounded border border-red-200 bg-red-50 p-2 text-[11px] text-red-700">{delMsg}</div>}
            <h4 className="mt-4 text-xs uppercase tracking-wider text-muted">Endpoints</h4>
            <div className="mt-2 space-y-2">
              {endpoints.map((e: any) => (
                <EndpointRow
                  key={e.endpointId}
                  endpoint={e}
                  orgId={selected.orgId}
                  onSaved={replaceEndpoint}
                  onDeleted={removeEndpoint}
                />
              ))}
              {endpoints.length === 0 && <div className="text-xs text-muted">No endpoints.</div>}
              <NewEndpointForm nodes={[selected]} defaultNodeId={selected.nodeId} onCreated={addEndpoint} />
            </div>
          </Card>
        ) : (
          <Card className="text-center text-muted">Select a node.</Card>
        )}
      </div>
    </div>
  );
}

function SyncTab() {
  const [status, setStatus] = useState<any | null>(null);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [orgId, setOrgId] = useState('');

  const load = () => api.syncStatus().then(setStatus).catch((e) => setStatus({ error: JSON.stringify(e.body) }));
  useEffect(() => { load(); }, []);

  const trigger = async () => {
    setBusy(true); setMsg(null);
    try { const r = await api.triggerSync(); setMsg(`Sync triggered: ${JSON.stringify(r)}`); await load(); }
    catch (e: any) { setMsg(`Failed: ${e.status} ${JSON.stringify(e.body)}`); }
    finally { setBusy(false); }
  };
  const flushAll = async () => {
    setBusy(true); setMsg(null);
    try { const r = await api.invalidateAllCache(); setMsg(`Cache flushed: ${JSON.stringify(r)}`); }
    catch (e: any) { setMsg(`Failed: ${e.status} ${JSON.stringify(e.body)}`); }
    finally { setBusy(false); }
  };
  const flushOne = async () => {
    if (!orgId) return;
    setBusy(true); setMsg(null);
    try { const r = await api.invalidateOrgCache(orgId); setMsg(`Org cache flushed: ${JSON.stringify(r)}`); }
    catch (e: any) { setMsg(`Failed: ${e.status} ${JSON.stringify(e.body)}`); }
    finally { setBusy(false); }
  };

  return (
    <div className="grid grid-cols-2 gap-4">
      <Card>
        <h3 className="mb-3 text-sm font-semibold text-fg">RCE sync status</h3>
        <pre className="overflow-auto rounded-md border border-border bg-slate-50 p-3 text-xs">{JSON.stringify(status, null, 2)}</pre>
        <div className="mt-3 flex gap-2">
          <Button onClick={load} variant="secondary" size="sm">Refresh</Button>
          <Button onClick={trigger} disabled={busy} size="sm">Trigger sync</Button>
        </div>
      </Card>
      <Card>
        <h3 className="mb-3 text-sm font-semibold text-fg">Cache invalidation</h3>
        <Button variant="danger" onClick={flushAll} disabled={busy}>Invalidate ALL cache</Button>
        <div className="mt-4 flex gap-2">
          <Input placeholder="Org ID (e.g. ORG-12345)" value={orgId} onChange={(e) => setOrgId(e.target.value)} />
          <Button onClick={flushOne} disabled={busy || !orgId} variant="secondary">Flush org</Button>
        </div>
        {msg && <div className="mt-3 rounded-md border border-border bg-slate-50 p-2 text-xs">{msg}</div>}
      </Card>
    </div>
  );
}

function Field({ label, value }: { label: string; value: any }) {
  return (
    <div>
      <div className="text-[10px] uppercase tracking-wider text-muted">{label}</div>
      <div className="mt-0.5 break-all text-fg">{String(value ?? '—')}</div>
    </div>
  );
}

/**
 * Inline editor for a directory endpoint. Operators can rewrite the URL,
 * toggle active, or delete the endpoint without applying a SQL migration;
 * the backend invalidates the per-org routing cache so the next request
 * picks up the new value.
 */
function EndpointRow({ endpoint, orgId, showNode, onSaved, onDeleted }: {
  endpoint: any;
  orgId?: string;
  showNode?: boolean;
  onSaved: (e: any) => void;
  onDeleted?: (id: string) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [url, setUrl] = useState(endpoint.url || '');
  const [active, setActive] = useState<boolean>(!!endpoint.active);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const dirty = url !== endpoint.url || active !== !!endpoint.active;

  const save = async () => {
    setBusy(true); setErr(null);
    try {
      const updated = await api.updateEndpoint(endpoint.endpointId, { url: url.trim(), active });
      onSaved(updated);
      // Belt-and-suspenders: backend invalidates per-org routing cache, but we re-fire
      // it from the UI too so the next partner request unambiguously sees the new URL.
      const oid = orgId || endpoint.orgId;
      if (oid) { api.invalidateOrgCache(oid).catch(() => { /* non-fatal */ }); }
      setEditing(false);
    } catch (e: any) {
      setErr(`${e.status}: ${typeof e.body === 'string' ? e.body : JSON.stringify(e.body)}`);
    } finally {
      setBusy(false);
    }
  };

  const cancel = () => {
    setUrl(endpoint.url || '');
    setActive(!!endpoint.active);
    setErr(null);
    setEditing(false);
  };

  const remove = async () => {
    if (!onDeleted) return;
    if (!confirm(`Delete endpoint ${endpoint.endpointId}? This cannot be undone.`)) return;
    setBusy(true); setErr(null);
    try {
      await api.deleteEndpoint(endpoint.endpointId);
      onDeleted(endpoint.endpointId);
    } catch (e: any) {
      setErr(`${e.status}: ${typeof e.body === 'string' ? e.body : JSON.stringify(e.body)}`);
      setBusy(false);
    }
  };

  return (
    <div className="rounded-md border border-border bg-slate-50 p-2 text-xs">
      <div className="flex items-center justify-between">
        <span className="font-mono">{endpoint.endpointId}</span>
        <Badge tone={active ? 'green' : 'amber'}>{active ? 'ACTIVE' : 'INACTIVE'}</Badge>
      </div>
      <div className="mt-1 text-muted">{endpoint.modality}{showNode && endpoint.nodeId ? ` • node ${endpoint.nodeId}` : ''}</div>
      {!editing && (
        <div className="mt-1 break-all text-blue-600">{endpoint.url}</div>
      )}
      {!editing && (
        <div className="mt-2 flex flex-wrap items-center gap-2 border-t border-border pt-2">
          <Button size="sm" onClick={() => setEditing(true)}>Edit URL</Button>
          {onDeleted && (
            <Button size="sm" variant="danger" onClick={remove} disabled={busy}>Delete</Button>
          )}
          <span className="ml-auto text-[10px] text-muted">Click “Edit URL” to change endpoint.</span>
        </div>
      )}
      {editing ? (
        <div className="mt-2 space-y-2">
          <Input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://partner.example.com/path" />
          <label className="flex items-center gap-2 text-xs">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
            <span>Active (participates in routing)</span>
          </label>
          {err && <div className="rounded border border-red-200 bg-red-50 p-1.5 text-[11px] text-red-700">{err}</div>}
          <div className="flex gap-2">
            <Button size="sm" onClick={save} disabled={busy || !dirty || !url.trim()}>{busy ? 'Saving…' : 'Save'}</Button>
            <Button size="sm" variant="secondary" onClick={cancel} disabled={busy}>Cancel</Button>
          </div>
          <div className="text-[10px] text-muted">URL changes are persisted immediately and the per-org routing cache is invalidated. Audit-logged.</div>
        </div>
      ) : (
        err && <div className="mt-1 rounded border border-red-200 bg-red-50 p-1.5 text-[11px] text-red-700">{err}</div>
      )}
    </div>
  );
}

const MODALITIES = [
  'XCPD', 'XCA_QUERY', 'XCA_RETRIEVE', 'XDR', 'FHIR', 'DIRECT',
  'PA_ORDER_SIGN', 'PA_ORDER_SELECT', 'PA_APPOINTMENT_BOOK', 'PA_ORDER_DISPATCH',
  'PA_ENCOUNTER_START', 'PA_ENCOUNTER_DISCHARGE',
  'PA_DTR_QUESTIONNAIRE_PACKAGE', 'PA_DTR_QUESTIONNAIRE_READ', 'PA_DTR_LIBRARY_READ',
  'PA_DTR_RESPONSE_SUBMIT', 'PA_DTR_RESPONSE_READ',
  'PA_CLAIM_SUBMIT', 'PA_CLAIM_INQUIRE', 'PA_CLAIM_RESPONSE_READ',
] as const;

/**
 * Inline form to register a new directory endpoint. Used from both the Orgs
 * tab (operator picks a node from the org's nodes) and the Nodes tab (node is
 * pre-filled). Submits to POST /api/v1/admin/directory/endpoints.
 */
function NewEndpointForm({ nodes, defaultNodeId, onCreated }: {
  nodes: any[];
  defaultNodeId?: string;
  onCreated: (e: any) => void;
}) {
  const [open, setOpen] = useState(false);
  const [endpointId, setEndpointId] = useState('');
  const [nodeId, setNodeId] = useState(defaultNodeId || '');
  const [url, setUrl] = useState('');
  const [modality, setModality] = useState<string>('FHIR');
  const [supportedOps, setSupportedOps] = useState('');
  const [timeoutMs, setTimeoutMs] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => { if (defaultNodeId) setNodeId(defaultNodeId); }, [defaultNodeId]);

  const reset = () => {
    setEndpointId(''); setUrl(''); setModality('FHIR');
    setSupportedOps(''); setTimeoutMs(''); setErr(null);
    if (!defaultNodeId) setNodeId('');
  };

  const submit = async () => {
    setBusy(true); setErr(null);
    try {
      const dto: any = {
        endpointId: endpointId.trim(),
        nodeId: nodeId.trim(),
        url: url.trim(),
        modality,
        active: true,
      };
      if (supportedOps.trim()) dto.supportedOperations = supportedOps.trim();
      if (timeoutMs.trim()) dto.timeoutMs = Number(timeoutMs);
      const created = await api.createEndpoint(dto);
      onCreated(created);
      reset();
      setOpen(false);
    } catch (e: any) {
      setErr(`${e.status}: ${typeof e.body === 'string' ? e.body : (e.body?.message || JSON.stringify(e.body))}`);
    } finally {
      setBusy(false);
    }
  };

  if (!open) {
    return (
      <Button size="sm" variant="secondary" onClick={() => setOpen(true)}>+ Add endpoint</Button>
    );
  }

  return (
    <div className="rounded-md border border-blue-200 bg-blue-50/40 p-3 text-xs">
      <div className="mb-2 text-xs font-semibold text-fg">New endpoint</div>
      <div className="grid grid-cols-2 gap-2">
        <label className="space-y-1">
          <div className="text-[10px] uppercase tracking-wider text-muted">Endpoint ID *</div>
          <Input value={endpointId} onChange={(e) => setEndpointId(e.target.value)} placeholder="EP-EPIC-PA-CRD-NEW" />
        </label>
        <label className="space-y-1">
          <div className="text-[10px] uppercase tracking-wider text-muted">Node *</div>
          {defaultNodeId ? (
            <Input value={nodeId} disabled />
          ) : (
            <select className="w-full rounded-md border border-border bg-white px-2 py-1.5 text-xs"
                    value={nodeId} onChange={(e) => setNodeId(e.target.value)}>
              <option value="">— select node —</option>
              {nodes.map((n) => (
                <option key={n.nodeId} value={n.nodeId}>{n.nodeId} ({n.name || n.orgId})</option>
              ))}
            </select>
          )}
        </label>
        <label className="col-span-2 space-y-1">
          <div className="text-[10px] uppercase tracking-wider text-muted">URL *</div>
          <Input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://partner.example.com/fhir" />
        </label>
        <label className="space-y-1">
          <div className="text-[10px] uppercase tracking-wider text-muted">Modality *</div>
          <select className="w-full rounded-md border border-border bg-white px-2 py-1.5 text-xs"
                  value={modality} onChange={(e) => setModality(e.target.value)}>
            {MODALITIES.map((m) => <option key={m} value={m}>{m}</option>)}
          </select>
        </label>
        <label className="space-y-1">
          <div className="text-[10px] uppercase tracking-wider text-muted">Timeout (ms)</div>
          <Input value={timeoutMs} onChange={(e) => setTimeoutMs(e.target.value)} placeholder="30000" />
        </label>
        <label className="col-span-2 space-y-1">
          <div className="text-[10px] uppercase tracking-wider text-muted">Supported operations (optional)</div>
          <Input value={supportedOps} onChange={(e) => setSupportedOps(e.target.value)} placeholder="PATIENT_DISCOVERY, DOCUMENT_QUERY" />
        </label>
      </div>
      {err && <div className="mt-2 rounded border border-red-200 bg-red-50 p-1.5 text-[11px] text-red-700">{err}</div>}
      <div className="mt-3 flex gap-2">
        <Button size="sm" onClick={submit}
                disabled={busy || !endpointId.trim() || !nodeId.trim() || !url.trim()}>
          {busy ? 'Creating…' : 'Create'}
        </Button>
        <Button size="sm" variant="secondary" onClick={() => { reset(); setOpen(false); }} disabled={busy}>
          Cancel
        </Button>
      </div>
    </div>
  );
}
