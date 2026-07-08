-- ========================================================
-- Database Schema for imageProduction Admin Web Tool
-- Copy and run this script in the Supabase SQL Editor.
-- ========================================================

-- Enable UUID extension if not already enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Categories Table
CREATE TABLE IF NOT EXISTS categories (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  "order" INTEGER NOT NULL DEFAULT 0,
  slug TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

-- 2. Templates Table
CREATE TABLE IF NOT EXISTS templates (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  template_id TEXT UNIQUE NOT NULL,                       -- Matches Android templateId (e.g. "TPL_abc123")
  category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
  title TEXT NOT NULL,
  status TEXT DEFAULT 'draft' CHECK (status IN ('draft', 'published')),
  environment TEXT DEFAULT 'all' CHECK (environment IN ('debug', 'release', 'all')),
  thumbnail_url TEXT,
  canvas_data JSONB NOT NULL,                             -- Full CloudTemplate JSON structure
  fabric_state JSONB,                                     -- Fabric.js JSON state for builder sessions
  is_premium BOOLEAN DEFAULT false,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);

-- 3. Assets Table
CREATE TABLE IF NOT EXISTS assets (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  folder TEXT DEFAULT 'uncategorized',                   -- E.g. 'backgrounds', 'stickers', 'decorations'
  source_path TEXT UNIQUE,                               -- Original source path from studio_edit/src/main/assets or imported bundle
  file_url TEXT NOT NULL,                                 -- Public CDN / Cloudflare R2 URL
  file_size INTEGER,
  width INTEGER,
  height INTEGER,
  mime_type TEXT,
  category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ DEFAULT now()
);

-- 4. Fonts Table
CREATE TABLE IF NOT EXISTS fonts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  family_slug TEXT UNIQUE NOT NULL,
  font_url TEXT NOT NULL,
  created_at TIMESTAMPTZ DEFAULT now()
);

-- ========================================================
-- INDEXES FOR PERFORMANCE OPTIMIZATION
-- ========================================================

-- Speed up filtering and queries by status and category
CREATE INDEX IF NOT EXISTS idx_templates_status ON templates(status);
CREATE INDEX IF NOT EXISTS idx_templates_category_id ON templates(category_id);

-- Speed up filtering assets by folder
CREATE INDEX IF NOT EXISTS idx_assets_folder ON assets(folder);
CREATE INDEX IF NOT EXISTS idx_assets_source_path ON assets(source_path);
CREATE INDEX IF NOT EXISTS idx_assets_category_id ON assets(category_id);

-- Unique index to guarantee category slugs are unique when not null
CREATE UNIQUE INDEX IF NOT EXISTS idx_categories_slug ON categories(slug) WHERE slug IS NOT NULL;

-- GIN Index on canvas_data JSONB for fast full-text searching inside template json metadata
CREATE INDEX IF NOT EXISTS idx_templates_canvas_data_gin ON templates USING GIN (canvas_data);

-- ========================================================
-- TRIGGERS TO AUTOMATICALLY UPDATE UPDATED_AT
-- ========================================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
   NEW.updated_at = now();
   RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_templates_updated_at ON templates;
CREATE TRIGGER update_templates_updated_at
BEFORE UPDATE ON templates
FOR EACH ROW
EXECUTE PROCEDURE update_updated_at_column();

-- ========================================================
-- STORAGE BUCKETS CONFIGURATION (Supabase Storage)
-- ========================================================

-- Create the public bucket 'assets' if it does not exist with 100MB size limit
INSERT INTO storage.buckets (id, name, public, file_size_limit)
VALUES ('assets', 'assets', true, 104857600)
ON CONFLICT (id) DO UPDATE SET file_size_limit = 104857600;

-- Enable public select access policy on storage.objects for anonymous users
DROP POLICY IF EXISTS "Public Select Access" ON storage.objects;
CREATE POLICY "Public Select Access" 
ON storage.objects FOR SELECT 
USING (bucket_id = 'assets');

-- Storage bucket 'fonts' configuration
INSERT INTO storage.buckets (id, name, public)
VALUES ('fonts', 'fonts', true)
ON CONFLICT (id) DO NOTHING;

DROP POLICY IF EXISTS "Public Select Fonts" ON storage.objects;
CREATE POLICY "Public Select Fonts"
ON storage.objects FOR SELECT
USING (bucket_id = 'fonts');

-- Allow all uploads/inserts in storage.objects for authenticated/anon users (required for admin tool upload)
DROP POLICY IF EXISTS "Allow All Uploads" ON storage.objects;
CREATE POLICY "Allow All Uploads"
ON storage.objects FOR INSERT
WITH CHECK (bucket_id IN ('assets', 'fonts'));

DROP POLICY IF EXISTS "Allow All Updates" ON storage.objects;
CREATE POLICY "Allow All Updates"
ON storage.objects FOR UPDATE
USING (bucket_id IN ('assets', 'fonts'));

-- Grant privileges on fonts table to standard Supabase roles
GRANT ALL ON TABLE public.fonts TO postgres;
GRANT ALL ON TABLE public.fonts TO service_role;
GRANT SELECT ON TABLE public.fonts TO anon;
GRANT SELECT ON TABLE public.fonts TO authenticated;

-- Grant privileges on categories table to standard Supabase roles
GRANT ALL ON TABLE public.categories TO postgres;
GRANT ALL ON TABLE public.categories TO service_role;
GRANT SELECT ON TABLE public.categories TO anon;
GRANT SELECT ON TABLE public.categories TO authenticated;

-- Grant privileges on templates table to standard Supabase roles
GRANT ALL ON TABLE public.templates TO postgres;
GRANT ALL ON TABLE public.templates TO service_role;
GRANT SELECT ON TABLE public.templates TO anon;
GRANT SELECT ON TABLE public.templates TO authenticated;

-- Grant privileges on assets table to standard Supabase roles
GRANT ALL ON TABLE public.assets TO postgres;
GRANT ALL ON TABLE public.assets TO service_role;
GRANT SELECT ON TABLE public.assets TO anon;
GRANT SELECT ON TABLE public.assets TO authenticated;
