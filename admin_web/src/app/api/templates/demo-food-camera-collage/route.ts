import { NextRequest, NextResponse } from 'next/server';
import { createDemoCollage } from '@/domains/templates/template.service';

export async function POST(req: NextRequest) {
  try {
    const body = await req.json().catch(() => ({}));
    const origin = req.nextUrl.origin;

    const template = await createDemoCollage({
      templateId: body?.templateId || undefined,
      categoryId: body?.categoryId || undefined,
      origin,
    });

    return NextResponse.json({
      success: true,
      template,
    });
  } catch (error: any) {
    console.error('Error creating demo collage template:', error);
    return NextResponse.json({ error: error.message || 'Failed to create demo collage template' }, { status: 500 });
  }
}
