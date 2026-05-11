/**
 * Lightweight typed fetch helpers that always go through the ingress admin
 * proxy. Cookies are sent automatically; the server injects the bearer token
 * downstream.
 */

export type ApiError = { status: number; body: unknown };

const PROXY_PREFIX = '/api/admin/proxy';

async function request<T>(
  service: 'policy' | 'routing' | 'directory' | 'ingress',
  path: string,
  init?: RequestInit
): Promise<T> {
  const url = `${PROXY_PREFIX}/${service}${path.startsWith('/') ? path : '/' + path}`;
  const r = await fetch(url, {
    ...init,
    credentials: 'include',
    headers: {
      'Accept': 'application/json',
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...(init?.headers || {}),
    },
  });
  const text = await r.text();
  let body: unknown = text;
  if (text) {
    try { body = JSON.parse(text); } catch { /* keep as text */ }
  }
  if (!r.ok) {
    const err: ApiError = { status: r.status, body };
    throw err;
  }
  return body as T;
}

export const api = {
  // Policy
  policyRules: (opts: { includeInactive?: boolean; category?: string } = {}) => {
    const q = new URLSearchParams();
    if (opts.includeInactive) q.set('includeInactive', 'true');
    if (opts.category) q.set('category', opts.category);
    const qs = q.toString();
    return request<any[]>('policy', `/api/v1/admin/policy/rules${qs ? '?' + qs : ''}`);
  },
  setPolicyRuleActive: (id: string, active: boolean) =>
    request<any>('policy', `/api/v1/admin/policy/rules/${id}/${active ? 'activate' : 'deactivate'}`, {
      method: 'POST',
    }),
  policyRule: (id: string) => request<any>('policy', `/api/v1/admin/policy/rules/${id}`),
  policyRuleHistory: (id: string) => request<any[]>('policy', `/api/v1/admin/policy/rules/${id}/history`),
  createPolicyRule: (dto: any) => request<any>('policy', '/api/v1/admin/policy/rules', {
    method: 'POST', body: JSON.stringify(dto),
  }),
  updatePolicyRule: (id: string, dto: any) => request<any>('policy', `/api/v1/admin/policy/rules/${id}`, {
    method: 'PUT', body: JSON.stringify(dto),
  }),
  deletePolicyRule: (id: string) => request<void>('policy', `/api/v1/admin/policy/rules/${id}`, {
    method: 'DELETE',
  }),
  policyAuditEntries: (params: Record<string, string | number | undefined> = {}) => {
    const q = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => v != null && v !== '' && q.set(k, String(v)));
    return request<{ content: any[]; totalElements: number; number: number; size: number }>(
      'policy', `/api/v1/admin/policy/audit-entries${q.toString() ? '?' + q.toString() : ''}`
    );
  },

  // Directory
  organizations: () => request<any[]>('directory', '/api/v1/directory/organizations'),
  organization: (id: string) => request<any>('directory', `/api/v1/directory/organizations/${id}`),
  orgEndpoints: (id: string) => request<any[]>('directory', `/api/v1/directory/organizations/${id}/endpoints`),
  nodes: (params: Record<string, string> = {}) => {
    const q = new URLSearchParams(params);
    return request<any[]>('directory', `/api/v1/directory/nodes${q.toString() ? '?' + q.toString() : ''}`);
  },
  node: (id: string) => request<any>('directory', `/api/v1/directory/nodes/${id}`),
  nodeEndpoints: (id: string) => request<any[]>('directory', `/api/v1/directory/nodes/${id}/endpoints`),
  updateEndpoint: (endpointId: string, patch: { url?: string; active?: boolean }) =>
    request<any>('directory', `/api/v1/admin/directory/endpoints/${endpointId}`, {
      method: 'PATCH', body: JSON.stringify(patch),
    }),
  createEndpoint: (dto: {
    endpointId: string; nodeId: string; url: string; modality: string;
    active?: boolean; supportedOperations?: string; timeoutMs?: number;
    healthCheckUrl?: string; certificateAlias?: string;
  }) =>
    request<any>('directory', '/api/v1/admin/directory/endpoints', {
      method: 'POST', body: JSON.stringify(dto),
    }),
  deleteEndpoint: (endpointId: string) =>
    request<void>('directory', `/api/v1/admin/directory/endpoints/${endpointId}`, {
      method: 'DELETE',
    }),
  deleteNode: (nodeId: string) =>
    request<void>('directory', `/api/v1/admin/directory/nodes/${nodeId}`, {
      method: 'DELETE',
    }),
  syncStatus: () => request<any>('directory', '/api/v1/admin/directory/sync/status'),
  triggerSync: () => request<any>('directory', '/api/v1/admin/directory/sync', { method: 'POST' }),
  invalidateAllCache: () => request<any>('directory', '/api/v1/admin/directory/cache/invalidate', { method: 'POST' }),
  invalidateOrgCache: (id: string) => request<any>('directory', `/api/v1/admin/directory/cache/invalidate/${id}`, { method: 'POST' }),

  // Audit (mounted in ingress)
  auditEvents: (params: Record<string, string | number | undefined> = {}) => {
    const q = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => v != null && v !== '' && q.set(k, String(v)));
    return request<{ content: any[]; totalElements: number; number: number; size: number }>(
      'ingress', `/api/v1/admin/audit/events${q.toString() ? '?' + q.toString() : ''}`
    );
  },

  // Health (per service)
  health: (service: 'policy' | 'routing' | 'directory' | 'ingress') =>
    request<any>(service, '/actuator/health'),

  // Prometheus text
  prometheus: async (service: 'policy' | 'routing' | 'directory' | 'ingress') => {
    const url = `${PROXY_PREFIX}/${service}/actuator/prometheus`;
    const r = await fetch(url, { credentials: 'include', headers: { 'Accept': 'text/plain' } });
    if (!r.ok) throw { status: r.status, body: await r.text() } as ApiError;
    return r.text();
  },

  // Env / loggers / configprops
  env: (service: 'policy' | 'routing' | 'directory' | 'ingress') =>
    request<any>(service, '/actuator/env'),
  loggers: (service: 'policy' | 'routing' | 'directory' | 'ingress') =>
    request<any>(service, '/actuator/loggers'),
  setLoggerLevel: (service: 'policy' | 'routing' | 'directory' | 'ingress', name: string, level: string) =>
    request<void>(service, `/actuator/loggers/${name}`, {
      method: 'POST', body: JSON.stringify({ configuredLevel: level }),
    }),
  configProps: (service: 'policy' | 'routing' | 'directory' | 'ingress') =>
    request<any>(service, '/actuator/configprops'),
  info: (service: 'policy' | 'routing' | 'directory' | 'ingress') =>
    request<any>(service, '/actuator/info'),

  // Aggregated observability (rolling window from ingress)
  observability: () =>
    request<Array<{
      service: string; up: boolean;
      rps: number; errorRate: number; avgLatencyMs: number;
      cpuPct: number; memoryUsedMb: number; dbPoolActive: number;
      series: Array<{
        ts: string; rps: number; errorRate: number; avgLatencyMs: number;
        cpuPct: number; memoryUsedMb: number; dbPoolActive: number;
      }>;
    }>>('ingress', '/api/v1/admin/observability/timeseries'),

  // Partner onboarding (mTLS cert)
  partners: (status?: string) =>
    request<Array<{ partnerId: string; orgId: string; name: string; status: string; environment: string }>>(
      'ingress', `/api/v1/admin/partners${status ? '?status=' + encodeURIComponent(status) : ''}`,
    ),
  onboardPartner: (dto: {
    orgId: string; name: string; environment: string; contactEmail: string;
    baaSignedAt?: string; certificatePem: string;
    allowedModalities?: string[]; allowedScopes?: string[]; requestsPerMinute?: number;
  }) => request<any>('ingress', '/api/v1/admin/partners', {
    method: 'POST', body: JSON.stringify(dto),
  }),
  suspendPartner: (partnerId: string, reason?: string) =>
    request<void>('ingress', `/api/v1/admin/partners/${partnerId}`, {
      method: 'DELETE', body: JSON.stringify({ reason: reason || 'Offboarded by operator' }),
    }),

  // TEFCA test calls (go directly through ingress, not the proxy)
  tefca: async (op: 'patient-discovery' | 'document-query' | 'document-retrieve' | 'message-delivery', body: any) => {
    const r = await fetch(`/api/v1/tefca/${op}`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-Correlation-ID': `ui-${Date.now()}`,
        'X-Idempotency-Key': crypto.randomUUID(),
      },
      body: JSON.stringify(body),
    });
    const text = await r.text();
    let parsed: any = text;
    try { parsed = JSON.parse(text); } catch {}
    return { status: r.status, body: parsed };
  },

  // Prior Authorization test calls — CRD (CDS Hooks 2.0), DTR (FHIR R4), PAS (Da Vinci).
  // The admin proxy reuses the operator session for mTLS bypass; in production
  // these endpoints are reached only over partner-cert mTLS at the ALB.
  tefcaPa: async (path: string, body: any) => {
    const r = await fetch(`/api/v1/pa/${path.replace(/^\/+/, '')}`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-Correlation-ID': `ui-${Date.now()}`,
        'X-Idempotency-Key': crypto.randomUUID(),
      },
      body: JSON.stringify(body),
    });
    const text = await r.text();
    let parsed: any = text;
    try { parsed = JSON.parse(text); } catch {}
    return { status: r.status, body: parsed };
  },
};
