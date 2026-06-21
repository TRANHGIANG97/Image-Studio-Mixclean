const { createClient } = require('@supabase/supabase-js');
require('dotenv').config({ path: '.env.local' });

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY;

const supabase = createClient(supabaseUrl, supabaseKey);

async function main() {
  const { data, error } = await supabase
    .from('templates')
    .select('*')
    .limit(5);

  if (error) {
    console.error(error);
  } else {
    console.log('Templates count:', data.length);
    data.forEach(t => {
      console.log(`Template ID: ${t.id}, Title: ${t.title}`);
      console.log('Canvas data:', JSON.stringify(t.canvas_data, null, 2));
    });
  }
}

main();
