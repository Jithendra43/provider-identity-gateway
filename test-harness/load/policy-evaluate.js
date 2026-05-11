import http from 'k6/http';
import { check } from 'k6';

// Policy evaluate is on the synchronous critical path of every TEFCA
// transaction. SLO: p95 ≤ 50ms. This script targets the policy-service
// directly, bypassing ingress, to measure pure decision latency.

const BASE_URL = __ENV.POLICY_URL || 'http://localhost:8081';

export const options = {
  scenarios: {
    decision_storm: {
      executor: 'constant-arrival-rate',
      rate: 1000,         // 1k req/s — saturate decision cache
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs: 100,
      maxVUs: 400,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<50', 'p(99)<200'],
    http_req_failed:   ['rate<0.005'],
  },
};

const PAYLOAD = JSON.stringify({
  requesterOrganizationId: 'org-partner-1',
  requesterNodeId:         'node-alpha',
  targetOrganizationId:    'org-partner-2',
  exchangePurpose:         'TREATMENT',
  resourceType:            'patient_discovery',
});

export default function () {
  const res = http.post(`${BASE_URL}/api/v1/policy/evaluate`, PAYLOAD, {
    headers: { 'Content-Type': 'application/json' },
  });

  check(res, {
    'status 200':                 (r) => r.status === 200,
    'decision PERMIT|DENY|INDETERMINATE': (r) => {
      try {
        const d = JSON.parse(r.body).decision;
        return d === 'PERMIT' || d === 'DENY' || d === 'INDETERMINATE';
      } catch { return false; }
    },
  });
}
