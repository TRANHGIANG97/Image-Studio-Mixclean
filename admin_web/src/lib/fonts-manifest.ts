import type { FontManifestEntry, FontsManifestResponse } from '@/domains/fonts/font.types';

let cachedManifest: FontsManifestResponse | null = null;
let inflight: Promise<FontsManifestResponse> | null = null;

export const FONT_CATEGORIES = [
  'Hệ thống',
  'Quảng cáo',
  'In ấn & Sách',
  'Thư pháp & Nghệ thuật',
  'Hiện đại',
  'Cổ điển',
  'Trang trí',
  'Chưa phân loại',
] as const;

export async function fetchFontsManifest(options?: { force?: boolean }): Promise<FontsManifestResponse> {
  if (!options?.force && cachedManifest) {
    return cachedManifest;
  }
  if (!options?.force && inflight) {
    return inflight;
  }

  inflight = fetch('/api/v1/fonts')
    .then(async (res) => {
      if (!res.ok) {
        throw new Error(`Fonts manifest HTTP ${res.status}`);
      }
      const data = (await res.json()) as FontsManifestResponse;
      if (!data.success || !Array.isArray(data.fonts)) {
        throw new Error('Invalid fonts manifest response');
      }
      cachedManifest = data;
      return data;
    })
    .finally(() => {
      inflight = null;
    });

  return inflight;
}

export function invalidateFontsManifestCache(): void {
  cachedManifest = null;
}

export function injectFontFace(entry: FontManifestEntry): void {
  if (typeof document === 'undefined' || !entry.font_url) return;

  const fontId = `manifest-font-${entry.family_slug.replace(/\s+/g, '-').toLowerCase()}`;
  if (document.getElementById(fontId)) return;

  const style = document.createElement('style');
  style.id = fontId;
  const faces = [entry.family_slug, entry.name, entry.name.replace(/\s+/g, '')]
    .filter((value, index, arr) => arr.indexOf(value) === index)
    .map(
      (family) => `
    @font-face {
      font-family: '${family}';
      src: url('${entry.font_url}') format('truetype');
      font-display: swap;
    }`,
    )
    .join('\n');
  style.appendChild(document.createTextNode(faces));
  document.head.appendChild(style);
}

export function injectManifestFontFaces(fonts: FontManifestEntry[]): void {
  for (const font of fonts) {
    if (font.font_url) {
      injectFontFace(font);
    }
  }
}

export function groupFontsByStyle(
  fonts: FontManifestEntry[],
): Record<string, (FontManifestEntry & { seq: number })[]> {
  const numbered = fonts.map((f, i) => ({ ...f, seq: i + 1 }));
  const grouped: Record<string, typeof numbered> = {};
  for (const font of numbered) {
    const cat = font.style || 'Chưa phân loại';
    if (!grouped[cat]) grouped[cat] = [];
    grouped[cat].push(font);
  }
  return grouped;
}

export function filterFonts(
  fonts: FontManifestEntry[],
  query: string,
): FontManifestEntry[] {
  const q = query.trim().toLowerCase();
  if (!q) return fonts;
  return fonts.filter(
    (f) =>
      f.name.toLowerCase().includes(q) ||
      f.family_slug.toLowerCase().includes(q) ||
      (f.style || '').toLowerCase().includes(q),
  );
}

export async function checkGoogleFont(fontFamily: string): Promise<boolean> {
  if (typeof window === 'undefined') return false;
  try {
    const res = await fetch(`/api/v1/fonts/check?family=${encodeURIComponent(fontFamily)}`);
    if (!res.ok) return false;
    const data = await res.json();
    return data.exists === true;
  } catch (error) {
    console.warn(`Failed to check Google Font "${fontFamily}" via API:`, error);
    return false;
  }
}

export function injectGoogleFont(fontFamily: string): void {
  if (typeof document === 'undefined') return;
  const linkId = `google-font-${fontFamily.toLowerCase().replace(/\s+/g, '-')}`;
  if (document.getElementById(linkId)) return;

  const link = document.createElement('link');
  link.id = linkId;
  link.rel = 'stylesheet';
  link.href = `https://fonts.googleapis.com/css2?family=${encodeURIComponent(fontFamily)}:ital,wght@0,300;0,400;0,500;0,700;1,300;1,400;1,500;1,700&display=swap`;
  document.head.appendChild(link);
}
