import type { FontManifestEntry } from '@/domains/fonts/font.types';

/** Manifest schema version — bump when breaking response shape changes. */
export const FONT_MANIFEST_SCHEMA_VERSION = 1;

/** Web-safe families with no downloadable file. */
export const SYSTEM_FONT_SLUGS = ['sans-serif', 'serif', 'monospace', 'cursive'] as const;

/**
 * Built-in editor fonts (versioned in repo).
 * Upload/custom fonts from DB override entries with the same family_slug.
 */
export const BUILTIN_FONT_CATALOG: FontManifestEntry[] = [
  {
    id: 'builtin-outfit',
    name: 'Outfit',
    family_slug: 'Outfit',
    style: 'Hệ thống',
    source: 'cdn',
    font_url:
      'https://github.com/google/fonts/raw/main/ofl/outfit/static/Outfit-Regular.ttf',
    weights: ['400', '600', '700'],
    aliases: ['Outfit', 'outfit', 'Outfit, sans-serif'],
  },
  {
    id: 'system-sans-serif',
    name: 'Sans-Serif',
    family_slug: 'sans-serif',
    style: 'Hệ thống',
    source: 'system',
    font_url: null,
    weights: ['400', '700'],
    aliases: ['sans-serif', 'Sans-Serif', 'arial', 'helvetica'],
  },
  {
    id: 'system-serif',
    name: 'Serif',
    family_slug: 'serif',
    style: 'Hệ thống',
    source: 'system',
    font_url: null,
    weights: ['400', '700'],
    aliases: ['serif', 'Serif', 'times new roman', 'times', 'georgia'],
  },
  {
    id: 'system-monospace',
    name: 'Monospace',
    family_slug: 'monospace',
    style: 'Hệ thống',
    source: 'system',
    font_url: null,
    weights: ['400', '700'],
    aliases: ['monospace', 'Monospace', 'courier new', 'courier'],
  },
  {
    id: 'system-cursive',
    name: 'Cursive',
    family_slug: 'cursive',
    style: 'Hệ thống',
    source: 'system',
    font_url: null,
    weights: ['400'],
    aliases: ['cursive', 'Cursive', 'comic sans ms'],
  },
];
