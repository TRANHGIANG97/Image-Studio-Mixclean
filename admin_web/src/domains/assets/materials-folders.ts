import { isBackgroundFolder } from '@/domains/assets/background-folders';

export function normalizeMaterialsFolder(folder: string): string {
  return folder.trim().toLowerCase();
}

/** Any folder under the materials library bucket. */
export function isMaterialsFolder(folder: string): boolean {
  const normalized = normalizeMaterialsFolder(folder);
  return normalized.startsWith('materials_');
}

/**
 * Sticker tabs on mobile — only `materials_*`, never `backgrounds_*`.
 * Also excludes misfiled `materials_backgrounds_*` folders.
 */
export function isMaterialsTabFolder(folder: string): boolean {
  const normalized = normalizeMaterialsFolder(folder);
  if (!normalized.startsWith('materials_')) return false;
  if (isBackgroundFolder(normalized)) return false;

  const suffix = normalized.slice('materials_'.length);
  if (suffix === 'backgrounds' || suffix.startsWith('backgrounds_')) return false;

  return true;
}

/** Display label derived from folder slug (no hardcoded map). */
export function materialsFolderLabel(slug: string): string {
  return humanizeMaterialsFolder(normalizeMaterialsFolder(slug));
}

export function materialsFolderSortKey(slug: string): [number, string] {
  return [0, normalizeMaterialsFolder(slug)];
}

function humanizeMaterialsFolder(slug: string): string {
  if (!slug.startsWith('materials_')) return slug;
  const suffix = slug.slice('materials_'.length);
  if (!suffix) return 'materials';
  const titleSuffix = suffix
    .split(/[_-]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
  return `materials ${titleSuffix}`;
}

export function isMaterialsImageMime(mime: string | null | undefined): boolean {
  const value = (mime ?? '').toLowerCase();
  return !value || value.startsWith('image/') || value === 'image/svg+xml';
}

/** Default quick-picker strip in the sticker tool. */
export const DEFAULT_STICKER_PREVIEW_FOLDER = 'materials_icon';
export const DEFAULT_STICKER_PREVIEW_LIMIT = 20;
