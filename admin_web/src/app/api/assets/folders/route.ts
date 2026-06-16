import { NextRequest, NextResponse } from 'next/server';
import { listAssetFolders } from '@/domains/assets/asset.service';

// GET: Fetch list of unique asset folders
export async function GET(req: NextRequest) {
  try {
    const folders = await listAssetFolders();
    return NextResponse.json({ success: true, folders });
  } catch (error: any) {
    console.error('Error fetching asset folders:', error);
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}
