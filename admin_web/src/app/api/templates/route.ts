import { NextRequest, NextResponse } from 'next/server';
import { listTemplates, createTemplate, deleteTemplatesBulk } from '@/domains/templates/template.service';

// GET: Fetch all templates (with search, category filter, status filter, pagination)
export async function GET(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url);
    const page = parseInt(searchParams.get('page') || '1', 10);
    const limit = parseInt(searchParams.get('limit') || '20', 10);

    const result = await listTemplates({
      search: searchParams.get('search') || undefined,
      categoryId: searchParams.get('categoryId') || undefined,
      status: searchParams.get('status') || undefined,
      sortBy: searchParams.get('sortBy') || undefined,
      sortOrder: searchParams.get('sortOrder') || undefined,
      page,
      limit,
    });
    return NextResponse.json({ success: true, ...result });
  } catch (error: any) {
    console.error('Error fetching templates:', error);
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}

// POST: Create a new template with initial default data
export async function POST(req: NextRequest) {
  try {
    const body = await req.json();
    const templateId = body.templateId ?? body.template_id;
    const categoryId = body.categoryId ?? body.category_id;
    const title = body.title;

    if (!templateId || !categoryId || !title) {
      return NextResponse.json(
        { error: 'templateId, categoryId, and title are required' },
        { status: 400 }
      );
    }

    const template = await createTemplate({
      templateId,
      categoryId,
      title,
      baseWidth: body.baseWidth ? Number(body.baseWidth) : undefined,
      baseHeight: body.baseHeight ? Number(body.baseHeight) : undefined,
      backgroundUrl: body.backgroundUrl ?? body.background_url ?? null,
      thumbnailUrl: body.thumbnailUrl ?? body.thumbnail_url ?? null,
      canvasData: body.canvasData ?? body.canvas_data,
    });

    return NextResponse.json({ success: true, template });
  } catch (error: any) {
    console.error('Error creating template:', error);
    const status = error.statusCode || 500;
    return NextResponse.json({ error: error.message }, { status });
  }
}

// DELETE: Bulk delete templates
export async function DELETE(req: NextRequest) {
  try {
    const body = await req.json();
    if (!body.ids || !Array.isArray(body.ids)) {
      return NextResponse.json({ error: 'ids array is required' }, { status: 400 });
    }
    await deleteTemplatesBulk(body.ids);
    return NextResponse.json({ success: true, message: `Deleted ${body.ids.length} templates` });
  } catch (error: any) {
    console.error('Error bulk deleting templates:', error);
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}
