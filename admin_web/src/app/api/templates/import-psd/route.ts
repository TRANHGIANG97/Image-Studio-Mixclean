import { NextRequest, NextResponse } from 'next/server';
import { importTemplatePsd } from '@/domains/templates/psd-import.service';

export const runtime = 'nodejs';

export async function POST(req: NextRequest) {
  try {
    const formData = await req.formData();
    const file = formData.get('file') as File | null;
    const categoryId = formData.get('categoryId') as string | null;
    const templateId = formData.get('templateId') as string | null;
    const title = formData.get('title') as string | null;

    if (!file) {
      return NextResponse.json({ error: 'No PSD file provided' }, { status: 400 });
    }

    const fileName = file.name || 'import.psd';
    const lowered = fileName.toLowerCase();
    if (!lowered.endsWith('.psd') && !lowered.endsWith('.psb')) {
      return NextResponse.json({ error: 'Only .psd or .psb files are supported' }, { status: 400 });
    }

    const template = await importTemplatePsd({
      fileBuffer: await file.arrayBuffer(),
      fileName,
      categoryId: categoryId || undefined,
      templateId: templateId || undefined,
      title: title || undefined,
    });

    return NextResponse.json({
      success: true,
      message: 'Template imported from PSD successfully',
      template,
    });
  } catch (error: any) {
    console.error('Import PSD API Error:', error);
    const status = error.statusCode || 500;
    return NextResponse.json({ error: error.message }, { status });
  }
}
