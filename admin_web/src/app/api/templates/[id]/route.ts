import { NextRequest, NextResponse } from 'next/server';
import { getTemplateById, updateTemplate, deleteTemplate } from '@/domains/templates/template.service';
import { writeAuditLog } from '@/lib/auditLog';
import { applyCDN, removeCDN } from '@/lib/cdn-rewriter';

interface Context {
  params: Promise<{ id: string }>;
}

// GET: Fetch details of a single template by ID
export async function GET(_req: NextRequest, context: Context) {
  try {
    const { id } = await context.params;
    let template = await getTemplateById(id);
    template = applyCDN(template);
    return NextResponse.json({ success: true, template });
  } catch (error: any) {
    console.error('Error fetching template details:', error);
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}

// PUT: Update details of a template
export async function PUT(req: NextRequest, context: Context) {
  try {
    const { id } = await context.params;
    let body = await req.json();
    body = removeCDN(body);

    const template = await updateTemplate(id, {
      title:        body.title,
      category_id:  body.category_id,
      status:       body.status,
      environment:  body.environment,
      is_premium:   body.is_premium,
      thumbnail_url: body.thumbnail_url,
      canvas_data:  body.canvas_data,
      fabric_state: body.fabric_state,
    });

    // Audit log for publish/unpublish actions
    if (body.status === 'published') {
      await writeAuditLog({
        action:      'template.publish',
        target_type: 'template',
        target_id:   id,
        details:     { environment: body.environment, title: template.title },
      });
    } else if (body.status === 'draft') {
      await writeAuditLog({
        action:      'template.unpublish',
        target_type: 'template',
        target_id:   id,
        details:     { title: template.title },
      });
    }

    return NextResponse.json({ success: true, template: applyCDN(template) });
  } catch (error: any) {
    console.error('Error updating template:', error);
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}

// DELETE: Delete a template
export async function DELETE(_req: NextRequest, context: Context) {
  try {
    const { id } = await context.params;
    await deleteTemplate(id);

    await writeAuditLog({
      action:      'template.delete',
      target_type: 'template',
      target_id:   id,
    });

    return NextResponse.json({ success: true, message: 'Template deleted successfully' });
  } catch (error: any) {
    console.error('Error deleting template:', error);
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}

