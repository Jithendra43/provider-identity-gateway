'use client';
import { AppShell } from '@/components/AppShell';
import { useState } from 'react';
import { api } from '@/lib/api';
import { Card, PageHeader, Badge, Button, Textarea, Select } from '@/components/ui';

type TefcaOp = 'patient-discovery' | 'document-query' | 'document-retrieve' | 'message-delivery';
type PaOp =
  | 'pa-crd' | 'pa-crd-select' | 'pa-appointment'
  | 'pa-order-dispatch' | 'pa-encounter-start' | 'pa-encounter-discharge'
  | 'dtr/questionnaire-package' | 'dtr/questionnaire-response'
  | 'pas/claim-submit' | 'pas/claim-inquire';
type Op = TefcaOp | PaOp;

const TEFCA_SAMPLES: Record<TefcaOp, any> = {
  'patient-discovery': {
    exchangePurpose: 'TREATMENT',
    patientFirstName: 'Jane',
    patientLastName: 'Doe',
    patientDateOfBirth: '1980-01-15',
    patientGender: 'F',
    patientIdSystem: '2.16.840.1.113883.4.1',
    targetOrgId: 'ORG-QHIN-001',
  },
  'document-query': {
    exchangePurpose: 'TREATMENT',
    patientId: 'patient-mock-001',
    patientIdSystem: '2.16.840.1.113883.4.1',
    targetOrgId: 'ORG-QHIN-001',
    documentType: 'C-CDA',
  },
  'document-retrieve': {
    exchangePurpose: 'TREATMENT',
    documentId: 'doc-001',
    repositoryId: 'REPO-CW-001',
    patientId: 'patient-mock-001',
    targetOrgId: 'ORG-QHIN-001',
  },
  'message-delivery': {
    exchangePurpose: 'TREATMENT',
    targetOrgId: 'ORG-SUB-001',
    messageType: 'DIRECT',
    patientId: 'patient-mock-001',
    messageBody: { subject: 'Smoke test', body: 'Hello from the admin Test Console' },
  },
};

// CDS Hooks 2.0 envelope shared by every CRD hook
const cdsHook = (hook: string) => ({
  hook,
  hookInstance: 'ui-' + Date.now(),
  fhirServer: 'https://example.org/fhir',
  context: {
    userId: 'Practitioner/example',
    patientId: 'patient-mock-001',
    encounterId: 'enc-001',
    draftOrders: { resourceType: 'Bundle', type: 'collection', entry: [] },
  },
});

const PA_SAMPLES: Record<PaOp, any> = {
  'pa-crd':              cdsHook('order-sign'),
  'pa-crd-select':       cdsHook('order-select'),
  'pa-appointment':      cdsHook('appointment-book'),
  'pa-order-dispatch':   cdsHook('order-dispatch'),
  'pa-encounter-start':  cdsHook('encounter-start'),
  'pa-encounter-discharge': cdsHook('encounter-discharge'),
  'dtr/questionnaire-package': {
    resourceType: 'Parameters',
    parameter: [
      { name: 'coverage', valueString: 'Coverage/example' },
      { name: 'order',    valueString: 'ServiceRequest/example' },
    ],
  },
  'dtr/questionnaire-response': {
    resourceType: 'QuestionnaireResponse',
    status: 'completed',
    questionnaire: 'Questionnaire/example',
    item: [{ linkId: '1', text: 'Diagnosis', answer: [{ valueString: 'M54.5' }] }],
  },
  'pas/claim-submit': {
    resourceType: 'Bundle',
    type: 'collection',
    entry: [{ resource: { resourceType: 'Claim', status: 'active', use: 'preauthorization' } }],
  },
  'pas/claim-inquire': {
    resourceType: 'Bundle',
    type: 'collection',
    entry: [{ resource: { resourceType: 'Claim', status: 'active', use: 'preauthorization' } }],
  },
};

const SAMPLES: Record<Op, any> = { ...TEFCA_SAMPLES, ...PA_SAMPLES };

const isPa = (op: Op): op is PaOp => op in PA_SAMPLES;

export default function TestConsolePage() {
  const [op, setOp] = useState<Op>('patient-discovery');
  const [body, setBody] = useState(JSON.stringify(SAMPLES['patient-discovery'], null, 2));
  const [result, setResult] = useState<any | null>(null);
  const [busy, setBusy] = useState(false);

  const swap = (next: Op) => {
    setOp(next);
    setBody(JSON.stringify(SAMPLES[next], null, 2));
    setResult(null);
  };

  const send = async () => {
    setBusy(true);
    setResult(null);
    try {
      const parsed = JSON.parse(body);
      const r = isPa(op) ? await api.tefcaPa(op, parsed) : await api.tefca(op, parsed);
      setResult(r);
    } catch (e: any) {
      setResult({ status: 'client-error', body: e.message || String(e) });
    } finally {
      setBusy(false);
    }
  };

  return (
    <AppShell>
      <PageHeader title="Test Console" description="Issue real TEFCA or Prior Authorization requests through the gateway and see policy decisions live." />
      <div className="grid grid-cols-12 gap-4">
        <div className="col-span-6">
          <Card>
            <div className="mb-3 grid grid-cols-2 gap-2">
              <div>
                <label className="mb-1 block text-xs uppercase text-muted">Operation</label>
                <Select value={op} onChange={(e) => swap(e.target.value as Op)}>
                  <optgroup label="TEFCA Core">
                    <option value="patient-discovery">Patient Discovery</option>
                    <option value="document-query">Document Query</option>
                    <option value="document-retrieve">Document Retrieve</option>
                    <option value="message-delivery">Message Delivery</option>
                  </optgroup>
                  <optgroup label="Prior Authorization — CRD (CDS Hooks)">
                    <option value="pa-crd">order-sign  (POST /api/v1/pa/pa-crd)</option>
                    <option value="pa-crd-select">order-select  (POST /api/v1/pa/pa-crd-select)</option>
                    <option value="pa-appointment">appointment-book  (POST /api/v1/pa/pa-appointment)</option>
                    <option value="pa-order-dispatch">order-dispatch  (POST /api/v1/pa/pa-order-dispatch)</option>
                    <option value="pa-encounter-start">encounter-start  (POST /api/v1/pa/pa-encounter-start)</option>
                    <option value="pa-encounter-discharge">encounter-discharge  (POST /api/v1/pa/pa-encounter-discharge)</option>
                  </optgroup>
                  <optgroup label="Prior Authorization — DTR (FHIR R4)">
                    <option value="dtr/questionnaire-package">questionnaire-package  (POST /api/v1/pa/dtr/questionnaire-package)</option>
                    <option value="dtr/questionnaire-response">questionnaire-response  (POST /api/v1/pa/dtr/questionnaire-response)</option>
                  </optgroup>
                  <optgroup label="Prior Authorization — PAS (Da Vinci)">
                    <option value="pas/claim-submit">claim-submit  (POST /api/v1/pa/pas/claim-submit)</option>
                    <option value="pas/claim-inquire">claim-inquire  (POST /api/v1/pa/pas/claim-inquire)</option>
                  </optgroup>
                </Select>
              </div>
              <div className="flex items-end">
                <Button className="w-full" onClick={send} disabled={busy}>{busy ? 'Sending…' : 'Send'}</Button>
              </div>
            </div>
            {isPa(op) && (
              <div className="mb-3 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">
                <strong>PA endpoints require client mTLS in production.</strong> The admin console
                bypasses mTLS by reusing your operator session, so the call is signed as <code>ORG-CHIT-ADMIN</code>
                rather than a real partner cert. For partner-cert smoke tests use the curl examples in <code>docs/POSTMAN_TESTING.md</code>.
              </div>
            )}
            <label className="mb-1 block text-xs uppercase text-muted">Request body</label>
            <Textarea rows={20} value={body} onChange={(e) => setBody(e.target.value)} />
          </Card>
        </div>
        <div className="col-span-6">
          <Card>
            <h3 className="mb-2 text-sm font-semibold text-fg">Response</h3>
            {!result && <div className="text-xs text-muted">Send a request to see the response.</div>}
            {result && (
              <>
                <div className="mb-2">
                  <Badge tone={typeof result.status === 'number' && result.status < 300 ? 'green' : 'red'}>
                    HTTP {String(result.status)}
                  </Badge>
                </div>
                <pre className="max-h-[60vh] overflow-auto rounded-md border border-border bg-slate-50 p-3 text-xs">
                  {typeof result.body === 'string' ? result.body : JSON.stringify(result.body, null, 2)}
                </pre>
              </>
            )}
          </Card>
        </div>
      </div>
    </AppShell>
  );
}
