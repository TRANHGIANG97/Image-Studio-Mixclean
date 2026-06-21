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
  console.log('Fetching all folder placeholders...');
  const { data, error } = await supabase
    .from('assets')
    .select('*')
    .eq('name', '.folder_placeholder');

  if (error) {
    console.error('Error fetching placeholders:', error);
  } else {
    console.log('Folder placeholders:', data);
  }
}

test();
