import Psd from '@webtoon/psd';
import { CloudLayer } from '@/types/cloud-template';
import { classifyPsdLayer, PsdLayerSignals } from '@/lib/psd/layer-classifier';
import { pixelsToBlob, pixelsToThumbnailBlob } from '@/lib/psd/rasterize-service';
import {
  createTimedPsdLogger,
  formatBytes,
  formatDurationMs,
  type PsdImportLogger,
} from '@/lib/psd/import-log';

export interface ExtractedLayer {
  layerId: string;
  type: 'TEXT' | 'IMAGE';
  zIndex: number;
  transform: {
    anchorX: number;
    anchorY: number;
    scale: number;
    rotation: number;
  };
  payload: any;
  imageBlob?: Blob | null;
  fileName?: string;
  name: string;
}

export interface ClientPsdImportResult {
  templateId: string;
  categoryId: string;
  title: string;
  canvasWidth: number;
  canvasHeight: number;
  aspectRatio: string;
  thumbnailBlob: Blob | null;
  backgroundUrl: string | null;
  backgroundBlob: Blob | null;
  backgroundName?: string;
  layers: ExtractedLayer[];
  warnings?: string[];
}

function gcd(a: number, b: number): number {
  return b === 0 ? a : gcd(b, a % b);
}

function uuidv4() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

function sanitizeLabel(input: string, fallback: string) {
  const cleaned = input
    .replace(/\.[^.]+$/, '')
    .replace(/[_-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  return cleaned || fallback;
}

function mapPsdBlendMode(psdBlend: string | undefined | null): string {
  if (!psdBlend) return 'normal';
  const cleaned = psdBlend.trim().toLowerCase();
  switch (cleaned) {
    case 'norm':
    case 'pass':
      return 'normal';
    case 'mul':
    case 'mul ':
      return 'multiply';
    case 'scrn':
      return 'screen';
    case 'over':
      return 'overlay';
    case 'dark':
      return 'darken';
    case 'lite':
      return 'lighten';
    case 'div':
    case 'div ':
      return 'color-dodge';
    case 'idiv':
      return 'color-burn';
    case 'hlit':
      return 'hard-light';
    case 'slit':
      return 'soft-light';
    case 'diff':
      return 'difference';
    case 'smud':
      return 'exclusion';
    case 'hue':
    case 'hue ':
      return 'hue';
    case 'sat':
    case 'sat ':
      return 'saturation';
    case 'colr':
      return 'color';
    case 'lum':
    case 'lum ':
      return 'luminosity';
    case 'lddg':
      return 'linear-dodge';
    case 'lbrn':
      return 'linear-burn';
    default:
      return 'normal';
  }
}

function toArgb(r: number, g: number, b: number, a = 255) {
  return ((a & 0xff) << 24) | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
}

function normalizeOpacity(value: number | undefined | null) {
  if (value === undefined || value === null) return 1;
  if (value <= 1) return Math.max(0, Math.min(1, value));
  return Math.max(0, Math.min(1, value / 255));
}

function isUniformRgba(raw: Uint8ClampedArray) {
  if (!raw || raw.length < 8) return false;
  const r = raw[0];
  const g = raw[1];
  const b = raw[2];
  const a = raw[3];
  for (let i = 4; i < raw.length; i += 4) {
    if (raw[i] !== r || raw[i + 1] !== g || raw[i + 2] !== b || raw[i + 3] !== a) {
      return false;
    }
  }
  return true;
}

function inferTextColor(rgba: Uint8ClampedArray) {
  let bestIndex = -1;
  let bestAlpha = -1;

  for (let i = 0; i < rgba.length; i += 4) {
    const alpha = rgba[i + 3] ?? 0;
    if (alpha > bestAlpha) {
      bestAlpha = alpha;
      bestIndex = i;
      if (alpha >= 250) break;
    }
  }

  if (bestIndex < 0 || bestAlpha <= 0) {
    return toArgb(0, 0, 0, 255);
  }

  return toArgb(
    rgba[bestIndex] ?? 0,
    rgba[bestIndex + 1] ?? 0,
    rgba[bestIndex + 2] ?? 0,
    rgba[bestIndex + 3] ?? 255
  );
}

function tryExtractTextScale(node: any): number {
  try {
    const tySh = node.additionalProperties?.TySh;
    if (tySh) {
      const xx = typeof tySh.transformXX === 'number' ? tySh.transformXX : 1;
      const xy = typeof tySh.transformXY === 'number' ? tySh.transformXY : 0;
      const scale = Math.sqrt(xx * xx + xy * xy);
      if (scale > 0) return scale;
    }
  } catch (e) {
    console.warn('[PSD_IMPORT_DEBUG] failed to parse text scale:', e);
  }
  return 1;
}

function tryExtractFontSize(node: any): number | null {
  try {
    const textProps = node.textProperties || node.additionalProperties?.TypeToolObject?.textProperties;
    const runs = textProps?.EngineDict?.StyleRun?.RunArray;
    if (Array.isArray(runs) && runs.length > 0) {
      const fontSize = runs[0]?.StyleSheet?.StyleSheetData?.FontSize;
      if (typeof fontSize === 'number' && fontSize > 0) {
        const scale = tryExtractTextScale(node);
        return Math.round(fontSize * scale);
      }
    }
  } catch (e) {
    console.warn('[PSD_IMPORT_DEBUG] failed to parse font size from EngineDict:', e);
  }
  return null;
}

function tryExtractFontFamily(node: any): string | null {
  try {
    const textProps = node.textProperties || node.additionalProperties?.TypeToolObject?.textProperties;
    const fontSet = textProps?.DocumentResources?.FontSet;
    const runs = textProps?.EngineDict?.StyleRun?.RunArray;
    if (Array.isArray(fontSet) && Array.isArray(runs) && runs.length > 0) {
      const fontIndex = runs[0]?.StyleSheet?.StyleSheetData?.Font;
      if (typeof fontIndex === 'number' && fontSet[fontIndex]) {
        const fontName = fontSet[fontIndex]?.Name || fontSet[fontIndex]?.FamilyName;
        if (typeof fontName === 'string') {
          return fontName.split('-')[0].trim();
        }
      }
    }
  } catch (e) {
    console.warn('[PSD_IMPORT_DEBUG] failed to parse font family from EngineDict:', e);
  }
  return null;
}

function tryExtractTextAlign(node: any): string | null {
  try {
    const textProps = node.textProperties || node.additionalProperties?.TypeToolObject?.textProperties;
    const runs = textProps?.EngineDict?.ParagraphRun?.RunArray;
    if (Array.isArray(runs) && runs.length > 0) {
      const justification = runs[0]?.ParagraphSheet?.Properties?.Justification;
      if (typeof justification === 'number') {
        switch (justification) {
          case 0: return 'left';
          case 1: return 'right';
          case 2: return 'center';
          default: return 'left';
        }
      }
    }
  } catch (e) {
    console.warn('[PSD_IMPORT_DEBUG] failed to parse text align:', e);
  }
  return null;
}

function tryExtractTextStyles(node: any) {
  const styles = {
    fontWeight: 'normal',
    fontStyle: 'normal',
    underline: false,
    linethrough: false,
  };
  try {
    const textProps = node.textProperties || node.additionalProperties?.TypeToolObject?.textProperties;
    const runs = textProps?.EngineDict?.StyleRun?.RunArray;
    if (Array.isArray(runs) && runs.length > 0) {
      const sheetData = runs[0]?.StyleSheet?.StyleSheetData;
      if (sheetData) {
        if (sheetData.FauxBold === true || sheetData.FauxBold === 1) {
          styles.fontWeight = 'bold';
        }
        if (sheetData.FauxItalic === true || sheetData.FauxItalic === 1) {
          styles.fontStyle = 'italic';
        }
        if (sheetData.Underline === true || sheetData.Underline === 1) {
          styles.underline = true;
        }
        if (sheetData.Strikethrough === true || sheetData.Strikethrough === 1) {
          styles.linethrough = true;
        }
      }
    }
  } catch (e) {
    console.warn('[PSD_IMPORT_DEBUG] failed to parse text styles:', e);
  }
  return styles;
}

function tryExtractCharSpacing(node: any): number | null {
  try {
    const textProps = node.textProperties || node.additionalProperties?.TypeToolObject?.textProperties;
    const runs = textProps?.EngineDict?.StyleRun?.RunArray;
    if (Array.isArray(runs) && runs.length > 0) {
      const tracking = runs[0]?.StyleSheet?.StyleSheetData?.Tracking;
      if (typeof tracking === 'number') {
        return tracking;
      }
    }
  } catch (e) {
    console.warn('[PSD_IMPORT_DEBUG] failed to parse tracking:', e);
  }
  return null;
}

function tryExtractLineHeight(node: any): number | null {
  try {
    const textProps = node.textProperties || node.additionalProperties?.TypeToolObject?.textProperties;
    const runs = textProps?.EngineDict?.StyleRun?.RunArray;
    if (Array.isArray(runs) && runs.length > 0) {
      const sheetData = runs[0]?.StyleSheet?.StyleSheetData;
      if (sheetData) {
        const fontSize = sheetData.FontSize;
        if (sheetData.AutoLeading === false && typeof sheetData.Leading === 'number' && typeof fontSize === 'number' && fontSize > 0) {
          const lh = parseFloat((sheetData.Leading / fontSize).toFixed(3));
          return lh >= 0.85 ? lh : null;
        }
      }
    }
  } catch (e) {
    console.warn('[PSD_IMPORT_DEBUG] failed to parse line height:', e);
  }
  return null;
}

/**
 * FIX 1: Correctly detect mixed text styles.
 * PSD always appends a sentinel (empty) run at the end of StyleRun array.
 * We only count runs that have actual text content (RunLength > 0).
 */
function hasMixedTextStyles(node: any): boolean {
  try {
    const textProps = node.textProperties || node.additionalProperties?.TypeToolObject?.textProperties;
    const runs = textProps?.EngineDict?.StyleRun?.RunArray;
    const runLengths = textProps?.EngineDict?.StyleRun?.RunLengthArray;
    if (!Array.isArray(runs) || runs.length <= 1) return false;

    // Filter out sentinel/empty runs (run length == 0 or 1 for the trailing newline)
    const contentRuns = runs.filter((_: any, i: number) => {
      const len = Array.isArray(runLengths) ? (runLengths[i] ?? 0) : 0;
      return len > 1; // Skip sentinel runs with length 0 or 1 (trailing newline only)
    });

    if (contentRuns.length <= 1) return false;

    // Check if content runs have meaningfully different styles
    const firstData = contentRuns[0]?.StyleSheet?.StyleSheetData;
    if (!firstData) return false;

    return contentRuns.slice(1).some((run: any) => {
      const d = run?.StyleSheet?.StyleSheetData;
      if (!d) return false;
      return (
        d.Font !== firstData.Font ||
        Math.abs((d.FontSize ?? 0) - (firstData.FontSize ?? 0)) > 0.5 ||
        d.FillColor?.values?.join() !== firstData.FillColor?.values?.join()
      );
    });
  } catch (e) {
    console.warn('[PSD_IMPORT_DEBUG] failed to check mixed styles:', e);
    return false;
  }
}

/**
 * FIX 2: Extract Drop Shadow from Layer Effects (lfx2/lrfx).
 * Returns canvas-compatible shadow properties if a Drop Shadow is enabled.
 */
function tryExtractDropShadow(node: any): {
  shadowAngle: number;
  shadowDistance: number;
  shadowBlur: number;
  shadowIntensity: number;
  shadowColorArgb: number;
} | null {
  try {
    const effects = node.additionalProperties?.lfx2 || node.additionalProperties?.lrfx;
    if (!effects) return null;
    const drSh = effects.DrSh;
    if (!drSh) return null;
    // Check if drop shadow is enabled
    if (drSh.enab === false || drSh.enab === 0) return null;

    const angle = typeof drSh.lagl?.value === 'number' ? drSh.lagl.value : 120;
    const distance = typeof drSh.Dstn?.value === 'number' ? drSh.Dstn.value : 0;
    const blur = typeof drSh.blur?.value === 'number' ? drSh.blur.value : 0;
    const opacity = typeof drSh.Opct?.value === 'number' ? drSh.Opct.value : 75;

    // Parse shadow color (Clr)
    let shadowColorArgb = -939524096; // default: semi-transparent black (0xC8000000)
    const clr = drSh.Clr;
    if (clr) {
      const r = Math.round((clr['Rd  ']?.value ?? clr.Rd ?? 0));
      const g = Math.round((clr['Grn ']?.value ?? clr.Grn ?? 0));
      const b = Math.round((clr['Bl  ']?.value ?? clr.Bl ?? 0));
      // Store opaque RGB; opacity is carried by shadowIntensity.
      shadowColorArgb = (0xff << 24) | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
    }

    return {
      shadowAngle: angle,
      shadowDistance: distance,
      shadowBlur: blur,
      shadowIntensity: Math.min(1, opacity / 100),
      shadowColorArgb,
    };
  } catch (e) {
    console.warn('[PSD_IMPORT_DEBUG] failed to parse drop shadow:', e);
    return null;
  }
}

/** Extract Photoshop Stroke (FrFX) when the layer is not rasterized. */
function tryExtractStroke(node: any): { stroke: string; strokeWidth: number } | null {
  try {
    const effects = node.additionalProperties?.lfx2 || node.additionalProperties?.lrfx;
    if (!effects) return null;
    const frFx = effects.FrFX;
    if (!frFx || frFx.enab === false || frFx.enab === 0) return null;

    const sizeRaw = frFx['Sz  ']?.value ?? frFx.Sz?.value;
    const size = typeof sizeRaw === 'number' ? sizeRaw : 0;
    if (size <= 0) return null;

    const opacity = typeof frFx.Opct?.value === 'number' ? frFx.Opct.value : 100;
    let stroke = '#000000';
    const clr = frFx.Clr;
    if (clr) {
      const r = Math.round(clr['Rd  ']?.value ?? clr.Rd ?? 0);
      const g = Math.round(clr['Grn ']?.value ?? clr.Grn ?? 0);
      const b = Math.round(clr['Bl  ']?.value ?? clr.Bl ?? 0);
      const a = Math.min(1, Math.max(0, opacity / 100));
      stroke = `rgba(${r}, ${g}, ${b}, ${a})`;
    }

    return { stroke, strokeWidth: Math.round(size) };
  } catch (e) {
    console.warn('[PSD_IMPORT_DEBUG] failed to parse stroke:', e);
    return null;
  }
}

/**
 * FIX 2: Detect if layer has complex effects beyond Drop Shadow
 * (Stroke, Bevel, Inner Glow, etc.) that cannot be mapped to canvas properties.
 */
function hasComplexEffects(node: any): boolean {
  try {
    const effects = node.additionalProperties?.lfx2 || node.additionalProperties?.lrfx;
    if (!effects) return false;
    // Stroke, Bevel+Emboss, Inner/Outer Glow, Inner Shadow, Color Overlay
    return !!(
      (effects.FrFX && effects.FrFX.enab !== false) || // Stroke
      (effects.ebbl && effects.ebbl.enab !== false) || // Bevel & Emboss
      (effects.IrGl && effects.IrGl.enab !== false) || // Inner Glow
      (effects.OrGl && effects.OrGl.enab !== false) || // Outer Glow
      (effects.IrSh && effects.IrSh.enab !== false) || // Inner Shadow
      (effects.SoCo && effects.SoCo.enab !== false) || // Color Overlay
      (effects.GrFl && effects.GrFl.enab !== false) || // Gradient Overlay
      (effects.patternFill && effects.patternFill.enab !== false) // Pattern Overlay
    );
  } catch {
    return false;
  }
}

/** Render raw RGBA pixels to compressed WebP Blob (delegates to rasterize-service). */
function pixelsToWebpBlob(pixels: Uint8ClampedArray, width: number, height: number): Promise<Blob | null> {
  return pixelsToBlob(pixels, width, height);
}

/** Render composite and resize for thumbnail WebP Blob (delegates to rasterize-service). */
function createThumbnailBlob(pixels: Uint8ClampedArray, width: number, height: number): Promise<Blob | null> {
  return pixelsToThumbnailBlob(pixels, width, height);
}

async function tryExtractBackgroundLayer(node: any, canvasWidth: number, canvasHeight: number) {
  if (!node || node.type !== 'Layer') return null;
  if (!node.width || !node.height) return null;
  if (Math.round(node.width) !== Math.round(canvasWidth) || Math.round(node.height) !== Math.round(canvasHeight)) return null;
  if (typeof node.text === 'string' && node.text.trim()) return null;

  try {
    const rawPixels = await node.composite(false);
    if (!isUniformRgba(rawPixels)) return null;

    const blob = await pixelsToWebpBlob(rawPixels, canvasWidth, canvasHeight);
    return {
      blob,
      name: node.name || 'Background',
    };
  } catch (error) {
    console.warn('[PSD_IMPORT_DEBUG] background extraction failed:', error);
    return null;
  }
}

function countPsdNodes(nodes: any[] | undefined): { layers: number; groups: number } {
  let layers = 0;
  let groups = 0;
  const visit = (list: any[] | undefined) => {
    for (const node of list || []) {
      if (node?.type === 'Group') {
        groups += 1;
        visit(node.children);
      } else if (node?.type === 'Layer') {
        layers += 1;
      }
    }
  };
  visit(nodes);
  return { layers, groups };
}

export async function parsePsdOnClient(
  file: File,
  categoryId?: string,
  templateId?: string,
  title?: string,
  onLog?: PsdImportLogger
): Promise<ClientPsdImportResult> {
  const { log, step } = createTimedPsdLogger(onLog);
  const parseStartedAt = Date.now();

  await log(`File: "${file.name}" (${formatBytes(file.size)}) — file từ AI→PSD thường rất lớn, bước parse có thể mất vài phút`);

  const fileBuffer = await step('Đọc dữ liệu file vào bộ nhớ', () => file.arrayBuffer());
  await log(`Buffer sẵn sàng: ${formatBytes(fileBuffer.byteLength)}`);

  // Psd.parse is synchronous and often the slowest step for AI-exported PSDs.
  await log('→ Phân tích cấu trúc PSD (Psd.parse) — UI có thể tạm đứng trong lúc này...');
  const parseT0 = Date.now();
  let psd: Awaited<ReturnType<typeof Psd.parse>>;
  try {
    psd = Psd.parse(fileBuffer);
  } catch (error) {
    await log(`✗ Psd.parse thất bại sau ${formatDurationMs(Date.now() - parseT0)}: ${(error as Error)?.message || error}`);
    throw error;
  }
  await log(`✓ Psd.parse xong (${formatDurationMs(Date.now() - parseT0)})`);

  const canvasWidth = Math.max(1, psd.width || 1080);
  const canvasHeight = Math.max(1, psd.height || 1080);
  const divisor = gcd(canvasWidth, canvasHeight);
  const aspectRatio = `${canvasWidth / divisor}:${canvasHeight / divisor}`;
  const treeStats = countPsdNodes(psd.children as any[]);
  await log(
    `Canvas ${canvasWidth}×${canvasHeight} (${aspectRatio}), colorMode=${psd.colorMode}, ` +
      `${treeStats.layers} layer / ${treeStats.groups} group trong cây PSD`,
  );

  const warnings: string[] = [];

  // Warn if color mode is not RGB (3)
  if (psd.colorMode !== 3) {
    const warn =
      `Cảnh báo: Template này dường như không phải hệ màu RGB (Color Mode: ${psd.colorMode}). ` +
      `Nó có thể sẽ hiển thị sai màu (ví dụ chuyển thành màu xám) hoặc không trích xuất được layer. ` +
      `Vui lòng vào Photoshop chọn Image > Mode > RGB Color và lưu lại.`;
    warnings.push(warn);
    await log(`⚠ ${warn}`);
  }

  const baseName = sanitizeLabel(title || file.name, 'Imported PSD Template');
  const finalTemplateId = (templateId?.trim() || `TPL_${uuidv4().replace(/-/g, '').slice(0, 8).toUpperCase()}`).toUpperCase();
  const slug = baseName.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '') || 'psd';

  let backgroundBlob: Blob | null = null;
  let backgroundName = 'Background';
  let backgroundLayerNode: any = null;

  // 1. Try finding background layer
  await log('→ Kiểm tra và trích xuất layer nền (Background)...');
  const bgT0 = Date.now();
  for (const child of [...(psd.children || [])]) {
    const bg = await tryExtractBackgroundLayer(child, canvasWidth, canvasHeight);
    if (bg && bg.blob) {
      backgroundBlob = bg.blob;
      backgroundName = bg.name;
      backgroundLayerNode = child;
      await log(`✓ Đã trích xuất layer nền "${bg.name}" (${formatBytes(bg.blob.size)}, ${formatDurationMs(Date.now() - bgT0)})`);
      break;
    }
  }
  if (!backgroundBlob) {
    await log(`Không tìm thấy layer nền riêng (${formatDurationMs(Date.now() - bgT0)})`);
  }

  // 2. Generate composite thumbnail
  const compositePixels = await step('Tạo thumbnail (psd.composite — bước nặng với file AI)', () => psd.composite());
  const thumbnailBlob = await step('Encode thumbnail WebP', () =>
    createThumbnailBlob(compositePixels, canvasWidth, canvasHeight),
  );
  if (thumbnailBlob) {
    await log(`Thumbnail: ${formatBytes(thumbnailBlob.size)}`);
  }

  const layers: ExtractedLayer[] = [];
  let order = 0;
  let skippedZeroSize = 0;
  let skippedEmptyAdjustment = 0;
  let textKept = 0;
  let imageLayers = 0;
  let rasterizedText = 0;

  await log(`→ Bắt đầu xử lý chi tiết ${treeStats.layers} layer...`);

  const walk = async (node: any, path: string[] = []) => {
    if (!node) return;
    if (node === backgroundLayerNode) return;

    if (node.type === 'Group') {
      const nextPath = [...path, node.name || 'Group'];
      await log(`Nhóm: "${node.name || 'Group'}" (${(node.children || []).length} phần tử)`);
      for (const child of [...(node.children || [])].reverse()) {
        await walk(child, nextPath);
      }
      return;
    }

    if (node.type !== 'Layer') return;
    if (!node.width || !node.height) {
      skippedZeroSize += 1;
      await log(`⏭ Bỏ qua layer kích thước 0: "${node.name || 'Layer'}"`);
      console.warn(`[PSD_IMPORT_DEBUG] skip zero-size layer name=${node.name || 'Layer'}`);
      return;
    }

    const layerPath = [...path, node.name || 'Layer'];
    const width = Math.max(1, Math.round(node.width || 1));
    const height = Math.max(1, Math.round(node.height || 1));
    const left = Number(node.left || 0);
    const top = Number(node.top || 0);
    const centerX = left + width / 2;
    const centerY = top + height / 2;
    const layerName = node.name || '';
    const nameLower = layerName.toLowerCase().trim();
    const forceImage = nameLower.startsWith('[img]') ||
                       nameLower.startsWith('[image]') ||
                       nameLower.startsWith('img:') ||
                       nameLower.startsWith('image:');

    const rawIsText = typeof node.text === 'string' && node.text.trim().length > 0 && !forceImage;
    const layerT0 = Date.now();
    await log(`→ Layer "${layerName}" ${width}×${height} (${rawIsText ? 'Chữ' : 'Ảnh'})`);

    // --- FIX 1: Correct warp detection ---
    const tySh = node.additionalProperties?.TySh;
    const warpStyle = tySh?.warpStyle?.toString() || tySh?.warp?.warpStyle?.toString();
    const hasWarp = !!(warpStyle && !/none/i.test(warpStyle));

    // --- FIX 1: Correct mixed styles detection (ignore PSD sentinel runs) ---
    const mixedStyles = hasMixedTextStyles(node);

    // --- FIX 2: Detect effect types separately ---
    const dropShadow = tryExtractDropShadow(node);
    const complexFx = hasComplexEffects(node); // Stroke, Bevel, Glow etc.
    const hasAnyEffects = !!(node.additionalProperties?.lfx2 || node.additionalProperties?.lrfx);

    // --- FIX 3: Detect adjustment/fill layers ---
    const isAdjustment = !!(
      node.additionalProperties?.Crvs ||
      node.additionalProperties?.Brit ||
      node.additionalProperties?.Hue  ||
      node.additionalProperties?.Hue2 ||
      node.additionalProperties?.Levl ||
      node.additionalProperties?.GdFl ||
      node.additionalProperties?.SoCo
    );

    const opacity = normalizeOpacity(node.composedOpacity ?? node.opacity);
    const rawBlendMode = node.layerFrame?.layerProperties?.blendMode || node.layerProperties?.blendMode;
    const blendMode = mapPsdBlendMode(rawBlendMode);

    const hasMask = !!(node.additionalProperties?.LMsk || node.additionalProperties?.VMsk || node.layerMask || node.vectorMask);
    const isClipping = !!node.clipping;

    // Rules Engine: single source of truth for keep-vector vs rasterize.
    const signals: PsdLayerSignals = {
      isText: rawIsText,
      hasWarp,
      warpStyle,
      hasMixedStyles: mixedStyles,
      hasComplexEffects: complexFx,
      hasDropShadow: !!dropShadow,
      isAdjustment,
      hasMask,
      isClipping,
      blendMode,
    };
    const classification = classifyPsdLayer(signals);
    const shouldRasterizeText = rawIsText && classification.strategy === 'rasterize';
    const isText = rawIsText && !shouldRasterizeText;

    const layerWarnings: string[] = [];

    if (shouldRasterizeText) {
      layerWarnings.push(`Tự động phẳng hóa thành Ảnh để giữ nguyên: ${classification.reasons.join(', ')}`);
    } else {
      layerWarnings.push(...classification.reasons);
      if (!isText && hasAnyEffects && !complexFx) {
        layerWarnings.push(`Hiệu ứng lớp/Layer Styles (Drop Shadow, Stroke, Glow, v.v.)`);
      }
    }

    if (!isText && !shouldRasterizeText && !isAdjustment) {
      const isVector = !!(node.additionalProperties?.vmsk || node.additionalProperties?.vsms);
      if (isVector) {
        layerWarnings.push(`Lớp vector/Shape layer`);
      }
      const isSmartObject = !!node.additionalProperties?.SoLd;
      if (isSmartObject) {
        layerWarnings.push(`Smart Object — composite thành Ảnh`);
      }
    }

    if (layerWarnings.length > 0) {
      const typeLabel = isText ? 'Chữ (editable)' : (shouldRasterizeText ? 'Chữ → Ảnh' : 'Ảnh');
      const desc = `Layer "${node.name || 'Layer'}" (${typeLabel}): ${layerWarnings.join(', ')}`;
      warnings.push(desc);
      console.warn(`[PSD_IMPORT_WARNING] ${desc}`);
      await log(`⚠ ${desc}`);
    }

    // --- FIX 2: Apply Drop Shadow from PSD to canvas payload ---
    const shadow = dropShadow ?? (isText ? null : tryExtractDropShadow(node));
    const psdStroke = !shouldRasterizeText ? tryExtractStroke(node) : null;
    const effectsBaked = classification.effectsBaked;

    const payload: any = {
      alpha: opacity,
      visible: node.isHidden !== true,
      locked: false,
      groupPath: layerPath.slice(0, -1).length ? layerPath.slice(0, -1).join(' / ') : null,
      sourceKind: isText ? 'psd-text' : (effectsBaked ? 'psd-rasterized' : 'psd-image'),
      // FIX 2: Map PSD Drop Shadow → canvas shadow properties
      shadowIntensity: effectsBaked ? 0 : (shadow?.shadowIntensity ?? 0),
      shadowAngle: shadow?.shadowAngle ?? 120,
      shadowDistance: effectsBaked ? 0 : (shadow?.shadowDistance ?? 0),
      shadowColorArgb: shadow?.shadowColorArgb ?? -939524096,
      shadowBlur: effectsBaked ? 0 : (shadow?.shadowBlur ?? 0),
      stroke: psdStroke?.stroke ?? null,
      strokeWidth: psdStroke?.strokeWidth ?? 0,
      strokeDashArray: null,
      blendMode,
    };

    if (isText) {
      let textPixels: Uint8ClampedArray | null = null;
      try {
        textPixels = await node.composite(false);
      } catch (error) {
        console.warn(`[PSD_IMPORT_DEBUG] text composite failed name=${node.name || 'Text'}`, error);
        await log(`⚠ Composite chữ thất bại cho "${layerName}": ${(error as Error)?.message || error}`);
      }

      const textColorArgb = textPixels ? inferTextColor(textPixels) : toArgb(0, 0, 0, 255);
      const extractedSize = tryExtractFontSize(node);
      const textSize = extractedSize || Math.max(12, Math.round((node.height || 48) * 0.8));
      const fontFamily = tryExtractFontFamily(node) || undefined;
      const textStyles = tryExtractTextStyles(node);
      const textAlign = tryExtractTextAlign(node) || 'left';
      const charSpacing = tryExtractCharSpacing(node) || 0;
      const lineHeight = tryExtractLineHeight(node) || 1.1;

      payload.text = node.text || '';
      payload.font = fontFamily || 'Outfit';
      payload.fontSize = textSize;
      payload.textColorArgb = textColorArgb;
      payload.fontWeight = textStyles.fontWeight;
      payload.fontStyle = textStyles.fontStyle;
      payload.underline = textStyles.underline;
      payload.linethrough = textStyles.linethrough;
      payload.textAlign = textAlign;
      payload.lineHeight = lineHeight;
      payload.charSpacing = charSpacing;
      payload.textBackgroundColor = null;
      payload.textTransform = 'none';
      payload._originalText = payload.text;
      payload.baseWidth = width;
      payload.baseHeight = height;

      layers.push({
        layerId: `psd_${uuidv4().replace(/-/g, '').slice(0, 16)}`,
        type: 'TEXT',
        zIndex: order++,
        transform: {
          anchorX: canvasWidth > 0 ? centerX / canvasWidth : 0.5,
          anchorY: canvasHeight > 0 ? centerY / canvasHeight : 0.5,
          scale: 1,
          rotation: 0,
        },
        payload,
        name: node.name || 'Text',
      });
      textKept += 1;
      await log(`✓ Giữ chữ editable "${layerName}" (${formatDurationMs(Date.now() - layerT0)})`);
    } else {
      // Image layer (includes rasterized text, smart objects, adjustment/fill layers, shapes)
      let imageBlob: Blob | null = null;
      try {
        // FIX 3: Adjustment/Fill layers must use composite(true) to bake their color effects.
        // Rasterized text layers also use composite(true) to capture Drop Shadow / Glow effects.
        const includeBackground = isAdjustment || shouldRasterizeText;
        await log(
          `  Rasterize "${layerName}" (composite${includeBackground ? '+bg' : ''} → WebP)...`,
        );
        const rasterT0 = Date.now();
        const rawPixels = await node.composite(includeBackground);
        imageBlob = await pixelsToWebpBlob(rawPixels, width, height);
        await log(
          `  Rasterize xong (${formatBytes(imageBlob?.size || 0)}, ${formatDurationMs(Date.now() - rasterT0)})`,
        );
      } catch (error) {
        console.warn(`[PSD_IMPORT_DEBUG] image composite failed name=${node.name || 'Layer'}`, error);
        await log(`✗ Rasterize thất bại "${layerName}": ${(error as Error)?.message || error}`);
      }

      // If adjustment layer produced empty/null blob, skip silently (e.g. invisible layer)
      if (isAdjustment && !imageBlob) {
        skippedEmptyAdjustment += 1;
        await log(`⏭ Bỏ qua adjustment layer rỗng: "${layerName}"`);
        console.warn(`[PSD_IMPORT_DEBUG] skip empty adjustment layer name=${node.name || 'Layer'}`);
        return;
      }

      payload.baseWidth = width;
      payload.baseHeight = height;

      const layerIdx = order++;
      layers.push({
        layerId: `psd_${uuidv4().replace(/-/g, '').slice(0, 16)}`,
        type: 'IMAGE',
        zIndex: layerIdx,
        transform: {
          anchorX: canvasWidth > 0 ? centerX / canvasWidth : 0.5,
          anchorY: canvasHeight > 0 ? centerY / canvasHeight : 0.5,
          scale: 1,
          rotation: 0,
        },
        payload,
        imageBlob,
        fileName: `${slug}_layer_${String(layerIdx + 1).padStart(2, '0')}.webp`,
        name: node.name || 'Layer',
      });
      if (shouldRasterizeText) rasterizedText += 1;
      else imageLayers += 1;
      await log(`✓ Layer ảnh "${layerName}" (${formatDurationMs(Date.now() - layerT0)})`);
    }
  };

  for (const child of [...(psd.children || [])].reverse()) {
    await walk(child, []);
  }

  // Handle fallback if no layers parsed
  if (layers.length === 0 && !backgroundBlob) {
    await log('Không có layer — flatten toàn bộ PSD làm nền...');
    const rawPixels = await psd.composite();
    backgroundBlob = await pixelsToWebpBlob(rawPixels, canvasWidth, canvasHeight);
    backgroundName = 'Flattened PSD';
    await log(`✓ Flatten xong (${formatBytes(backgroundBlob?.size || 0)})`);
  }

  await log(
    `Parse xong trong ${formatDurationMs(Date.now() - parseStartedAt)}: ` +
      `${textKept} chữ, ${imageLayers} ảnh, ${rasterizedText} chữ→ảnh, ` +
      `bỏ qua ${skippedZeroSize} size-0 / ${skippedEmptyAdjustment} adjustment rỗng` +
      (warnings.length ? `, ${warnings.length} cảnh báo` : ''),
  );

  return {
    templateId: finalTemplateId,
    categoryId: categoryId || 'uncategorized',
    title: baseName,
    canvasWidth,
    canvasHeight,
    aspectRatio,
    thumbnailBlob,
    backgroundUrl: null,
    backgroundBlob,
    backgroundName,
    layers,
    warnings,
  };
}
