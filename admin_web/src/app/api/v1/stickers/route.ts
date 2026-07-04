import { NextRequest, NextResponse } from 'next/server';
import { createSupabaseAdmin } from '@/lib/supabase';

export const dynamic = 'force-dynamic';

export async function GET(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url);
    const folder = searchParams.get('folder') || '';
    const page = Math.max(1, parseInt(searchParams.get('page') || '1', 10));
    const limit = Math.min(parseInt(searchParams.get('limit') || '30', 10), 50);

    if (!folder) {
      return NextResponse.json({ error: 'folder parameter is required' }, { status: 400 });
    }

    const supabase = createSupabaseAdmin();

    // Map the requested folder to support both lowercase and capitalized variants
    const targetFolders = [folder];
    if (folder.toLowerCase() === 'sticker_meme') {
      targetFolders.push('sticker_meme', 'Sticker_meme');
    } else if (folder.toLowerCase() === 'sticker_decor') {
      targetFolders.push('sticker_decor', 'Sticker_decor');
    }

    const from = (page - 1) * limit;
    const to = from + limit - 1;

    // Fetch paginated results and total count from Supabase directly
    const { data, error, count } = await supabase
      .from('assets')
      .select('id, file_url', { count: 'exact' })
      .in('folder', targetFolders)
      .order('created_at', { ascending: false })
      .range(from, to);

    if (error) throw error;

    const stickers = (data || []).map((a) => ({ id: a.id, url: a.file_url }));
    const hasMore = count ? from + stickers.length < count : false;

    return NextResponse.json(
      {
        success: true,
        stickers,
        total: count || 0,
        hasMore,
        page,
      },
      {
        headers: {
          'Cache-Control': 'public, s-maxage=300, stale-while-revalidate=60',
        },
      },
    );
  } catch (error: any) {
    console.error('Error fetching stickers:', error);
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}
