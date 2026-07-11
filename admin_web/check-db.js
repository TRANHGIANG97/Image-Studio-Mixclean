const { createClient } = require('@supabase/supabase-js');
require('dotenv').config({ path: '.env.local' });

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY;

if (!supabaseUrl || !supabaseKey) {
  console.error('Missing Supabase environment variables');
  process.exit(1);
}

const supabase = createClient(supabaseUrl, supabaseKey);

async function main() {
  console.log('Querying categories table...');
  const { data, error } = await supabase
    .from('categories')
    .select('id, name, slug');

  if (error) {
    console.error('Error:', error);
  } else {
    console.log('Categories:', JSON.stringify(data, null, 2));
  }
}

main();
