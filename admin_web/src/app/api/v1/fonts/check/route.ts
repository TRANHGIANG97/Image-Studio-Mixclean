import { NextRequest, NextResponse } from 'next/server';

export const dynamic = 'force-dynamic';

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url);
  const family = searchParams.get('family');

  if (!family) {
    return NextResponse.json({ error: 'Missing family parameter' }, { status: 400 });
  }

  try {
    const res = await fetch(`https://fonts.googleapis.com/css2?family=${encodeURIComponent(family)}`);
    return NextResponse.json({ exists: res.ok });
  } catch (error) {
    console.error(`Error checking Google Font ${family}:`, error);
    return NextResponse.json({ exists: false });
  }
}
