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
  console.log('Querying assets table...');
  const { data, error } = await supabase
    .from('assets')
    .select('*')
    .limit(10);

  if (error) {
    console.error('Error:', error);
  } else {
    console.log('Assets count:', data.length);
    console.log('Sample data:', JSON.stringify(data, null, 2));
  }
}

main();
