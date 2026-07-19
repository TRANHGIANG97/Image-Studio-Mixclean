export const BACKGROUND_FOLDER_ORDER = [
  'backgrounds',
  'backgrounds_minimal',
  'backgrounds_gradient',
  'backgrounds_ecommerce',
  'backgrounds_shopee',
];

export const BACKGROUND_FOLDER_LABELS: Record<string, string> = {
  backgrounds: 'Tất cả',
  backgrounds_minimal: 'Tối giản',
  backgrounds_gradient: 'Gradient',
  backgrounds_ecommerce: 'Bán hàng',
  backgrounds_shopee: 'Thương mại',
};

export function normalizeBackgroundFolder(folder: string): string {
  return folder.trim().toLowerCase();
}

export function isBackgroundFolder(folder: string): boolean {
  const normalized = normalizeBackgroundFolder(folder);
  return normalized === 'backgrounds' || normalized.startsWith('backgrounds_') || normalized.startsWith('bg_');
}

export function backgroundFolderLabel(slug: string): string {
  const normalized = normalizeBackgroundFolder(slug);
  return (
    BACKGROUND_FOLDER_LABELS[normalized] ??
    humanizeBackgroundFolder(normalized)
  );
}

export function backgroundFolderSortKey(slug: string): [number, string] {
  const normalized = normalizeBackgroundFolder(slug);
  const priority = BACKGROUND_FOLDER_ORDER.indexOf(normalized);
  return [priority === -1 ? BACKGROUND_FOLDER_ORDER.length : priority, normalized];
}

function humanizeBackgroundFolder(slug: string): string {
  return slug
    .replace(/^backgrounds?_/, '')
    .replace(/^bg_/, '')
    .split(/[_-]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ') || 'Nền';
}

export function isBackgroundImageMime(mime: string | null | undefined): boolean {
  const value = (mime ?? '').toLowerCase();
  return !value || value.startsWith('image/') || value === 'image/svg+xml';
}
