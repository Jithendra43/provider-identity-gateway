import type { Config } from 'tailwindcss';

const config: Config = {
  content: [
    './app/**/*.{ts,tsx}',
    './components/**/*.{ts,tsx}',
    './lib/**/*.{ts,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        bg: '#f8fafc',
        panel: '#ffffff',
        elevated: '#ffffff',
        border: '#e2e8f0',
        fg: '#0f172a',
        muted: '#64748b',
        subtle: '#94a3b8',
        accent: '#2563eb',
        accentDark: '#1d4ed8',
        accentLight: '#dbeafe',
        success: '#059669',
        successLight: '#d1fae5',
        warn: '#d97706',
        warnLight: '#fef3c7',
        danger: '#dc2626',
        dangerLight: '#fee2e2',
      },
      boxShadow: {
        soft: '0 1px 2px 0 rgba(15,23,42,0.04), 0 1px 3px 0 rgba(15,23,42,0.06)',
        card: '0 1px 3px rgba(15,23,42,0.06), 0 1px 2px rgba(15,23,42,0.04)',
        hover: '0 4px 12px rgba(15,23,42,0.08), 0 2px 4px rgba(15,23,42,0.04)',
        focus: '0 0 0 3px rgba(37,99,235,0.15)',
        modal: '0 25px 50px -12px rgba(15,23,42,0.25)',
      },
      fontFamily: {
        sans: ['"Inter"', 'ui-sans-serif', 'system-ui', '-apple-system', 'Segoe UI', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'SF Mono', 'Menlo', 'monospace'],
      },
      backgroundImage: {
        'sidebar-gradient': 'linear-gradient(180deg, #0f172a 0%, #1e3a8a 100%)',
        'login-gradient': 'linear-gradient(135deg, #2563eb 0%, #1e40af 50%, #0f172a 100%)',
      },
      keyframes: {
        'fade-in': { '0%': { opacity: '0', transform: 'translateY(4px)' }, '100%': { opacity: '1', transform: 'translateY(0)' } },
      },
      animation: {
        'fade-in': 'fade-in 200ms ease-out',
      },
    },
  },
  plugins: [],
};

export default config;
