'use client';

import React from 'react';
import { Eclipse, Image as ImageIcon, Shapes, Type } from 'lucide-react';
import { LayerState } from '@/store/layers.store';
import {
  CHECKERBOARD_BG,
  LAYER_PREVIEW_SIZE_PX,
  normalizeLayerDisplayType,
  textPreviewStyle,
} from './layer-preview-utils';

interface LayerPreviewProps {
  layer: LayerState;
  thumbnail?: string | null;
  size?: number;
}

export function LayerPreview({ layer, thumbnail, size = LAYER_PREVIEW_SIZE_PX }: LayerPreviewProps) {
  const displayType = normalizeLayerDisplayType(layer.type);
  const frameStyle: React.CSSProperties = {
    width: size,
    height: size,
    ...CHECKERBOARD_BG,
  };

  if (displayType === 'SHADOW_REGION' || layer.isShadowRegion) {
    if (thumbnail) {
      return (
        <div
          className="rounded-xl border border-slate-300/80 overflow-hidden"
          style={frameStyle}
          title={layer.name}
        >
          <img src={thumbnail} className="w-full h-full object-contain opacity-90" alt="" draggable={false} />
        </div>
      );
    }
    return (
      <div
        className="rounded-xl border border-slate-300/80 overflow-hidden flex items-center justify-center relative"
        style={frameStyle}
        title={layer.name}
      >
        <div
          className="absolute inset-[14%] rounded-full"
          style={{
            background:
              'radial-gradient(circle, rgba(0,0,0,0.55) 0%, rgba(0,0,0,0.22) 48%, rgba(0,0,0,0.04) 72%, transparent 78%)',
          }}
        />
        <Eclipse className="w-7 h-7 text-slate-500/70 relative z-[1]" strokeWidth={1.5} />
      </div>
    );
  }

  if (displayType === 'TEXT') {
    const preview = layer.textPreview || 'Aa';
    const short = preview.length > 24 ? `${preview.slice(0, 23)}…` : preview;
    const color = typeof layer.fill === 'string' ? layer.fill : '#475569';
    return (
      <div
        className="rounded-xl border border-slate-300/80 flex items-center justify-center overflow-hidden px-1.5"
        style={frameStyle}
        title={preview}
      >
        <span
          className="text-[11px] leading-tight text-center line-clamp-3 max-w-full font-semibold break-words"
          style={{
            fontFamily: layer.fontFamily ? `${layer.fontFamily}, sans-serif` : 'Outfit, sans-serif',
            ...textPreviewStyle(color),
          }}
        >
          {short}
        </span>
      </div>
    );
  }

  if (thumbnail) {
    return (
      <div
        className="rounded-xl border border-slate-300/80 overflow-hidden"
        style={frameStyle}
      >
        <img src={thumbnail} className="w-full h-full object-contain" alt="" draggable={false} />
      </div>
    );
  }

  const iconClass = 'w-8 h-8';
  let icon: React.ReactNode;
  switch (displayType) {
    case 'IMAGE':
      icon = <ImageIcon className={`${iconClass} text-cyan-500`} strokeWidth={1.5} />;
      break;
    case 'DECORATION':
      icon = <Shapes className={`${iconClass} text-amber-500`} strokeWidth={1.5} />;
      break;
    default:
      icon = <Type className={`${iconClass} text-slate-400`} strokeWidth={1.5} />;
  }

  return (
    <div
      className="rounded-xl border border-slate-300/80 flex items-center justify-center"
      style={frameStyle}
      title={layer.name}
    >
      {icon}
    </div>
  );
}
