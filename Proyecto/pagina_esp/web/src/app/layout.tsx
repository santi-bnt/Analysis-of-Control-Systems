import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'DC Motor Cloud Control Dashboard',
  description: 'Encoder position controller for ESP32 with a local PID loop',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-slate-950 text-slate-100 antialiased selection:bg-indigo-500 selection:text-white">
        {children}
      </body>
    </html>
  );
}
