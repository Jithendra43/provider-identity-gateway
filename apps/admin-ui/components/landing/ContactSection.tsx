import Link from 'next/link';
import { ArrowRight, Mail } from './Icons';

export function ContactSection() {
  return (
    <section id="contact" className="relative overflow-hidden bg-gradient-to-br from-sky-600 to-blue-700 py-20">
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,rgba(255,255,255,0.18),transparent_60%)]" />
      <div className="relative mx-auto grid max-w-6xl gap-10 px-4 sm:px-6 lg:grid-cols-2 lg:items-center lg:px-8">
        <div className="text-white">
          <div className="text-xs font-semibold uppercase tracking-wider text-sky-100">Contact</div>
          <h2 className="mt-3 text-3xl font-bold tracking-tight sm:text-4xl">
            Ready to connect your organization?
          </h2>
          <p className="mt-4 max-w-xl text-base text-sky-50/90">
            Onboarding, day-to-day support, and security questions are handled by the C-HIT
            operations team. Most new partners are exchanging information within a couple of
            weeks of getting started.
          </p>
        </div>

        <div className="rounded-2xl bg-white p-8 shadow-2xl">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-sky-100 text-sky-700">
              <Mail size={20} />
            </div>
            <div>
              <div className="text-sm font-semibold text-slate-900">Operations</div>
              <a href="mailto:ops@c-hit.com" className="text-sm text-sky-700 hover:underline">
                ops@c-hit.com
              </a>
            </div>
          </div>
          <div className="my-6 h-px bg-slate-200" />
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-emerald-100 text-emerald-700">
              <Mail size={20} />
            </div>
            <div>
              <div className="text-sm font-semibold text-slate-900">Security disclosures</div>
              <a href="mailto:security@c-hit.com" className="text-sm text-emerald-700 hover:underline">
                security@c-hit.com
              </a>
            </div>
          </div>
          <a
            href="/oauth2/authorization/cognito"
            className="mt-8 inline-flex w-full items-center justify-center gap-2 rounded-lg bg-sky-600 px-5 py-3 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-sky-700"
          >
            Open Admin Console
            <ArrowRight size={16} />
          </a>
        </div>
      </div>
    </section>
  );
}
