/**
 * Audit, classify, and quarantine OpenMoji sticker PNGs.
 *
 * Usage (from admin_web/):
 *   node scripts/classify-sticker-folder.mjs --input=../sticker
 *   node scripts/classify-sticker-folder.mjs --input="../sticker open emoji" --output=../sticker_cleaned
 *   node scripts/classify-sticker-folder.mjs --input=../sticker --dry-run
 *
 * Moves files into quarantine subfolders (never deletes).
 * Generates sticker_audit_report.csv with KEEP / REMOVE / DUPLICATE actions.
 *
 * safe_reaction: ONLY openmoji-reaction-allowlist.json (~79 hexcodes)
 * safe_decor:    ONLY openmoji-decor-allowlist.json (~50 hexcodes)
 * Everything else safe-but-unlisted → rejected_uncurated (not dumped into decor)
 */

import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  allowlistCategory,
  analyzeImageContent,
  collectAllowlistHexcodes,
  csvEscape,
  extractHexFromFilename,
  hasSkinToneModifier,
  isExcludedByPolicy,
  isFlagHex,
  matchesAllowlist,
  normalizeHex,
  normalizeHexForDedup,
  representativeScore,
} from './sticker-image-utils.mjs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const DEFAULT_REACTION_ALLOWLIST = path.resolve(__dirname, 'openmoji-reaction-allowlist.json');
const DEFAULT_DECOR_ALLOWLIST = path.resolve(__dirname, 'openmoji-decor-allowlist.json');
const DEFAULT_METADATA = path.resolve(__dirname, 'openmoji.json');
const OPENMOJI_JSON_URL =
  'https://raw.githubusercontent.com/hfg-gmuend/openmoji/master/data/openmoji.json';

const OUTPUT_BUCKETS = [
  'safe_reaction',
  'safe_decor',
  'remove_brand',
  'duplicates',
  'rejected_flags',
  'rejected_empty',
  'rejected_variants',
  'rejected_uncurated',
];

const BRAND_FILENAME_PATTERNS = [
  /\bapple\b/i,
  /\bwindows\b/i,
  /\bmicrosoft\b/i,
  /\bubuntu\b/i,
  /\bgithub\b/i,
  /\boctocat\b/i,
  /\bgoogle\b/i,
  /\bfacebook\b/i,
  /\bmeta\b/i,
  /\bnike\b/i,
  /\badidas\b/i,
  /\bstarbucks\b/i,
  /\bdisney\b/i,
  /\bmarvel\b/i,
  /\bbrand\b/i,
  /\blogo\b/i,
];

function parseArgs(argv) {
  const opts = {
    input: null,
    output: path.resolve(__dirname, '../../sticker_cleaned'),
    reactionAllowlist: DEFAULT_REACTION_ALLOWLIST,
    decorAllowlist: DEFAULT_DECOR_ALLOWLIST,
    metadata: DEFAULT_METADATA,
    dryRun: false,
  };

  for (const arg of argv) {
    if (arg.startsWith('--input=')) opts.input = path.resolve(process.cwd(), arg.slice('--input='.length));
    if (arg.startsWith('--output=')) opts.output = path.resolve(process.cwd(), arg.slice('--output='.length));
    if (arg.startsWith('--allowlist=')) {
      opts.reactionAllowlist = path.resolve(process.cwd(), arg.slice('--allowlist='.length));
    }
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
  console.log(`Audit and organize OpenMoji sticker PNGs.

Usage:
  node scripts/classify-sticker-folder.mjs --input=<folder> [options]

Options:
  --input=PATH                Source folder with OpenMoji PNGs (required)
  --output=PATH               Cleaned output root (default: repo/sticker_cleaned)
  --reaction-allowlist=PATH   Reaction allowlist JSON (~79 hexcodes)
  --decor-allowlist=PATH      Decor allowlist JSON (~50 hexcodes)
  --allowlist=PATH            Alias for --reaction-allowlist
  --metadata=PATH             openmoji.json path (auto-downloaded if missing)
  --dry-run                   Report only — do not move files
  --help                      Show this message
`);
}

function hasBrandFilenameLocal(filename) {
  return BRAND_FILENAME_PATTERNS.some((re) => re.test(filename));
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
  if (!entry) return { ok: true, reason: 'not in metadata' };

  const exclude = allowlist.exclude ?? {};

  if ((exclude.groups ?? []).includes(entry.group)) {
    return { ok: false, reason: `group=${entry.group}` };
  }
  if ((exclude.subgroups ?? []).includes(entry.subgroups)) {
    return { ok: false, reason: `subgroups=${entry.subgroups}` };
  }
  if (hasBrandTag(entry)) {
    return { ok: false, reason: 'brand/logo tag in metadata' };
  }
  for (const keyword of exclude.tagKeywords ?? []) {
    const haystack = `${entry.tags ?? ''} ${entry.openmoji_tags ?? ''} ${entry.annotation ?? ''}`.toLowerCase();
    if (haystack.includes(keyword.toLowerCase())) {
      return { ok: false, reason: `tag keyword: ${keyword}` };
    }
  }

  return { ok: true, reason: `group=${entry.group}` };
}

function mapGroupToCategory(group, subgroups) {
  if (!group) return 'unknown';
  if (group === 'smileys-emotion') return 'smileys';
  if (group === 'people-body') return subgroups?.includes('hand-fingers') ? 'gestures' : 'people';
  if (group === 'animals-nature') return 'animals_nature';
  if (group === 'food-drink') return 'food_drink';
  if (group === 'travel-places') return 'travel_places';
  if (group === 'activities') return 'activities';
  if (group === 'objects') return 'objects';
  if (group === 'symbols') return 'symbols';
  if (group === 'flags') return 'flags';
  if (group === 'extras-openmoji' || group === 'extras-unicode') return 'extras';
  return group.replace(/-/g, '_');
}

async function loadJson(filePath) {
  const raw = await fs.readFile(filePath, 'utf8');
  return JSON.parse(raw);
}

async function ensureMetadata(metadataPath) {
  try {
    await fs.access(metadataPath);
    return metadataPath;
  } catch {
    console.log(`Downloading openmoji.json → ${metadataPath}`);
    const res = await fetch(OPENMOJI_JSON_URL);
    if (!res.ok) throw new Error(`Failed to download openmoji.json: ${res.status}`);
    const text = await res.text();
    await fs.writeFile(metadataPath, text, 'utf8');
    return metadataPath;
  }
}

async function collectPngFiles(dir) {
  const files = [];
  const skipDirs = new Set(OUTPUT_BUCKETS);

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

async function ensureDir(dir) {
  await fs.mkdir(dir, { recursive: true });
}

async function moveFile(src, dest, dryRun) {
  if (dryRun) return;
  await ensureDir(path.dirname(dest));
  await fs.rename(src, dest);
}

async function resolveInputPath(requested) {
  if (requested) {
    try {
      await fs.access(requested);
      return requested;
    } catch {
      throw new Error(`Input folder not found: ${requested}`);
    }
  }

  const candidates = [
    path.resolve(__dirname, '../../sticker'),
    path.resolve(__dirname, '../../sticker open emoji'),
  ];

  for (const candidate of candidates) {
    try {
      await fs.access(candidate);
      const files = await collectPngFiles(candidate);
      if (files.length > 0) return candidate;
    } catch {
      // try next
    }
  }

  throw new Error('No input folder found. Pass --input=PATH (sticker open emoji is empty — restore from zip or use sticker_cleaned)');
}

function classifyBucket(file, reactionMap, decorMap) {
  const hex = file.hex;

  if (file.removeReason) return { bucket: 'remove_brand', reason: file.removeReason };
  if (isFlagHex(hex)) return { bucket: 'rejected_flags', reason: 'cờ / regional indicator' };
  if (hasSkinToneModifier(hex)) return { bucket: 'rejected_variants', reason: 'biến thể skin-tone' };

  const inReaction = matchesAllowlist(hex, reactionMap);
  const inDecor = matchesAllowlist(hex, decorMap);

  if (inReaction) return { bucket: 'safe_reaction', reason: 'trong reaction allowlist' };
  if (inDecor) return { bucket: 'safe_decor', reason: 'trong decor allowlist' };

  return { bucket: 'rejected_uncurated', reason: 'không trong allowlist reaction/decor' };
}

async function main() {
  const opts = parseArgs(process.argv.slice(2));
  if (opts.help) {
    printHelp();
    process.exit(0);
  }

  const inputPath = await resolveInputPath(opts.input);
  const reactionAllowlist = await loadJson(opts.reactionAllowlist);
  const decorAllowlist = await loadJson(opts.decorAllowlist);
  const reactionMap = collectAllowlistHexcodes(reactionAllowlist);
  const decorMap = collectAllowlistHexcodes(decorAllowlist);

  const metadataPath = await ensureMetadata(opts.metadata);
  const metadata = await loadJson(metadataPath);
  if (!Array.isArray(metadata)) throw new Error('openmoji.json must be an array');
  const metadataIndex = buildMetadataIndex(metadata);

  const rawFiles = await collectPngFiles(inputPath);
  console.log(`Input : ${inputPath}`);
  console.log(`Files : ${rawFiles.length} PNG`);
  console.log(`Metadata: ${metadata.length} entries`);
  console.log(`Reaction allowlist: ${reactionMap.size} hexcodes`);
  console.log(`Decor allowlist   : ${decorMap.size} hexcodes`);

  const enriched = [];
  for (const file of rawFiles) {
    const hex = extractHexFromFilename(file.filename);
    const dedupKey = normalizeHexForDedup(hex);
    const metadataEntry = metadataIndex.get(hex);

    const policyReason =
      isExcludedByPolicy(hex, reactionAllowlist) ?? isExcludedByPolicy(hex, decorAllowlist);
    const metaValidation = validateAgainstMetadata(hex, metadataIndex, {
      exclude: {
        groups: [
          ...new Set([
            ...(reactionAllowlist.exclude?.groups ?? []),
            ...(decorAllowlist.exclude?.groups ?? []),
          ]),
        ],
        subgroups: [
          ...new Set([
            ...(reactionAllowlist.exclude?.subgroups ?? []),
            ...(decorAllowlist.exclude?.subgroups ?? []),
          ]),
        ],
        tagKeywords: [
          ...new Set([
            ...(reactionAllowlist.exclude?.tagKeywords ?? []),
            ...(decorAllowlist.exclude?.tagKeywords ?? []),
          ]),
        ],
      },
    });
    const brandFilename = hasBrandFilenameLocal(file.filename);

    let removeReason = null;
    if (policyReason) removeReason = policyReason;
    else if (!metaValidation.ok) removeReason = metaValidation.reason;
    else if (brandFilename) removeReason = 'brand-looking filename';

    const inReaction = matchesAllowlist(hex, reactionMap);
    const inDecor = matchesAllowlist(hex, decorMap);
    const category = inReaction
      ? allowlistCategory(hex, reactionMap) ?? 'reaction'
      : inDecor
        ? allowlistCategory(hex, decorMap) ?? 'decor'
        : mapGroupToCategory(metadataEntry?.group, metadataEntry?.subgroups);

    enriched.push({
      ...file,
      hex,
      dedupKey,
      inReaction,
      inDecor,
      metadataEntry,
      category,
      removeReason,
    });
  }

  const dedupGroups = new Map();
  for (const file of enriched) {
    if (!dedupGroups.has(file.dedupKey)) dedupGroups.set(file.dedupKey, []);
    dedupGroups.get(file.dedupKey).push(file);
  }

  const reportRows = [];
  const bucketCounts = Object.fromEntries(OUTPUT_BUCKETS.map((b) => [b, 0]));
  const actionCounts = { KEEP: 0, REMOVE: 0, DUPLICATE: 0, REJECT: 0 };

  for (const [, group] of dedupGroups) {
    const sorted = [...group].sort((a, b) => {
      const scoreA = representativeScore(a, { inReactionAllowlist: a.inReaction, inDecorAllowlist: a.inDecor });
      const scoreB = representativeScore(b, { inReactionAllowlist: b.inReaction, inDecorAllowlist: b.inDecor });
      return scoreB - scoreA;
    });

    let canonical = null;
    for (const file of sorted) {
      const cls = classifyBucket(file, reactionMap, decorMap);
      if (cls.bucket === 'safe_reaction' || cls.bucket === 'safe_decor') {
        canonical = file;
        break;
      }
    }
    if (!canonical) canonical = sorted[0];

    for (const file of group) {
      let cls = classifyBucket(file, reactionMap, decorMap);
      let action;

      if (file.removeReason) {
        action = 'REMOVE';
      } else if (
        (cls.bucket === 'safe_reaction' || cls.bucket === 'safe_decor') &&
        file !== canonical &&
        canonical &&
        (canonical.inReaction || canonical.inDecor)
      ) {
        action = 'DUPLICATE';
        cls = {
          bucket: 'duplicates',
          reason: `trùng hex chuẩn hóa ${file.dedupKey} (giữ ${canonical.filename})`,
        };
      } else if (cls.bucket === 'safe_reaction' || cls.bucket === 'safe_decor') {
        const analysis = await analyzeImageContent(file.fullPath);
        if (analysis.isEmptyRectangle || analysis.isMostlyEmpty) {
          action = 'REJECT';
          cls = {
            bucket: 'rejected_empty',
            reason: `ảnh gần trống (${Math.round(analysis.emptyRatio * 100)}% trống/trắng)`,
          };
        } else {
          action = 'KEEP';
        }
      } else {
        action = 'REJECT';
      }

      const destPath = path.join(opts.output, cls.bucket, file.filename);
      await moveFile(file.fullPath, destPath, opts.dryRun);

      reportRows.push({
        filename: file.filename,
        hex: file.hex,
        category: file.category,
        action,
        reason: cls.reason,
        bucket: cls.bucket,
        annotation: file.metadataEntry?.annotation ?? '',
        group: file.metadataEntry?.group ?? '',
      });

      bucketCounts[cls.bucket]++;
      actionCounts[action]++;
    }
  }

  reportRows.sort((a, b) => a.filename.localeCompare(b.filename));

  const csvHeaders = ['filename', 'hex', 'category', 'action', 'reason', 'bucket', 'group', 'annotation'];
  const csvLines = [
    csvHeaders.join(','),
    ...reportRows.map((row) => csvHeaders.map((h) => csvEscape(row[h])).join(',')),
  ];
  const reportPath = path.join(opts.output, 'sticker_audit_report.csv');

  if (!opts.dryRun) {
    await ensureDir(opts.output);
    for (const bucket of OUTPUT_BUCKETS) await ensureDir(path.join(opts.output, bucket));
    await fs.writeFile(reportPath, csvLines.join('\n'), 'utf8');
  } else {
    console.log(`(dry-run) Would write report → ${reportPath}`);
  }

  const uniqueKeep = bucketCounts.safe_reaction + bucketCounts.safe_decor;

  console.log('\n=== TÓM TẮT RÀ SOÁT STICKER (OpenMoji) ===');
  console.log(`Tổng file quét       : ${rawFiles.length}`);
  console.log(`Giữ (KEEP)           : ${actionCounts.KEEP}${opts.dryRun ? ' (dry-run)' : ''}`);
  console.log(`Gỡ bản quyền (REMOVE): ${actionCounts.REMOVE}`);
  console.log(`Trùng (DUPLICATE)    : ${actionCounts.DUPLICATE}`);
  console.log(`Loại (REJECT)        : ${actionCounts.REJECT}`);
  console.log(`\nTheo thư mục đích:`);
  console.log(`  safe_reaction/       : ${bucketCounts.safe_reaction} (allowlist reaction)`);
  console.log(`  safe_decor/          : ${bucketCounts.safe_decor} (allowlist decor)`);
  console.log(`  TỔNG KEEP            : ${uniqueKeep} (mục tiêu ~100-150)`);
  console.log(`  remove_brand/        : ${bucketCounts.remove_brand}`);
  console.log(`  duplicates/          : ${bucketCounts.duplicates}`);
  console.log(`  rejected_flags/      : ${bucketCounts.rejected_flags}`);
  console.log(`  rejected_empty/      : ${bucketCounts.rejected_empty}`);
  console.log(`  rejected_variants/   : ${bucketCounts.rejected_variants}`);
  console.log(`  rejected_uncurated/  : ${bucketCounts.rejected_uncurated}`);
  console.log(`\nBáo cáo CSV : ${reportPath}`);
  console.log(`Thư mục sạch : ${opts.output}`);
  if (opts.dryRun) console.log('\nChạy lại không có --dry-run để di chuyển file thật.');
}

main().catch((err) => {
  console.error(err.message || err);
  process.exit(1);
});
