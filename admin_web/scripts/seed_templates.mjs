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

const CATEGORY_MAP = {
  'digital_life': 'Đời sống số',
  'food_selfie': 'Mê ăn uống',
  'phone_mode': 'Chuyên nghiệp',
};

function getCategoryNameFromFilename(filename) {
  if (filename.startsWith('digital_life')) return CATEGORY_MAP['digital_life'];
  if (filename.startsWith('food_selfie')) return CATEGORY_MAP['food_selfie'];
  if (filename.startsWith('phone_mode')) return CATEGORY_MAP['phone_mode'];
  return null;
}

function generateTitle(filename) {
  return filename
    .replace('.png', '')
    .split('_')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

async function main() {
  await loadDotEnv(path.resolve(process.cwd(), '.env.local'));

  const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const SUPABASE_SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY;

  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
    throw new Error('Missing NEXT_PUBLIC_SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY');
  }

  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

  // 1. Fetch categories
  const { data: categories, error: catError } = await supabase.from('categories').select('id, name');
  if (catError) throw catError;

  const categoryMap = new Map(categories.map(c => [c.name, c.id]));

  // 2. Read images
  const themeplatesDir = 'C:\\Users\\Toshiba\\Desktop\\imageProduction\\scratch\\generated_themeplates';
  const files = await fs.readdir(themeplatesDir);
  const imageFiles = files.filter(f => f.endsWith('.png'));

  console.log(`Found ${imageFiles.length} template images.`);

  for (const filename of imageFiles) {
    const categoryName = getCategoryNameFromFilename(filename);
    if (!categoryName) {
      console.log(`Skipping ${filename} - no mapped category.`);
      continue;
    }

    const categoryId = categoryMap.get(categoryName);
    if (!categoryId) {
      console.log(`Skipping ${filename} - category '${categoryName}' not found in DB. Run sync-default-categories.mjs first.`);
      continue;
    }

    const filePath = path.join(themeplatesDir, filename);
    const fileBuffer = await fs.readFile(filePath);

    // 3. Upload to Supabase Storage
    const storagePath = `templates/${filename}`;
    console.log(`Uploading ${filename}...`);
    const { error: uploadError } = await supabase.storage
      .from('assets')
      .upload(storagePath, fileBuffer, {
        contentType: 'image/png',
        upsert: true,
      });

    if (uploadError) {
      console.error(`Failed to upload ${filename}:`, uploadError);
      continue;
    }

    // 4. Get Public URL
    const { data: publicUrlData } = supabase.storage.from('assets').getPublicUrl(storagePath);
    const publicUrl = publicUrlData.publicUrl;

    // 5. Insert/Update Template
    const templateId = `TPL_${filename.replace('.png', '')}`;
    const title = generateTitle(filename);

    const canvasData = {
      canvas: {
        baseWidth: 1080,
        baseHeight: 1920,
        aspectRatio: "9:16",
        backgroundUrl: publicUrl
      },
      layers: [
        {
          layerId: "placeholder-1",
          type: "PLACEHOLDER_OBJECT",
          transform: {
            anchorX: 0.5,
            anchorY: 0.5,
            scale: 0.85,
            rotation: 0
          },
          payload: {
            imageUrl: publicUrl,
            defaultImageUrl: publicUrl
          }
        }
      ],
      metadata: {
        title: title,
        status: "published"
      }
    };

    const templateData = {
      template_id: templateId,
      category_id: categoryId,
      title: title,
      status: 'published',
      thumbnail_url: publicUrl,
      canvas_data: canvasData,
      updated_at: new Date().toISOString()
    };

    // Upsert template
    const { error: upsertError } = await supabase
      .from('templates')
      .upsert(templateData, { onConflict: 'template_id' });

    if (upsertError) {
      console.error(`Failed to insert template ${title}:`, upsertError);
    } else {
      console.log(`Successfully published template: ${title}`);
    }
  }

  console.log('Template seeding complete!');
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
