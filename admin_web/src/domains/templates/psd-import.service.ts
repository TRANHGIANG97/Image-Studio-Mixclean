import { randomUUID } from 'crypto';
import sharp from 'sharp';
import Psd from '@webtoon/psd';
import { createSupabaseAdmin } from '@/lib/supabase';
import { CloudLayer, CloudTemplate } from '@/types/cloud-template';
import { gcd } from './template.helpers';

export interface ImportPsdInput {
  fileBuffer: ArrayBuffer;
  fileName: string;
  categoryId?: string | null;
  templateId?: string | null;
  title?: string | null;
}

const DB = () => createSupabaseAdmin();

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

function isUniformRgba(raw: Uint8ClampedArray | Buffer) {
  if (!raw || raw.length < 8) return false;
  const r = raw[0] ?? 0;
  const g = raw[1] ?? 0;
  const b = raw[2] ?? 0;
  const a = raw[3] ?? 255;
  for (let i = 4; i < raw.length; i += 4) {
    if ((raw[i] ?? 0) !== r || (raw[i + 1] ?? 0) !== g || (raw[i + 2] ?? 0) !== b || (raw[i + 3] ?? 255) !== a) {
      return false;
    }
  }
  return true;
}

function inferTextColor(rgba: Uint8ClampedArray | Buffer) {
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
    const tySh = node.additionalLayerProperties?.TySh;
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
    const textProps = node.textProperties || node.additionalLayerProperties?.TypeToolObject?.textProperties;
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
    const textProps = node.textProperties || node.additionalLayerProperties?.TypeToolObject?.textProperties;
    const fontSet = textProps?.DocumentResources?.FontSet;
    const runs = textProps?.EngineDict?.StyleRun?.RunArray;
    if (Array.isArray(fontSet) && Array.isArray(runs) && runs.length > 0) {
      const fontIndex = runs[0]?.StyleSheet?.StyleSheetData?.Font;
      if (typeof fontIndex === 'number' && fontSet[fontIndex]) {
        const fontName = fontSet[fontIndex]?.Name || fontSet[fontIndex]?.FamilyName;
        if (typeof fontName === 'string') {
          return fontName.split('-')[0].trim(); // clean e.g. Montserrat-Bold -> Montserrat
        }
      }
    }
  } catch (e) {
    console.warn('[PSD_IMPORT_DEBUG] failed to parse font family from EngineDict:', e);
  }
  return null;
}


async function uploadImageBuffer({
  buffer,
  fileName,
  folder,
  mimeType,
}: {
  buffer: Buffer;
  fileName: string;
  folder: string;
  mimeType: string;
}) {
  const supabaseAdmin = DB();
  const safeName = fileName.replace(/[^\w.-]+/g, '_');
  const uniqueKey = `${folder}/${Date.now()}_${randomUUID()}_${safeName}`;

  const { error: uploadError } = await supabaseAdmin.storage
    .from('assets')
    .upload(uniqueKey, buffer, { contentType: mimeType, upsert: true });

  if (uploadError) {
    throw new Error(`Upload failed: ${uploadError.message}`);
  }

  const {
    data: { publicUrl },
  } = supabaseAdmin.storage.from('assets').getPublicUrl(uniqueKey);

  await supabaseAdmin.from('assets').insert({
    name: fileName,
    folder,
    file_url: publicUrl,
    file_size: buffer.length,
    mime_type: mimeType,
  }).maybeSingle();

  return publicUrl;
}

async function layerToAssetUrl(layer: any, slug: string, index: number) {
  const width = Math.max(1, Math.round(layer.width || 0));
  const height = Math.max(1, Math.round(layer.height || 0));
  const rawPixels = await layer.composite(false);
  const pngBuffer = await sharp(Buffer.from(rawPixels), {
    raw: { width, height, channels: 4 },
  })
    .png()
    .toBuffer();

  const fileName = `${slug}_layer_${String(index + 1).padStart(2, '0')}.png`;
  const publicUrl = await uploadImageBuffer({
    buffer: pngBuffer,
    fileName,
    folder: 'imported-psd',
    mimeType: 'image/png',
  });

  return publicUrl;
}

function tryExtractTextAlign(node: any): string | null {
  try {
    const textProps = node.textProperties || node.additionalLayerProperties?.TypeToolObject?.textProperties;
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
    const textProps = node.textProperties || node.additionalLayerProperties?.TypeToolObject?.textProperties;
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
    const textProps = node.textProperties || node.additionalLayerProperties?.TypeToolObject?.textProperties;
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
    const textProps = node.textProperties || node.additionalLayerProperties?.TypeToolObject?.textProperties;
    const runs = textProps?.EngineDict?.StyleRun?.RunArray;
    if (Array.isArray(runs) && runs.length > 0) {
      const sheetData = runs[0]?.StyleSheet?.StyleSheetData;
      if (sheetData) {
        const fontSize = sheetData.FontSize;
        if (sheetData.AutoLeading === false && typeof sheetData.Leading === 'number' && typeof fontSize === 'number' && fontSize > 0) {
          return parseFloat((sheetData.Leading / fontSize).toFixed(3));
        }
      }
    }
  } catch (e) {
    console.warn('[PSD_IMPORT_DEBUG] failed to parse line height:', e);
  }
  return null;
}

function buildCloudLayer({
  layer,
  index,
  canvasWidth,
  canvasHeight,
  imageUrl,
  textColorArgb,
  textSize,
  fontFamily,
  groupPath,
}: {
  layer: any;
  index: number;
  canvasWidth: number;
  canvasHeight: number;
  imageUrl?: string | null;
  textColorArgb?: number;
  textSize?: number;
  fontFamily?: string;
  groupPath: string[];
}): CloudLayer {
  const width = Math.max(1, Math.round(layer.width || 1));
  const height = Math.max(1, Math.round(layer.height || 1));
  const left = Number(layer.left || 0);
  const top = Number(layer.top || 0);
  const centerX = left + width / 2;
  const centerY = top + height / 2;
  const isText = typeof layer.text === 'string' && layer.text.trim().length > 0;
  const opacity = normalizeOpacity(layer.composedOpacity ?? layer.opacity);
  
  const rawBlendMode = (layer as any).layerFrame?.layerProperties?.blendMode || (layer as any).layerProperties?.blendMode;
  const blendMode = mapPsdBlendMode(rawBlendMode);
  console.log(`[PSD_IMPORT_DEBUG] Layer name="${layer.name}" rawBlendMode="${rawBlendMode}" mapped="${blendMode}" isText=${isText}`);

  const payload: any = {
    alpha: opacity,
    visible: layer.isHidden !== true,
    locked: false,
    groupPath: groupPath.length ? groupPath.join(' / ') : null,
    sourceKind: isText ? 'psd-text' : 'psd-image',
    shadowIntensity: 0,
    shadowAngle: 45,
    shadowDistance: 0,
    shadowColorArgb: -939524096,
    shadowBlur: 0,
    stroke: null,
    strokeWidth: 0,
    strokeDashArray: null,
    blendMode,
  };

  if (isText) {
    const textStyles = tryExtractTextStyles(layer);
    const textAlign = tryExtractTextAlign(layer) || 'left';
    const charSpacing = tryExtractCharSpacing(layer) || 0;
    const extractedFontSize = textSize || Math.max(12, Math.round(height * 0.8));
    const lineHeight = tryExtractLineHeight(layer) || 1.1;

    payload.text = layer.text || '';
    payload.font = fontFamily || 'Outfit';
    payload.fontSize = extractedFontSize;
    payload.textColorArgb = textColorArgb || toArgb(0, 0, 0, 255);
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
  } else {
    payload.imageUrl = imageUrl || null;
    payload.defaultImageUrl = imageUrl || null;
    payload.baseWidth = width;
    payload.baseHeight = height;
  }

  return {
    layerId: `psd_${randomUUID()}`,
    type: isText ? 'TEXT' : 'IMAGE',
    zIndex: index,
    transform: {
      anchorX: canvasWidth > 0 ? centerX / canvasWidth : 0.5,
      anchorY: canvasHeight > 0 ? centerY / canvasHeight : 0.5,
      scale: 1,
      rotation: 0,
    },
    payload,
  };
}

async function buildThumbnail(psd: any, width: number, height: number, slug: string) {
  try {
    const flattened = await psd.composite();
    const thumbBuffer = await sharp(Buffer.from(flattened), {
      raw: { width, height, channels: 4 },
    })
      .flatten({ background: '#ffffff' })
      .resize({ width: 720, withoutEnlargement: true })
      .webp({ quality: 88 })
      .toBuffer();

    return uploadImageBuffer({
      buffer: thumbBuffer,
      fileName: `${slug}_thumbnail.webp`,
      folder: 'imported-psd',
      mimeType: 'image/webp',
    });
  } catch (error) {
    console.warn('[PSD_IMPORT_DEBUG] thumbnail generation failed:', error);
    return null;
  }
}

async function tryExtractBackgroundLayer(node: any, canvasWidth: number, canvasHeight: number) {
  if (!node || node.type !== 'Layer') return null;
  if (!node.width || !node.height) return null;
  if (Math.round(node.width) !== Math.round(canvasWidth) || Math.round(node.height) !== Math.round(canvasHeight)) return null;
  if (typeof node.text === 'string' && node.text.trim()) return null;

  try {
    const rawPixels = await node.composite(false);
    if (!isUniformRgba(rawPixels)) return null;

    const pngBuffer = await sharp(Buffer.from(rawPixels), {
      raw: { width: canvasWidth, height: canvasHeight, channels: 4 },
    })
      .png()
      .toBuffer();

    const publicUrl = await uploadImageBuffer({
      buffer: pngBuffer,
      fileName: `psd_background_${randomUUID()}.png`,
      folder: 'imported-psd',
      mimeType: 'image/png',
    });

    return {
      publicUrl,
      name: node.name || 'Background',
    };
  } catch (error) {
    console.warn('[PSD_IMPORT_DEBUG] background extraction failed:', error);
    return null;
  }
}

export async function importTemplatePsd(input: ImportPsdInput) {
  const baseName = sanitizeLabel(input.title || input.fileName, 'Imported PSD Template');
  const templateId = (input.templateId?.trim() || `TPL_${randomUUID().replace(/-/g, '').slice(0, 8).toUpperCase()}`).toUpperCase();
  const psd = Psd.parse(input.fileBuffer);
  const canvasWidth = Math.max(1, psd.width || 1080);
  const canvasHeight = Math.max(1, psd.height || 1080);
  const divisor = gcd(canvasWidth, canvasHeight);
  const aspectRatio = `${canvasWidth / divisor}:${canvasHeight / divisor}`;
  const slug = baseName.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '') || 'psd';
  const layers: CloudLayer[] = [];
  let backgroundUrl: string | null = null;
  let backgroundLayerNode: any = null;

  console.log(
    `[PSD_IMPORT_DEBUG] templateId=${templateId} file=${input.fileName} size=${canvasWidth}x${canvasHeight} layers=${psd.layers?.length || 0}`
  );

  let order = 0;

  for (const child of [...(psd.children || [])]) {
    const background = await tryExtractBackgroundLayer(child, canvasWidth, canvasHeight);
    if (background) {
      backgroundUrl = background.publicUrl;
      backgroundLayerNode = child;
      console.log(`[PSD_IMPORT_DEBUG] background layer detected name=${background.name} url=${background.publicUrl}`);
      break;
    }
  }

  const walk = async (node: any, path: string[] = []) => {
    if (!node) return;
    if (node === backgroundLayerNode) return;

    if (node.type === 'Group') {
      const nextPath = [...path, node.name || 'Group'];
      for (const child of [...(node.children || [])].reverse()) {
        await walk(child, nextPath);
      }
      return;
    }

    if (node.type !== 'Layer') return;
    if (!node.width || !node.height) {
      console.warn(`[PSD_IMPORT_DEBUG] skip zero-size layer name=${node.name || 'Layer'}`);
      return;
    }

    const layerPath = [...path, node.name || 'Layer'];

    if (typeof node.text === 'string' && node.text.trim()) {
      let textPixels: Uint8ClampedArray | Buffer | null = null;
      try {
        textPixels = await node.composite(false);
      } catch (error) {
        console.warn(`[PSD_IMPORT_DEBUG] text composite failed name=${node.name || 'Text'}`, error);
      }

      const textColorArgb = textPixels ? inferTextColor(textPixels) : toArgb(0, 0, 0, 255);
      const extractedSize = tryExtractFontSize(node);
      const textSize = extractedSize || Math.max(12, Math.round((node.height || 48) * 0.8));
      const fontFamily = tryExtractFontFamily(node) || undefined;

      layers.push(
        buildCloudLayer({
          layer: node,
          index: order++,
          canvasWidth,
          canvasHeight,
          textColorArgb,
          textSize,
          fontFamily,
          groupPath: layerPath.slice(0, -1),
        })
      );
      return;
    }

    try {
      const publicUrl = await layerToAssetUrl(node, slug, order);
      layers.push(
        buildCloudLayer({
          layer: node,
          index: order++,
          canvasWidth,
          canvasHeight,
          imageUrl: publicUrl,
          groupPath: layerPath.slice(0, -1),
        })
      );
    } catch (error) {
      console.warn(`[PSD_IMPORT_DEBUG] image layer import failed name=${node.name || 'Layer'}`, error);
    }
  };

  for (const child of [...(psd.children || [])].reverse()) {
    await walk(child, []);
  }

  if (layers.length === 0) {
    const flattened = await psd.composite();
    const fallbackBuffer = await sharp(Buffer.from(flattened), {
      raw: { width: canvasWidth, height: canvasHeight, channels: 4 },
    })
      .png()
      .toBuffer();

    const publicUrl = await uploadImageBuffer({
      buffer: fallbackBuffer,
      fileName: `${slug}_flattened.png`,
      folder: 'imported-psd',
      mimeType: 'image/png',
    });

    layers.push(
      buildCloudLayer({
        layer: {
          width: canvasWidth,
          height: canvasHeight,
          left: 0,
          top: 0,
          composedOpacity: 1,
          opacity: 255,
          isHidden: false,
          name: 'Flattened PSD',
        },
        index: 0,
        canvasWidth,
        canvasHeight,
        imageUrl: publicUrl,
        groupPath: [],
      })
    );
  }

  const thumbnailUrl = await buildThumbnail(psd, canvasWidth, canvasHeight, slug);
  const now = Date.now();
  const supabaseAdmin = DB();
  let categoryId = input.categoryId || '';

  const { data: catData } = await supabaseAdmin.from('categories').select('id').eq('id', categoryId);
  if (!catData || catData.length === 0) {
    const { data: firstCat } = await supabaseAdmin.from('categories').select('id').limit(1);
    if (firstCat?.length) {
      categoryId = firstCat[0].id;
    } else {
      throw Object.assign(new Error('No categories exist. Create a category first.'), { statusCode: 400 });
    }
  }

  const cloudTemplate: CloudTemplate = {
    templateId,
    categoryId,
    metadata: {
      title: baseName,
      thumbnailUrl: thumbnailUrl || '',
      status: 'draft',
      schemaVersion: 1,
      createdAt: now,
      updatedAt: now,
    },
    canvas: {
      baseWidth: canvasWidth,
      baseHeight: canvasHeight,
      aspectRatio,
      backgroundUrl,
    },
    layers,
  };

  const { data, error } = await supabaseAdmin
    .from('templates')
    .insert({
      template_id: templateId,
      category_id: categoryId,
      title: baseName,
      status: 'draft',
      thumbnail_url: thumbnailUrl,
      canvas_data: cloudTemplate,
      fabric_state: null,
    })
    .select('*, categories(id, name)')
    .single();

  if (error) {
    if (error.code === '23505') {
      throw Object.assign(new Error(`Template ID "${templateId}" already exists`), {
        statusCode: 400,
        code: 'DUPLICATE_TEMPLATE_ID',
      });
    }
    throw error;
  }

  console.log(`[PSD_IMPORT_DEBUG] imported templateId=${templateId} layers=${layers.length} thumbnail=${thumbnailUrl || 'null'}`);
  return data;
}
