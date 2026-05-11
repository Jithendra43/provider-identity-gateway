'use client';

import Link from 'next/link';
import { useState } from 'react';
import { Menu, X } from './Icons';

const LINKS = [
  { href: '#capabilities', label: 'Platform' },
  { href: '#standards', label: 'Standards' },
  { href: '#compliance', label: 'Compliance' },
  { href: '#contact', label: 'Contact' },
];

export function TopNavPublic() {
  const [open, setOpen] = useState(false);

  return (
    <header className="sticky top-0 z-40 w-full border-b border-slate-200/70 bg-white/85 backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        <Link href="/" className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-white p-1 ring-1 ring-slate-200 shadow-sm">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src="/admin/chit-logo.png" alt="C-HIT" className="h-full w-full object-contain" />
          </div>
          <div className="leading-tight">
            <div className="text-sm font-semibold tracking-tight text-slate-900">C-HIT Provider</div>
            <div className="text-sm font-semibold tracking-tight text-slate-900">Identity Gateway</div>
          </div>
        </Link>

        <nav className="hidden items-center gap-1 md:flex">
          {LINKS.map((l) => (
            <a
              key={l.href}
              href={l.href}
              className="rounded-md px-3 py-2 text-sm font-medium text-slate-600 transition-colors hover:bg-sky-50 hover:text-sky-700"
            >
              {l.label}
            </a>
          ))}
        </nav>

        <div className="hidden md:block">
          <a
            href="/oauth2/authorization/cognito"
            className="inline-flex items-center rounded-lg bg-sky-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-sky-700"
          >
            Sign in
          </a>
        </div>

        <button
          onClick={() => setOpen((v) => !v)}
          className="inline-flex items-center justify-center rounded-md p-2 text-slate-600 hover:bg-slate-100 md:hidden"
          aria-label={open ? 'Close menu' : 'Open menu'}
        >
          {open ? <X size={22} /> : <Menu size={22} />}
        </button>
      </div>

      {open && (
        <div className="border-t border-slate-200 bg-white md:hidden">
          <div className="space-y-1 px-4 py-3">
            {LINKS.map((l) => (
              <a
                key={l.href}
                href={l.href}
                onClick={() => setOpen(false)}
                className="block rounded-md px-3 py-2 text-sm font-medium text-slate-700 hover:bg-sky-50 hover:text-sky-700"
              >
                {l.label}
              </a>
            ))}
            <a
              href="/oauth2/authorization/cognito"
              className="mt-2 block rounded-lg bg-sky-600 px-3 py-2.5 text-center text-sm font-semibold text-white hover:bg-sky-700"
            >
              Sign in
            </a>
          </div>
        </div>
      )}
    </header>
  );
}
