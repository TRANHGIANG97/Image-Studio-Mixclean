import { z } from 'zod';
import type { CloudTemplate } from '@/types/cloud-template';

/**
 * Data Contract giữa Web Admin và App Mobile (DATA_PIPELINE_PLAN.md — Phase 2).
 *
 * Đây là nguồn sự thật duy nhất (single source of truth) cho:
 *  - CURRENT_SCHEMA_VERSION (mọi nơi khác phải import từ đây, không hardcode).
 *  - Zod schema runtime cho CloudTemplate (mirror của src/types/cloud-template.ts).
 *  - validateCloudTemplate(): cổng kiểm duyệt tầng 1 — clamp giá trị lệch (warning)
 *    và chặn cấu trúc hỏng (error) trước khi publish.
 */

/** Bump theo quy tắc trong DATA_PIPELINE_PLAN.md §2.2 (chỉ khai báo tại đây). */
export const CURRENT_SCHEMA_VERSION = 1;

/** Các layer type mà App Mobile hiểu được. Type lạ → warning + App skip gracefully. */
export const KNOWN_LAYER_TYPES = [
  'IMAGE',
  'DECORATION',
  'TEXT',
  'SHAPE_TEXT',
  'PLACEHOLDER_OBJECT',
  'SHADOW_REGION',
] as const;

export const LayerTypeEnum = z.enum(KNOWN_LAYER_TYPES);
export type KnownLayerType = (typeof KNOWN_LAYER_TYPES)[number];

export const BLEND_MODES = [
  'normal',
  'multiply',
  'screen',
  'overlay',
  'darken',
  'lighten',
  'color-dodge',
  'color-burn',
  'hard-light',
  'soft-light',
  'difference',
  'exclusion',
  'hue',
  'saturation',
  'color',
  'luminosity',
  'linear-dodge',
] as const;

export const BlendModeEnum = z.enum(BLEND_MODES);

// ---------------------------------------------------------------------------
// Numeric bounds — dùng chung cho cả Zod schema (.min/.max) lẫn bước clamp
// tự động trong validateCloudTemplate, đảm bảo hai nơi không bao giờ lệch nhau.
// ---------------------------------------------------------------------------

interface NumericBound {
  min: number;
  max: number;
  /** Giá trị thay thế khi gặp NaN/Infinity. */
  def: number;
  /** Góc quay: wrap theo modulo 360 thay vì clamp cứng (giữ đúng hình ảnh). */
  wrapDegrees?: boolean;
}

const TRANSFORM_BOUNDS: Record<string, NumericBound> = {
  anchorX: { min: -0.5, max: 2, def: 0.5 },
  anchorY: { min: -0.5, max: 2, def: 0.5 },
  scale: { min: 0.01, max: 50, def: 1 },
  rotation: { min: -360, max: 360, def: 0, wrapDegrees: true },
};

const PAYLOAD_BOUNDS: Record<string, NumericBound> = {
  alpha: { min: 0, max: 1, def: 1 },
  shadowIntensity: { min: 0, max: 1, def: 0 },
  shadowAngle: { min: 0, max: 360, def: 45, wrapDegrees: true },
  shadowDistance: { min: 0, max: 5000, def: 0 },
  shadowBlur: { min: 0, max: 200, def: 15 },
  fontSize: { min: 1, max: 1000, def: 60 },
  lineHeight: { min: 0.05, max: 20, def: 1.16 },
  charSpacing: { min: -5000, max: 10000, def: 0 },
  baseWidth: { min: 0, max: 32768, def: 0 },
  baseHeight: { min: 0, max: 32768, def: 0 },
  rx: { min: 0, max: 10000, def: 0 },
  ry: { min: 0, max: 10000, def: 0 },
};

const LAYER_BOUNDS: Record<string, NumericBound> = {
  zIndex: { min: 0, max: 10000, def: 0 },
};

const bounded = (b: NumericBound) => z.number().min(b.min).max(b.max);

/** ARGB 32-bit: chấp nhận cả dạng signed (bitwise JS) lẫn unsigned. */
const ArgbInt = z.number().int().min(-2147483648).max(4294967295);

// ---------------------------------------------------------------------------
// Schemas — dùng looseObject để trường lạ (từ schema version mới hơn hoặc
// metadata nội bộ như _originalText, fillGradient...) đi xuyên qua thay vì mất.
// ---------------------------------------------------------------------------

export const CloudTransformSchema = z.object({
  anchorX: bounded(TRANSFORM_BOUNDS.anchorX).default(0.5),
  anchorY: bounded(TRANSFORM_BOUNDS.anchorY).default(0.5),
  scale: bounded(TRANSFORM_BOUNDS.scale).default(1),
  rotation: bounded(TRANSFORM_BOUNDS.rotation).default(0),
});

export const CloudPayloadSchema = z.looseObject({
  defaultImageUrl: z.string().nullish(),
  imageUrl: z.string().nullish(),
  visible: z.boolean().nullish().default(true),
  locked: z.boolean().nullish().default(false),
  groupPath: z.string().nullish(),
  sourceKind: z.string().nullish(),
  shadowIntensity: bounded(PAYLOAD_BOUNDS.shadowIntensity).nullish().default(0),
  shadowAngle: bounded(PAYLOAD_BOUNDS.shadowAngle).nullish().default(45),
  shadowDistance: bounded(PAYLOAD_BOUNDS.shadowDistance).nullish().default(0),
  alpha: bounded(PAYLOAD_BOUNDS.alpha).nullish().default(1),
  shadowColorArgb: ArgbInt.nullish(),
  shadowBlur: bounded(PAYLOAD_BOUNDS.shadowBlur).nullish().default(15),
  cropRatio: z.string().nullish(),
  flippedH: z.boolean().nullish().default(false),
  flippedV: z.boolean().nullish().default(false),
  baseWidth: bounded(PAYLOAD_BOUNDS.baseWidth).nullish(),
  baseHeight: bounded(PAYLOAD_BOUNDS.baseHeight).nullish(),
  text: z.string().nullish(),
  font: z.string().nullish(),
  textColorArgb: ArgbInt.nullish(),
  fontSize: bounded(PAYLOAD_BOUNDS.fontSize).nullish(),
  fill: z.string().nullish(),
  fontWeight: z.union([z.string(), z.number()]).nullish(),
  fontStyle: z.string().nullish(),
  textAlign: z.string().nullish(),
  underline: z.boolean().nullish(),
  lineHeight: bounded(PAYLOAD_BOUNDS.lineHeight).nullish(),
  charSpacing: bounded(PAYLOAD_BOUNDS.charSpacing).nullish(),
  textBackgroundColor: z.string().nullish(),
  linethrough: z.boolean().nullish(),
  textTransform: z.string().nullish(),
  shapeType: z.string().nullish(),
  fillColor: z.string().nullish(),
  rx: bounded(PAYLOAD_BOUNDS.rx).nullish(),
  ry: bounded(PAYLOAD_BOUNDS.ry).nullish(),
  blendMode: BlendModeEnum.nullish().default('normal'),
  pathData: z.string().nullish(),
  polygonPoints: z.array(z.number()).nullish(),
  replaceable: z.boolean().nullish(),
});

export const TemplateMetadataSchema = z.looseObject({
  title: z.string().default(''),
  thumbnailUrl: z.string().nullish().default(''),
  status: z.enum(['draft', 'published']).default('draft'),
  environment: z.enum(['debug', 'release', 'all']).nullish(),
  schemaVersion: z.number().int().min(1).max(1000).default(CURRENT_SCHEMA_VERSION),
  createdAt: z.number().min(0).default(() => Date.now()),
  updatedAt: z.number().min(0).default(() => Date.now()),
});

export const TemplateCanvasSchema = z.looseObject({
  baseWidth: z.number().min(1).max(16384),
  baseHeight: z.number().min(1).max(16384),
  aspectRatio: z.string().min(1).default('9:16'),
  backgroundUrl: z.string().nullish().default(null),
  backgroundColorArgb: ArgbInt.nullish(),
});

/**
 * Warning được phát qua superRefine bằng prefix này để validateCloudTemplate
 * phân loại issue thành warning (hiển thị) thay vì error (chặn publish).
 */
const WARNING_PREFIX = '[WARN]';

const buildCloudLayerSchema = (emitWarnings: boolean) =>
  z
    .looseObject({
      layerId: z.string().min(1),
      type: z.string().min(1),
      zIndex: z.number().int().min(LAYER_BOUNDS.zIndex.min).max(LAYER_BOUNDS.zIndex.max).default(0),
      transform: CloudTransformSchema,
      payload: CloudPayloadSchema,
    })
    .superRefine((layer, ctx) => {
      const payload = (layer.payload ?? {}) as Record<string, unknown>;

      // (a) Bài học bug "đối tượng thay thế" 07/2026: replaceable bắt buộc có ảnh mặc định.
      const isReplaceable = layer.type === 'PLACEHOLDER_OBJECT' || payload.replaceable === true;
      const hasImage =
        (typeof payload.defaultImageUrl === 'string' && payload.defaultImageUrl.trim() !== '') ||
        (typeof payload.imageUrl === 'string' && payload.imageUrl.trim() !== '');
      if (isReplaceable && !hasImage) {
        ctx.addIssue({
          code: 'custom',
          path: ['payload', 'defaultImageUrl'],
          message: 'Đối tượng thay thế bắt buộc có defaultImageUrl hoặc imageUrl.',
        });
      }

      if (!emitWarnings) return;

      // Layer type lạ: cho đi qua (App skip gracefully) nhưng cảnh báo admin.
      if (!(KNOWN_LAYER_TYPES as readonly string[]).includes(layer.type)) {
        ctx.addIssue({
          code: 'custom',
          path: ['type'],
          message: `${WARNING_PREFIX} Layer type "${layer.type}" không nằm trong danh sách App hỗ trợ (${KNOWN_LAYER_TYPES.join(', ')}) — App sẽ bỏ qua layer này.`,
        });
      }

      // (b) Bài học bug "bóng kép" 07/2026: gradient radial fade-to-transparent
      // đã là bóng "nướng sẵn" — shadowIntensity phải bằng 0.
      const shadowIntensity = typeof payload.shadowIntensity === 'number' ? payload.shadowIntensity : 0;
      if (hasRadialFadeGradient(payload.fillGradient) && shadowIntensity > 0) {
        ctx.addIssue({
          code: 'custom',
          path: ['payload', 'shadowIntensity'],
          message: `${WARNING_PREFIX} Layer có gradient radial fade-to-transparent (bóng nướng sẵn) nhưng shadowIntensity = ${shadowIntensity} — sẽ tự chỉnh về 0 để tránh bóng kép.`,
        });
      }

      // (c) Đồng bộ 2 cách đánh dấu vùng bóng.
      if (layer.type === 'SHADOW_REGION' && payload.sourceKind !== 'shadow-region') {
        ctx.addIssue({
          code: 'custom',
          path: ['payload', 'sourceKind'],
          message: `${WARNING_PREFIX} Layer SHADOW_REGION nên có sourceKind = "shadow-region" (hiện tại: ${JSON.stringify(payload.sourceKind ?? null)}).`,
        });
      }
    });

/** Schema layer đầy đủ (bao gồm cả warning qua superRefine). */
export const CloudLayerSchema = buildCloudLayerSchema(true);

const buildCloudTemplateSchema = (emitWarnings: boolean) =>
  z.looseObject({
    templateId: z.string().min(1),
    categoryId: z.string().default(''),
    metadata: TemplateMetadataSchema,
    canvas: TemplateCanvasSchema,
    layers: z.array(buildCloudLayerSchema(emitWarnings)).default([]),
  });

export const CloudTemplateSchema = buildCloudTemplateSchema(true);
/** Bản không phát warning — dùng để lấy data khi issue chỉ toàn warning. */
const CloudTemplateSchemaSilent = buildCloudTemplateSchema(false);

// ---------------------------------------------------------------------------
// Gradient helpers
// ---------------------------------------------------------------------------

/** Trích alpha (0..1) từ chuỗi màu CSS: rgba(...), #rrggbbaa, 'transparent'. */
function colorAlpha(color: unknown): number {
  if (typeof color !== 'string') return 1;
  const c = color.trim().toLowerCase();
  if (c === 'transparent') return 0;
  if (c.startsWith('rgba')) {
    const match = c.match(/rgba\(\s*[\d.]+\s*,\s*[\d.]+\s*,\s*[\d.]+\s*,\s*([\d.]+)\s*\)/);
    return match ? parseFloat(match[1]) : 1;
  }
  if (c.startsWith('#')) {
    const hex = c.slice(1);
    if (hex.length === 8) return parseInt(hex.slice(6, 8), 16) / 255;
  }
  return 1;
}

/**
 * Gradient radial có colorStop cuối cùng trong suốt (fade-to-transparent) —
 * dấu hiệu "bóng đổ sàn" đã được nướng sẵn vào gradient.
 * Mirror của heuristic hasGradientBakedShadow() phía App (CloudTemplateExtensions.kt).
 */
export function hasRadialFadeGradient(gradient: unknown): boolean {
  if (!gradient || typeof gradient !== 'object') return false;
  const g = gradient as { type?: unknown; colorStops?: unknown };
  if (g.type !== 'radial' || !Array.isArray(g.colorStops) || g.colorStops.length === 0) {
    return false;
  }
  const stops = [...(g.colorStops as Array<Record<string, unknown>>)].sort(
    (a, b) => (Number(a?.offset) || 0) - (Number(b?.offset) || 0)
  );
  const last = stops[stops.length - 1];
  if (!last || typeof last !== 'object') return false;
  if (typeof last.opacity === 'number') return last.opacity === 0;
  return colorAlpha(last.color) === 0;
}

// ---------------------------------------------------------------------------
// validateCloudTemplate — cổng kiểm duyệt tầng 1
// ---------------------------------------------------------------------------

export interface CloudTemplateValidationResult {
  /** Template đã được clamp/fix an toàn — null nếu có error chặn publish. */
  data: CloudTemplate | null;
  /** Lỗi cấu trúc — CHẶN publish. */
  errors: string[];
  /** Cảnh báo (giá trị đã tự chỉnh, layer type lạ...) — hiển thị, không chặn. */
  warnings: string[];
}

function deepClone<T>(value: T): T {
  try {
    return structuredClone(value);
  } catch {
    return JSON.parse(JSON.stringify(value)) as T;
  }
}

function wrapDegrees(value: number, bound: NumericBound): number {
  // min = 0 (shadowAngle): chuẩn hóa về [0, 360). min = -360 (rotation): giữ dấu.
  const wrapped = bound.min >= 0 ? ((value % 360) + 360) % 360 : value % 360;
  return Math.min(bound.max, Math.max(bound.min, wrapped));
}

function clampNumericFields(
  target: Record<string, unknown>,
  bounds: Record<string, NumericBound>,
  contextLabel: string,
  warnings: string[]
): void {
  for (const [key, bound] of Object.entries(bounds)) {
    const value = target[key];
    if (value === undefined || value === null || typeof value !== 'number') continue;
    if (!Number.isFinite(value)) {
      target[key] = bound.def;
      warnings.push(
        `${contextLabel}.${key}: giá trị không hợp lệ (${value}) → dùng mặc định ${bound.def}.`
      );
      continue;
    }
    if (value < bound.min || value > bound.max) {
      const fixed = bound.wrapDegrees
        ? wrapDegrees(value, bound)
        : Math.min(bound.max, Math.max(bound.min, value));
      target[key] = fixed;
      warnings.push(
        `${contextLabel}.${key}: ${value} ngoài khoảng [${bound.min}, ${bound.max}] → tự chỉnh thành ${fixed}.`
      );
    }
  }
}

function layerLabel(layer: unknown, index: number): string {
  const layerId =
    layer && typeof layer === 'object' && typeof (layer as { layerId?: unknown }).layerId === 'string'
      ? ` (${(layer as { layerId: string }).layerId})`
      : '';
  return `layers[${index}]${layerId}`;
}

/**
 * Bước tiền xử lý: clamp giá trị số lệch chuẩn + whitelist blendMode.
 * Mọi thay đổi đều phát warning — không sửa dữ liệu im lặng (xem risk table trong plan).
 */
function sanitizeTemplate(template: Record<string, unknown>, warnings: string[]): Record<string, unknown> {
  const layers = template.layers;
  if (!Array.isArray(layers)) return template;

  layers.forEach((layer, index) => {
    if (!layer || typeof layer !== 'object') return;
    const layerRecord = layer as Record<string, unknown>;
    const label = layerLabel(layerRecord, index);

    clampNumericFields(layerRecord, LAYER_BOUNDS, label, warnings);

    const transform = layerRecord.transform;
    if (transform && typeof transform === 'object') {
      clampNumericFields(transform as Record<string, unknown>, TRANSFORM_BOUNDS, `${label}.transform`, warnings);
    }

    const payload = layerRecord.payload;
    if (payload && typeof payload === 'object') {
      const payloadRecord = payload as Record<string, unknown>;
      clampNumericFields(payloadRecord, PAYLOAD_BOUNDS, `${label}.payload`, warnings);

      const blendMode = payloadRecord.blendMode;
      if (
        typeof blendMode === 'string' &&
        !(BLEND_MODES as readonly string[]).includes(blendMode)
      ) {
        payloadRecord.blendMode = 'normal';
        warnings.push(
          `${label}.payload.blendMode: "${blendMode}" không được App hỗ trợ → tự chỉnh thành "normal".`
        );
      }
    }
  });

  return template;
}

/** Auto-fix invariant (b): gradient bóng nướng sẵn ⟹ shadowIntensity = 0. */
function applyGradientShadowFix(template: CloudTemplate): CloudTemplate {
  for (const layer of template.layers ?? []) {
    const payload = layer.payload as Record<string, unknown> | undefined;
    if (!payload) continue;
    const shadowIntensity = typeof payload.shadowIntensity === 'number' ? payload.shadowIntensity : 0;
    if (shadowIntensity > 0 && hasRadialFadeGradient(payload.fillGradient)) {
      payload.shadowIntensity = 0;
    }
  }
  return template;
}

interface IssueLike {
  path: PropertyKey[];
  message: string;
}

function formatIssue(issue: IssueLike, input: Record<string, unknown>): string {
  const path = issue.path;
  const message = issue.message.startsWith(WARNING_PREFIX)
    ? issue.message.slice(WARNING_PREFIX.length).trim()
    : issue.message;

  if (path[0] === 'layers' && typeof path[1] === 'number') {
    const layers = input.layers;
    const layer = Array.isArray(layers) ? layers[path[1]] : undefined;
    const rest = path.slice(2).map(String).join('.');
    return `${layerLabel(layer, path[1])}${rest ? `.${rest}` : ''}: ${message}`;
  }

  const label = path.map(String).join('.');
  return label ? `${label}: ${message}` : message;
}

/**
 * Tầng 1 của Validation Gate (DATA_PIPELINE_PLAN.md §2.4):
 *  - Clamp an toàn các giá trị số lệch chuẩn (kèm warning, không sửa im lặng).
 *  - safeParse qua Zod schema — cấu trúc hỏng → error chặn publish.
 *  - Cross-field invariants (superRefine): replaceable thiếu ảnh (error),
 *    bóng kép gradient (warning + auto-fix), SHADOW_REGION lệch sourceKind (warning).
 */
export function validateCloudTemplate(template: unknown): CloudTemplateValidationResult {
  const warnings: string[] = [];
  const errors: string[] = [];

  if (!template || typeof template !== 'object' || Array.isArray(template)) {
    return {
      data: null,
      errors: ['Template phải là một object JSON hợp lệ.'],
      warnings,
    };
  }

  const sanitized = sanitizeTemplate(deepClone(template) as Record<string, unknown>, warnings);
  const result = CloudTemplateSchema.safeParse(sanitized);

  if (result.success) {
    return {
      data: applyGradientShadowFix(result.data as unknown as CloudTemplate),
      errors,
      warnings,
    };
  }

  for (const issue of result.error.issues) {
    const formatted = formatIssue(issue as IssueLike, sanitized);
    if (issue.message.startsWith(WARNING_PREFIX)) {
      warnings.push(formatted);
    } else {
      errors.push(formatted);
    }
  }

  if (errors.length > 0) {
    return { data: null, errors, warnings };
  }

  // Chỉ toàn warning: parse lại bằng schema "im lặng" để lấy data đã validate.
  const silent = CloudTemplateSchemaSilent.safeParse(sanitized);
  if (silent.success) {
    return {
      data: applyGradientShadowFix(silent.data as unknown as CloudTemplate),
      errors,
      warnings,
    };
  }

  // Không xảy ra trong thực tế (hai schema chỉ khác phần warning), nhưng phòng hờ.
  return {
    data: null,
    errors: silent.error.issues.map((issue) => formatIssue(issue as IssueLike, sanitized)),
    warnings,
  };
}
