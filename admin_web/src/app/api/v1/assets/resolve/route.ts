import { NextRequest, NextResponse } from 'next/server';
import { applyCDN } from '@/lib/cdn-rewriter';

export async function GET(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url);
    const sourcePath = searchParams.get('sourcePath');

    if (!sourcePath) {
      return NextResponse.json({ error: 'sourcePath is required' }, { status: 400 });
    }

    const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
    if (!supabaseUrl) {
      return NextResponse.json({ error: 'Supabase URL is not configured' }, { status: 500 });
    }

    const cleanPath = sourcePath.replace(/^\/+/, '');
    const encodedPath = cleanPath
      .split('/')
      .map((segment) => encodeURIComponent(segment))
      .join('/');
    
    let publicUrl = `${supabaseUrl}/storage/v1/object/public/assets/${encodedPath}`;
    if (cleanPath.startsWith('studio-edit/')) {
      publicUrl = `${supabaseUrl}/storage/v1/object/public/assets/${encodedPath}`;
    }

    // Nếu đường dẫn đã bắt đầu bằng studio-edit, ta có thể redirect trực tiếp
    if (cleanPath.startsWith('studio-edit/')) {
      const cdnUrl = applyCDN(publicUrl);
      console.log(`[resolve] Direct redirect (studio-edit): ${cdnUrl}`);
      return NextResponse.redirect(cdnUrl, 307);
    }

    // Kiểm tra nhanh sự tồn tại bằng phương thức HEAD (chỉ lấy header, không tải file)
    console.log(`[resolve] Checking path (HEAD): ${publicUrl}`);
    let upstream = await fetch(publicUrl, { method: 'HEAD' });
    
    if (upstream.ok) {
      const cdnUrl = applyCDN(publicUrl);
      console.log(`[resolve] Path verified. Redirecting to: ${cdnUrl}`);
      return NextResponse.redirect(cdnUrl, 307);
    }

    // Fallback thử thư mục studio-edit/
    const fallbackUrl = `${supabaseUrl}/storage/v1/object/public/assets/studio-edit/${encodedPath}`;
    console.log(`[resolve] Primary path failed. Checking fallback (HEAD): ${fallbackUrl}`);
    const fallbackUpstream = await fetch(fallbackUrl, { method: 'HEAD' });

    if (fallbackUpstream.ok) {
      const cdnFallbackUrl = applyCDN(fallbackUrl);
      console.log(`[resolve] Fallback path verified. Redirecting to: ${cdnFallbackUrl}`);
      return NextResponse.redirect(cdnFallbackUrl, 307);
    }

    console.error(`[resolve] Asset not found: ${sourcePath}`);
    return NextResponse.json(
      { error: 'Asset not found' },
      { status: 404 }
    );
  } catch (error: any) {
    console.error('Error resolving asset:', error);
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}

