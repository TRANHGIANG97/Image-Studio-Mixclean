import type { FontManifestEntry } from '@/domains/fonts/font.types';

function normalizeSlug(slug: string): string {
  return slug.trim().toLowerCase();
}

function buildAliasKeys(entry: FontManifestEntry): string[] {
  const keys = new Set<string>();
  keys.add(normalizeSlug(entry.family_slug));
  keys.add(normalizeSlug(entry.name));
  keys.add(normalizeSlug(entry.name.replace(/\s+/g, '')));
  for (const alias of entry.aliases ?? []) {
    const base = alias.split(',')[0]?.trim() ?? alias;
    keys.add(normalizeSlug(base));
    keys.add(normalizeSlug(base.replace(/\s+/g, '')));
  }
  return [...keys];
}

/** Merge builtin catalog with uploaded fonts. Upload wins on duplicate family_slug. */
export function mergeFontManifest(
  builtin: FontManifestEntry[],
  uploaded: FontManifestEntry[],
): FontManifestEntry[] {
  const bySlug = new Map<string, FontManifestEntry>();

  for (const font of builtin) {
    bySlug.set(normalizeSlug(font.family_slug), font);
  }
  for (const font of uploaded) {
    bySlug.set(normalizeSlug(font.family_slug), font);
  }

  return [...bySlug.values()].sort((a, b) => a.name.localeCompare(b.name, 'vi'));
}

/** Resolve manifest entry by family name / alias (case-insensitive). */
export function resolveFontEntry(
  fonts: FontManifestEntry[],
  family: string,
): FontManifestEntry | undefined {
  if (!family.trim()) return undefined;
  const normalized = normalizeSlug(family.split(',')[0]?.trim() ?? family);
  const compact = normalizeSlug(normalized.replace(/\s+/g, ''));

  for (const entry of fonts) {
    if (normalizeSlug(entry.family_slug) === normalized) return entry;
    if (normalizeSlug(entry.name) === normalized) return entry;
    for (const key of buildAliasKeys(entry)) {
      if (key === normalized || key === compact) return entry;
    }
  }
  return undefined;
}
