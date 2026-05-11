import './globals.css';
import type { Metadata } from 'next';
import { AuthProvider } from '@/lib/auth';

export const metadata: Metadata = {
  title: 'C-HIT Provider Identity Gateway',
  description:
    'A secure platform for connecting healthcare organizations and exchanging patient information with confidence.',
  icons: {
    icon: '/admin/chit-logo.png',
    shortcut: '/admin/chit-logo.png',
    apple: '/admin/chit-logo.png',
  },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="" />
        <link
          href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap"
          rel="stylesheet"
        />
      </head>
      <body className="bg-bg text-fg font-sans antialiased">
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
