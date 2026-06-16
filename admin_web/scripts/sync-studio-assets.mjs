import fs from 'node:fs/promises';
import path from 'node:path';
import { createClient } from '@supabase/supabase-js';

function loadDotEnv(filePath) {
  return fs.readFile(filePath, 'utf8')
    .then((content) => {
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
    })
    .catch(() => {});
}

await loadDotEnv(path.resolve(process.cwd(), '.env.local'));

const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL;
const SUPABASE_SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY;
const STUDIO_ASSETS_ROOT =
  process.env.STUDIO_ASSETS_ROOT ||
  path.resolve(process.cwd(), '..', 'studio_edit', 'src', 'main', 'assets');

if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
  throw new Error('Missing NEXT_PUBLIC_SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY');
}

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

const contentTypeByExt = new Map([
  ['.png', 'image/png'],
  ['.jpg', 'image/jpeg'],
  ['.jpeg', 'image/jpeg'],
  ['.webp', 'image/webp'],
  ['.gif', 'image/gif'],
  ['.svg', 'image/svg+xml'],
  ['.avif', 'image/avif'],
]);

async function listFiles(dir) {
  const entries = await fs.readdir(dir, { withFileTypes: true });
  const files = [];

  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...await listFiles(fullPath));
    } else if (entry.isFile()) {
      files.push(fullPath);
    }
  }

  return files;
}

function toPosixRelative(root, fullPath) {
  return path.relative(root, fullPath).split(path.sep).join('/');
}

async function main() {
  const rootFiles = await listFiles(STUDIO_ASSETS_ROOT);
  console.log(`Found ${rootFiles.length} files under ${STUDIO_ASSETS_ROOT}`);

  for (const filePath of rootFiles) {
    const relPath = toPosixRelative(STUDIO_ASSETS_ROOT, filePath);
    const bucketKey = `studio-edit/${relPath}`;
    const folder = relPath.split('/')[0] || 'uncategorized';
    const ext = path.extname(filePath).toLowerCase();
    const contentType = contentTypeByExt.get(ext) || 'application/octet-stream';
    const fileBuffer = await fs.readFile(filePath);

    const { error: uploadError } = await supabase.storage
      .from('assets')
      .upload(bucketKey, fileBuffer, {
        contentType,
        upsert: true,
      });

    if (uploadError) {
      console.error(`Upload failed for ${relPath}: ${uploadError.message}`);
      continue;
    }

    const { data: { publicUrl } } = supabase.storage.from('assets').getPublicUrl(bucketKey);

    const { data: existing, error: lookupError } = await supabase
      .from('assets')
      .select('id')
      .eq('file_url', publicUrl)
      .maybeSingle();

    if (lookupError) {
      console.error(`DB lookup failed for ${relPath}: ${lookupError.message}`);
      continue;
    }

    const payload = {
      name: path.basename(filePath),
      folder,
      file_url: publicUrl,
      file_size: fileBuffer.length,
      mime_type: contentType,
    };

    if (existing?.id) {
      const { error: updateError } = await supabase
        .from('assets')
        .update(payload)
        .eq('id', existing.id);

      if (updateError) {
        console.error(`DB update failed for ${relPath}: ${updateError.message}`);
        continue;
      }
    } else {
      const { error: insertError } = await supabase
        .from('assets')
        .insert(payload);

      if (insertError) {
        console.error(`DB insert failed for ${relPath}: ${insertError.message}`);
        continue;
      }
    }

    console.log(`Synced ${relPath}`);
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
