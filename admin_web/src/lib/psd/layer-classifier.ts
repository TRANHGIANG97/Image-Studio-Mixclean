/**
 * Rules Engine — Phase 1 of DATA_PIPELINE_PLAN.md.
 *
 * Pure classification logic: given PSD layer signals (already extracted from
 * @webtoon/psd metadata), decide whether the layer can stay vector/editable or
 * must be rasterized (flattened to a static image) before entering the pipeline.
 *
 * Keeping this module pure (no PSD parsing, no DOM) makes the rules unit-testable
 * and lets psd-client-import.ts stay a thin extraction layer.
 */

export type LayerStrategy = 'keep-vector' | 'rasterize';

/** Signals extracted from a PSD layer node by psd-client-import.ts. */
export interface PsdLayerSignals {
  /** Layer contains editable text (node.text non-empty, not forced to image). */
  isText: boolean;
  /** Text has Warp (arc/bulge/...) — impossible to reproduce on canvas. */
  hasWarp: boolean;
  /** Warp style raw name for warning messages (e.g. "warpArc"). */
  warpStyle?: string | null;
  /** Multiple fonts/sizes/colors inside one text layer. */
  hasMixedStyles: boolean;
  /** Stroke / Bevel / Inner-Outer Glow / Inner Shadow / Color-Gradient-Pattern Overlay. */
  hasComplexEffects: boolean;
  /** Simple Drop Shadow that CAN be mapped to canvas shadow properties. */
  hasDropShadow: boolean;
  /** Adjustment or fill layer (Curves, Levels, Hue, Solid/Gradient fill...). */
  isAdjustment: boolean;
  /** Layer mask or vector mask present. */
  hasMask: boolean;
  /** Clipping mask participant. */
  isClipping: boolean;
  /** Normalized blend mode name (already mapped from PSD codes). */
  blendMode: string;
}

export interface LayerClassification {
  strategy: LayerStrategy;
  /** Human-readable reasons (Vietnamese, shown in admin import warnings). */
  reasons: string[];
  /**
   * True when effects are baked into the raster output, so exported payload
   * must zero out shadowIntensity/shadowDistance/shadowBlur (already-rendered
   * effects must not be re-applied by the app renderer).
   */
  effectsBaked: boolean;
}

/** Blend modes both Fabric.js and Compose render consistently. */
export const SAFE_BLEND_MODES: ReadonlySet<string> = new Set([
  'normal',
  'multiply',
  'screen',
  'overlay',
  'darken',
  'lighten',
]);

/**
 * Classify a text layer.
 * Text stays vector unless it has effects the canvas cannot express.
 */
function classifyText(signals: PsdLayerSignals): LayerClassification {
  const reasons: string[] = [];
  if (signals.hasWarp) {
    reasons.push(`chữ uốn cong (Warp Text: "${signals.warpStyle ?? 'unknown'}")`);
  }
  if (signals.hasMixedStyles) {
    reasons.push('định dạng chữ hỗn hợp (nhiều font/cỡ/màu trong một layer)');
  }
  if (signals.hasComplexEffects) {
    reasons.push('hiệu ứng phức tạp (Stroke/Bevel/Glow)');
  }

  if (reasons.length > 0) {
    return { strategy: 'rasterize', reasons, effectsBaked: true };
  }

  const keepReasons: string[] = [];
  if (signals.hasDropShadow) {
    keepReasons.push('Drop Shadow đơn giản — map sang canvas shadow');
  }
  return { strategy: 'keep-vector', reasons: keepReasons, effectsBaked: false };
}

/**
 * Classify an image-like layer (smart object, shape, pixels, adjustment...).
 *
 * Note: in this pipeline every non-text layer is exported as raster pixels
 * anyway (composite → WebP), so "rasterize" here specifically means the layer
 * must be composited WITH its effects/background baked in (composite(true))
 * and its shadow metadata zeroed so the app does not re-apply effects.
 */
function classifyImage(signals: PsdLayerSignals): LayerClassification {
  const reasons: string[] = [];

  if (signals.hasComplexEffects) {
    reasons.push('hiệu ứng phức tạp (Stroke/Bevel/Glow) — có thể khác biệt nhỏ');
  }
  if (!SAFE_BLEND_MODES.has(signals.blendMode)) {
    reasons.push(`blend mode "${signals.blendMode}" có thể render khác nhau giữa Web và App`);
  }
  if (signals.hasMask || signals.isClipping) {
    reasons.push('mặt nạ lớp (Layer/Vector/Clipping Mask)');
  }

  if (signals.isAdjustment) {
    reasons.push('lớp chỉnh màu/tô màu — tự động composite thành Ảnh');
    return { strategy: 'rasterize', reasons, effectsBaked: true };
  }

  // Raster pixels already contain the masked result; keep as plain image and
  // surface the reasons as warnings. Drop shadow from metadata stays valid.
  return { strategy: 'keep-vector', reasons, effectsBaked: false };
}

export function classifyPsdLayer(signals: PsdLayerSignals): LayerClassification {
  return signals.isText ? classifyText(signals) : classifyImage(signals);
}

// ─────────────────────────────────────────────────────────────────────────────
// Ground-shadow contract (bug 07/2026: double shadow on radial-fade ellipses)
// ─────────────────────────────────────────────────────────────────────────────

interface GradientLike {
  type?: string | null;
  colorStops?: Array<{ offset: number; color: string }> | null;
}

function colorAlpha(color: string): number {
  const trimmed = color.trim().toLowerCase();
  const rgbaMatch = trimmed.match(/rgba?\(\s*[\d.]+\s*,\s*[\d.]+\s*,\s*[\d.]+\s*(?:,\s*([\d.]+)\s*)?\)/);
  if (rgbaMatch) {
    return rgbaMatch[1] !== undefined ? parseFloat(rgbaMatch[1]) : 1;
  }
  if (trimmed.startsWith('#')) {
    const hex = trimmed.slice(1);
    if (hex.length === 8) return parseInt(hex.slice(6, 8), 16) / 255;
    if (hex.length === 4) return parseInt(hex[3] + hex[3], 16) / 255;
    return 1;
  }
  if (trimmed === 'transparent') return 0;
  return 1;
}

/**
 * Detects the "ground shadow" signature: a radial gradient that fades to
 * transparent at the outer edge. Such layers ARE the shadow — any extra
 * shadowIntensity in the payload must be zeroed, otherwise the app renders
 * a double shadow (gradient + drop shadow).
 */
export function isGroundShadowGradient(gradient: GradientLike | null | undefined): boolean {
  if (!gradient) return false;
  if ((gradient.type ?? '').toLowerCase() !== 'radial') return false;
  const stops = gradient.colorStops;
  if (!stops || stops.length < 2) return false;
  const last = [...stops].sort((a, b) => a.offset - b.offset)[stops.length - 1];
  return colorAlpha(last.color) <= 0.05;
}
