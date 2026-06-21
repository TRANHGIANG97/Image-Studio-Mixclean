import dotenv from 'dotenv';
import path from 'path';
import { fileURLToPath } from 'url';
import { createClient } from '@supabase/supabase-js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

dotenv.config({ path: path.resolve(__dirname, '../.env.local') });

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;

const supabase = createClient(supabaseUrl, serviceKey);

async function test() {
  console.log('Testing inserting multiple folder placeholders...');
  
  // Clean first
  await supabase.from('assets').delete().in('folder', ['test_folder_1', 'test_folder_2']);

  const { data: data1, error: error1 } = await supabase
    .from('assets')
    .insert({
      name: '.folder_placeholder',
      folder: 'test_folder_1',
      file_url: 'placeholder',
    })
    .select();

  console.log('Insert 1:', { data1, error1 });

  const { data: data2, error: error2 } = await supabase
    .from('assets')
    .insert({
      name: '.folder_placeholder',
      folder: 'test_folder_2',
      file_url: 'placeholder',
    })
    .select();

  console.log('Insert 2:', { data2, error2 });

  // Cleanup
  await supabase.from('assets').delete().in('folder', ['test_folder_1', 'test_folder_2']);
}

test();
