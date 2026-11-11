/** @type {import('next').NextConfig} */

const nextConfig = {
  productionBrowserSourceMaps: process.env.NODE_ENV !== 'production',
  output: 'standalone',
  // eslint: { ignoreDuringBuilds: true },
}

module.exports = nextConfig
