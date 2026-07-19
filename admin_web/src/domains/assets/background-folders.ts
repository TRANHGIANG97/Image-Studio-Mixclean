export function normalizeBackgroundFolder(folder: string): string {
  return folder.trim().toLowerCase();
}

export function isBackgroundFolder(folder: string): boolean {
  const normalized = normalizeBackgroundFolder(folder);
  return normalized === 'backgrounds' || normalized.startsWith('backgrounds_');
}

/** Background tabs on mobile — only `backgrounds_*` (excludes root `backgrounds`). */
export function isBackgroundTabFolder(folder: string): boolean {
  const normalized = normalizeBackgroundFolder(folder);
  return normalized.startsWith('backgrounds_');
}

/** Display label derived from folder slug (no hardcoded map). */
export function backgroundFolderLabel(slug: string): string {
  return humanizeBackgroundFolder(normalizeBackgroundFolder(slug));
}

export function backgroundFolderSortKey(slug: string): [number, string] {
  return [0, normalizeBackgroundFolder(slug)];
}

function humanizeBackgroundFolder(slug: string): string {
  if (!slug.startsWith('backgrounds_')) {
    return slug === 'backgrounds' ? 'backgrounds' : slug;
  }
  const suffix = slug.slice('backgrounds_'.length);
  if (!suffix) return 'backgrounds';
  const titleSuffix = suffix
    .split(/[_-]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
  return `backgrounds ${titleSuffix}`;
}

export function isBackgroundImageMime(mime: string | null | undefined): boolean {
  const value = (mime ?? '').toLowerCase();
  return !value || value.startsWith('image/') || value === 'image/svg+xml';
}
