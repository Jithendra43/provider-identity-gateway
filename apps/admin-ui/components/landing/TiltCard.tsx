'use client';

import { useTilt } from '@/lib/use-tilt';

export function TiltCard({
  icon,
  title,
  description,
}: {
  icon: React.ReactNode;
  title: string;
  description: string;
}) {
  const { ref, style, onMouseMove, onMouseLeave } = useTilt(7);
  return (
    <div
      ref={ref}
      onMouseMove={onMouseMove}
      onMouseLeave={onMouseLeave}
      style={{ ...style, transformStyle: 'preserve-3d' }}
      className="group relative rounded-2xl border border-slate-200 bg-white p-6 shadow-lg transition-shadow hover:border-sky-200 hover:shadow-xl"
    >
      <div
        className="pointer-events-none absolute inset-x-0 top-0 h-1 rounded-t-2xl bg-gradient-to-r from-sky-400 via-sky-500 to-blue-600 opacity-0 transition-opacity group-hover:opacity-100"
        style={{ transform: 'translateZ(20px)' }}
      />
      <div
        className="mb-4 inline-flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-sky-50 to-sky-100 text-sky-600 ring-1 ring-sky-200/60"
        style={{ transform: 'translateZ(40px)' }}
      >
        {icon}
      </div>
      <h3
        className="text-base font-semibold tracking-tight text-slate-900"
        style={{ transform: 'translateZ(30px)' }}
      >
        {title}
      </h3>
      <p
        className="mt-2 text-sm leading-relaxed text-slate-600"
        style={{ transform: 'translateZ(20px)' }}
      >
        {description}
      </p>
    </div>
  );
}
