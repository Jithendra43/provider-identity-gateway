import { FileCheck, Lock, ShieldCheck, Database, Network } from './Icons';

const BADGES = [
  {
    icon: <ShieldCheck size={20} />,
    title: 'HIPAA',
    description:
      'Administrative, physical, and technical safeguards built into how patient information is handled across the platform.',
  },
  {
    icon: <FileCheck size={20} />,
    title: 'Audit-ready reporting',
    description:
      'Pre-built reports and exports cover the questions auditors ask most often, so reviews finish faster with less manual work.',
  },
  {
    icon: <Lock size={20} />,
    title: 'Strong encryption everywhere',
    description:
      'Information is encrypted while it moves between partners and while it sits in storage — no extra configuration required.',
  },
  {
    icon: <Network size={20} />,
    title: 'TEFCA-aligned',
    description:
      'Built around the federal Trusted Exchange Framework so you can participate in nationwide exchange with confidence.',
  },
  {
    icon: <Database size={20} />,
    title: 'Seven-year activity history',
    description:
      'Every exchange and administrative action is preserved in a tamper-evident record for the full retention period regulators expect.',
  },
];

export function ComplianceSection() {
  return (
    <section id="compliance" className="bg-white py-20 sm:py-24">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-2xl text-center">
          <div className="text-xs font-semibold uppercase tracking-wider text-sky-600">
            Compliance
          </div>
          <h2 className="mt-3 text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl">
            Engineered for healthcare regulators
          </h2>
          <p className="mt-4 text-base text-slate-600">
            Every part of the platform is mapped to the controls auditors and exchange partners
            expect — so compliance reviews are routine, not fire drills.
          </p>
        </div>

        <div className="mt-14 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {BADGES.map((b) => (
            <div
              key={b.title}
              className="flex gap-4 rounded-xl border border-slate-200 bg-gradient-to-br from-white to-sky-50/40 p-5 shadow-sm transition-shadow hover:shadow-md"
            >
              <div className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-lg bg-sky-100 text-sky-700">
                {b.icon}
              </div>
              <div>
                <div className="text-sm font-semibold text-slate-900">{b.title}</div>
                <div className="mt-1 text-sm leading-relaxed text-slate-600">{b.description}</div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
