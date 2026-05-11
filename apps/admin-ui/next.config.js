/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'export',
  basePath: '/admin',
  assetPrefix: '/admin',
  trailingSlash: true,
  images: { unoptimized: true },
  reactStrictMode: true,
  // During `next dev` proxy /api -> ingress on 8080 so login/proxy work locally.
  async rewrites() {
    return [];
  },
};

module.exports = nextConfig;
