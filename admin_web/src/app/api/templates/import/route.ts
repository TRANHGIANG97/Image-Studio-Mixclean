import { NextRequest, NextResponse } from 'next/server';
import { importTemplateZip } from '@/domains/templates/template.service';

export async function POST(req: NextRequest) {
  try {
    const formData = await req.formData();
    const file = formData.get('file') as File | null;
    const categoryId = formData.get('categoryId') as string | null;

    if (!file) {
      return NextResponse.json({ error: 'No zip file provided' }, { status: 400 });
    }

    const template = await importTemplateZip({
      fileBuffer: await file.arrayBuffer(),
      categoryId: categoryId || undefined,
    });

    return NextResponse.json({
      success: true,
      message: 'Template imported successfully',
      template,
    });
  } catch (error: any) {
    console.error('Import ZIP API Error:', error);
    const status = error.statusCode || 500;
    return NextResponse.json({ error: error.message }, { status });
  }
}
