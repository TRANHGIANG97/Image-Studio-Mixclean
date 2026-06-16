import { NextResponse } from 'next/server';
import { createSupabaseAdmin } from '@/lib/supabase';

export const dynamic = 'force-dynamic';

export async function GET() {
  try {
    const supabase = createSupabaseAdmin();
    const { data, error } = await supabase
      .from('categories')
      .select('id, name, "order"')
      .order('order', { ascending: true });

    if (error) throw error;

    return NextResponse.json(
      { success: true, categories: data || [] },
      { headers: { 'Cache-Control': 'no-store' } }
    );
  } catch (error: unknown) {
    console.error('Error fetching public categories:', error);
    const message = error instanceof Error ? error.message : 'Failed to fetch public categories';
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
