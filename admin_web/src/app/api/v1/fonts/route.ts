import { NextRequest, NextResponse } from 'next/server';
import { getFontsManifest, uploadFont } from '@/domains/fonts/font.service';
import { applyCDN } from '@/lib/cdn-rewriter';

export const dynamic = 'force-dynamic';

// GET: Full font manifest (builtin + uploaded)
export async function GET() {
  try {
    const manifest = applyCDN(await getFontsManifest());
    return NextResponse.json(manifest, { headers: { 'Cache-Control': 'no-store' } });
  } catch (error: unknown) {
    console.error('Error fetching fonts:', error);
    const message = error instanceof Error ? error.message : 'Failed to fetch fonts';
    return NextResponse.json({ error: message }, { status: 500 });
  }
}

// POST: Upload a new font (.ttf)
export async function POST(req: NextRequest) {
  try {
    const formData = await req.formData();
    const file = formData.get('file') as File | null;
    const name = formData.get('name') as string | null;
    const familySlug = formData.get('family_slug') as string | null;
    const style = formData.get('style') as string | null;

    if (!file || !name || !familySlug) {
      return NextResponse.json(
        { error: 'file, name, and family_slug are required' },
        { status: 400 }
      );
    }

    const font = await uploadFont({ 
      file, 
      name, 
      family_slug: familySlug, 
      style: style || undefined 
    });
    return NextResponse.json({ success: true, font });
  } catch (error: any) {
    console.error('Fonts upload error:', error);
    const status = error.statusCode || 500;
    return NextResponse.json({ error: error.message || 'Failed to upload font' }, { status });
  }
}
