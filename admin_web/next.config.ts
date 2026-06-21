import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  allowedDevOrigins: ['192.168.1.17', 'localhost:3000'],
  experimental: {
    proxyClientMaxBodySize: '200mb'
  }
};

export default nextConfig;
