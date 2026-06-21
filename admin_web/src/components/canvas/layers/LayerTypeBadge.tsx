'use client';

import React from 'react';
import { LAYER_TYPE_BADGES, normalizeLayerDisplayType } from './layer-preview-utils';

export function LayerTypeBadge({ type }: { type: string }) {
  const key = normalizeLayerDisplayType(type);
  const badge = LAYER_TYPE_BADGES[key];
  return (
    <span
      className={`inline-flex items-center px-1.5 py-0.5 rounded-md text-[9px] font-bold uppercase tracking-wide border ${badge.className}`}
    >
      {badge.label}
    </span>
  );
}
