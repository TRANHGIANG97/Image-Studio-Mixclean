-- ========================================================
-- Migration: Add category_id to assets table
-- Run this in the Supabase SQL Editor
-- ========================================================

-- 1. Add category_id column if it doesn't exist
ALTER TABLE assets 
ADD COLUMN IF NOT EXISTS category_id UUID REFERENCES categories(id) ON DELETE SET NULL;

-- 2. Create index on category_id for faster lookups
CREATE INDEX IF NOT EXISTS idx_assets_category_id ON assets(category_id);

-- 3. Print confirmation (optional)
SELECT 'Migration successful: category_id added to assets table' as status;
