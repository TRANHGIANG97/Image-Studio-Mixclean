const R2_PUBLIC_BASE = 'https://pub-d63489ecea7149a585628ea6a2c2da7f.r2.dev';

/** Unwrap `/api/proxy?url=` and `http://host/api/proxy?url=` to the original asset URL. */
export function unwrapProxyUrl(value: string): string {
  if (!value || typeof value !== 'string') return value;

  const trimmed = value.trim();
  const proxyMatch = trimmed.match(/^(?:https?:\/\/[^/?#]+)?\/api\/proxy\?url=(.+)$/i);
  if (proxyMatch) {
    try {
      return decodeURIComponent(proxyMatch[1]);
    } catch {
      return trimmed;
    }
  }
  return trimmed;
}

export function rewriteUrls(obj: any, fromUrl: string, toUrl: string): any {
  if (!obj) return obj;
  if (typeof obj === 'string') {
    if (obj.startsWith(fromUrl)) {
      return obj.replace(fromUrl, toUrl);
    }
    return obj;
  }
  if (Array.isArray(obj)) {
    return obj.map((item) => rewriteUrls(item, fromUrl, toUrl));
  }
  if (typeof obj === 'object') {
    const newObj: any = {};
    for (const key in obj) {
      if (Object.prototype.hasOwnProperty.call(obj, key)) {
        newObj[key] = rewriteUrls(obj[key], fromUrl, toUrl);
      }
    }
    return newObj;
  }
  return obj;
}

/** Browser editor: rewrite R2 URLs to same-origin proxy (avoids CORS in dev). */
export function applyCDN(data: any): any {
  return rewriteR2ToProxy(data, R2_PUBLIC_BASE);
}

/** Persist / mobile API: store and serve direct CDN URLs (no localhost proxy). */
export function removeCDN(data: any): any {
  return restoreProxyToDirect(data);
}

function rewriteR2ToProxy(obj: any, r2Url: string): any {
  if (!obj) return obj;
  if (typeof obj === 'string') {
    const direct = unwrapProxyUrl(obj);
    if (direct.startsWith(r2Url)) {
      return `/api/proxy?url=${encodeURIComponent(direct)}`;
    }
    return obj;
  }
  if (Array.isArray(obj)) {
    return obj.map((item) => rewriteR2ToProxy(item, r2Url));
  }
  if (typeof obj === 'object') {
    const newObj: any = {};
    for (const key in obj) {
      if (Object.prototype.hasOwnProperty.call(obj, key)) {
        newObj[key] = rewriteR2ToProxy(obj[key], r2Url);
      }
    }
    return newObj;
  }
  return obj;
}

function restoreProxyToDirect(obj: any): any {
  if (!obj) return obj;
  if (typeof obj === 'string') {
    return unwrapProxyUrl(obj);
  }
  if (Array.isArray(obj)) {
    return obj.map((item) => restoreProxyToDirect(item));
  }
  if (typeof obj === 'object') {
    const newObj: any = {};
    for (const key in obj) {
      if (Object.prototype.hasOwnProperty.call(obj, key)) {
        newObj[key] = restoreProxyToDirect(obj[key]);
      }
    }
    return newObj;
  }
  return obj;
}
