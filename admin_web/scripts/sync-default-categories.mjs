import fs from 'node:fs/promises';
import path from 'node:path';
import { createClient } from '@supabase/supabase-js';

async function loadDotEnv(filePath) {
  try {
    const content = await fs.readFile(filePath, 'utf8');
    for (const line of content.split(/\r?\n/)) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      const eqIndex = trimmed.indexOf('=');
      if (eqIndex === -1) continue;
      const key = trimmed.slice(0, eqIndex).trim();
      let value = trimmed.slice(eqIndex + 1).trim();
      if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
        value = value.slice(1, -1);
      }
      if (!process.env[key]) {
        process.env[key] = value;
      }
    }
  } catch {}
}

await loadDotEnv(path.resolve(process.cwd(), '.env.local'));

const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL;
const SUPABASE_SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY;

if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
  throw new Error('Missing NEXT_PUBLIC_SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY');
}

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

const DEFAULT_CATEGORIES = [
  { name: 'Chuyên nghiệp', order: 0 },
  { name: 'Mỹ Phẩm', order: 1 },
  { name: 'Đời sống số', order: 2 },
  { name: 'Mê ăn uống', order: 3 },
];

function normalizeName(name) {
  return name
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();
}

async function main() {
  const { data: existing, error: fetchError } = await supabase
    .from('categories')
    .select('id, name, "order"');

  if (fetchError) {
    throw fetchError;
  }

  const existingByName = new Map(
    (existing || []).map((item) => [normalizeName(item.name), item])
  );

  for (const category of DEFAULT_CATEGORIES) {
    const match = existingByName.get(normalizeName(category.name));
    if (match) {
      const { error: updateError } = await supabase
        .from('categories')
        .update({
          name: category.name,
          order: category.order,
        })
        .eq('id', match.id);

      if (updateError) {
        console.error(`Failed to update category ${category.name}: ${updateError.message}`);
      } else {
        console.log(`Updated category: ${category.name}`);
      }
      continue;
    }

    const { error: insertError } = await supabase
      .from('categories')
      .insert({
        name: category.name,
        order: category.order,
      });

    if (insertError) {
      console.error(`Failed to insert category ${category.name}: ${insertError.message}`);
    } else {
      console.log(`Inserted category: ${category.name}`);
    }
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
