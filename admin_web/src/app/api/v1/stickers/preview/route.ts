import { NextRequest, NextResponse } from 'next/server';
import { listAssets } from '@/domains/assets/asset.service';
import {
  DEFAULT_STICKER_PREVIEW_FOLDER,
  DEFAULT_STICKER_PREVIEW_LIMIT,
  isMaterialsTabFolder,
  normalizeMaterialsFolder,
} from '@/domains/assets/materials-folders';

export const dynamic = 'force-dynamic';

/** Same query path as Media Library GET /api/assets — keeps preview in sync with admin UI. */
async function loadFolderPreview(folder: string, limit: number) {
  const { assets } = await listAssets({ folder, page: 1, limit });
  return (assets ?? [])
    .filter((asset) => asset.file_url)
    .map((asset) => ({ id: asset.id, url: asset.file_url as string }));
}

/**
 * Quick sticker strip — defaults to 20 icons from `materials_icon`.
 * Query: ?folder=materials_icon&limit=20
 */
export async function GET(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url);
    const folder = normalizeMaterialsFolder(
      searchParams.get('folder') || DEFAULT_STICKER_PREVIEW_FOLDER,
    );
    const limit = Math.min(
      parseInt(searchParams.get('limit') || String(DEFAULT_STICKER_PREVIEW_LIMIT), 10),
      50,
    );

    if (!isMaterialsTabFolder(folder)) {
      return NextResponse.json(
        { error: 'folder must be a materials_* slug' },
        { status: 400 },
      );
    }

    const stickers = await loadFolderPreview(folder, limit);

    return NextResponse.json(
      {
        success: true,
        folder,
        stickers,
        // Legacy keys for older app builds
        meme: stickers,
        decor: [],
      },
      {
        headers: {
          'Cache-Control': 'public, s-maxage=300, stale-while-revalidate=60',
        },
      },
    );
  } catch (error: any) {
    console.error('Error fetching sticker preview:', error);
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}
