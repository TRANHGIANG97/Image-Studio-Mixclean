/**
 * Visual dedup + curated sticker final pack builder.
 *
 * Reads classified stickers (safe_reaction + safe_decor) or raw input folder,
 * applies strict allowlists, rejects flags/empty/variants, deduplicates by
 * SHA256 + perceptual hash, and outputs sticker_final/.
 *
 * Usage (from admin_web/):
 *   node scripts/dedupe-sticker-visual.mjs --input=../sticker_cleaned
 *   node scripts/dedupe-sticker-visual.mjs --input=../sticker_cleaned --dry-run
 */

import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  allowlistCategory,
  analyzeImageContent,
  collectAllowlistHexcodes,
  computeDHash,
  csvEscape,
  extractHexFromFilename,
  hammingDistance,
  hasSkinToneModifier,
  isFlagHex,
  matchesAllowlist,
  representativeScore,
  sha256File,
} from './sticker-image-utils.mjs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const DEFAULT_REACTION_ALLOWLIST = path.resolve(__dirname, 'openmoji-reaction-allowlist.json');
const DEFAULT_DECOR_ALLOWLIST = path.resolve(__dirname, 'openmoji-decor-allowlist.json');
const DEFAULT_METADATA = path.resolve(__dirname, 'openmoji.json');

const OUTPUT_BUCKETS = [
  'reaction',
  'decor',
  'duplicates_visual',
  'rejected_flags',
  'rejected_empty',
  'rejected_variants',
  'rejected_uncurated',
];

const PHASH_DISTANCE_THRESHOLD = 2;

function parseArgs(argv) {
  const opts = {
    input: path.resolve(__dirname, '../../sticker_cleaned'),
    output: path.resolve(__dirname, '../../sticker_final'),
    reactionAllowlist: DEFAULT_REACTION_ALLOWLIST,
    decorAllowlist: DEFAULT_DECOR_ALLOWLIST,
    metadata: DEFAULT_METADATA,
    dryRun: false,
  };

  for (const arg of argv) {
    if (arg.startsWith('--input=')) opts.input = path.resolve(process.cwd(), arg.slice('--input='.length));
    if (arg.startsWith('--output=')) opts.output = path.resolve(process.cwd(), arg.slice('--output='.length));
    if (arg.startsWith('--reaction-allowlist=')) {
      opts.reactionAllowlist = path.resolve(process.cwd(), arg.slice('--reaction-allowlist='.length));
    }
    if (arg.startsWith('--decor-allowlist=')) {
      opts.decorAllowlist = path.resolve(process.cwd(), arg.slice('--decor-allowlist='.length));
    }
    if (arg.startsWith('--metadata=')) opts.metadata = path.resolve(process.cwd(), arg.slice('--metadata='.length));
    if (arg === '--dry-run') opts.dryRun = true;
    if (arg === '--help' || arg === '-h') opts.help = true;
  }

  return opts;
}

function printHelp() {
  console.log(`Visual dedup + curated sticker pack builder.

Usage:
  node scripts/dedupe-sticker-visual.mjs [options]

Options:
  --input=PATH              Source (sticker_cleaned or folder with PNGs)
  --output=PATH             Final output root (default: repo/sticker_final)
  --reaction-allowlist=PATH Reaction allowlist JSON (~79 hexcodes)
  --decor-allowlist=PATH    Decor allowlist JSON (~50 hexcodes)
  --metadata=PATH           openmoji.json for group validation
  --dry-run                 Report only — do not move files
  --help                    Show this message
`);
}

async function loadJson(filePath) {
  const raw = await fs.readFile(filePath, 'utf8');
  return JSON.parse(raw);
}

async function ensureDir(dir) {
  await fs.mkdir(dir, { recursive: true });
}

async function moveFile(src, dest, dryRun) {
  if (dryRun) return;
  await ensureDir(path.dirname(dest));
  await fs.rename(src, dest);
}

async function collectPngFiles(dir) {
  const files = [];
  const skipDirs = new Set([
    'remove_brand',
    'duplicates',
    'duplicates_visual',
    'rejected_flags',
    'rejected_empty',
    'rejected_variants',
    'rejected_uncurated',
    'reaction',
    'decor',
  ]);

  async function walk(current, relative = '') {
    const entries = await fs.readdir(current, { withFileTypes: true });
    for (const entry of entries) {
      const full = path.join(current, entry.name);
      const rel = relative ? path.join(relative, entry.name) : entry.name;

      if (entry.isDirectory()) {
        if (skipDirs.has(entry.name)) continue;
        await walk(full, rel);
        continue;
      }

      if (!/\.png$/i.test(entry.name)) continue;
      files.push({ fullPath: full, relativePath: rel, filename: entry.name });
    }
  }

  await walk(dir);
  return files;
}

function buildMetadataIndex(entries) {
  const byHex = new Map();
  for (const entry of entries) {
    if (!entry?.hexcode) continue;
    byHex.set(entry.hexcode.toUpperCase(), entry);
  }
  return byHex;
}

function isExcludedByMetadata(hex, metadataIndex, exclude) {
  const entry = metadataIndex.get(hex);
  if (!entry) return null;

  if ((exclude.groups ?? []).includes(entry.group)) return `group=${entry.group}`;
  if ((exclude.subgroups ?? []).includes(entry.subgroups)) return `subgroups=${entry.subgroups}`;

  for (const keyword of exclude.tagKeywords ?? []) {
    const haystack = `${entry.tags ?? ''} ${entry.openmoji_tags ?? ''} ${entry.annotation ?? ''}`.toLowerCase();
    if (haystack.includes(keyword.toLowerCase())) return `tag keyword: ${keyword}`;
  }

  return null;
}

function classifyFile(file, reactionMap, decorMap, metadataIndex, reactionExclude, decorExclude) {
  const hex = extractHexFromFilename(file.filename);
  const metadataEntry = metadataIndex.get(hex);
  const inReaction = matchesAllowlist(hex, reactionMap);
  const inDecor = matchesAllowlist(hex, decorMap);

  if (isFlagHex(hex)) {
    return { bucket: 'rejected_flags', reason: 'cờ / regional indicator', hex, category: 'flags' };
  }

  const metaReject =
    isExcludedByMetadata(hex, metadataIndex, reactionExclude) ??
    isExcludedByMetadata(hex, metadataIndex, decorExclude);
  if (metaReject?.includes('flag')) {
    return { bucket: 'rejected_flags', reason: metaReject, hex, category: 'flags' };
  }

  if (hasSkinToneModifier(hex)) {
    return { bucket: 'rejected_variants', reason: 'biến thể skin-tone (giữ bản vàng mặc định)', hex, category: 'skin_tone' };
  }

  if (inReaction) {
    return {
      bucket: 'reaction',
      reason: 'trong reaction allowlist',
      hex,
      category: allowlistCategory(hex, reactionMap) ?? 'reaction',
    };
  }

  if (inDecor) {
    const decorMetaReject = isExcludedByMetadata(hex, metadataIndex, decorExclude);
    if (decorMetaReject && !decorMetaReject.includes('flag')) {
      return { bucket: 'rejected_uncurated', reason: decorMetaReject, hex, category: 'decor_reject' };
    }
    return {
      bucket: 'decor',
      reason: 'trong decor allowlist',
      hex,
      category: allowlistCategory(hex, decorMap) ?? 'decor',
    };
  }

  return { bucket: 'rejected_uncurated', reason: 'không trong allowlist reaction/decor', hex, category: 'uncurated' };
}

async function dedupeVisualGroup(files, bucket, opts) {
  const kept = [];
  const duplicates = [];
  const shaGroups = new Map();

  for (const file of files) {
    const sha = await sha256File(file.fullPath);
    file.sha256 = sha;

    if (!shaGroups.has(sha)) shaGroups.set(sha, []);
    shaGroups.get(sha).push(file);
  }

  const shaCanonical = new Map();
  for (const [, group] of shaGroups) {
    const sorted = [...group].sort(
      (a, b) =>
        representativeScore(b, {
          inReactionAllowlist: bucket === 'reaction',
          inDecorAllowlist: bucket === 'decor',
        }) -
        representativeScore(a, {
          inReactionAllowlist: bucket === 'reaction',
          inDecorAllowlist: bucket === 'decor',
        }),
    );
    const canonical = sorted[0];
    shaCanonical.set(canonical.fullPath, canonical);
    for (const file of sorted.slice(1)) {
      duplicates.push({ file, reason: `SHA256 trùng (giữ ${canonical.filename})`, keeper: canonical });
    }
  }

  const uniqueBySha = [...shaCanonical.values()];

  // pHash: chỉ gộp ảnh gần giống (biến thể ZWJ/gender), không gộp hex allowlist khác nhau
  const dhashGroups = [];
  for (const file of uniqueBySha) {
    file.dhash = await computeDHash(file.fullPath);
    let matched = null;

    for (const group of dhashGroups) {
      if (hammingDistance(file.dhash, group[0].dhash) <= PHASH_DISTANCE_THRESHOLD) {
        matched = group;
        break;
      }
    }

    if (!matched) {
      dhashGroups.push([file]);
      kept.push(file);
      continue;
    }

    const sorted = [...matched, file].sort(
      (a, b) =>
        representativeScore(b, {
          inReactionAllowlist: bucket === 'reaction',
          inDecorAllowlist: bucket === 'decor',
        }) -
        representativeScore(a, {
          inReactionAllowlist: bucket === 'reaction',
          inDecorAllowlist: bucket === 'decor',
        }),
    );
    const canonical = sorted[0];

    if (canonical === file) {
      const prev = matched[0];
      const idx = kept.indexOf(prev);
      if (idx >= 0) kept.splice(idx, 1);
      kept.push(file);
      duplicates.push({ file: prev, reason: `pHash gần giống (giữ ${file.filename})`, keeper: file });
      matched[0] = file;
    } else {
      duplicates.push({ file, reason: `pHash gần giống (giữ ${canonical.filename})`, keeper: canonical });
    }
  }

  return { kept, duplicates };
}

async function main() {
  const opts = parseArgs(process.argv.slice(2));
  if (opts.help) {
    printHelp();
    process.exit(0);
  }

  await fs.access(opts.input);

  const reactionAllowlist = await loadJson(opts.reactionAllowlist);
  const decorAllowlist = await loadJson(opts.decorAllowlist);
  const reactionMap = collectAllowlistHexcodes(reactionAllowlist);
  const decorMap = collectAllowlistHexcodes(decorAllowlist);

  let metadataIndex = new Map();
  try {
    const metadata = await loadJson(opts.metadata);
    if (Array.isArray(metadata)) metadataIndex = buildMetadataIndex(metadata);
  } catch {
    console.log('(warn) openmoji.json not found — metadata checks skipped');
  }

  const rawFiles = await collectPngFiles(opts.input);
  console.log(`Input  : ${opts.input}`);
  console.log(`Files  : ${rawFiles.length} PNG`);
  console.log(`Output : ${opts.output}`);

  const classified = [];
  const bucketPreCounts = Object.fromEntries(OUTPUT_BUCKETS.map((b) => [b, 0]));

  for (const file of rawFiles) {
    const cls = classifyFile(
      file,
      reactionMap,
      decorMap,
      metadataIndex,
      reactionAllowlist.exclude ?? {},
      decorAllowlist.exclude ?? {},
    );

    if (cls.bucket === 'reaction' || cls.bucket === 'decor') {
      const analysis = await analyzeImageContent(file.fullPath);
      if (analysis.isEmptyRectangle || analysis.isMostlyEmpty) {
        cls.bucket = 'rejected_empty';
        cls.reason = `ảnh gần trống (${Math.round(analysis.emptyRatio * 100)}% trống/trắng)`;
        cls.category = 'empty';
      }
    }

    classified.push({ ...file, ...cls });
    bucketPreCounts[cls.bucket]++;
  }

  const reportRows = [];
  const bucketCounts = Object.fromEntries(OUTPUT_BUCKETS.map((b) => [b, 0]));

  for (const bucket of ['reaction', 'decor']) {
    const candidates = classified.filter((f) => f.bucket === bucket);
    const { kept, duplicates } = await dedupeVisualGroup(candidates, bucket, opts);

    for (const file of kept) {
      const dest = path.join(opts.output, bucket, file.filename);
      await moveFile(file.fullPath, dest, opts.dryRun);
      reportRows.push({
        filename: file.filename,
        hex: file.hex,
        category: file.category,
        action: 'KEEP',
        reason: file.reason,
        bucket,
        sha256: file.sha256,
        group: '',
        annotation: '',
      });
      bucketCounts[bucket]++;
    }

    for (const { file, reason, keeper } of duplicates) {
      const dest = path.join(opts.output, 'duplicates_visual', file.filename);
      await moveFile(file.fullPath, dest, opts.dryRun);
      reportRows.push({
        filename: file.filename,
        hex: file.hex,
        category: file.category,
        action: 'DUPLICATE',
        reason,
        bucket: 'duplicates_visual',
        sha256: file.sha256 ?? '',
        group: '',
        annotation: keeper?.filename ?? '',
      });
      bucketCounts.duplicates_visual++;
    }
  }

  for (const file of classified) {
    if (file.bucket === 'reaction' || file.bucket === 'decor') continue;

    const dest = path.join(opts.output, file.bucket, file.filename);
    await moveFile(file.fullPath, dest, opts.dryRun);
    reportRows.push({
      filename: file.filename,
      hex: file.hex,
      category: file.category,
      action: 'REJECT',
      reason: file.reason,
      bucket: file.bucket,
      sha256: '',
      group: '',
      annotation: '',
    });
    bucketCounts[file.bucket]++;
  }

  reportRows.sort((a, b) => a.filename.localeCompare(b.filename));

  const csvHeaders = ['filename', 'hex', 'category', 'action', 'reason', 'bucket', 'sha256', 'keeper', 'annotation'];
  const csvLines = [
    csvHeaders.join(','),
    ...reportRows.map((row) =>
      csvHeaders
        .map((h) => {
          if (h === 'keeper') return csvEscape(row.annotation);
          return csvEscape(row[h]);
        })
        .join(','),
    ),
  ];

  const reportPath = path.join(opts.output, 'sticker_final_report.csv');
  if (!opts.dryRun) {
    for (const bucket of OUTPUT_BUCKETS) await ensureDir(path.join(opts.output, bucket));
    await fs.writeFile(reportPath, csvLines.join('\n'), 'utf8');
  } else {
    console.log(`(dry-run) Would write report → ${reportPath}`);
  }

  const uniqueTotal = bucketCounts.reaction + bucketCounts.decor;

  console.log('\n=== STICKER FINAL — TÓM TẮT ===');
  console.log(`Nguồn quét           : ${rawFiles.length} file`);
  console.log(`\nPhân loại sơ bộ (trước dedup):`);
  console.log(`  → reaction         : ${bucketPreCounts.reaction}`);
  console.log(`  → decor            : ${bucketPreCounts.decor}`);
  console.log(`  → rejected_flags   : ${bucketPreCounts.rejected_flags}`);
  console.log(`  → rejected_empty   : ${bucketPreCounts.rejected_empty}`);
  console.log(`  → rejected_variants: ${bucketPreCounts.rejected_variants}`);
  console.log(`  → rejected_uncurated: ${bucketPreCounts.rejected_uncurated}`);
  console.log(`\nKết quả cuối (sau dedup visual):`);
  console.log(`  reaction/          : ${bucketCounts.reaction} (mục tiêu ~60-80)`);
  console.log(`  decor/             : ${bucketCounts.decor} (mục tiêu ~40-60)`);
  console.log(`  TỔNG UNIQUE        : ${uniqueTotal} (mục tiêu ~100-150)`);
  console.log(`  duplicates_visual/ : ${bucketCounts.duplicates_visual}`);
  console.log(`  rejected_flags/    : ${bucketCounts.rejected_flags}`);
  console.log(`  rejected_empty/    : ${bucketCounts.rejected_empty}`);
  console.log(`  rejected_variants/ : ${bucketCounts.rejected_variants}`);
  console.log(`  rejected_uncurated/: ${bucketCounts.rejected_uncurated}`);
  console.log(`\nBáo cáo CSV : ${reportPath}`);
  if (opts.dryRun) console.log('\nChạy lại không có --dry-run để di chuyển file thật.');
}

main().catch((err) => {
  console.error(err.message || err);
  process.exit(1);
});
