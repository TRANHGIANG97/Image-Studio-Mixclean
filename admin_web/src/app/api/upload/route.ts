import { NextRequest, NextResponse } from 'next/server';
import { uploadAsset } from '@/domains/assets/asset.service';

export async function POST(req: NextRequest) {
  try {
    const formData = await req.formData();
    const file = formData.get('file') as File | null;
    const folder = (formData.get('folder') as string) || 'uncategorized';
    const categoryId = formData.get('categoryId') as string | null;
    const registerAsset = formData.get('registerAsset') !== 'false';

    if (!file) {
      return NextResponse.json({ error: 'No file provided' }, { status: 400 });
    }

    const result = await uploadAsset(file, folder, categoryId, registerAsset);
    return NextResponse.json({
      success: true,
      fileUrl: result.fileUrl,
      asset: result.asset,
      isDuplicate: result.isDuplicate || false,
    });
  } catch (error: any) {
    console.error('Upload handler error:', error);
    const status = error.statusCode || 500;
    return NextResponse.json({ error: error.message || 'Failed to upload asset' }, { status });
  }
}
