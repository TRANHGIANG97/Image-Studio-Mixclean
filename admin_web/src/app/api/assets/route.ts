import { NextRequest, NextResponse } from 'next/server';
import { listAssets, deleteAsset, updateAsset, updateAssetsBulk, deleteAssetsBulk } from '@/domains/assets/asset.service';

// GET: Fetch all assets with search and folder filters
export async function GET(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url);
    const page = parseInt(searchParams.get('page') || '1', 10);
    const limit = parseInt(searchParams.get('limit') || '20', 10);

    const result = await listAssets({
      search: searchParams.get('search') || undefined,
      folder: searchParams.get('folder') || undefined,
      categoryId: searchParams.get('categoryId') || undefined,
      mimeType: searchParams.get('mimeType') || undefined,
      sortBy: searchParams.get('sortBy') || undefined,
      sortOrder: (searchParams.get('sortOrder') as 'asc' | 'desc') || undefined,
      page,
      limit,
    });
    return NextResponse.json({ success: true, ...result });
  } catch (error: any) {
    console.error('Error fetching assets:', error);
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}

// DELETE: Delete asset(s) from both Supabase DB and Storage
export async function DELETE(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url);
    const id = searchParams.get('id');
    const idsParam = searchParams.get('ids');

    if (idsParam) {
      const ids = idsParam.split(',').filter(Boolean);
      if (ids.length > 0) {
        await deleteAssetsBulk(ids);
        return NextResponse.json({ success: true, message: 'Assets deleted successfully' });
      }
    }

    if (!id) {
      return NextResponse.json({ error: 'Asset ID is required' }, { status: 400 });
    }

    await deleteAsset(id);
    return NextResponse.json({ success: true, message: 'Asset deleted successfully' });
  } catch (error: any) {
    console.error('Error deleting asset:', error);
    const status = error.statusCode || 500;
    return NextResponse.json({ error: error.message }, { status });
  }
}

// PUT: Update asset attributes (like category_id, folder, name)
export async function PUT(req: NextRequest) {
  try {
    const body = await req.json();
    const { id, ids, folder, categoryId, name } = body;

    if (ids && Array.isArray(ids) && ids.length > 0) {
      const assets = await updateAssetsBulk(ids, { folder, categoryId });
      return NextResponse.json({ success: true, count: assets.length });
    }

    if (!id) {
      return NextResponse.json({ error: 'Asset ID or IDs is required' }, { status: 400 });
    }

    const asset = await updateAsset(id, { folder, categoryId, name });
    return NextResponse.json({ success: true, asset });
  } catch (error: any) {
    console.error('Error updating asset:', error);
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}
