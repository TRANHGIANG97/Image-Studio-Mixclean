import type { CloudTemplate } from '@/types/cloud-template';
import { BLEND_MODES, KNOWN_LAYER_TYPES } from '@/lib/schema/template-contract';

/** Shape subtypes the Android mapper understands (studio_edit CloudLayerToEditorMapper). */
export const MOBILE_KNOWN_SHAPE_TYPES = new Set([
  'rect',
  'circle',
  'ellipse',
  'triangle',
  'line',
  'polygon',
  'path',
  'arrow',
  'pill',
  'card',
  'teardrop',
  'star',
  'hexagon',
  'diamond',
  'parallelogram',
  'text_only',
]);

const MOBILE_CROP_RATIOS = new Set([
  'ORIGINAL',
  'RATIO_1_1',
  'RATIO_3_4',
  'RATIO_4_3',
  'RATIO_9_16',
  'RATIO_16_9',
  '1:1',
  '3:4',
  '4:3',
  '9:16',
  '16:9',
  'original',
]);

function hasGradient(value: unknown): boolean {
  if (!value || typeof value !== 'object') return false;
  const g = value as { type?: unknown; colorStops?: unknown };
  return typeof g.type === 'string' && Array.isArray(g.colorStops) && g.colorStops.length > 0;
}

/**
 * Audits a CloudTemplate for fields the Android studio_edit mapper expects.
 * Returns human-readable gap messages (empty = OK for parity audit).
 */
export function auditMobileParityFields(template: CloudTemplate): string[] {
  const gaps: string[] = [];
  const layers = template.layers ?? [];

  if (!template.templateId?.trim()) {
    gaps.push('templateId is missing');
  }
  if (!template.canvas?.baseWidth || !template.canvas?.baseHeight) {
    gaps.push('canvas baseWidth/baseHeight missing');
  }

  for (const layer of layers) {
    const id = layer.layerId || '(unknown)';
    const payload = layer.payload ?? {};

    if (!KNOWN_LAYER_TYPES.includes(layer.type as (typeof KNOWN_LAYER_TYPES)[number])) {
      gaps.push(`layer "${id}": unknown type "${layer.type}"`);
    }

    if (payload.blendMode != null && !BLEND_MODES.includes(payload.blendMode as (typeof BLEND_MODES)[number])) {
      gaps.push(`layer "${id}": blendMode "${payload.blendMode}" not in mobile whitelist`);
    }

    if (payload.cropRatio != null && !MOBILE_CROP_RATIOS.has(String(payload.cropRatio))) {
      gaps.push(`layer "${id}": cropRatio "${payload.cropRatio}" may not map on mobile`);
    }

    if (payload.cropOffsetX != null && typeof payload.cropOffsetX !== 'number') {
      gaps.push(`layer "${id}": cropOffsetX must be a number`);
    }
    if (payload.cropOffsetY != null && typeof payload.cropOffsetY !== 'number') {
      gaps.push(`layer "${id}": cropOffsetY must be a number`);
    }

    if (layer.type === 'TEXT' || layer.type === 'SHAPE_TEXT') {
      if (payload.text == null || String(payload.text).length === 0) {
        gaps.push(`layer "${id}": TEXT layer missing text`);
      }
      if (payload.charSpacing != null && typeof payload.charSpacing !== 'number') {
        gaps.push(`layer "${id}": charSpacing must be numeric for mobile tracking`);
      }
    }

    if (layer.type === 'PLACEHOLDER_OBJECT' || layer.type === 'IMAGE') {
      if (payload.replaceable === true && !payload.defaultImageUrl && !payload.imageUrl) {
        gaps.push(`layer "${id}": replaceable layer missing defaultImageUrl/imageUrl`);
      }
    }

    const shapeType = payload.shapeType;
    if (shapeType != null && !MOBILE_KNOWN_SHAPE_TYPES.has(String(shapeType).toLowerCase())) {
      gaps.push(`layer "${id}": shapeType "${shapeType}" not in mobile known set`);
    }

    if (shapeType === 'arrow' && !payload.pathData) {
      gaps.push(`layer "${id}": arrow shape missing pathData`);
    }

    if (shapeType === 'line' && (payload.strokeWidth == null || payload.strokeWidth === 0)) {
      gaps.push(`layer "${id}": line shape should have strokeWidth for mobile stroke`);
    }

    const fillGrad = (payload as { fillGradient?: unknown }).fillGradient;
    const textGrad = (payload as { textColorGradient?: unknown }).textColorGradient;
    if (fillGrad != null && !hasGradient(fillGrad)) {
      gaps.push(`layer "${id}": fillGradient malformed`);
    }
    if (textGrad != null && !hasGradient(textGrad)) {
      gaps.push(`layer "${id}": textColorGradient malformed`);
    }

    if (payload.shadowIntensity != null && payload.shadowIntensity > 0) {
      if (payload.shadowBlur == null && payload.shadowDistance == null) {
        gaps.push(`layer "${id}": shadowIntensity set but blur/distance missing`);
      }
    }

    if (layer.type === 'SHADOW_REGION' && payload.sourceKind !== 'shadow-region') {
      gaps.push(`layer "${id}": SHADOW_REGION should have sourceKind shadow-region`);
    }

    const w = payload.baseWidth ?? 0;
    const h = payload.baseHeight ?? 0;
    if (w <= 1 && h <= 1 && layer.type === 'DECORATION' && !shapeType) {
      gaps.push(`layer "${id}": junk decoration ${w}x${h} (mobile may skip)`);
    }
  }

  return gaps;
}
