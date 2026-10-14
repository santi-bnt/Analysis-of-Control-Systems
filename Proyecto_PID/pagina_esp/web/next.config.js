/** @type {import('next').NextConfig} */
const nextConfig = {
  output: process.env.FIREBASE_EXPORT === 'true' ? 'export' : 'standalone',
  reactStrictMode: true,
};

module.exports = nextConfig;
