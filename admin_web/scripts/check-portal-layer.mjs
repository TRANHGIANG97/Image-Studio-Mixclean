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

async function check() {
  const { data: templates, error } = await supabase
    .from('templates')
    .select('*')
    .order('created_at', { ascending: false })
    .limit(1);

  if (error || !templates || templates.length === 0) {
    console.error('Error fetching template:', error);
    return;
  }

  const tpl = templates[0];

  console.log('\n========================================');
  console.log(`TEMPLATE ID: ${tpl.id} | TEMPLATE_ID: ${tpl.template_id} | TITLE: ${tpl.title} | CREATED: ${tpl.created_at}`);
  
  // check canvas_data
  const canvasData = tpl.canvas_data;
  if (canvasData && Array.isArray(canvasData.layers)) {
    console.log('\n--- Layers inside canvas_data (TEXT ONLY) ---');
    canvasData.layers.forEach((layer, idx) => {
      if (layer.type === 'TEXT') {
        console.log(`\n  [Layer ${idx}] Name="${layer.name}"`);
        console.log(`    text: "${layer.payload?.text}"`);
        console.log(`    font: "${layer.payload?.font}"`);
        console.log(`    fontSize: ${layer.payload?.fontSize}`);
        console.log(`    transform:`, JSON.stringify(layer.transform));
        console.log(`    lineHeight: ${layer.payload?.lineHeight}`);
        console.log(`    charSpacing: ${layer.payload?.charSpacing}`);
        console.log(`    fontWeight: "${layer.payload?.fontWeight}"`);
        console.log(`    fontStyle: "${layer.payload?.fontStyle}"`);
      } else {
        console.log(`  [Layer ${idx}] Name="${layer.name}" Type=${layer.type} BlendMode=${layer.payload?.blendMode}`);
      }
    });
  } else {
    console.log('No layers inside canvas_data.');
  }
}

check();
