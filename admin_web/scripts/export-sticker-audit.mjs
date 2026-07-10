/**
 * Export sticker assets from Supabase to CSV for copyright audit.
 *
 * Usage (from admin_web/):
 *   node scripts/export-sticker-audit.mjs
 *   node scripts/export-sticker-audit.mjs --folder=sticker_meme
 *   node scripts/export-sticker-audit.mjs --output=../sticker_audit.csv
 *
 * Requires .env.local with NEXT_PUBLIC_SUPABASE_URL + SUPABASE_SERVICE_ROLE_KEY
 *
 * Does NOT delete or modify any assets — read-only export.
 */

import dotenv from 'dotenv';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createClient } from '@supabase/supabase-js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

dotenv.config({ path: path.resolve(__dirname, '../.env.local') });

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;

if (!supabaseUrl || !serviceKey) {
  console.error('Missing NEXT_PUBLIC_SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY in .env.local');
  process.exit(1);
}

const supabase = createClient(supabaseUrl, serviceKey);

/** Folders used by mobile app + admin_web sticker gallery */
const STICKER_FOLDERS = [
  'sticker_meme',
  'Sticker_meme',
  'sticker_decor',
  'Sticker_decor',
  'stickers',
  'sticker',
];

const RED_FLAG_PATTERNS = [
  { label: 'celebrity/character', regex: /bean|spider|simpson|hasbulla|tom.?jerry|flork|marvel|disney|pokemon|naruto|mickey|minion/i },
  { label: 'watermark', regex: /watermark|9gag|ifunny|shutterstock|freepik|getty|istock|alamy|depositphotos/i },
  { label: 'brand/logo', regex: /vinai|nike|adidas|apple|google|facebook|instagram|tiktok|starbucks|coca.?cola/i },
  { label: 'meme-keyword', regex: /meme|viral|deep.?fried|reaction.?face/i },
];

function parseArgs(argv) {
  const opts = {
    folder: null,
    output: path.resolve(__dirname, '../sticker_audit_export.csv'),
    format: 'csv',
  };
  for (const arg of argv) {
    if (arg.startsWith('--folder=')) opts.folder = arg.slice('--folder='.length);
    if (arg.startsWith('--output=')) opts.output = path.resolve(process.cwd(), arg.slice('--output='.length));
    if (arg.startsWith('--format=')) opts.format = arg.slice('--format='.length);
  }
  return opts;
}

function csvEscape(value) {
  const str = value == null ? '' : String(value);
  if (/[",\n\r]/.test(str)) return `"${str.replace(/"/g, '""')}"`;
  return str;
}

function guessRiskFromName(name) {
  const hits = RED_FLAG_PATTERNS.filter((p) => p.regex.test(name)).map((p) => p.label);
  if (hits.length > 0) return { autoFlag: 'REVIEW', reasons: hits.join('; ') };
  return { autoFlag: '', reasons: '' };
}

async function fetchAllStickerAssets(folderFilter) {
  const folders = folderFilter ? [folderFilter] : STICKER_FOLDERS;
  const all = [];
  const pageSize = 500;
  let page = 0;
  let hasMore = true;

  while (hasMore) {
    const from = page * pageSize;
    const to = from + pageSize - 1;

    const { data, error } = await supabase
      .from('assets')
      .select('id, name, folder, file_url, file_size, mime_type, source_path, category_id, created_at')
      .in('folder', folders)
      .neq('name', '.folder_placeholder')
      .order('folder', { ascending: true })
      .order('created_at', { ascending: true })
      .range(from, to);

    if (error) throw error;

    if (!data || data.length === 0) {
      hasMore = false;
    } else {
      all.push(...data);
      hasMore = data.length === pageSize;
      page++;
    }
  }

  return all;
}

function toCsvRows(assets) {
  const headers = [
    'id',
    'name',
    'folder',
    'file_url',
    'mime_type',
    'file_size',
    'source_path',
    'created_at',
    'auto_flag',
    'auto_flag_reasons',
    'triage',       // user fills: KEEP | REMOVE | VERIFY
    'source_url',   // user fills
    'license_type', // user fills
    'notes',        // user fills
  ];

  const lines = [headers.join(',')];

  for (const asset of assets) {
    const { autoFlag, reasons } = guessRiskFromName(asset.name || '');
    const row = [
      asset.id,
      asset.name,
      asset.folder,
      asset.file_url,
      asset.mime_type,
      asset.file_size,
      asset.source_path,
      asset.created_at,
      autoFlag,
      reasons,
      '', // triage
      '', // source_url
      '', // license_type
      '', // notes
    ].map(csvEscape);
    lines.push(row.join(','));
  }

  return lines.join('\n');
}

function summarize(assets) {
  const byFolder = {};
  let autoReview = 0;

  for (const asset of assets) {
    byFolder[asset.folder] = (byFolder[asset.folder] || 0) + 1;
    const { autoFlag } = guessRiskFromName(asset.name || '');
    if (autoFlag === 'REVIEW') autoReview++;
  }

  console.log('\n--- Sticker audit export summary ---');
  console.log(`Total assets: ${assets.length}`);
  console.log('By folder:');
  for (const [folder, count] of Object.entries(byFolder).sort()) {
    console.log(`  ${folder}: ${count}`);
  }
  console.log(`Auto-flagged for REVIEW (filename heuristic): ${autoReview}`);
  console.log('Note: auto_flag is a hint only — always verify visually.\n');
}

async function main() {
  const opts = parseArgs(process.argv.slice(2));
  console.log('Fetching sticker assets from Supabase...');
  if (opts.folder) console.log(`Filter: folder=${opts.folder}`);

  const assets = await fetchAllStickerAssets(opts.folder);
  summarize(assets);

  if (assets.length === 0) {
    console.log('No sticker assets found. Check folder names or Supabase connection.');
    return;
  }

  const csv = toCsvRows(assets);
  await fs.writeFile(opts.output, csv, 'utf8');
  console.log(`Exported ${assets.length} rows → ${opts.output}`);
  console.log('\nNext steps:');
  console.log('  1. Open CSV in Excel/Google Sheets');
  console.log('  2. Add preview: =IMAGE(file_url) or open URLs in browser');
  console.log('  3. Fill triage column: KEEP | REMOVE | VERIFY');
  console.log('  4. See STICKER_AUDIT_CHECKLIST.md for visual audit rules');
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
