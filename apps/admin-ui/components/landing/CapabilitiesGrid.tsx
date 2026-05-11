import { TiltCard } from './TiltCard';
import {
  KeyRound,
  ShieldCheck,
  ScrollText,
  Globe,
  ArrowLeftRight,
  Database,
} from './Icons';

const CAPABILITIES = [
  {
    icon: <KeyRound size={22} />,
    title: 'Verified partner connections',
    description:
      'Every connecting partner is verified using a digital certificate before any data is exchanged, so you always know who is on the other end.',
  },
  {
    icon: <ShieldCheck size={22} />,
    title: 'Single sign-on for staff',
    description:
      'Your team signs in once with the credentials they already use. No new passwords, no shared logins, full visibility into who did what.',
  },
  {
    icon: <ScrollText size={22} />,
    title: 'Plain-language access policies',
    description:
      'Decide who can see what with simple, reviewable rules. Every allow or deny decision is logged with a reason you can show an auditor.',
  },
  {
    icon: <Globe size={22} />,
    title: 'Connected partner directory',
    description:
      'A live address book of every partner you exchange with, with built-in health checks so requests always reach a healthy endpoint.',
  },
  {
    icon: <ArrowLeftRight size={22} />,
    title: 'Real-time exchange monitor',
    description:
      'Watch traffic move through the platform as it happens. Inspect a request, check response times, and resolve issues without leaving the console.',
  },
  {
    icon: <Database size={22} />,
    title: 'Complete audit history',
    description:
      'Every action and exchange is recorded in a tamper-evident log retained for seven years, ready for compliance reviews and investigations.',
  },
];

export function CapabilitiesGrid() {
  return (
    <section id="capabilities" className="bg-white py-20 sm:py-24">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-2xl text-center">
          <div className="text-xs font-semibold uppercase tracking-wider text-sky-600">
            Platform
          </div>
          <h2 className="mt-3 text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl">
            Everything you need to exchange health data
          </h2>
          <p className="mt-4 text-base text-slate-600">
            Six capabilities your team will actually use — designed to make secure exchange the
            easy choice for everyone in your organization.
          </p>
        </div>

        <div className="mt-14 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {CAPABILITIES.map((c) => (
            <TiltCard key={c.title} icon={c.icon} title={c.title} description={c.description} />
          ))}
        </div>
      </div>
    </section>
  );
}
