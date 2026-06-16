import { createClient } from '@supabase/supabase-js';
import { config } from 'dotenv';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
config({ path: path.resolve(__dirname, '..', '.env.local') });

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;

if (!supabaseUrl || !serviceKey) {
  console.error('Missing env vars in .env.local');
  process.exit(1);
}

const supabase = createClient(supabaseUrl, serviceKey);

async function sync() {
  console.log('🔄 Đang quét danh sách file font trong storage...');
  
  // 1. List files from fonts bucket
  const { data: files, error: listError } = await supabase.storage
    .from('fonts')
    .list('', { limit: 100 });

  if (listError) {
    console.error('Lỗi quét file storage:', listError.message);
    process.exit(1);
  }

  const ttfFiles = (files || []).filter(f => f.name.endsWith('.ttf'));
  
  if (ttfFiles.length === 0) {
    console.log('⚠️ Không tìm thấy file .ttf nào trong bucket fonts.');
    return;
  }

  console.log(`Tìm thấy ${ttfFiles.length} file font .ttf. Đang đồng bộ vào database...`);

  let successCount = 0;

  for (const file of ttfFiles) {
    const filename = file.name;
    
    // Clean name: remove prefix timestamp if any, replace _ or - with spaces, capitalize words
    let cleanName = filename.replace(/\.ttf$/, '');
    if (/^\d+_/.test(cleanName)) {
      cleanName = cleanName.replace(/^\d+_/, '');
    }
    const displayName = cleanName
      .replace(/[_-]/g, ' ')
      .split(' ')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');

    const familySlug = cleanName.toLowerCase().replace(/\s+/g, '-').replace(/[_-]+/g, '-');

    // Get public URL
    const { data: { publicUrl } } = supabase.supabaseUrl 
      ? supabase.storage.from('fonts').getPublicUrl(filename)
      : { data: { publicUrl: `${supabaseUrl}/storage/v1/object/public/fonts/${filename}` } };

    // Insert to DB
    const { error: dbError } = await supabase
      .from('fonts')
      .insert({
        name: displayName,
        family_slug: familySlug,
        font_url: publicUrl
      })
      .select();

    if (dbError) {
      if (dbError.code === '23505') {
        console.log(`ℹ️ Bỏ qua: Font "${displayName}" (slug: ${familySlug}) đã có trong DB.`);
      } else {
        console.error(`❌ Lỗi lưu font "${displayName}":`, dbError.message);
      }
    } else {
      console.log(`✅ Đã đồng bộ: Font "${displayName}"`);
      successCount++;
    }
  }

  console.log(`🎉 Hoàn tất! Đã thêm mới thành công ${successCount}/${ttfFiles.length} fonts.`);
}

sync().catch(console.error);
