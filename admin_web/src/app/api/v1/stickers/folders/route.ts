import { NextResponse } from 'next/server';
import { createSupabaseAdmin } from '@/lib/supabase';
import {
  isMaterialsImageMime,
  isMaterialsTabFolder,
  materialsFolderLabel,
  materialsFolderSortKey,
  normalizeMaterialsFolder,
} from '@/domains/assets/materials-folders';

export const dynamic = 'force-dynamic';

async function loadMaterialsFolderCounts() {
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
      if (!folder || !isMaterialsTabFolder(folder)) continue;
      if (!isMaterialsImageMime(row.mime_type)) continue;

      const canonical = normalizeMaterialsFolder(folder);
      counts.set(canonical, (counts.get(canonical) ?? 0) + 1);
    }

    hasMore = data.length === pageSize;
    page += 1;
  }

  return counts;
}

/**
 * Returns sticker tab metadata for the mobile editor.
 * Folders: materials_* only (e.g. materials_icon, materials_christmas).
 */
export async function GET() {
  try {
    const counts = await loadMaterialsFolderCounts();

    const folders = Array.from(counts.entries())
      .filter(([, count]) => count > 0)
      .map(([id, count]) => ({
        id,
        label: materialsFolderLabel(id),
        count,
      }))
      .sort((a, b) => {
        const [aOrder, aSlug] = materialsFolderSortKey(a.id);
        const [bOrder, bSlug] = materialsFolderSortKey(b.id);
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
