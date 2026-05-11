import clsx from 'clsx';
import { ButtonHTMLAttributes, InputHTMLAttributes, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react';

export function Button({
  variant = 'primary',
  size = 'md',
  className,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'danger' | 'ghost' | 'outline'; size?: 'sm' | 'md' | 'lg' }) {
  const variants = {
    primary: 'bg-accent text-white shadow-soft hover:bg-accentDark hover:shadow-hover active:scale-[0.98]',
    secondary: 'bg-white text-fg border border-border shadow-soft hover:bg-slate-50 hover:border-slate-300',
    outline: 'bg-transparent text-accent border border-accent/30 hover:bg-accentLight',
    danger: 'bg-danger text-white shadow-soft hover:bg-red-700 active:scale-[0.98]',
    ghost: 'text-slate-600 hover:bg-slate-100 hover:text-fg',
  };
  const sizes = {
    sm: 'px-2.5 py-1 text-xs gap-1',
    md: 'px-3.5 py-2 text-sm gap-1.5',
    lg: 'px-5 py-2.5 text-sm font-semibold gap-2',
  };
  return (
    <button
      className={clsx(
        'inline-flex items-center justify-center rounded-lg font-medium transition-all duration-150 focus-ring disabled:opacity-50 disabled:cursor-not-allowed disabled:shadow-none disabled:scale-100',
        variants[variant], sizes[size], className
      )}
      {...props}
    />
  );
}

export function Input({ className, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className={clsx(
        'w-full rounded-lg border border-border bg-white px-3 py-2 text-sm text-fg shadow-soft placeholder:text-subtle transition-colors focus:outline-none focus:border-accent focus:shadow-focus',
        className
      )}
      {...props}
    />
  );
}

export function Textarea({ className, ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      className={clsx(
        'w-full rounded-lg border border-border bg-white px-3 py-2 text-sm text-fg font-mono shadow-soft placeholder:text-subtle transition-colors focus:outline-none focus:border-accent focus:shadow-focus',
        className
      )}
      {...props}
    />
  );
}

export function Select({ className, children, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      className={clsx(
        'w-full rounded-lg border border-border bg-white px-3 py-2 text-sm text-fg shadow-soft transition-colors focus:outline-none focus:border-accent focus:shadow-focus',
        className
      )}
      {...props}
    >
      {children}
    </select>
  );
}

export function Card({ className, children, hover = false }: { className?: string; children: React.ReactNode; hover?: boolean }) {
  return (
    <div className={clsx('rounded-xl border border-border bg-panel p-5 shadow-card transition-shadow', hover && 'hover:shadow-hover', className)}>
      {children}
    </div>
  );
}

export function Badge({ tone = 'gray', children, className }: { tone?: 'gray' | 'green' | 'red' | 'amber' | 'blue'; children: React.ReactNode; className?: string }) {
  const tones = {
    gray: 'bg-slate-100 text-slate-700 border border-slate-200',
    green: 'bg-successLight text-success border border-emerald-200',
    red: 'bg-dangerLight text-danger border border-red-200',
    amber: 'bg-warnLight text-warn border border-amber-200',
    blue: 'bg-accentLight text-accent border border-blue-200',
  };
  return <span className={clsx('inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium', tones[tone], className)}>{children}</span>;
}

export function StatTile({ label, value, hint, tone = 'blue' }: { label: string; value: string | number; hint?: string; tone?: 'green' | 'red' | 'amber' | 'blue' }) {
  const accentBar = {
    green: 'from-emerald-500 to-emerald-300',
    red: 'from-red-500 to-red-300',
    amber: 'from-amber-500 to-amber-300',
    blue: 'from-blue-600 to-blue-400',
  };
  const text = {
    green: 'text-success',
    red: 'text-danger',
    amber: 'text-warn',
    blue: 'text-accent',
  };
  return (
    <div className="relative overflow-hidden rounded-xl border border-border bg-panel p-5 shadow-card transition-shadow hover:shadow-hover">
      <div className={clsx('absolute inset-x-0 top-0 h-1 bg-gradient-to-r', accentBar[tone])} />
      <div className="text-xs font-medium uppercase tracking-wider text-muted">{label}</div>
      <div className={clsx('mt-2 text-3xl font-bold tabular-nums tracking-tight', text[tone])}>{value}</div>
      {hint && <div className="mt-1 text-xs text-subtle">{hint}</div>}
    </div>
  );
}

export function PageHeader({ title, description, actions }: { title: string; description?: string; actions?: React.ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-start justify-between gap-4 border-b border-border pb-5">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-fg">{title}</h1>
        {description && <p className="mt-1.5 text-sm text-muted">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

export function EmptyState({ message }: { message: string }) {
  return (
    <Card className="text-center">
      <div className="py-10 text-sm text-muted">{message}</div>
    </Card>
  );
}

export function Spinner({ size = 'md', className }: { size?: 'sm' | 'md' | 'lg'; className?: string }) {
  const dim = { sm: 'h-3 w-3 border', md: 'h-5 w-5 border-2', lg: 'h-8 w-8 border-2' }[size];
  return <div className={clsx(dim, 'inline-block animate-spin rounded-full border-accent border-t-transparent', className)} role="status" aria-label="Loading" />;
}
