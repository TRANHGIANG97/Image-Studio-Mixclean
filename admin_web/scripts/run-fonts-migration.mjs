/**
 * Run this SQL in Supabase Dashboard:
 * https://supabase.com/dashboard/project/chsngmfncmvfkjfudnjo/sql/new
 *
 * Or run: node scripts/run-fonts-migration.mjs
 */
import { createClient } from '@supabase/supabase-js';
import { config } from 'dotenv';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
config({ path: path.resolve(__dirname, '..', '.env.local') });

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;

if (!supabaseUrl || !serviceKey) {
  console.error('Missing Supabase env vars in .env.local');
  process.exit(1);
}

const supabase = createClient(supabaseUrl, serviceKey);

async function run() {
  // Check if table exists by trying to query it
  const { error: queryError } = await supabase
    .from('fonts')
    .select('id')
    .limit(1);

  if (queryError && queryError.code === 'PGRST205') {
    console.log(`
❌ Bang 'fonts' chua ton tai.

👉 Mo link nay trong trinh duyet:
   https://supabase.com/dashboard/project/chsngmfncmvfkjfudnjo/sql/new

👉 Paste va chay:

CREATE TABLE IF NOT EXISTS fonts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  family_slug TEXT UNIQUE NOT NULL,
  font_url TEXT NOT NULL,
  created_at TIMESTAMPTZ DEFAULT now()
);

-- Grant privileges on fonts table to standard Supabase roles
GRANT ALL ON TABLE public.fonts TO postgres;
GRANT ALL ON TABLE public.fonts TO service_role;
GRANT SELECT ON TABLE public.fonts TO anon;
GRANT SELECT ON TABLE public.fonts TO authenticated;

INSERT INTO storage.buckets (id, name, public)
VALUES ('fonts', 'fonts', true)
ON CONFLICT (id) DO NOTHING;

DROP POLICY IF EXISTS "Public Select Fonts" ON storage.objects;
CREATE POLICY "Public Select Fonts"
ON storage.objects FOR SELECT
USING (bucket_id = 'fonts');
    `);
    process.exit(1);
  } else if (queryError) {
    console.log('Khong the kiem tra: ', queryError.message);
    process.exit(1);
  } else {
    console.log('✅ Bang fonts da ton tai!');
  }
}

run().catch(console.error);
