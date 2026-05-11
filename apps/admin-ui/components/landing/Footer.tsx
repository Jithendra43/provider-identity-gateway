import Link from 'next/link';

const COLS = [
  {
    title: 'Platform',
    items: [
      { label: 'Partner connections', href: '#capabilities' },
      { label: 'Single sign-on', href: '#capabilities' },
      { label: 'Access policies', href: '#capabilities' },
      { label: 'Partner directory', href: '#capabilities' },
    ],
  },
  {
    title: 'Compliance',
    items: [
      { label: 'TEFCA alignment', href: '#compliance' },
      { label: 'HIPAA', href: '#compliance' },
      { label: 'Audit-ready reporting', href: '#compliance' },
      { label: 'Seven-year history', href: '#compliance' },
    ],
  },
  {
    title: 'Company',
    items: [
      { label: 'About C-HIT', href: '#contact' },
      { label: 'Documentation', href: '#standards' },
      { label: 'Support', href: 'mailto:ops@c-hit.com' },
      { label: 'Security', href: 'mailto:security@c-hit.com' },
    ],
  },
  {
    title: 'Resources',
    items: [
      { label: 'Admin Console', href: '/login' },
      { label: 'Status', href: '#' },
      { label: 'Privacy Policy', href: '#' },
      { label: 'Terms of Service', href: '#' },
    ],
  },
];

export function Footer() {
  return (
    <footer className="border-t border-slate-200 bg-slate-950 text-slate-300">
      <div className="mx-auto max-w-7xl px-4 py-14 sm:px-6 lg:px-8">
        <div className="grid gap-10 lg:grid-cols-5">
          <div className="lg:col-span-2">
            <Link href="/" className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-white p-1 ring-1 ring-white/20">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src="/admin/chit-logo.png" alt="C-HIT" className="h-full w-full object-contain" />
              </div>
              <div className="leading-tight">
                <div className="text-sm font-semibold text-white">C-HIT Provider</div>
                <div className="text-sm font-semibold text-white">Identity Gateway</div>
              </div>
            </Link>
            <p className="mt-4 max-w-sm text-sm leading-relaxed text-slate-400">
              A secure platform for connecting healthcare organizations and exchanging patient
              information with confidence.
            </p>
          </div>

          {COLS.map((col) => (
            <div key={col.title}>
              <div className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                {col.title}
              </div>
              <ul className="mt-4 space-y-2">
                {col.items.map((it) => (
                  <li key={it.label}>
                    <Link
                      href={it.href}
                      className="text-sm text-slate-300 transition-colors hover:text-white"
                    >
                      {it.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        <div className="mt-12 flex flex-col items-start justify-between gap-3 border-t border-white/10 pt-6 text-xs text-slate-400 sm:flex-row sm:items-center">
          <div>&copy; {new Date().getFullYear()} C-HIT. All rights reserved.</div>
          <div className="flex flex-wrap gap-4">
            <span>TEFCA-aligned</span>
            <span>HIPAA-ready</span>
            <span>Encrypted end-to-end</span>
          </div>
        </div>
      </div>
    </footer>
  );
}
