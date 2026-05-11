'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useState } from 'react';
import clsx from 'clsx';
import { useAuth } from '@/lib/auth';
import { Spinner } from '@/components/ui';
import {
  Activity,
  ShieldCheck,
  ScrollText,
  Globe,
  ArrowLeftRight,
  FlaskConical,
  FileCheck,
  BarChart3,
  Settings,
  Menu,
  X,
  ChevronDown,
} from '@/components/landing/Icons';

type NavItem = { href: string; label: string; Icon: typeof Activity };
type NavSection = { id: string; label: string; Icon: typeof Activity; items: NavItem[] };

const SECTIONS: NavSection[] = [
  {
    id: 'overview',
    label: 'Overview',
    Icon: Activity,
    items: [{ href: '/dashboard', label: 'Dashboard', Icon: Activity }],
  },
  {
    id: 'governance',
    label: 'Governance',
    Icon: ShieldCheck,
    items: [
      { href: '/policies', label: 'Policies', Icon: ScrollText },
      { href: '/policy-audit', label: 'Policy Audit', Icon: ShieldCheck },
      { href: '/config', label: 'Configuration', Icon: Settings },
    ],
  },
  {
    id: 'exchange',
    label: 'Exchange',
    Icon: ArrowLeftRight,
    items: [
      { href: '/directory', label: 'Directory', Icon: Globe },
      { href: '/transactions', label: 'Transactions', Icon: ArrowLeftRight },
      { href: '/test-console', label: 'Test Console', Icon: FlaskConical },
    ],
  },
  {
    id: 'observability',
    label: 'Observability',
    Icon: BarChart3,
    items: [
      { href: '/metrics', label: 'Metrics', Icon: BarChart3 },
      { href: '/audit', label: 'Audit Trail', Icon: FileCheck },
    ],
  },
];

function findActiveSection(pathname: string | null): NavSection {
  if (pathname) {
    for (const s of SECTIONS) {
      if (s.items.some((i) => pathname === i.href || pathname.startsWith(i.href + '/'))) {
        return s;
      }
    }
  }
  return SECTIONS[0];
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { operator, logout, loading } = useAuth();
  const [navOpen, setNavOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-bg">
        <div className="flex items-center gap-3 text-muted">
          <Spinner />
          <span className="text-sm">Loading console…</span>
        </div>
      </div>
    );
  }
  if (!operator) return null;

  const initials =
    (operator.subject || '?')
      .split(/[@.\s_-]/)
      .filter(Boolean)
      .slice(0, 2)
      .map((s) => s[0]?.toUpperCase() ?? '')
      .join('') || 'U';

  const isActive = (href: string) => pathname === href || pathname?.startsWith(href + '/');
  const activeSection = findActiveSection(pathname);
  const showSidebar = activeSection.items.length > 1;

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/90 shadow-sm backdrop-blur">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between gap-4 px-4 sm:px-6 lg:px-8">
          <Link href="/dashboard" className="flex flex-shrink-0 items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-white p-1 ring-1 ring-slate-200 shadow-sm">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src="/admin/chit-logo.png" alt="C-HIT" className="h-full w-full object-contain" />
            </div>
            <div className="hidden leading-tight sm:block">
              <div className="text-sm font-semibold tracking-tight text-slate-900">
                C-HIT Provider
              </div>
              <div className="text-sm font-semibold tracking-tight text-slate-900">
                Identity Gateway
              </div>
            </div>
          </Link>

          <nav className="hidden flex-1 items-center justify-center gap-1 lg:flex">
            {SECTIONS.map(({ id, label, Icon, items }) => {
              const active = activeSection.id === id;
              const href = items[0].href;
              return (
                <Link
                  key={id}
                  href={href}
                  className={clsx(
                    'inline-flex items-center gap-2 rounded-md px-3.5 py-2 text-sm font-medium transition-colors',
                    active
                      ? 'bg-sky-50 text-sky-700'
                      : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900',
                  )}
                >
                  <Icon size={16} />
                  <span>{label}</span>
                </Link>
              );
            })}
          </nav>

          <div className="flex items-center gap-2">
            <div className="relative hidden sm:block">
              <button
                onClick={() => setMenuOpen((v) => !v)}
                className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-sm font-medium text-slate-700 shadow-sm hover:border-sky-300 hover:bg-sky-50"
              >
                <span className="flex h-7 w-7 items-center justify-center rounded-full bg-sky-600 text-xs font-semibold text-white">
                  {initials}
                </span>
                <span className="hidden max-w-[140px] truncate md:inline">{operator.subject}</span>
                <ChevronDown size={14} />
              </button>
              {menuOpen && (
                <div className="absolute right-0 mt-2 w-72 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl">
                  <div className="border-b border-slate-100 px-4 py-3">
                    <div className="truncate text-sm font-semibold text-slate-900">
                      {operator.subject}
                    </div>
                    <div className="mt-0.5 truncate text-xs text-slate-500">
                      {operator.orgId || '—'} · {operator.nodeId || '—'}
                    </div>
                    <div className="mt-2 flex flex-wrap gap-1">
                      {(operator.roles || []).map((r) => (
                        <span
                          key={r}
                          className="rounded-full bg-sky-50 px-2 py-0.5 text-[10px] font-medium text-sky-700 ring-1 ring-sky-200"
                        >
                          {r}
                        </span>
                      ))}
                    </div>
                  </div>
                  <button
                    onClick={() => {
                      setMenuOpen(false);
                      logout();
                    }}
                    className="block w-full px-4 py-2.5 text-left text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50 hover:text-slate-900"
                  >
                    Sign out
                  </button>
                </div>
              )}
            </div>

            <button
              onClick={() => setNavOpen((v) => !v)}
              className="inline-flex items-center justify-center rounded-md p-2 text-slate-600 hover:bg-slate-100 lg:hidden"
              aria-label={navOpen ? 'Close menu' : 'Open menu'}
            >
              {navOpen ? <X size={22} /> : <Menu size={22} />}
            </button>
          </div>
        </div>

        {navOpen && (
          <div className="border-t border-slate-200 bg-white lg:hidden">
            <div className="mx-auto max-w-7xl space-y-4 px-4 py-3 sm:px-6">
              {SECTIONS.map((section) => (
                <div key={section.id}>
                  <div className="px-3 pb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
                    {section.label}
                  </div>
                  {section.items.map(({ href, label, Icon }) => {
                    const active = isActive(href);
                    return (
                      <Link
                        key={href}
                        href={href}
                        onClick={() => setNavOpen(false)}
                        className={clsx(
                          'flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium',
                          active
                            ? 'bg-sky-50 text-sky-700'
                            : 'text-slate-700 hover:bg-slate-100',
                        )}
                      >
                        <Icon size={16} />
                        {label}
                      </Link>
                    );
                  })}
                </div>
              ))}
              <div className="mt-3 border-t border-slate-100 pt-3">
                <div className="px-3 py-1 text-xs font-medium text-slate-500">
                  {operator.subject}
                </div>
                <button
                  onClick={() => {
                    setNavOpen(false);
                    logout();
                  }}
                  className="block w-full rounded-md px-3 py-2 text-left text-sm font-medium text-slate-700 hover:bg-slate-100"
                >
                  Sign out
                </button>
              </div>
            </div>
          </div>
        )}
      </header>

      <div className="mx-auto flex max-w-7xl gap-6 px-4 py-8 sm:px-6 lg:px-8">
        {showSidebar && (
          <aside className="hidden w-56 flex-shrink-0 lg:block">
            <div className="sticky top-24">
              <div className="px-3 pb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
                {activeSection.label}
              </div>
              <nav className="space-y-0.5">
                {activeSection.items.map(({ href, label, Icon }) => {
                  const active = isActive(href);
                  return (
                    <Link
                      key={href}
                      href={href}
                      className={clsx(
                        'flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                        active
                          ? 'bg-sky-50 text-sky-700'
                          : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900',
                      )}
                    >
                      <Icon size={16} />
                      <span>{label}</span>
                    </Link>
                  );
                })}
              </nav>
            </div>
          </aside>
        )}
        <main className="min-w-0 flex-1 animate-fade-in">{children}</main>
      </div>
    </div>
  );
}
