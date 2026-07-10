/**
 * Import curated OpenMoji PNG stickers for mobile sticker_meme folder.
 *
 * Usage (from admin_web/):
 *   node scripts/import-openmoji-stickers.mjs --input=./openmoji-72x72-color
 *   node scripts/import-openmoji-stickers.mjs --input=./openmoji-72x72-color.zip
 *   node scripts/import-openmoji-stickers.mjs --input=./openmoji-72x72-color --metadata=./openmoji.json
 *   node scripts/import-openmoji-stickers.mjs --input=./openmoji-72x72-color --output=./sticker_meme_import
 *
 * Does NOT upload to R2 — copies PNGs locally for manual upload via Asset Library.
 */

import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import JSZip from 'jszip';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const DEFAULT_ALLOWLIST = path.resolve(__dirname, 'openmoji-reaction-allowlist.json');
const DEFAULT_OUTPUT = path.resolve(__dirname, '../sticker_meme_import');

function parseArgs(argv) {
  const opts = {
    input: null,
    output: DEFAULT_OUTPUT,
    allowlist: DEFAULT_ALLOWLIST,
    metadata: null,
    dryRun: false,
  };

  for (const arg of argv) {
    if (arg.startsWith('--input=')) opts.input = path.resolve(process.cwd(), arg.slice('--input='.length));
    if (arg.startsWith('--output=')) opts.output = path.resolve(process.cwd(), arg.slice('--output='.length));
    if (arg.startsWith('--allowlist=')) opts.allowlist = path.resolve(process.cwd(), arg.slice('--allowlist='.length));
    if (arg.startsWith('--metadata=')) opts.metadata = path.resolve(process.cwd(), arg.slice('--metadata='.length));
    if (arg === '--dry-run') opts.dryRun = true;
    if (arg === '--help' || arg === '-h') opts.help = true;
  }

  return opts;
}

function printHelp() {
  console.log(`Import curated OpenMoji PNGs for sticker_meme upload.

Usage:
  node scripts/import-openmoji-stickers.mjs --input=<folder-or-zip> [options]

Options:
  --input=PATH       Extracted OpenMoji color PNG folder OR .zip file (required)
  --output=PATH      Output folder (default: admin_web/sticker_meme_import)
  --allowlist=PATH   JSON allowlist (default: scripts/openmoji-reaction-allowlist.json)
  --metadata=PATH    Optional openmoji.json for group/tag safety filtering
  --dry-run          Print plan without copying files
  --help             Show this message

Examples:
  node scripts/import-openmoji-stickers.mjs --input=../downloads/openmoji-72x72-color.zip
  node scripts/import-openmoji-stickers.mjs --input=../downloads/openmoji-72x72-color --metadata=../openmoji.json
`);
}

function normalizeHex(hex) {
  return String(hex).toUpperCase().replace(/\.PNG$/i, '');
}

function collectAllowlistHexcodes(allowlist) {
  const fromCategories = Object.values(allowlist.categories ?? {}).flatMap((cat) => cat.hexcodes ?? []);
  const unique = [...new Set(fromCategories.map(normalizeHex))];
  return unique;
}

function isExcludedByPolicy(hex, allowlist) {
  const exclude = allowlist.exclude ?? {};
  const upper = normalizeHex(hex);

  if ((exclude.knownBrandHexcodes ?? []).includes(upper)) return 'known brand hexcode';
  if ((exclude.hexPrefixes ?? []).some((prefix) => upper.startsWith(prefix))) return 'private use area prefix';

  return null;
}

function hasBrandTag(entry) {
  const haystack = `${entry.tags ?? ''} ${entry.openmoji_tags ?? ''} ${entry.annotation ?? ''}`.toLowerCase();
  return /\bbrand\b|\blogo\b/.test(haystack);
}

function buildMetadataIndex(entries) {
  const byHex = new Map();
  for (const entry of entries) {
    if (!entry?.hexcode) continue;
    byHex.set(normalizeHex(entry.hexcode), entry);
  }
  return byHex;
}

function validateAgainstMetadata(hex, metadataIndex, allowlist) {
  const entry = metadataIndex.get(hex);
  if (!entry) return { ok: true, reason: 'not in metadata (allowlist only)' };

  const exclude = allowlist.exclude ?? {};

  if ((exclude.groups ?? []).includes(entry.group)) {
    return { ok: false, reason: `group=${entry.group}` };
  }
  if ((exclude.subgroups ?? []).includes(entry.subgroups)) {
    return { ok: false, reason: `subgroups=${entry.subgroups}` };
  }
  if (hasBrandTag(entry)) {
    return { ok: false, reason: 'brand/logo tag' };
  }
  for (const keyword of exclude.tagKeywords ?? []) {
    const haystack = `${entry.tags ?? ''} ${entry.openmoji_tags ?? ''} ${entry.annotation ?? ''}`.toLowerCase();
    if (haystack.includes(keyword.toLowerCase())) {
      return { ok: false, reason: `tag keyword: ${keyword}` };
    }
  }

  return { ok: true, reason: `group=${entry.group}` };
}

async function loadJson(filePath) {
  const raw = await fs.readFile(filePath, 'utf8');
  return JSON.parse(raw);
}

async function collectPngMapFromDirectory(dir) {
  const map = new Map();

  async function walk(current) {
    const entries = await fs.readdir(current, { withFileTypes: true });
    for (const entry of entries) {
      const full = path.join(current, entry.name);
      if (entry.isDirectory()) {
        await walk(full);
        continue;
      }
      if (!/\.png$/i.test(entry.name)) continue;
      const hex = normalizeHex(path.basename(entry.name, path.extname(entry.name)));
      if (!map.has(hex)) map.set(hex, full);
    }
  }

  await walk(dir);
  return map;
}

async function collectPngMapFromZip(zipPath) {
  const buffer = await fs.readFile(zipPath);
  const zip = await JSZip.loadAsync(buffer);
  const map = new Map();

  for (const [relativePath, file] of Object.entries(zip.files)) {
    if (file.dir) continue;
    if (!/\.png$/i.test(relativePath)) continue;
    const base = path.basename(relativePath, path.extname(relativePath));
    const hex = normalizeHex(base);
    const data = await file.async('nodebuffer');
    if (!map.has(hex)) map.set(hex, data);
  }

  return { map, source: 'zip-buffer' };
}

async function resolveInputPngMap(inputPath) {
  const stat = await fs.stat(inputPath);
  if (stat.isDirectory()) {
    const map = await collectPngMapFromDirectory(inputPath);
    return { map, source: 'directory', tempDir: null };
  }

  if (/\.zip$/i.test(inputPath)) {
    const { map } = await collectPngMapFromZip(inputPath);
    return { map, source: 'zip', tempDir: null };
  }

  throw new Error(`Input must be a directory or .zip file: ${inputPath}`);
}

async function ensureDir(dir) {
  await fs.mkdir(dir, { recursive: true });
}

async function main() {
  const opts = parseArgs(process.argv.slice(2));
  if (opts.help || !opts.input) {
    printHelp();
    process.exit(opts.input ? 0 : 1);
  }

  const allowlist = await loadJson(opts.allowlist);
  const wanted = collectAllowlistHexcodes(allowlist);

  let metadataIndex = null;
  if (opts.metadata) {
    const metadata = await loadJson(opts.metadata);
    if (!Array.isArray(metadata)) {
      throw new Error(`Metadata must be a JSON array (openmoji.json): ${opts.metadata}`);
    }
    metadataIndex = buildMetadataIndex(metadata);
    console.log(`Loaded metadata: ${metadata.length} entries from ${opts.metadata}`);
  }

  const { map: pngMap, source } = await resolveInputPngMap(opts.input);
  console.log(`Input: ${opts.input} (${source}, ${pngMap.size} PNG files indexed)`);

  const copied = [];
  const skippedPolicy = [];
  const skippedMetadata = [];
  const missing = [];

  for (const hex of wanted) {
    const policyReason = isExcludedByPolicy(hex, allowlist);
    if (policyReason) {
      skippedPolicy.push({ hex, reason: policyReason });
      continue;
    }

    if (metadataIndex) {
      const validation = validateAgainstMetadata(hex, metadataIndex, allowlist);
      if (!validation.ok) {
        skippedMetadata.push({ hex, reason: validation.reason });
        continue;
      }
    }

    const src = pngMap.get(hex);
    if (!src) {
      missing.push(hex);
      continue;
    }

    const destName = `openmoji_${hex.toLowerCase()}.png`;
    const destPath = path.join(opts.output, destName);

    if (!opts.dryRun) {
      await ensureDir(opts.output);
      if (Buffer.isBuffer(src)) {
        await fs.writeFile(destPath, src);
      } else {
        await fs.copyFile(src, destPath);
      }
    }

    copied.push({ hex, destName });
  }

  console.log('\n=== OpenMoji import summary ===');
  console.log(`Allowlist target : ${wanted.length}`);
  console.log(`Copied           : ${copied.length}${opts.dryRun ? ' (dry-run)' : ''}`);
  console.log(`Missing PNG      : ${missing.length}`);
  console.log(`Skipped (policy) : ${skippedPolicy.length}`);
  if (metadataIndex) console.log(`Skipped (meta)   : ${skippedMetadata.length}`);
  console.log(`Output folder    : ${opts.output}`);

  if (missing.length > 0) {
    console.log('\nMissing files (not found in input):');
    for (const hex of missing) console.log(`  - ${hex}.png`);
  }

  if (skippedMetadata.length > 0) {
    console.log('\nSkipped by metadata filter:');
    for (const item of skippedMetadata) console.log(`  - ${item.hex}: ${item.reason}`);
  }

  if (!opts.dryRun && copied.length > 0) {
    console.log('\nNext step: upload files via admin_web → Assets → folder sticker_meme');
    console.log('Suggested naming already applied: openmoji_{hex}.png');
  }

  if (copied.length === 0) {
    process.exitCode = 2;
  }
}

main().catch((err) => {
  console.error(err.message || err);
  process.exit(1);
});
