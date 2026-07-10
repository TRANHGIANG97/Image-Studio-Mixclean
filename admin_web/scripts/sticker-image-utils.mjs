/**
 * Shared sticker image analysis helpers (sharp + crypto).
 */

import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import sharp from 'sharp';

const SKIN_TONE_PARTS = new Set(['1F3FB', '1F3FC', '1F3FD', '1F3FE', '1F3FF']);
const REGIONAL_INDICATOR_RE = /^1F1E[6-9A-F]/i;

export function normalizeHex(hex) {
  return String(hex).toUpperCase().replace(/\.PNG$/i, '');
}

export function extractHexFromFilename(filename) {
  const base = filename.replace(/\.png$/i, '');
  return normalizeHex(base);
}

export function normalizeHexForDedup(hex) {
  return normalizeHex(hex)
    .split('-')
    .filter((part) => part !== 'FE0F')
    .join('-');
}

export function isRegionalIndicatorHex(hex) {
  const upper = normalizeHex(hex);
  const parts = upper.split('-');
  return parts.some((part) => REGIONAL_INDICATOR_RE.test(part));
}

export function isFlagHex(hex) {
  const upper = normalizeHex(hex);
  const parts = upper.split('-');
  if (parts.length === 2 && parts.every((p) => REGIONAL_INDICATOR_RE.test(p))) return true;
  return isRegionalIndicatorHex(hex);
}

export function hasSkinToneModifier(hex) {
  return normalizeHex(hex)
    .split('-')
    .some((part) => SKIN_TONE_PARTS.has(part));
}

export function stripSkinTone(hex) {
  return normalizeHex(hex)
    .split('-')
    .filter((part) => !SKIN_TONE_PARTS.has(part))
    .join('-');
}

export function isZwjSequence(hex) {
  return normalizeHex(hex).includes('200D');
}

export function collectAllowlistHexcodes(allowlist) {
  const hexToCategory = new Map();
  for (const [key, cat] of Object.entries(allowlist.categories ?? {})) {
    for (const hex of cat.hexcodes ?? []) {
      hexToCategory.set(normalizeHex(hex), key);
    }
  }
  return hexToCategory;
}

export function matchesAllowlist(hex, allowlistMap) {
  const normalized = normalizeHex(hex);
  if (allowlistMap.has(normalized)) return true;
  const deduped = normalizeHexForDedup(normalized);
  if (allowlistMap.has(deduped)) return true;
  return false;
}

export function isExcludedByPolicy(hex, allowlist) {
  const exclude = allowlist.exclude ?? {};
  const upper = normalizeHex(hex);

  if ((exclude.knownBrandHexcodes ?? []).includes(upper)) return 'known brand hexcode';
  if ((exclude.hexPrefixes ?? []).some((prefix) => upper.startsWith(prefix))) {
    return 'private use area prefix (E/F)';
  }

  const primary = upper.split('-')[0];
  if ((exclude.knownBrandHexcodes ?? []).includes(primary)) {
    return 'known brand hexcode (primary segment)';
  }

  return null;
}

export function allowlistCategory(hex, allowlistMap) {
  const normalized = normalizeHex(hex);
  return allowlistMap.get(normalized) ?? allowlistMap.get(normalizeHexForDedup(normalized)) ?? null;
}

export async function sha256File(filePath) {
  const buf = await fs.readFile(filePath);
  return crypto.createHash('sha256').update(buf).digest('hex');
}

export async function computeDHash(filePath) {
  const width = 9;
  const height = 8;
  const { data, info } = await sharp(filePath)
    .flatten({ background: { r: 255, g: 255, b: 255 } })
    .resize(width, height, { fit: 'contain', background: { r: 255, g: 255, b: 255 } })
    .grayscale()
    .raw()
    .toBuffer({ resolveWithObject: true });

  const w = info.width;
  let hash = '';
  for (let y = 0; y < info.height; y++) {
    for (let x = 0; x < w - 1; x++) {
      const left = data[y * w + x];
      const right = data[y * w + x + 1];
      hash += left < right ? '1' : '0';
    }
  }
  return hash;
}

export function hammingDistance(a, b) {
  if (a.length !== b.length) return Infinity;
  let dist = 0;
  for (let i = 0; i < a.length; i++) {
    if (a[i] !== b[i]) dist++;
  }
  return dist;
}

/**
 * Detect mostly-empty renders: >90% transparent/white with almost no visible content.
 * OpenMoji strokes are often dark — count all opaque non-white pixels as content.
 */
export async function analyzeImageContent(filePath) {
  const { data, info } = await sharp(filePath)
    .ensureAlpha()
    .raw()
    .toBuffer({ resolveWithObject: true });

  const total = info.width * info.height;
  let transparent = 0;
  let white = 0;
  let content = 0;

  for (let i = 0; i < data.length; i += 4) {
    const a = data[i + 3];
    const r = data[i];
    const g = data[i + 1];
    const b = data[i + 2];
    if (a < 20) transparent++;
    else if (r > 240 && g > 240 && b > 240) white++;
    else content++;
  }

  const emptyRatio = (transparent + white) / total;
  const contentRatio = content / total;
  const isMostlyEmpty = emptyRatio >= 0.95 && contentRatio < 0.025;
  const isEmptyRectangle = emptyRatio >= 0.9 && contentRatio < 0.035;

  return {
    width: info.width,
    height: info.height,
    emptyRatio,
    contentRatio,
    isMostlyEmpty,
    isEmptyRectangle,
  };
}

export function representativeScore(file, { inReactionAllowlist = false, inDecorAllowlist = false } = {}) {
  const hex = file.hex;
  let score = 0;
  if (!hex.includes('FE0F')) score += 100;
  if (!hasSkinToneModifier(hex)) score += 80;
  if (!isZwjSequence(hex)) score += 60;
  if (inReactionAllowlist) score += 200;
  if (inDecorAllowlist) score += 150;
  const parts = hex.split('-');
  if (parts.length === 1) score += 50;
  score -= hex.length;
  return score;
}

export function csvEscape(value) {
  const str = value == null ? '' : String(value);
  if (/[",\n\r]/.test(str)) return `"${str.replace(/"/g, '""')}"`;
  return str;
}
