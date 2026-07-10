import { CloudTemplate, CloudLayer, CloudPayload, CloudTransform } from '@/types/cloud-template';
import { arrowPath } from '@/lib/fabric-shape-utils';
import { removeCDN } from '@/lib/cdn-rewriter';
import { isFabricTextObject } from '@/lib/canvas-object-props';
import { CURRENT_SCHEMA_VERSION, validateCloudTemplate } from '@/lib/schema/template-contract';

// Helper to calculate GCD for aspect ratio
const gcd = (a: number, b: number): number => (b === 0 ? a : gcd(b, a % b));

/** Gradient serialized from a Fabric fill object (kept as-is in payload). */
interface SerializedGradient {
  type: unknown;
  colorStops: unknown;
  coords: unknown;
}

/**
 * Payload being built during conversion. Extends CloudPayload with
 * converter-internal fields (stroke, gradients, _originalText...) that ride
 * along in the JSON but are not part of the mobile-facing interface.
 */
type ConverterPayload = Partial<CloudPayload> & {
  stroke?: string | null;
  strokeWidth?: number | null;
  strokeDashArray?: unknown;
  imageFilters?: unknown;
  _originalText?: string;
  textColorGradient?: SerializedGradient;
  fillGradient?: SerializedGradient;
};

/** Fabric gradient fill (subset the converter reads). */
interface FabricGradient {
  type?: string;
  colorStops?: Array<{ offset?: number; color: string }>;
  coords?: unknown;
}

type FabricFill = string | FabricGradient | null | undefined;

interface FabricShadowLike {
  offsetX?: number;
  offsetY?: number;
  color?: string;
  blur?: number;
}

/**
 * Structural view of the Fabric.js object properties the converter reads.
 * Fabric's own types churn between versions, so we only pin what we use.
 */
interface FabricObjectLike {
  width: number;
  height: number;
  left: number;
  top: number;
  type?: string;
  originX?: string;
  originY?: string;
  scaleX?: number;
  scaleY?: number;
  angle?: number;
  opacity?: number;
  flipX?: boolean;
  flipY?: boolean;
  visible?: boolean;
  lockMovementX?: boolean;
  lockMovementY?: boolean;
  selectable?: boolean;
  shadow?: FabricShadowLike | null;
  fill?: FabricFill;
  stroke?: FabricFill;
  strokeWidth?: number;
  strokeDashArray?: unknown;
  blendMode?: string;
  globalCompositeOperation?: string;
  layerId?: string;
  layerType?: string;
  isReplaceable?: boolean;
  isShadowRegion?: boolean;
  sourceKind?: string;
  groupPath?: string;
  _isBackground?: boolean;
  // Text properties
  text?: string;
  fontFamily?: string;
  fontSize?: number;
  fontWeight?: string | number;
  fontStyle?: string;
  underline?: boolean;
  textAlign?: string;
  lineHeight?: number;
  charSpacing?: number;
  textBackgroundColor?: string | null;
  linethrough?: boolean;
  textTransform?: string;
  _originalText?: string;
  // Image / shape properties
  src?: string;
  defaultImageUrl?: string;
  cropRatio?: string;
  cropOffsetX?: number;
  cropOffsetY?: number;
  imageFilters?: unknown;
  shapeSubtype?: string;
  path?: unknown;
  points?: unknown;
  rx?: number;
  ry?: number;
}

interface FabricImageLike {
  src?: string;
  defaultImageUrl?: string;
  getSrc?: () => unknown;
  _element?: { src?: string } | null;
  _originalElement?: { src?: string } | null;
}

interface FabricCanvasLike {
  getObjects: () => FabricObjectLike[];
  backgroundImage?: FabricImageLike | null;
  backgroundColor?: FabricFill;
}

/**
 * Converts a hex or rgba color to a 32-bit ARGB integer used by Android.
 * Example: "rgba(0, 0, 0, 0.5)" -> -2147483648
 */
export function colorToArgbInt(colorString: string, opacity: number = 1): number {
  if (!colorString) return -16777216; // Default black ARGB

  let r = 0, g = 0, b = 0, a = opacity;

  if (colorString.startsWith('rgba')) {
    const match = colorString.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)/);
    if (match) {
      r = parseInt(match[1]);
      g = parseInt(match[2]);
      b = parseInt(match[3]);
      a = match[4] !== undefined ? parseFloat(match[4]) : opacity;
    }
  } else if (colorString.startsWith('#')) {
    const hex = colorString.replace('#', '');
    if (hex.length === 3) {
      r = parseInt(hex[0] + hex[0], 16);
      g = parseInt(hex[1] + hex[1], 16);
      b = parseInt(hex[2] + hex[2], 16);
    } else if (hex.length === 6) {
      r = parseInt(hex.slice(0, 2), 16);
      g = parseInt(hex.slice(2, 4), 16);
      b = parseInt(hex.slice(4, 6), 16);
    } else if (hex.length === 8) {
      r = parseInt(hex.slice(0, 2), 16);
      g = parseInt(hex.slice(2, 4), 16);
      b = parseInt(hex.slice(4, 6), 16);
      a = parseInt(hex.slice(6, 8), 16) / 255;
    }
  }

  // Calculate 32-bit ARGB value (signed int)
  const alphaVal = Math.round(a * 255);
  const argb = (alphaVal << 24) | (r << 16) | (g << 8) | b;
  return argb;
}

/**
 * Converts a 32-bit ARGB integer to a standard CSS rgba string.
 */
export function argbIntToRgba(argb: number, opacityOverride?: number): { rgba: string; alpha: number } {
  // Convert signed int to unsigned 32-bit number
  const unsigned = argb >>> 0;
  const a = ((unsigned >> 24) & 0xff) / 255;
  const r = (unsigned >> 16) & 0xff;
  const g = (unsigned >> 8) & 0xff;
  const b = unsigned & 0xff;

  const finalAlpha = opacityOverride !== undefined ? opacityOverride : a;
  return {
    rgba: `rgba(${r}, ${g}, ${b}, ${finalAlpha.toFixed(2)})`,
    alpha: a
  };
}

function fabricPathToSvg(path: unknown): string | null {
  if (typeof path === 'string' && path.trim()) return path;
  if (!Array.isArray(path)) return null;
  return path
    .map((segment) => {
      if (!Array.isArray(segment) || segment.length === 0) return '';
      const [command, ...coords] = segment;
      return `${command} ${coords.join(' ')}`.trim();
    })
    .filter(Boolean)
    .join(' ');
}

function fabricPointsToFlat(points: unknown): number[] | null {
  if (!Array.isArray(points) || points.length === 0) return null;
  const flat: number[] = [];
  for (const point of points) {
    if (point && typeof point === 'object' && 'x' in point && 'y' in point) {
      flat.push(Number((point as { x: number; y: number }).x));
      flat.push(Number((point as { x: number; y: number }).y));
    }
  }
  return flat.length >= 6 ? flat : null;
}

function fabricBackgroundColorToArgb(backgroundColor: FabricFill): number | null {
  if (!backgroundColor) return null;
  if (typeof backgroundColor === 'string') return colorToArgbInt(backgroundColor);
  const firstStop = backgroundColor.colorStops?.[0]?.color;
  return typeof firstStop === 'string' ? colorToArgbInt(firstStop) : null;
}

function fabricImageUrl(image: FabricImageLike | null | undefined): string | null {
  if (!image) return null;
  if (typeof image.src === 'string' && image.src.trim()) return image.src;
  if (typeof image.defaultImageUrl === 'string' && image.defaultImageUrl.trim()) return image.defaultImageUrl;
  if (typeof image.getSrc === 'function') {
    const src = image.getSrc();
    if (typeof src === 'string' && src.trim()) return src;
  }
  const elementSrc = image._element?.src || image._originalElement?.src;
  return typeof elementSrc === 'string' && elementSrc.trim() ? elementSrc : null;
}

function getBlendModeFromComposite(gco?: string | null): string {
  if (!gco) return 'normal';
  switch (gco) {
    case 'source-over':
      return 'normal';
    case 'multiply':
      return 'multiply';
    case 'screen':
      return 'screen';
    case 'overlay':
      return 'overlay';
    case 'darken':
      return 'darken';
    case 'lighten':
      return 'lighten';
    case 'color-dodge':
      return 'color-dodge';
    case 'color-burn':
      return 'color-burn';
    case 'hard-light':
      return 'hard-light';
    case 'soft-light':
      return 'soft-light';
    case 'difference':
      return 'difference';
    case 'exclusion':
      return 'exclusion';
    case 'hue':
      return 'hue';
    case 'saturation':
      return 'saturation';
    case 'color':
      return 'color';
    case 'luminosity':
      return 'luminosity';
    case 'lighter':
      return 'linear-dodge';
    default:
      return 'normal';
  }
}

export interface ShadowParams {
  shadowIntensity: number;
  shadowAngle: number;
  shadowDistance: number;
  shadowColorArgb: number;
  shadowBlur: number;
}

/** Extracts drop-shadow parameters from a Fabric object's shadow property. */
export function extractShadowParams(obj: FabricObjectLike): ShadowParams {
  let shadowIntensity = 0;
  let shadowAngle = 45;
  let shadowDistance = 0;
  let shadowColorArgb = colorToArgbInt('rgba(0, 0, 0, 0.4)');
  let shadowBlur = 15;

  if (obj.shadow) {
    const shadow = obj.shadow;
    const offsetX = shadow.offsetX || 0;
    const offsetY = shadow.offsetY || 0;

    shadowDistance = Math.sqrt(offsetX * offsetX + offsetY * offsetY);
    // Angle in degrees from radians
    shadowAngle = Math.atan2(offsetY, offsetX) * (180 / Math.PI);
    if (shadowAngle < 0) shadowAngle += 360;

    shadowColorArgb = colorToArgbInt(shadow.color || 'rgba(0, 0, 0, 0.4)');
    shadowBlur = shadow.blur || 15;

    // Extract opacity from shadow color
    if (shadow.color) {
      const colorStr = shadow.color.trim();
      if (colorStr.startsWith('rgba')) {
        const match = colorStr.match(/rgba?\((?:\d+,\s*){3}([\d.]+)\)/);
        if (match) shadowIntensity = parseFloat(match[1]);
      } else if (colorStr.startsWith('rgb')) {
        shadowIntensity = 1.0;
      } else if (colorStr.startsWith('#')) {
        const hex = colorStr.replace('#', '');
        if (hex.length === 8) {
          shadowIntensity = parseFloat((parseInt(hex.slice(6, 8), 16) / 255).toFixed(2));
        } else {
          shadowIntensity = 1.0;
        }
      } else {
        shadowIntensity = 1.0;
      }
    } else {
      shadowIntensity = 0.4; // Default fallback if no color
    }
  }

  return { shadowIntensity, shadowAngle, shadowDistance, shadowColorArgb, shadowBlur };
}

/**
 * Converts a Fabric object's position (any origin) into relative anchor
 * coordinates (0.0 → 1.0 of the canvas) plus scale/rotation.
 */
export function extractTransform(
  obj: FabricObjectLike,
  canvasBaseWidth: number,
  canvasBaseHeight: number
): CloudTransform {
  // Calculate absolute center of the object (origin independent)
  const originX = obj.originX || 'left';
  const originY = obj.originY || 'top';

  const width = obj.width * (obj.scaleX || 1);
  const height = obj.height * (obj.scaleY || 1);

  let centerX = obj.left;
  let centerY = obj.top;

  if (originX !== 'center') {
    centerX = obj.left + width / 2;
  }
  if (originY !== 'center') {
    centerY = obj.top + height / 2;
  }

  return {
    anchorX: centerX / canvasBaseWidth,
    anchorY: centerY / canvasBaseHeight,
    scale: obj.scaleX || 1.0,
    rotation: obj.angle || 0.0,
  };
}

/** Extracts text-specific payload fields from a Fabric text object. */
function extractTextPayload(obj: FabricObjectLike): ConverterPayload {
  const payload: ConverterPayload = {
    text: obj.text || '',
    font: obj.fontFamily || 'sans-serif',
    fontSize: obj.fontSize || 60,
  };

  let textColorStr = '#ffffff';
  if (typeof obj.fill === 'string') {
    textColorStr = obj.fill;
  } else if (obj.fill && typeof obj.fill === 'object' && obj.fill.colorStops?.[0]?.color) {
    textColorStr = obj.fill.colorStops[0].color;
  }
  payload.textColorArgb = colorToArgbInt(textColorStr);

  payload.fontWeight = obj.fontWeight || 'normal';
  payload.fontStyle = obj.fontStyle || 'normal';
  payload.underline = obj.underline || false;
  payload.textAlign = obj.textAlign || 'left';
  payload.lineHeight = obj.lineHeight || 1.16;
  payload.charSpacing = obj.charSpacing || 0;
  payload.textBackgroundColor = obj.textBackgroundColor || null;
  payload.linethrough = obj.linethrough || false;
  payload.textTransform = obj.textTransform || 'none';
  payload._originalText = obj._originalText || obj.text || '';
  payload.baseWidth = Math.round(obj.width * (obj.scaleX || 1));
  payload.baseHeight = Math.round(obj.height * (obj.scaleY || 1));

  if (obj.fill && typeof obj.fill === 'object') {
    payload.textColorGradient = {
      type: obj.fill.type,
      colorStops: obj.fill.colorStops,
      coords: obj.fill.coords,
    };
  }

  return payload;
}

/** Extracts image/shape payload fields from a non-text Fabric object. */
function extractImagePayload(obj: FabricObjectLike, isShadowRegion: boolean): ConverterPayload {
  const payload: ConverterPayload = {
    imageUrl: obj.src || null,
    defaultImageUrl: obj.defaultImageUrl || null,
    baseWidth: Math.round(obj.width),
    baseHeight: Math.round(obj.height),
  };

  if (obj.cropRatio) {
    payload.cropRatio = obj.cropRatio;
  }
  if (obj.cropOffsetX != null && obj.cropOffsetX !== 0) {
    payload.cropOffsetX = obj.cropOffsetX;
  }
  if (obj.cropOffsetY != null && obj.cropOffsetY !== 0) {
    payload.cropOffsetY = obj.cropOffsetY;
  }
  if (obj.imageFilters) {
    payload.imageFilters = obj.imageFilters;
  }

  // Support vector shapes serialization
  const isShape =
    ['rect', 'circle', 'triangle', 'line', 'polygon', 'path', 'ellipse'].includes(obj.type ?? '') ||
    obj.shapeSubtype;
  if (isShape || isShadowRegion) {
    const shapeType = obj.shapeSubtype || (isShadowRegion ? 'ellipse' : obj.type);
    payload.shapeType = shapeType;

    let fillColorStr = '#6366f1';
    if (typeof obj.fill === 'string') {
      fillColorStr = obj.fill;
    } else if (obj.fill && typeof obj.fill === 'object' && obj.fill.colorStops?.[0]?.color) {
      fillColorStr = obj.fill.colorStops[0].color;
    }
    if (shapeType === 'line') {
      fillColorStr = typeof obj.stroke === 'string' ? obj.stroke : fillColorStr;
      payload.strokeWidth = obj.strokeWidth ?? 6;
    }
    payload.fillColor = fillColorStr;

    if (obj.fill && typeof obj.fill === 'object') {
      payload.fillGradient = {
        type: obj.fill.type,
        colorStops: obj.fill.colorStops,
        coords: obj.fill.coords,
      };
    }

    if (shapeType === 'arrow' || obj.type === 'path') {
      payload.pathData = fabricPathToSvg(obj.path) ?? (shapeType === 'arrow' ? arrowPath : null);
    }
    if (obj.type === 'polygon' || shapeType === 'polygon') {
      payload.polygonPoints = fabricPointsToFlat(obj.points);
    }

    if (obj.type === 'rect' || obj.shapeSubtype === 'rect') {
      payload.rx = obj.rx || 0;
      payload.ry = obj.ry || 0;
    }
  }

  return payload;
}

/** Builds the raw CloudTemplate from a Fabric canvas (no contract validation). */
function buildCloudTemplate(
  fabricCanvas: FabricCanvasLike | null | undefined,
  canvasBaseWidth: number,
  canvasBaseHeight: number,
  templateId: string,
  categoryId: string,
  title: string,
  status: 'draft' | 'published',
  thumbnailUrl: string | null
): CloudTemplate {
  if (!fabricCanvas) {
    throw new Error('Fabric canvas is not initialized');
  }

  // Get background URL from background image
  const backgroundUrl = fabricImageUrl(fabricCanvas.backgroundImage);
  const backgroundColorArgb = fabricBackgroundColorToArgb(fabricCanvas.backgroundColor);
  const divisor = gcd(canvasBaseWidth, canvasBaseHeight);
  const aspectRatio = `${canvasBaseWidth / divisor}:${canvasBaseHeight / divisor}`;

  // Get layers (excluding background objects)
  const objects = fabricCanvas.getObjects();
  const layers: CloudLayer[] = objects
    .filter((obj) => obj._isBackground !== true)
    .map((obj, index): CloudLayer => {
      const transform = extractTransform(obj, canvasBaseWidth, canvasBaseHeight);
      const shadowParams = extractShadowParams(obj);

      const layerId = obj.layerId || `layer_${Date.now()}_${index}`;
      let layerType = obj.layerType || 'DECORATION';
      const isReplaceable =
        obj.isReplaceable === true || layerType === 'PLACEHOLDER_OBJECT';
      const isShadowRegion =
        obj.isShadowRegion === true ||
        obj.sourceKind === 'shadow-region' ||
        layerType === 'SHADOW_REGION';
      if (isShadowRegion) {
        layerType = 'SHADOW_REGION';
      } else if (isFabricTextObject(obj)) {
        layerType = 'TEXT';
      } else if (isReplaceable) {
        // Normalize checkbox-only path so mobile + admin counts/badges agree.
        layerType = 'PLACEHOLDER_OBJECT';
      }

      let strokeColorStr = null;
      if (typeof obj.stroke === 'string') {
        strokeColorStr = obj.stroke;
      } else if (obj.stroke && typeof obj.stroke === 'object' && obj.stroke.colorStops?.[0]?.color) {
        strokeColorStr = obj.stroke.colorStops[0].color;
      }

      const payload: ConverterPayload = {
        alpha: obj.opacity !== undefined ? obj.opacity : 1,
        flippedH: obj.flipX || false,
        flippedV: obj.flipY || false,
        visible: obj.visible !== false,
        locked: obj.lockMovementX === true || obj.lockMovementY === true || obj.selectable === false,
        groupPath: obj.groupPath || null,
        sourceKind: isShadowRegion ? 'shadow-region' : (obj.sourceKind || null),
        ...shadowParams,
        stroke: strokeColorStr,
        strokeWidth: obj.strokeWidth || 0,
        strokeDashArray: obj.strokeDashArray || null,
        blendMode: obj.blendMode || getBlendModeFromComposite(obj.globalCompositeOperation),
        replaceable: isReplaceable ? true : undefined,
      };

      if (layerType === 'TEXT') {
        Object.assign(payload, extractTextPayload(obj));
      } else {
        Object.assign(payload, extractImagePayload(obj, isShadowRegion));
      }

      return {
        layerId,
        type: layerType,
        zIndex: index,
        transform,
        payload,
      };
    })
    .filter((layer, index) => {
      const obj = objects.filter((o) => o._isBackground !== true)[index];
      if (!obj) return true;
      if (layer.type === 'TEXT' || layer.type === 'SHADOW_REGION') return true;
      const w = Math.round((obj.width ?? layer.payload.baseWidth ?? 0) * (obj.scaleX || 1));
      const h = Math.round((obj.height ?? layer.payload.baseHeight ?? 0) * (obj.scaleY || 1));
      if (w <= 1 && h <= 1) return false;
      const isShape =
        ['rect', 'circle', 'triangle', 'line', 'polygon', 'path', 'ellipse'].includes(obj.type ?? '') ||
        obj.shapeSubtype ||
        layer.payload.shapeType;
      if (isShape) return true;
      if (layer.payload.imageUrl || layer.payload.defaultImageUrl) return true;
      return true;
    });

  return removeCDN({
    templateId,
    categoryId,
    metadata: {
      title,
      thumbnailUrl: thumbnailUrl || backgroundUrl || '',
      status,
      schemaVersion: CURRENT_SCHEMA_VERSION,
      createdAt: Date.now(), // Fallbacks
      updatedAt: Date.now(),
    },
    canvas: {
      baseWidth: canvasBaseWidth,
      baseHeight: canvasBaseHeight,
      aspectRatio,
      backgroundUrl,
      backgroundColorArgb,
    },
    layers,
  }) as CloudTemplate;
}

export interface FabricConversionResult {
  /** Converted template — contract-clamped when validation passes. */
  template: CloudTemplate;
  /** Contract warnings (clamped values, unknown types...). Never throws. */
  warnings: string[];
  /** Contract errors — surfaced to callers; publish gate blocks on these. */
  errors: string[];
}

/**
 * Converts a Fabric.js canvas and validates the result against the data
 * contract (template-contract.ts). Warnings never throw; on contract errors
 * the raw (unclamped) template is still returned so drafts keep working —
 * validateTemplateForPublish blocks the actual publish.
 */
export function fabricToCloudTemplateValidated(
  fabricCanvas: unknown,
  canvasBaseWidth: number,
  canvasBaseHeight: number,
  templateId: string,
  categoryId: string,
  title: string,
  status: 'draft' | 'published' = 'draft',
  thumbnailUrl: string | null = null
): FabricConversionResult {
  const rawTemplate = buildCloudTemplate(
    fabricCanvas as FabricCanvasLike | null,
    canvasBaseWidth,
    canvasBaseHeight,
    templateId,
    categoryId,
    title,
    status,
    thumbnailUrl
  );

  const validation = validateCloudTemplate(rawTemplate);
  return {
    template: validation.data ?? rawTemplate,
    warnings: validation.warnings,
    errors: validation.errors,
  };
}

/**
 * Converts a Fabric.js Canvas state into the standard CloudTemplate JSON structure.
 */
export function fabricToCloudTemplate(
  fabricCanvas: unknown,
  canvasBaseWidth: number,
  canvasBaseHeight: number,
  templateId: string,
  categoryId: string,
  title: string,
  status: 'draft' | 'published' = 'draft',
  thumbnailUrl: string | null = null
): CloudTemplate {
  const { template, warnings, errors } = fabricToCloudTemplateValidated(
    fabricCanvas,
    canvasBaseWidth,
    canvasBaseHeight,
    templateId,
    categoryId,
    title,
    status,
    thumbnailUrl
  );

  if (warnings.length > 0) {
    console.warn('[template-contract] Cảnh báo khi chuyển đổi template:', warnings);
  }
  if (errors.length > 0) {
    console.error('[template-contract] Template vi phạm data contract:', errors);
  }

  return template;
}
