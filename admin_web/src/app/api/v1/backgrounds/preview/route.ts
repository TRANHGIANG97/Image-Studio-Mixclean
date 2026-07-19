import { NextRequest, NextResponse } from 'next/server';
import { createSupabaseAdmin } from '@/lib/supabase';
import { isBackgroundTabFolder, normalizeBackgroundFolder } from '@/domains/assets/background-folders';

export const dynamic = 'force-dynamic';

async function loadBackgroundFolderSlugs(): Promise<string[]> {
  const supabase = createSupabaseAdmin();
  const folders = new Set<string>();

  let page = 0;
  const pageSize = 1000;
  let hasMore = true;

  while (hasMore) {
    const { data, error } = await supabase
      .from('assets')
      .select('folder')
      .not('name', 'eq', '.folder_placeholder')
      .range(page * pageSize, (page + 1) * pageSize - 1);

    if (error) throw error;

    if (!data || data.length === 0) {
      hasMore = false;
      break;
    }

    for (const row of data) {
      const folder = (row.folder ?? '').trim();
      if (folder && isBackgroundTabFolder(folder)) {
        folders.add(normalizeBackgroundFolder(folder));
      }
    }

    hasMore = data.length === pageSize;
    page += 1;
  }

  return Array.from(folders).sort();
}

/**
 * Returns a mixed preview strip of background images for the mobile editor.
 */
export async function GET(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url);
    const limit = Math.min(parseInt(searchParams.get('limit') || '20', 10), 50);

    const supabase = createSupabaseAdmin();
    const folders = await loadBackgroundFolderSlugs();

    if (folders.length === 0) {
      return NextResponse.json({ success: true, backgrounds: [] });
    }

    const perFolder = Math.max(1, Math.ceil(limit / folders.length));
    const backgrounds: Array<{ id: string; url: string; folder: string }> = [];

    for (const folder of folders) {
      if (backgrounds.length >= limit) break;

      const { data, error } = await supabase
        .from('assets')
        .select('id, file_url, folder')
        .ilike('folder', folder)
        .not('name', 'eq', '.folder_placeholder')
        .order('created_at', { ascending: false })
        .limit(perFolder);

      if (error) throw error;

      for (const asset of data ?? []) {
        if (backgrounds.length >= limit) break;
        backgrounds.push({
          id: asset.id,
          url: asset.file_url,
          folder: normalizeBackgroundFolder(asset.folder ?? folder),
        });
      }
    }

    return NextResponse.json(
      { success: true, backgrounds: backgrounds.slice(0, limit) },
      {
        headers: {
          'Cache-Control': 'public, s-maxage=300, stale-while-revalidate=60',
        },
      },
    );
  } catch (error: any) {
    console.error('Error fetching background preview:', error);
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}
