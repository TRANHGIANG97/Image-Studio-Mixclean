-- ========================================================
-- Migration: Add `slug` column to categories table
-- Run this in Supabase SQL Editor.
-- ========================================================

-- 1. Thêm cột slug (có thể null để tương thích ngược)
ALTER TABLE categories ADD COLUMN IF NOT EXISTS slug TEXT;

-- 2. Tạo unique index để đảm bảo không bị trùng slug
CREATE UNIQUE INDEX IF NOT EXISTS idx_categories_slug
  ON categories(slug) WHERE slug IS NOT NULL;

-- 3. Điền dữ liệu slug cho các category hiện có theo tên tiếng Việt
--    Chỉnh sửa lại nếu tên category của bạn khác với bên dưới.

UPDATE categories SET slug = 'professional'
  WHERE name ILIKE '%thời trang%' OR name ILIKE '%chuyên nghiệp%';

UPDATE categories SET slug = 'cosmetics'
  WHERE name ILIKE '%mỹ phẩm%';

UPDATE categories SET slug = 'digital_life'
  WHERE name ILIKE '%đời sống số%';

UPDATE categories SET slug = 'selfie_food'
  WHERE name ILIKE '%mê ăn uống%' OR name ILIKE '%ẩm thực%' OR name ILIKE '%food%';

-- 4. Kiểm tra kết quả
SELECT id, name, "order", slug FROM categories ORDER BY "order";
