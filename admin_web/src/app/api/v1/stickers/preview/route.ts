import { NextRequest, NextResponse } from 'next/server';
import { createSupabaseAdmin } from '@/lib/supabase';

export const dynamic = 'force-dynamic';

export async function GET(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url);
    const limit = Math.min(parseInt(searchParams.get('limit') || '10', 10), 50);

    const supabase = createSupabaseAdmin();

    // Query both meme and decor folder stickers (case-insensitive fallback via .in)
    const [memeRes, decorRes] = await Promise.all([
      supabase
        .from('assets')
        .select('id, file_url')
        .in('folder', ['sticker_meme', 'Sticker_meme'])
        .order('created_at', { ascending: false })
        .limit(limit),
      supabase
        .from('assets')
        .select('id, file_url')
        .in('folder', ['sticker_decor', 'Sticker_decor'])
        .order('created_at', { ascending: false })
        .limit(limit),
    ]);

    if (memeRes.error) throw memeRes.error;
    if (decorRes.error) throw decorRes.error;

    return NextResponse.json(
      {
        success: true,
        meme: (memeRes.data || []).map((a) => ({ id: a.id, url: a.file_url })),
        decor: (decorRes.data || []).map((a) => ({ id: a.id, url: a.file_url })),
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
