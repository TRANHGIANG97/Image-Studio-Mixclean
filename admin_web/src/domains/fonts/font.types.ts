export type FontSource = 'system' | 'cdn' | 'upload';

export interface FontManifestEntry {
  id: string;
  name: string;
  family_slug: string;
  style: string;
  source: FontSource;
  font_url: string | null;
  weights?: string[];
  aliases?: string[];
  created_at?: string;
}

export interface FontsManifestResponse {
  success: true;
  schema_version: number;
  system_fonts: string[];
  fonts: FontManifestEntry[];
}

/** Legacy DB row shape (Supabase fonts table). */
export interface FontRecord {
  id: string;
  name: string;
  family_slug: string;
  font_url: string;
  style?: string;
  created_at: string;
}
