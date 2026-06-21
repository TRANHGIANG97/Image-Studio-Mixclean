import type { CSSProperties } from 'react';

/** Shared layer preview helpers — checkerboard, contrast, type labels. */

export const LAYER_PREVIEW_SIZE_PX = 72;

export const CHECKERBOARD_BG: CSSProperties = {
  backgroundColor: '#f8fafc',
  backgroundImage: `
    linear-gradient(45deg, #cbd5e1 25%, transparent 25%),
    linear-gradient(-45deg, #cbd5e1 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, #cbd5e1 75%),
    linear-gradient(-45deg, transparent 75%, #cbd5e1 75%)
  `,
  backgroundSize: '10px 10px',
  backgroundPosition: '0 0, 0 5px, 5px -5px, -5px 0',
};

export type LayerDisplayType = 'TEXT' | 'IMAGE' | 'SHADOW_REGION' | 'DECORATION' | 'PLACEHOLDER_OBJECT';

export const LAYER_TYPE_BADGES: Record<
  LayerDisplayType,
  { label: string; className: string }
> = {
  TEXT: { label: 'Chữ', className: 'bg-pink-100 text-pink-700 border-pink-200/80' },
  IMAGE: { label: 'Ảnh', className: 'bg-cyan-100 text-cyan-700 border-cyan-200/80' },
  SHADOW_REGION: { label: 'Bóng', className: 'bg-slate-200 text-slate-700 border-slate-300/80' },
  DECORATION: { label: 'Hình', className: 'bg-amber-100 text-amber-800 border-amber-200/80' },
  PLACEHOLDER_OBJECT: { label: 'Thay thế', className: 'bg-pink-100 text-pink-700 border-pink-200/80' },
};

export function normalizeLayerDisplayType(type: string): LayerDisplayType {
  if (type === 'TEXT') return 'TEXT';
  if (type === 'PLACEHOLDER_OBJECT') return 'PLACEHOLDER_OBJECT';
  if (type === 'IMAGE') return 'IMAGE';
  if (type === 'SHADOW_REGION') return 'SHADOW_REGION';
  return 'DECORATION';
}

function parseRgb(color: string): { r: number; g: number; b: number } | null {
  const hex = color.trim();
  if (hex.startsWith('#')) {
    const h = hex.slice(1);
    const full =
      h.length === 3
        ? h.split('').map((c) => c + c).join('')
        : h.length >= 6
          ? h.slice(0, 6)
          : null;
    if (!full) return null;
    return {
      r: parseInt(full.slice(0, 2), 16),
      g: parseInt(full.slice(2, 4), 16),
      b: parseInt(full.slice(4, 6), 16),
    };
  }
  const rgba = color.match(/rgba?\(([^)]+)\)/i);
  if (rgba) {
    const parts = rgba[1].split(',').map((p) => parseFloat(p.trim()));
    if (parts.length >= 3) {
      return { r: parts[0], g: parts[1], b: parts[2] };
    }
  }
  return null;
}

export function isLightFillColor(color: string | undefined | null): boolean {
  if (!color || typeof color !== 'string') return false;
  const lower = color.toLowerCase();
  if (lower === 'white' || lower === '#fff' || lower === '#ffffff') return true;
  const rgb = parseRgb(color);
  if (!rgb) return false;
  const luminance = (0.299 * rgb.r + 0.587 * rgb.g + 0.114 * rgb.b) / 255;
  return luminance > 0.72;
}

export function textPreviewStyle(color: string): CSSProperties {
  const light = isLightFillColor(color);
  return light
    ? {
        color,
        textShadow:
          '0 0 3px rgba(0,0,0,0.9), 0 1px 4px rgba(0,0,0,0.75), 0 0 1px rgba(0,0,0,1)',
      }
    : {
        color,
        textShadow: '0 0 2px rgba(255,255,255,0.35)',
      };
}
