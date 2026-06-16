import { NextRequest, NextResponse } from 'next/server';
import { cloneTemplate } from '@/domains/templates/template.service';

export async function POST(req: NextRequest) {
  try {
    const body = await req.json();
    const { sourceId, newTemplateId, newTitle } = body;

    if (!sourceId || !newTemplateId || !newTitle) {
      return NextResponse.json(
        { error: 'Missing required fields: sourceId, newTemplateId, newTitle' },
        { status: 400 }
      );
    }

    const template = await cloneTemplate({ sourceId, newTemplateId, newTitle });
    return NextResponse.json({ success: true, template });
  } catch (error: any) {
    console.error('Error cloning template:', error);
    const status = error.statusCode || 500;
    return NextResponse.json({ error: error.message }, { status });
  }
}
