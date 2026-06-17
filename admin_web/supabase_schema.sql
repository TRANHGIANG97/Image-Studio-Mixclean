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
-- First drop to prevent conflict on re-runs
DROP POLICY IF EXISTS "Public Select Access" ON storage.objects;
CREATE POLICY "Public Select Access" 
ON storage.objects FOR SELECT 
USING (bucket_id = 'assets');

-- 4. Fonts Table
CREATE TABLE IF NOT EXISTS fonts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  family_slug TEXT UNIQUE NOT NULL,
  font_url TEXT NOT NULL,
  style TEXT DEFAULT 'Quảng cáo',
  created_at TIMESTAMPTZ DEFAULT now()
);

-- Grant privileges on fonts table to standard Supabase roles
GRANT ALL ON TABLE public.fonts TO postgres;
GRANT ALL ON TABLE public.fonts TO service_role;
GRANT SELECT ON TABLE public.fonts TO anon;
GRANT SELECT ON TABLE public.fonts TO authenticated;


-- Storage bucket 'fonts' configuration
INSERT INTO storage.buckets (id, name, public)
VALUES ('fonts', 'fonts', true)
ON CONFLICT (id) DO NOTHING;

DROP POLICY IF EXISTS "Public Select Fonts" ON storage.objects;
CREATE POLICY "Public Select Fonts"
ON storage.objects FOR SELECT
USING (bucket_id = 'fonts');


-- ========================================================
-- 5. Audit Logs Table (Truy vết hành động nhạy cảm)
-- ========================================================
CREATE TABLE IF NOT EXISTS audit_logs (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID REFERENCES auth.users(id) ON DELETE SET NULL,  -- NULL until Auth is implemented
  actor_label  TEXT NOT NULL DEFAULT 'admin_web',                   -- Human-readable actor name
  action       TEXT NOT NULL,                                       -- e.g. 'template.delete', 'template.publish'
  target_type  TEXT NOT NULL,                                       -- 'template' | 'category' | 'asset'
  target_id    TEXT NOT NULL,                                       -- UUID of the affected resource
  details      JSONB,                                               -- Extra context (title, environment, etc.)
  ip_address   INET,                                                -- Client IP (for future use)
  user_agent   TEXT,                                                -- Client user agent (for future use)
  created_at   TIMESTAMPTZ DEFAULT now()
);

-- Index for fast lookup by target resource
CREATE INDEX IF NOT EXISTS idx_audit_logs_target ON audit_logs(target_type, target_id);
-- Index for user activity timeline
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id ON audit_logs(user_id);
-- Index for time-based queries
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at DESC);

-- Only admin/service_role can read audit logs
GRANT ALL ON TABLE public.audit_logs TO postgres;
GRANT ALL ON TABLE public.audit_logs TO service_role;
-- (No SELECT granted to anon or authenticated — admin only)
