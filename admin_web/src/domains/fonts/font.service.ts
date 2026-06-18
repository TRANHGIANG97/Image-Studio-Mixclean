import {
  BUILTIN_FONT_CATALOG,
  FONT_MANIFEST_SCHEMA_VERSION,
  SYSTEM_FONT_SLUGS,
} from '@/domains/fonts/font.catalog';
import { mergeFontManifest } from '@/domains/fonts/font.manifest-utils';
import type { FontManifestEntry, FontRecord, FontsManifestResponse } from '@/domains/fonts/font.types';
import { createSupabaseAdmin } from '@/lib/supabase';

const DB = () => createSupabaseAdmin();

function dbRowToManifestEntry(row: FontRecord): FontManifestEntry {
  return {
    id: row.id,
    name: row.name,
    family_slug: row.family_slug,
    style: row.style || 'Chưa phân loại',
    source: 'upload',
    font_url: row.font_url,
    weights: ['400'],
    aliases: [row.name, row.family_slug, row.name.replace(/\s+/g, '')],
    created_at: row.created_at,
  };
}

/**
 * List all fonts (legacy — returns upload rows only).
 */
export async function listFonts(): Promise<FontRecord[]> {
  let { data, error } = await DB()
    .from('fonts')
    .select('id, name, family_slug, font_url, style, created_at')
    .order('name', { ascending: true });

  if (error && error.code === '42703') {
    const fallback = await DB()
      .from('fonts')
      .select('id, name, family_slug, font_url, created_at')
      .order('name', { ascending: true });

    if (fallback.error) {
      if (fallback.error.code === 'PGRST205') return [];
      if (fallback.error.code === '42501') return [];
      throw fallback.error;
    }

    return (fallback.data || []).map((f) => ({ ...f, style: 'Chưa phân loại' }));
  }

  if (error) {
    if (error.code === 'PGRST205') {
      console.warn("Table 'public.fonts' does not exist. Run the schema migration to create it.");
      return [];
    }
    if (error.code === '42501') {
      console.warn(
        'Current Supabase role cannot read public.fonts. Returning no cloud fonts until SELECT is granted.',
      );
      return [];
    }
    throw error;
  }

  return data || [];
}

/**
 * Full editor font manifest — single source of truth for admin_web + studio_edit.
 */
export async function getFontsManifest(): Promise<FontsManifestResponse> {
  const uploaded = (await listFonts()).map(dbRowToManifestEntry);
  const fonts = mergeFontManifest(BUILTIN_FONT_CATALOG, uploaded);

  return {
    success: true,
    schema_version: FONT_MANIFEST_SCHEMA_VERSION,
    system_fonts: [...SYSTEM_FONT_SLUGS],
    fonts,
  };
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

  const { error: uploadError } = await supabaseAdmin.storage
    .from('fonts')
    .upload(uniqueKey, buffer, { contentType: 'font/ttf', upsert: true });

  if (uploadError) {
    throw Object.assign(
      new Error(`Storage upload failed: ${uploadError.message}. Ensure the 'fonts' bucket exists.`),
      { statusCode: 500 },
    );
  }

  const {
    data: { publicUrl },
  } = supabaseAdmin.storage.from('fonts').getPublicUrl(uniqueKey);

  const insertPayload: Record<string, string> = {
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
