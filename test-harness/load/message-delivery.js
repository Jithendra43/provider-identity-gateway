import http from 'k6/http';
import { check, sleep } from 'k6';

// Message Delivery is the asynchronous fan-out path: ingress accepts and
// returns 202 quickly; downstream routing handles the multi-recipient
// fan-out. SLO: p95 ingress-side accept latency ≤ 250ms; backpressure
// signalled via 429 with Retry-After.

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT      = __ENV.SMOKE_JWT;
if (!JWT) { throw new Error('SMOKE_JWT env var is required'); }

export const options = {
  scenarios: {
    burst: {
      executor: 'constant-arrival-rate',
      rate: 200,
      timeUnit: '1s',
      duration: '3m',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<250'],
    http_req_failed:   ['rate<0.01'],
    'http_reqs{status:202}': ['count>0'],  // confirm async accepts
  },
};

const RECIPIENTS = ['org-partner-1', 'org-partner-2', 'org-partner-3'];

function payload(vu, iter) {
  return JSON.stringify({
    resourceType: 'Bundle',
    type: 'message',
    identifier: { value: `msg-${vu}-${iter}` },
    entry: [{
      resource: {
        resourceType: 'MessageHeader',
        eventCoding: { system: 'urn:tefca:message-types', code: 'document.delivery' },
        destination: RECIPIENTS.map(r => ({ name: r, endpoint: `urn:tefca:org:${r}` })),
      },
    }],
  });
}

export default function () {
  const res = http.post(`${BASE_URL}/api/v1/tefca/message_delivery`, payload(__VU, __ITER), {
    headers: {
      'Authorization': `Bearer ${JWT}`,
      'Content-Type':  'application/fhir+json',
      'X-Correlation-Id': `k6-md-${__VU}-${__ITER}`,
      'X-Purpose-of-Use': 'TREATMENT',
    },
  });

  check(res, {
    'accepted 202 or 429':     (r) => r.status === 202 || r.status === 429,
    'Retry-After on 429':      (r) => r.status !== 429 || !!r.headers['Retry-After'],
  });

  sleep(0.1);
}
