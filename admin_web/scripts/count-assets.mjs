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
  console.log('Testing paginated folder fetch...');
  const allFolders = new Set();
  const defaultFolders = ['backgrounds', 'stickers', 'fonts', 'uncategorized'];
  defaultFolders.forEach(f => allFolders.add(f));

  let page = 0;
  const pageSize = 1000;
  let hasMore = true;

  while (hasMore) {
    console.log(`Fetching page ${page}...`);
    const { data, error } = await supabase
      .from('assets')
      .select('folder')
      .range(page * pageSize, (page + 1) * pageSize - 1);

    if (error) {
      console.error('Error:', error);
      break;
    }

    console.log(`Page ${page} returned ${data?.length || 0} rows.`);

    if (!data || data.length === 0) {
      hasMore = false;
    } else {
      data.forEach((item) => {
        if (item.folder) {
          allFolders.add(item.folder);
        }
      });
      if (data.length < pageSize) {
        hasMore = false;
      } else {
        page++;
      }
    }
  }

  const result = Array.from(allFolders);
  console.log('All unique folders found:', result);
  console.log('Is "obj" in folders?', result.includes('obj'));
}

test();
