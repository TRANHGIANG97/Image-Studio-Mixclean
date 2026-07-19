import { NextResponse } from 'next/server';
import { createSupabaseAdmin } from '@/lib/supabase';

export const dynamic = 'force-dynamic';

/** Folders managed by Media Library but not shown as sticker tabs on mobile. */
const EXCLUDED_FOLDERS = new Set([
  'backgrounds',
  'fonts',
  'uncategorized',
  'imported-psd',
  'imported-psd-temp',
]);

/** Preferred tab order for known sticker packs (others sort alphabetically after these). */
const FOLDER_ORDER = ['materials_icon', 'svg_undraw', 'sticker_decor', 'sticker_meme'];

const FOLDER_LABELS: Record<string, string> = {
  materials_icon: 'Biểu tượng',
  svg_undraw: 'Minh họa phẳng',
  sticker_decor: 'Trang trí',
  sticker_meme: 'Biểu cảm',
  stickers: 'Nhãn dán',
  sticker: 'Nhãn dán',
};

function humanizeFolder(slug: string): string {
  return slug
    .split(/[_-]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

function folderLabel(slug: string): string {
  return FOLDER_LABELS[slug] ?? FOLDER_LABELS[slug.toLowerCase()] ?? humanizeFolder(slug);
}

function folderSortKey(slug: string): [number, string] {
  const priority = FOLDER_ORDER.indexOf(slug);
  return [priority === -1 ? FOLDER_ORDER.length : priority, slug];
}

/**
 * Returns sticker tab metadata for the mobile editor.
 * Any Media Library folder with at least one real image/SVG asset becomes a tab,
 * except system folders (backgrounds, fonts, uncategorized, PSD imports).
 */
export async function GET() {
  try {
    const supabase = createSupabaseAdmin();

    const { data, error } = await supabase
      .from('assets')
      .select('folder, mime_type')
      .not('name', 'eq', '.folder_placeholder');

    if (error) throw error;

    const counts = new Map<string, number>();

    for (const row of data ?? []) {
      const folder = (row.folder ?? '').trim();
      if (!folder || EXCLUDED_FOLDERS.has(folder)) continue;

      const mime = (row.mime_type ?? '').toLowerCase();
      const isStickerMime =
        !mime ||
        mime.startsWith('image/') ||
        mime === 'image/svg+xml' ||
        mime.startsWith('font/') === false;

      if (!isStickerMime) continue;

      counts.set(folder, (counts.get(folder) ?? 0) + 1);
    }

    const folders = Array.from(counts.entries())
      .filter(([, count]) => count > 0)
      .map(([id, count]) => ({
        id,
        label: folderLabel(id),
        count,
      }))
      .sort((a, b) => {
        const [aOrder, aSlug] = folderSortKey(a.id);
        const [bOrder, bSlug] = folderSortKey(b.id);
        if (aOrder !== bOrder) return aOrder - bOrder;
        return aSlug.localeCompare(bSlug);
      });

    return NextResponse.json(
      { success: true, folders },
      {
        headers: {
          'Cache-Control': 'public, s-maxage=300, stale-while-revalidate=60',
        },
      },
    );
  } catch (error: any) {
    console.error('Error fetching sticker folders:', error);
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}
