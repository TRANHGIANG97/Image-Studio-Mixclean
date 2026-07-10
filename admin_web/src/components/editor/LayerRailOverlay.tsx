'use client';

import React from 'react';
import { Layers, ChevronRight, ChevronLeft, X } from 'lucide-react';
import LayerPanel from '@/components/canvas/LayerPanel';
import { useEditorUiStore } from '@/store/editor-ui.store';
import { EDITOR_MOTION } from '@/lib/editor-tokens';
import { useLayersStore } from '@/store/layers.store';

interface LayerRailOverlayProps {
  onDirty: () => void;
}

export default function LayerRailOverlay({ onDirty }: LayerRailOverlayProps) {
  const layerRailOpen = useEditorUiStore((s) => s.layerRailOpen);
  const layerRailExpanded = useEditorUiStore((s) => s.layerRailExpanded);
  const setLayerRailOpen = useEditorUiStore((s) => s.setLayerRailOpen);
  const setLayerRailExpanded = useEditorUiStore((s) => s.setLayerRailExpanded);
  const { layers } = useLayersStore();

  if (!layerRailOpen) return null;

  const width = layerRailExpanded
    ? 'var(--editor-layer-rail-expanded)'
    : 'var(--editor-layer-rail-collapsed)';

  return (
    <>
      <div
        className="absolute inset-0 z-20 bg-black/10"
        onClick={() => setLayerRailOpen(false)}
      />
      <aside
        className="absolute top-0 left-0 bottom-0 z-30 flex flex-col border-r shadow-xl overflow-hidden"
        style={{
          width,
          background: 'var(--editor-panel-bg)',
          borderColor: 'var(--editor-border)',
          transition: `width 280ms ${EDITOR_MOTION.springPanel}`,
        }}
      >
        <div className="flex items-center justify-between px-2 py-2 border-b shrink-0" style={{ borderColor: 'var(--editor-border)' }}>
          <span className="text-xs font-bold flex items-center gap-1.5" style={{ color: 'var(--editor-text-primary)' }}>
            <Layers className="w-3.5 h-3.5" style={{ color: 'var(--editor-accent)' }} />
            {layerRailExpanded && 'Layers'}
          </span>
          <div className="flex items-center gap-0.5">
            <button
              type="button"
              onClick={() => setLayerRailExpanded(!layerRailExpanded)}
              className="p-1 rounded-md hover:bg-slate-100"
              aria-label={layerRailExpanded ? 'Thu gọn' : 'Mở rộng'}
            >
              {layerRailExpanded ? <ChevronLeft className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
            </button>
            <button type="button" onClick={() => setLayerRailOpen(false)} className="p-1 rounded-md hover:bg-slate-100" aria-label="Đóng">
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {layerRailExpanded ? (
          <div className="flex-1 min-h-0 overflow-hidden p-2">
            <LayerPanel onDirty={onDirty} />
          </div>
        ) : (
          <div className="flex-1 overflow-y-auto py-2 flex flex-col items-center gap-2">
            <span className="text-[10px] font-mono" style={{ color: 'var(--editor-text-secondary)' }}>
              {layers.length}
            </span>
            {layers.slice(0, 8).map((layer) => (
              <div
                key={layer.id}
                className="w-10 h-10 rounded-lg border flex items-center justify-center text-[9px] font-bold truncate px-1"
                style={{ borderColor: 'var(--editor-border)', color: 'var(--editor-text-secondary)' }}
                title={layer.name}
              >
                {(layer.name || 'L').slice(0, 2)}
              </div>
            ))}
          </div>
        )}
      </aside>
    </>
  );
}
