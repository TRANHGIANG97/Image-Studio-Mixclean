import { NextRequest, NextResponse } from 'next/server';
import { R2_PUBLIC_BASE } from '@/lib/cdn-rewriter';

/**
 * CORS proxy for the browser canvas editor.
 *
 * SECURITY: this endpoint fetches server-side, so it MUST NOT be an open
 * proxy (SSRF / bandwidth abuse). Only whitelisted asset hosts are allowed:
 * the R2 public bucket plus any hosts in PROXY_ALLOWED_HOSTS (comma-separated).
 */
function buildAllowedHosts(): Set<string> {
  const hosts = new Set<string>();

  try {
    hosts.add(new URL(R2_PUBLIC_BASE).hostname);
  } catch {
    // ignore malformed constant
  }

  const cdnUrl = process.env.NEXT_PUBLIC_ASSET_CDN_URL;
  if (cdnUrl) {
    try {
      hosts.add(new URL(cdnUrl).hostname);
    } catch {
      console.warn('[proxy] NEXT_PUBLIC_ASSET_CDN_URL is not a valid URL, ignoring');
    }
  }

  const extra = process.env.PROXY_ALLOWED_HOSTS;
  if (extra) {
    for (const raw of extra.split(',')) {
      const host = raw.trim().toLowerCase();
      if (host) hosts.add(host);
    }
  }

  return hosts;
}

const ALLOWED_HOSTS = buildAllowedHosts();

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url);
  const url = searchParams.get('url');

  if (!url) {
    return NextResponse.json({ error: 'URL is required' }, { status: 400 });
  }

  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return NextResponse.json({ error: 'Invalid URL' }, { status: 400 });
  }

  if (parsed.protocol !== 'https:') {
    return NextResponse.json({ error: 'Only https URLs are allowed' }, { status: 403 });
  }

  if (!ALLOWED_HOSTS.has(parsed.hostname.toLowerCase())) {
    return NextResponse.json(
      { error: 'Host not allowed. Only configured asset CDN hosts can be proxied.' },
      { status: 403 }
    );
  }

  try {
    // redirect: 'error' prevents allowlisted hosts from bouncing us to arbitrary targets.
    const response = await fetch(parsed.toString(), { redirect: 'error' });
    if (!response.ok) {
      return NextResponse.json(
        { error: `Failed to fetch image: ${response.statusText}` },
        { status: response.status }
      );
    }

    const blob = await response.blob();
    const contentType = response.headers.get('Content-Type') || 'application/octet-stream';

    return new NextResponse(blob, {
      status: 200,
      headers: {
        'Content-Type': contentType,
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, OPTIONS',
        'Cache-Control': 'public, max-age=31536000, immutable',
      },
    });
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Proxy fetch failed';
    console.error('CORS proxy error:', error);
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
