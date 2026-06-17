import { createSupabaseAdmin } from '@/lib/supabase';

const DB = () => createSupabaseAdmin();

/**
 * List all fonts.
 */
export async function listFonts() {
  let { data, error } = await DB()
    .from('fonts')
    .select('id, name, family_slug, font_url, style, created_at')
    .order('name', { ascending: true });

  if (error && error.code === '42703') { // undefined_column fallback
    const fallback = await DB()
      .from('fonts')
      .select('id, name, family_slug, font_url, created_at')
      .order('name', { ascending: true });
    
    if (fallback.error) {
      if (fallback.error.code === 'PGRST205') return [];
      if (fallback.error.code === '42501') return [];
      throw fallback.error;
    }
    
    return (fallback.data || []).map(f => ({ ...f, style: 'Chưa phân loại' }));
  }

  if (error) {
    if (error.code === 'PGRST205') {
      console.warn("Table 'public.fonts' does not exist. Run the schema migration to create it.");
      return [];
    }
    if (error.code === '42501') {
      console.warn("Current Supabase role cannot read public.fonts. Returning no cloud fonts until SELECT is granted.");
      return [];
    }
    throw error;
  }

  return data || [];
}

export interface UploadFontInput {
  file: File;
  name: string;
  family_slug: string;
  style?: string;
}

/**
 * Upload a .ttf font to Storage and register in the fonts table.
 */
export async function uploadFont(input: UploadFontInput) {
  const bytes = await input.file.arrayBuffer();
  const buffer = Buffer.from(bytes);

  const cleanSlug = input.family_slug.replace(/[^a-zA-Z0-9]/g, '_').toLowerCase();
  const uniqueKey = `${Date.now()}_${cleanSlug}.ttf`;

  const supabaseAdmin = DB();

  // 1. Upload to Storage
  const { error: uploadError } = await supabaseAdmin.storage
    .from('fonts')
    .upload(uniqueKey, buffer, { contentType: 'font/ttf', upsert: true });

  if (uploadError) {
    throw Object.assign(
      new Error(`Storage upload failed: ${uploadError.message}. Ensure the 'fonts' bucket exists.`),
      { statusCode: 500 }
    );
  }

  // 2. Get public URL
  const { data: { publicUrl } } = supabaseAdmin.storage.from('fonts').getPublicUrl(uniqueKey);

  // 3. Insert metadata
  const insertPayload: any = {
    name: input.name,
    family_slug: input.family_slug.toLowerCase().trim(),
    font_url: publicUrl,
  };
  
  if (input.style) {
    insertPayload.style = input.style;
  }

  const { data, error: dbError } = await supabaseAdmin
    .from('fonts')
    .insert(insertPayload)
    .select()
    .single();

  if (dbError) throw dbError;
  return data;
}
