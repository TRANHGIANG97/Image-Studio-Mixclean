import { NextResponse } from 'next/server';
import { createSupabaseAdmin } from '@/lib/supabase';
import {
  backgroundFolderLabel,
  backgroundFolderSortKey,
  isBackgroundImageMime,
  isBackgroundTabFolder,
  normalizeBackgroundFolder,
} from '@/domains/assets/background-folders';

export const dynamic = 'force-dynamic';

async function loadBackgroundFolderCounts() {
  const supabase = createSupabaseAdmin();
  const counts = new Map<string, number>();

  let page = 0;
  const pageSize = 1000;
  let hasMore = true;

  while (hasMore) {
    const { data, error } = await supabase
      .from('assets')
      .select('folder, mime_type')
      .not('name', 'eq', '.folder_placeholder')
      .range(page * pageSize, (page + 1) * pageSize - 1);

    if (error) throw error;

    if (!data || data.length === 0) {
      hasMore = false;
      break;
    }

    for (const row of data) {
      const folder = (row.folder ?? '').trim();
      if (!folder || !isBackgroundTabFolder(folder)) continue;
      if (!isBackgroundImageMime(row.mime_type)) continue;

      const canonical = normalizeBackgroundFolder(folder);
      counts.set(canonical, (counts.get(canonical) ?? 0) + 1);
    }

    hasMore = data.length === pageSize;
    page += 1;
  }

  return counts;
}

/**
 * Returns background tab metadata for the mobile editor.
 * Folders: backgrounds_* only (e.g. backgrounds_ecommerce, backgrounds_christmas)
 */
export async function GET() {
  try {
    const counts = await loadBackgroundFolderCounts();

    const folders = Array.from(counts.entries())
      .filter(([, count]) => count > 0)
      .map(([id, count]) => ({
        id,
        label: backgroundFolderLabel(id),
        count,
      }))
      .sort((a, b) => {
        const [aOrder, aSlug] = backgroundFolderSortKey(a.id);
        const [bOrder, bSlug] = backgroundFolderSortKey(b.id);
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
    console.error('Error fetching background folders:', error);
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}
