/**
 * Bulk-delete all assets in one or more Media Library folders (Supabase + R2).
 *
 * Default is DRY-RUN — nothing is deleted until you pass --confirm.
 *
 * Usage (from admin_web/):
 *   node scripts/bulk-delete-folder.mjs
 *   node scripts/bulk-delete-folder.mjs --folders=sticker_meme,Sticker_meme
 *   node scripts/bulk-delete-folder.mjs --folders=Sticker_meme --confirm
 *   node scripts/bulk-delete-folder.mjs --via-api --folders=sticker_meme --confirm
 *
 * Requires .env.local:
 *   NEXT_PUBLIC_SUPABASE_URL
 *   SUPABASE_SERVICE_ROLE_KEY
 *   (direct mode also needs R2 vars for storage cleanup)
 *     CLOUDFLARE_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY
 *     R2_BUCKET_NAME, NEXT_PUBLIC_ASSET_CDN_URL
 *
 * npm shortcut:
 *   npm run delete:folder -- --folders=sticker_meme,Sticker_meme --confirm
 */

import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createClient } from '@supabase/supabase-js';
import { S3Client, DeleteObjectCommand } from '@aws-sdk/client-s3';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

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
      if (
        (value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))
      ) {
        value = value.slice(1, -1);
      }
      if (!process.env[key]) process.env[key] = value;
    }
  } catch {
    // .env.local may not exist yet
  }
}

await loadDotEnv(path.resolve(__dirname, '../.env.local'));

const DEFAULT_STICKER_MEME_FOLDERS = ['sticker_meme', 'Sticker_meme'];
const PAGE_SIZE = 500;
const DELETE_BATCH = 100;

function parseArgs(argv) {
  const opts = {
    folders: null,
    confirm: false,
    viaApi: false,
    baseUrl: process.env.ADMIN_WEB_URL || 'http://localhost:3000',
    listOnly: false,
  };

  for (const arg of argv) {
    if (arg === '--confirm') opts.confirm = true;
    else if (arg === '--via-api') opts.viaApi = true;
    else if (arg === '--list') opts.listOnly = true;
    else if (arg.startsWith('--folders=')) {
      opts.folders = arg
        .slice('--folders='.length)
        .split(',')
        .map((f) => f.trim())
        .filter(Boolean);
    } else if (arg.startsWith('--base-url=')) {
      opts.baseUrl = arg.slice('--base-url='.length).replace(/\/$/, '');
    } else if (arg === '--help' || arg === '-h') {
      printHelp();
      process.exit(0);
    }
  }

  if (!opts.folders) opts.folders = [...DEFAULT_STICKER_MEME_FOLDERS];
  return opts;
}

function printHelp() {
  console.log(`Bulk delete assets in Media Library folders.

Options:
  --folders=a,b     Comma-separated folder names (default: sticker_meme,Sticker_meme)
  --confirm         Actually delete (default: dry-run only)
  --via-api         Call DELETE /api/assets/folders on running admin_web dev server
  --base-url=URL    API base URL for --via-api (default: http://localhost:3000)
  --list            Show asset counts per folder and exit

Examples:
  node scripts/bulk-delete-folder.mjs
  node scripts/bulk-delete-folder.mjs --list
  node scripts/bulk-delete-folder.mjs --folders=Sticker_meme --confirm
  npm run delete:folder -- --folders=sticker_meme,Sticker_meme --confirm
`);
}

function requireSupabaseEnv() {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!url || !key) {
    console.error(`
Thiếu biến môi trường trong admin_web/.env.local:
  NEXT_PUBLIC_SUPABASE_URL=...
  SUPABASE_SERVICE_ROLE_KEY=...

Tạo file .env.local (xem hướng dẫn trên dashboard admin_web) rồi chạy lại.
`);
    process.exit(1);
  }
  return createClient(url, key);
}

function getR2Client() {
  const accountId = process.env.CLOUDFLARE_ACCOUNT_ID;
  const accessKeyId = process.env.R2_ACCESS_KEY_ID;
  const secretAccessKey = process.env.R2_SECRET_ACCESS_KEY;
  const bucket = process.env.R2_BUCKET_NAME || 'assets';
  const publicUrl = (process.env.NEXT_PUBLIC_ASSET_CDN_URL || '').replace(/\/$/, '');

  if (!accountId || !accessKeyId || !secretAccessKey) {
    return { client: null, bucket, publicUrl, configured: false };
  }

  const client = new S3Client({
    region: 'auto',
    endpoint: `https://${accountId}.r2.cloudflarestorage.com`,
    credentials: { accessKeyId, secretAccessKey },
  });

  return { client, bucket, publicUrl, configured: true };
}

function extractR2Key(fileUrl, publicUrl) {
  if (!fileUrl || fileUrl === 'placeholder') return null;
  const base = publicUrl.replace(/\/$/, '');
  if (base && fileUrl.startsWith(base)) {
    return fileUrl.substring(base.length).replace(/^\//, '');
  }
  const idx = fileUrl.indexOf('/assets/');
  if (idx !== -1) return fileUrl.substring(idx + 1);
  return null;
}

async function fetchAssetsInFolder(supabase, folder) {
  const all = [];
  let page = 0;
  let hasMore = true;

  while (hasMore) {
    const from = page * PAGE_SIZE;
    const to = from + PAGE_SIZE - 1;
    const { data, error } = await supabase
      .from('assets')
      .select('id, name, folder, file_url')
      .eq('folder', folder)
      .order('created_at', { ascending: true })
      .range(from, to);

    if (error) throw error;
    if (!data || data.length === 0) {
      hasMore = false;
    } else {
      all.push(...data);
      hasMore = data.length === PAGE_SIZE;
      page++;
    }
  }

  return all;
}

async function countByFolders(supabase, folders) {
  const counts = {};
  for (const folder of folders) {
    const { count, error } = await supabase
      .from('assets')
      .select('id', { count: 'exact', head: true })
      .eq('folder', folder)
      .neq('name', '.folder_placeholder');
    if (error) throw error;
    counts[folder] = count ?? 0;
  }
  return counts;
}

async function deleteR2Keys(r2, keys) {
  if (!r2.client || keys.length === 0) return { deleted: 0, skipped: keys.length };
  let deleted = 0;
  for (const key of keys) {
    try {
      await r2.client.send(new DeleteObjectCommand({ Bucket: r2.bucket, Key: key }));
      deleted++;
    } catch (err) {
      console.warn(`  R2 delete failed for ${key}:`, err.message || err);
    }
  }
  return { deleted, skipped: keys.length - deleted };
}

async function deleteAssetBatch(supabase, r2, assets) {
  const ids = assets.map((a) => a.id);
  const keys = [];
  for (const asset of assets) {
    const key = extractR2Key(asset.file_url, r2.publicUrl);
    if (key) keys.push(key);
  }

  const r2Result = await deleteR2Keys(r2, keys);

  const { error } = await supabase.from('assets').delete().in('id', ids);
  if (error) throw error;

  return { dbDeleted: ids.length, r2Deleted: r2Result.deleted };
}

async function deleteFolderDirect(supabase, r2, folder, confirm) {
  const assets = await fetchAssetsInFolder(supabase, folder);
  const realAssets = assets.filter((a) => a.name !== '.folder_placeholder');
  const placeholders = assets.filter((a) => a.name === '.folder_placeholder');

  console.log(`\n📁 ${folder}: ${realAssets.length} asset(s), ${placeholders.length} placeholder(s)`);

  if (!confirm) {
    if (realAssets.length > 0) {
      console.log('  [dry-run] Sẽ xóa:', realAssets.slice(0, 5).map((a) => a.name).join(', '), realAssets.length > 5 ? '...' : '');
    }
    return { folder, dbDeleted: 0, r2Deleted: 0, wouldDelete: realAssets.length + placeholders.length };
  }

  let dbDeleted = 0;
  let r2Deleted = 0;

  for (let i = 0; i < realAssets.length; i += DELETE_BATCH) {
    const batch = realAssets.slice(i, i + DELETE_BATCH);
    const result = await deleteAssetBatch(supabase, r2, batch);
    dbDeleted += result.dbDeleted;
    r2Deleted += result.r2Deleted;
    process.stdout.write(`  Đã xóa ${Math.min(i + DELETE_BATCH, realAssets.length)}/${realAssets.length}\r`);
  }

  if (placeholders.length > 0) {
    const phIds = placeholders.map((p) => p.id);
    const { error } = await supabase.from('assets').delete().in('id', phIds);
    if (error) throw error;
    dbDeleted += phIds.length;
  }

  console.log(`\n  ✓ Xóa xong: ${dbDeleted} bản ghi DB, ${r2Deleted} file R2`);
  return { folder, dbDeleted, r2Deleted, wouldDelete: 0 };
}

async function deleteFolderViaApi(baseUrl, folder, confirm) {
  const url = `${baseUrl}/api/assets/folders?folderName=${encodeURIComponent(folder)}&deleteFiles=true`;
  console.log(`\n📁 ${folder} (via API)`);
  console.log(`  DELETE ${url}`);

  if (!confirm) {
    console.log('  [dry-run] Bỏ qua — thêm --confirm để gọi API');
    return { folder, viaApi: true, skipped: true };
  }

  const res = await fetch(url, { method: 'DELETE' });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  console.log('  ✓ API trả về success');
  return { folder, viaApi: true, success: true };
}

async function main() {
  const opts = parseArgs(process.argv.slice(2));
  requireSupabaseEnv();
  const supabase = createClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL,
    process.env.SUPABASE_SERVICE_ROLE_KEY
  );

  console.log('=== Bulk delete folder assets ===');
  console.log('Mode:', opts.confirm ? 'DELETE (live)' : 'DRY-RUN (preview only)');
  console.log('Folders:', opts.folders.join(', '));

  const counts = await countByFolders(supabase, opts.folders);
  console.log('\nSố asset thực tế trong DB (không tính placeholder):');
  for (const [folder, count] of Object.entries(counts)) {
    console.log(`  ${folder}: ${count}`);
  }

  if (opts.listOnly) return;

  const total = Object.values(counts).reduce((a, b) => a + b, 0);
  if (total === 0) {
    console.log('\nKhông có asset nào để xóa. Kiểm tra tên thư mục (phân biệt hoa/thường).');
    return;
  }

  if (!opts.confirm) {
    console.log(`
\n⚠️  DRY-RUN — chưa xóa gì.
Để xóa thật, chạy lại với --confirm:

  node scripts/bulk-delete-folder.mjs --folders=${opts.folders.join(',')} --confirm

Hoặc qua npm:

  npm run delete:folder -- --folders=${opts.folders.join(',')} --confirm
`);
    return;
  }

  console.log('\n⚠️  --confirm: bắt đầu xóa trong 3 giây... (Ctrl+C để hủy)');
  await new Promise((r) => setTimeout(r, 3000));

  if (opts.viaApi) {
    for (const folder of opts.folders) {
      if ((counts[folder] ?? 0) === 0 && folder !== opts.folders[0]) continue;
      await deleteFolderViaApi(opts.baseUrl, folder, true);
    }
    console.log('\nLưu ý: API server-side có thể chỉ xóa tối đa ~1000 asset/lần (giới hạn Supabase).');
    console.log('Nếu còn asset, chạy lại script hoặc dùng chế độ direct (bỏ --via-api).');
    return;
  }

  const r2 = getR2Client();
  if (!r2.configured) {
    console.warn(`
⚠️  Thiếu biến R2 — chỉ xóa bản ghi Supabase, file trên CDN/R2 có thể còn orphan:
  CLOUDFLARE_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY
`);
  }

  const results = [];
  for (const folder of opts.folders) {
    if ((counts[folder] ?? 0) === 0) {
      console.log(`\n📁 ${folder}: bỏ qua (0 asset)`);
      continue;
    }
    results.push(await deleteFolderDirect(supabase, r2, folder, true));
  }

  console.log('\n=== Hoàn tất ===');
  const totalDb = results.reduce((s, r) => s + (r.dbDeleted || 0), 0);
  const totalR2 = results.reduce((s, r) => s + (r.r2Deleted || 0), 0);
  console.log(`Tổng: ${totalDb} bản ghi DB, ${totalR2} file R2`);
}

main().catch((err) => {
  console.error('\nLỗi:', err.message || err);
  process.exit(1);
});
