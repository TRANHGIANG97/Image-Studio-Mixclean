-- ========================================================
-- Migration: Add environment to templates table
-- Run this in the Supabase SQL Editor
-- ========================================================

-- 1. Add environment column with constraints if it doesn't exist
ALTER TABLE templates 
ADD COLUMN IF NOT EXISTS environment TEXT DEFAULT 'all' CHECK (environment IN ('debug', 'release', 'all'));

-- 2. Print confirmation
SELECT 'Migration successful: environment column added to templates table' as status;
