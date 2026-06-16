import dotenv from 'dotenv';
import path from 'path';
import { fileURLToPath } from 'url';
import { createClient } from '@supabase/supabase-js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

dotenv.config({ path: path.resolve(__dirname, '../.env.local') });

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;

if (!supabaseUrl || !serviceKey) {
  console.error('Missing env vars');
  process.exit(1);
}

const supabase = createClient(supabaseUrl, serviceKey);

async function checkCounts() {
  const { count: templateCount } = await supabase.from('templates').select('*', { count: 'exact', head: true });
  const { count: categoryCount } = await supabase.from('categories').select('*', { count: 'exact', head: true });
  const { count: assetCount } = await supabase.from('assets').select('*', { count: 'exact', head: true });
  
  console.log(`📊 DB Counts:`);
  console.log(`- Templates: ${templateCount}`);
  console.log(`- Categories: ${categoryCount}`);
  console.log(`- Assets: ${assetCount}`);

  const { data: folders } = await supabase.rpc('get_unique_asset_folders');
  if (folders) {
    console.log('Unique folders (via RPC):', folders);
  } else {
    // Fallback: fetch distinct folders from a sample
    const { data } = await supabase.from('assets').select('folder');
    const folderSet = new Set(data?.map(d => d.folder) || []);
    console.log('Unique folders (via select):', Array.from(folderSet));
  }
}

checkCounts().catch(console.error);
