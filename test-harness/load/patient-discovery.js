import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

// TEFCA QHIN Tech Framework §6 performance targets:
//   - Patient Discovery p95 ≤ 500ms (synchronous fan-out path)
//   - Error rate < 1%
// We hit the ingress-auth-service directly with a pre-issued partner JWT;
// the JWT is loaded from the SMOKE_JWT env var to avoid re-running the
// token-exchange flow on every iteration.

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT      = __ENV.SMOKE_JWT;
if (!JWT) { throw new Error('SMOKE_JWT env var is required'); }

const lookupLatency = new Trend('patient_discovery_latency', true);

export const options = {
  scenarios: {
    steady_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20  },  // ramp
        { duration: '2m',  target: 50  },  // sustained
        { duration: '1m',  target: 100 },  // peak
        { duration: '30s', target: 0   },  // cooldown
      ],
      gracefulRampDown: '15s',
    },
  },
  thresholds: {
    http_req_duration:        ['p(95)<500', 'p(99)<1500'],
    http_req_failed:          ['rate<0.01'],
    patient_discovery_latency:['p(95)<500'],
  },
};

const PAYLOAD = JSON.stringify({
  resourceType: 'Parameters',
  parameter: [
    { name: 'patient.identifier', valueIdentifier: { system: 'urn:oid:2.16.840.1.113883.4.1', value: '123-45-6789' }},
    { name: 'patient.given',      valueString: 'Jane' },
    { name: 'patient.family',     valueString: 'Doe' },
    { name: 'patient.birthDate',  valueDate: '1980-05-12' },
    { name: 'targetOrganizationId', valueString: 'org-test-partner-2' },
  ],
});

export default function () {
  const res = http.post(`${BASE_URL}/api/v1/tefca/patient_discovery`, PAYLOAD, {
    headers: {
      'Authorization': `Bearer ${JWT}`,
      'Content-Type':  'application/fhir+json',
      'X-Correlation-Id': `k6-${__VU}-${__ITER}`,
      'X-Purpose-of-Use': 'TREATMENT',
    },
  });

  lookupLatency.add(res.timings.duration);

  check(res, {
    'status 200':        (r) => r.status === 200,
    'json body':         (r) => (r.headers['Content-Type'] || '').includes('json'),
    'no PHI in error':   (r) => !/123-45-6789|Doe|Jane/.test(r.body || ''),
  });

  sleep(0.5 + Math.random());  // jitter to avoid lockstep waves
}
