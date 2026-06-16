import dotenv from 'dotenv';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

dotenv.config({ path: path.resolve(__dirname, '../.env.local') });

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;

import { createClient } from '@supabase/supabase-js';
const supabase = createClient(supabaseUrl, serviceKey);

async function test() {
  console.log('Testing template update with environment...');
  const { data, error } = await supabase
    .from('templates')
    .update({
      environment: 'debug',
      updated_at: new Date().toISOString(),
    })
    .eq('id', 'a32c859c-6a01-472f-b345-6ec18d9bbd3e')
    .select();

  if (error) {
    console.error('Error updating template:', error);
  } else {
    console.log('Success updating template:', data);
  }
}

test();
