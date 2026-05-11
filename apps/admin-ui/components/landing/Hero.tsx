import Link from 'next/link';
import { ArrowRight, ShieldCheck } from './Icons';

export function Hero() {
  return (
    <section className="relative overflow-hidden bg-gradient-to-br from-sky-50 via-white to-sky-100">
      <div className="pointer-events-none absolute -left-24 -top-24 h-96 w-96 rounded-full bg-sky-300/30 blur-3xl" />
      <div className="pointer-events-none absolute -bottom-32 -right-24 h-[28rem] w-[28rem] rounded-full bg-blue-400/20 blur-3xl" />

      <div className="relative mx-auto flex max-w-7xl flex-col items-center px-4 py-20 text-center sm:px-6 lg:py-28">
        <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-sky-200 bg-white/80 px-4 py-1.5 text-xs font-medium text-sky-700 shadow-sm backdrop-blur">
          <ShieldCheck size={14} />
          Trusted health information exchange
        </div>

        <h1 className="max-w-4xl text-4xl font-bold tracking-tight text-slate-900 sm:text-5xl lg:text-6xl">
          C-HIT Provider Identity Gateway
        </h1>

        <p className="mt-6 max-w-2xl text-base leading-relaxed text-slate-600 sm:text-lg">
          A secure way to connect with healthcare partners and exchange patient information.
          Strong identity verification, clear access controls, and a complete activity history
          you can hand to auditors at any time.
        </p>

        <div className="mt-10 flex flex-col items-center gap-3 sm:flex-row">
          <a
            href="/oauth2/authorization/cognito"
            className="inline-flex items-center gap-2 rounded-lg bg-sky-600 px-6 py-3 text-sm font-semibold text-white shadow-md transition-all hover:bg-sky-700 hover:shadow-lg"
          >
            Open Admin Console
            <ArrowRight size={16} />
          </a>
          <a
            href="#capabilities"
            className="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-6 py-3 text-sm font-semibold text-slate-700 shadow-sm transition-colors hover:border-sky-300 hover:text-sky-700"
          >
            Explore Capabilities
          </a>
        </div>

        <dl className="mt-16 grid w-full max-w-3xl grid-cols-2 gap-6 sm:grid-cols-4">
          {[
            { v: 'Sub-second', l: 'Partner connection' },
            { v: '99.95%', l: 'Service uptime' },
            { v: 'Encrypted', l: 'End-to-end' },
            { v: '7 yrs', l: 'Audit history' },
          ].map((s) => (
            <div key={s.l} className="text-center">
              <dt className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
                {s.v}
              </dt>
              <dd className="mt-1 text-xs font-medium uppercase tracking-wider text-slate-500">
                {s.l}
              </dd>
            </div>
          ))}
        </dl>
      </div>
    </section>
  );
}
