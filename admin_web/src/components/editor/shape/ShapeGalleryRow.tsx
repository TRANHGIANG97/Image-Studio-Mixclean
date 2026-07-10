'use client';

import React from 'react';
import type { ShapeSubtype } from '@/lib/canvas-factory';
import { t } from '@/i18n/editor';
import type { EditorI18nKey } from '@/i18n/editor';

export interface GalleryShape {
  id: ShapeSubtype | 'text_only';
  labelKey: EditorI18nKey;
  preview: React.ReactNode;
}

const SHAPE_GALLERY: GalleryShape[] = [
  {
    id: 'text_only',
    labelKey: 'studio_label_shape_text_only',
    preview: <span className="text-lg font-bold">T</span>,
  },
  {
    id: 'rect',
    labelKey: 'studio_label_shape_card',
    preview: <div className="w-7 h-5 rounded-sm border-2" style={{ borderColor: 'var(--editor-accent)' }} />,
  },
  {
    id: 'circle',
    labelKey: 'studio_label_shape_circle',
    preview: <div className="w-6 h-6 rounded-full border-2" style={{ borderColor: 'var(--editor-accent)' }} />,
  },
  {
    id: 'star',
    labelKey: 'studio_label_shape_star',
    preview: <span className="text-base">★</span>,
  },
  {
    id: 'triangle',
    labelKey: 'studio_label_shape_triangle',
    preview: <span className="text-base">△</span>,
  },
  {
    id: 'diamond',
    labelKey: 'studio_label_shape_diamond',
    preview: <span className="text-base">◇</span>,
  },
  {
    id: 'hexagon',
    labelKey: 'studio_label_shape_hexagon',
    preview: <span className="text-base">⬡</span>,
  },
  {
    id: 'line',
    labelKey: 'studio_label_shape_line',
    preview: <div className="w-7 h-0.5 rounded" style={{ background: 'var(--editor-accent)' }} />,
  },
  {
    id: 'arrow',
    labelKey: 'studio_label_shape_arrow',
    preview: <span className="text-base">→</span>,
  },
];

interface ShapeGalleryRowProps {
  onSelect: (shape: ShapeSubtype | 'text_only') => void;
  selected?: ShapeSubtype | 'text_only' | null;
  compact?: boolean;
}

export default function ShapeGalleryRow({ onSelect, selected, compact }: ShapeGalleryRowProps) {
  return (
    <div className="space-y-2">
      {!compact && (
        <span className="text-[10px] font-semibold" style={{ color: 'var(--editor-text-secondary)' }}>
          {t('studio_shape_gallery_title')}
        </span>
      )}
      <div className="flex gap-2 overflow-x-auto no-scrollbar pb-1">
        {SHAPE_GALLERY.map((shape) => {
          const active = selected === shape.id;
          return (
            <button
              key={shape.id}
              type="button"
              onClick={() => onSelect(shape.id)}
              className="flex flex-col items-center justify-center gap-1 min-w-[56px] h-14 rounded-xl border shrink-0 transition-colors"
              style={{
                borderColor: active ? 'var(--editor-accent)' : 'var(--editor-border)',
                background: active ? 'var(--editor-accent-soft)' : 'var(--editor-panel-bg)',
              }}
              aria-label={t(shape.labelKey)}
            >
              <div style={{ color: 'var(--editor-accent)' }}>{shape.preview}</div>
              <span
                className="text-[8px] font-semibold leading-none truncate max-w-[52px]"
                style={{ color: active ? 'var(--editor-accent)' : 'var(--editor-text-secondary)' }}
              >
                {t(shape.labelKey)}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}

export { SHAPE_GALLERY };
