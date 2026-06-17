import { NextRequest, NextResponse } from 'next/server';
import { importTemplatePsd } from '@/domains/templates/psd-import.service';
import { createSupabaseAdmin } from '@/lib/supabase';
import { randomUUID } from 'crypto';

export const runtime = 'nodejs';

export async function GET(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url);
    const fileName = searchParams.get('fileName') || 'import.psd';

    const safeName = fileName.replace(/[^\w.-]+/g, '_');
    const path = `temp-psd/${Date.now()}_${randomUUID()}_${safeName}`;

    const supabaseAdmin = createSupabaseAdmin();

    // Auto-update the bucket's file size limit to 100MB (104,857,600 bytes)
    try {
      await supabaseAdmin.storage.updateBucket('assets', {
        public: true,
        fileSizeLimit: 104857600,
      });
    } catch (bucketErr) {
      console.warn('Failed to auto-update assets bucket file size limit:', bucketErr);
    }

    const { data, error } = await supabaseAdmin.storage
      .from('assets')
      .createSignedUploadUrl(path);

    if (error) {
      console.error('Failed to create signed upload URL:', error);
      return NextResponse.json({ error: error.message }, { status: 500 });
    }

    return NextResponse.json({
      signedUrl: data.signedUrl,
      storagePath: path,
    });
  } catch (error: any) {
    console.error('Error generating signed URL:', error);
    return NextResponse.json({ error: error.message || 'Internal Server Error' }, { status: 500 });
  }
}

export async function POST(req: NextRequest) {
  let storagePath: string | null = null;
  const supabaseAdmin = createSupabaseAdmin();
  try {
    const contentType = req.headers.get('content-type') || '';
    let categoryId: string | null = null;
    let templateId: string | null = null;
    let title: string | null = null;
    let fileBuffer: ArrayBuffer;
    let fileName: string;

    if (contentType.includes('application/json')) {
      const body = await req.json();
      storagePath = body.storagePath as string | null;
      categoryId = body.categoryId as string | null;
      templateId = body.templateId as string | null;
      title = body.title as string | null;

      if (!storagePath) {
        return NextResponse.json({ error: 'No storage path provided' }, { status: 400 });
      }

      const { data: fileData, error: downloadError } = await supabaseAdmin.storage
        .from('assets')
        .download(storagePath);

      if (downloadError) {
        console.error('Failed to download PSD file from storage:', downloadError);
        return NextResponse.json({ error: `Failed to download file: ${downloadError.message}` }, { status: 400 });
      }

      fileBuffer = await fileData.arrayBuffer();
      fileName = storagePath.split('/').pop() || 'import.psd';
    } else {
      const formData = await req.formData();
      const file = formData.get('file') as File | null;
      categoryId = formData.get('categoryId') as string | null;
      templateId = formData.get('templateId') as string | null;
      title = formData.get('title') as string | null;

      if (!file) {
        return NextResponse.json({ error: 'No PSD file provided' }, { status: 400 });
      }

      fileBuffer = await file.arrayBuffer();
      fileName = file.name || 'import.psd';
    }

    const lowered = fileName.toLowerCase();
    if (!lowered.endsWith('.psd') && !lowered.endsWith('.psb')) {
      return NextResponse.json({ error: 'Only .psd or .psb files are supported' }, { status: 400 });
    }

    const template = await importTemplatePsd({
      fileBuffer,
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
  } finally {
    if (storagePath) {
      try {
        await supabaseAdmin.storage.from('assets').remove([storagePath]);
      } catch (cleanupErr) {
        console.warn('Failed to clean up temporary PSD file:', cleanupErr);
      }
    }
  }
}
