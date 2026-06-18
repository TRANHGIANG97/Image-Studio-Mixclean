import { describe, expect, it } from 'vitest';
import { BUILTIN_FONT_CATALOG } from '@/domains/fonts/font.catalog';
import { mergeFontManifest, resolveFontEntry } from '@/domains/fonts/font.manifest-utils';
import type { FontManifestEntry } from '@/domains/fonts/font.types';

describe('font manifest', () => {
  it('merges builtin with upload and upload wins on slug', () => {
    const uploaded: FontManifestEntry[] = [
      {
        id: 'upload-outfit',
        name: 'Outfit Custom',
        family_slug: 'Outfit',
        style: 'Hiện đại',
        source: 'upload',
        font_url: 'https://cdn.example/custom.ttf',
      },
    ];
    const merged = mergeFontManifest(BUILTIN_FONT_CATALOG, uploaded);
    const outfit = merged.find((f) => f.family_slug === 'Outfit');
    expect(outfit?.source).toBe('upload');
    expect(outfit?.name).toBe('Outfit Custom');
  });

  it('resolves aliases case-insensitively', () => {
    const fonts = mergeFontManifest(BUILTIN_FONT_CATALOG, []);
    expect(resolveFontEntry(fonts, 'outfit')?.family_slug).toBe('Outfit');
    expect(resolveFontEntry(fonts, 'Outfit, sans-serif')?.family_slug).toBe('Outfit');
    expect(resolveFontEntry(fonts, 'sans-serif')?.source).toBe('system');
  });

  it('includes outfit in builtin catalog', () => {
    expect(BUILTIN_FONT_CATALOG.some((f) => f.family_slug === 'Outfit')).toBe(true);
  });
});
