import { NextRequest, NextResponse } from 'next/server';
import { exportTemplateZip } from '@/domains/templates/template.service';

interface Context {
  params: Promise<{ id: string }>;
}

export async function GET(_req: NextRequest, context: Context) {
  try {
    const { id } = await context.params;
    const { buffer, filename } = await exportTemplateZip(id);

    return new NextResponse(buffer, {
      status: 200,
      headers: {
        'Content-Type': 'application/zip',
        'Content-Disposition': `attachment; filename="${filename}"`,
      },
    });
  } catch (error: any) {
    console.error('Export ZIP API Error:', error);
    const status = error.statusCode || 500;
    return NextResponse.json({ error: error.message }, { status });
  }
}
