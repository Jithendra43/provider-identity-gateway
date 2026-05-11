import { ShieldCheck } from './Icons';

const STANDARDS = [
  { k: 'TEFCA', v: 'Aligned with the federal trust framework for health data exchange.' },
  { k: 'HL7 FHIR', v: 'Speaks the modern healthcare standard your partners already use.' },
  { k: 'HIPAA', v: 'Privacy and security safeguards built into every workflow.' },
  { k: 'Single sign-on', v: 'Works with the identity provider your staff already trust.' },
  { k: 'End-to-end encryption', v: 'Protects information in transit and at rest by default.' },
];

export function StandardsSection() {
  return (
    <section id="standards" className="bg-slate-50 py-20 sm:py-24">
      <div className="mx-auto grid max-w-7xl gap-12 px-4 sm:px-6 lg:grid-cols-2 lg:gap-16 lg:px-8">
        <div>
          <div className="text-xs font-semibold uppercase tracking-wider text-sky-600">
            Built on Healthcare Standards
          </div>
          <h2 className="mt-3 text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl">
            Built on standards, not lock-in
          </h2>
          <p className="mt-4 text-base text-slate-600">
            We use the same standards your partners already use, so connecting to a new
            organization is a straightforward conversation — not a custom integration project.
          </p>
          <ul className="mt-8 space-y-4">
            {STANDARDS.map((s) => (
              <li key={s.k} className="flex gap-3">
                <span className="mt-0.5 flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-full bg-emerald-100 text-emerald-600">
                  <ShieldCheck size={14} />
                </span>
                <div>
                  <div className="text-sm font-semibold text-slate-900">{s.k}</div>
                  <div className="text-sm text-slate-600">{s.v}</div>
                </div>
              </li>
            ))}
          </ul>
        </div>

        <div className="rounded-2xl border border-slate-800 bg-slate-900 p-1 shadow-2xl">
          <div className="flex items-center gap-1.5 px-3 py-2">
            <span className="h-3 w-3 rounded-full bg-red-400" />
            <span className="h-3 w-3 rounded-full bg-amber-400" />
            <span className="h-3 w-3 rounded-full bg-emerald-400" />
            <span className="ml-3 font-mono text-[10px] text-slate-400">
              activity feed
            </span>
          </div>
          <pre className="overflow-x-auto rounded-xl bg-slate-950 p-5 font-mono text-[12px] leading-6 text-slate-200">
{`12:04:01   Partner connected            Acme Health Network
12:04:01   Identity verified             certificate valid through 2027
12:04:01   Access policy applied         Patient lookup — allowed
12:04:01   Patient record requested      reason: care coordination
12:04:01   Response delivered            156 ms
12:04:01   Activity recorded             audit history updated
`}
          </pre>
        </div>
      </div>
    </section>
  );
}
