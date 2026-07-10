'use client';

import React from 'react';
import { ChevronUp, ChevronDown, Type, Shapes, RotateCw, Sun, Droplets, Crop, ImageIcon, Sticker } from 'lucide-react';
import AssetSidebar from '@/components/canvas/AssetSidebar';
import PropertiesPanel from '@/components/canvas/PropertiesPanel';
import LabelToolPanel from '@/components/editor/label/LabelToolPanel';
import ShapeToolPanel from '@/components/editor/shape/ShapeToolPanel';
import { useEditorUiStore, type EditorToolId } from '@/store/editor-ui.store';
import { EDITOR_MOTION } from '@/lib/editor-tokens';
import { t } from '@/i18n/editor';

const TOOL_CHIPS: { id: EditorToolId; labelKey: Parameters<typeof t>[0]; icon: React.ElementType }[] = [
  { id: 'replace', labelKey: 'studio_tool_replace', icon: ImageIcon },
  { id: 'sticker', labelKey: 'studio_tool_sticker', icon: Sticker },
  { id: 'label', labelKey: 'studio_tool_label', icon: Type },
  { id: 'shape', labelKey: 'studio_tool_shape', icon: Shapes },
  { id: 'rotate', labelKey: 'studio_tool_rotateflip', icon: RotateCw },
  { id: 'shadow', labelKey: 'studio_tool_shadow', icon: Sun },
  { id: 'transparency', labelKey: 'studio_tool_transparency', icon: Droplets },
  { id: 'crop', labelKey: 'studio_tool_crop', icon: Crop },
];

interface ContextPanelProps {
  categoryId: string;
  onDirty: () => void;
}

export default function ContextPanel({ categoryId, onDirty }: ContextPanelProps) {
  const selectedTool = useEditorUiStore((s) => s.selectedTool);
  const controlsExpanded = useEditorUiStore((s) => s.controlsExpanded);
  const toggleControlsExpanded = useEditorUiStore((s) => s.toggleControlsExpanded);
  const toggleTool = useEditorUiStore((s) => s.toggleTool);

  return (
    <section
      className="shrink-0 border-t overflow-hidden flex flex-col"
      style={{
        borderColor: 'var(--editor-border)',
        background: 'var(--editor-panel-bg)',
        maxHeight: controlsExpanded ? 'var(--editor-context-expanded)' : 'var(--editor-context-peek)',
        transition: `max-height 280ms ${EDITOR_MOTION.springPanel}`,
      }}
    >
      <div className="flex items-center gap-2 px-3 py-2 shrink-0 min-h-[44px]">
        <div className="flex items-center gap-1 overflow-x-auto no-scrollbar flex-1">
          {TOOL_CHIPS.map((chip) => {
            const Icon = chip.icon;
            const active = selectedTool === chip.id;
            const label = t(chip.labelKey);
            return (
              <button
                key={chip.id}
                type="button"
                aria-label={label}
                onClick={() => toggleTool(chip.id)}
                className="flex items-center gap-1 px-2.5 py-1.5 rounded-full text-[10px] font-semibold whitespace-nowrap transition-colors shrink-0"
                style={{
                  background: active ? 'var(--editor-accent-soft)' : 'transparent',
                  color: active ? 'var(--editor-accent)' : 'var(--editor-text-secondary)',
                }}
              >
                <Icon className="w-3.5 h-3.5" />
                {label}
              </button>
            );
          })}
        </div>
        <button
          type="button"
          onClick={toggleControlsExpanded}
          className="p-1.5 rounded-lg hover:bg-slate-100 shrink-0"
          aria-label={controlsExpanded ? 'Thu gọn panel' : 'Mở rộng panel'}
          style={{ color: 'var(--editor-text-secondary)' }}
        >
          {controlsExpanded ? <ChevronDown className="w-4 h-4" /> : <ChevronUp className="w-4 h-4" />}
        </button>
      </div>

      {controlsExpanded && selectedTool && (
        <div className="flex-1 min-h-0 overflow-y-auto px-3 pb-3 border-t" style={{ borderColor: 'var(--editor-border)' }}>
          {selectedTool === 'label' ? (
            <LabelToolPanel onDirty={onDirty} />
          ) : selectedTool === 'shape' ? (
            <ShapeToolPanel onDirty={onDirty} />
          ) : selectedTool === 'sticker' || selectedTool === 'replace' ? (
            <AssetSidebar categoryId={categoryId} onDirty={onDirty} />
          ) : (
            <PropertiesPanel onDirty={onDirty} toolFilter={selectedTool} />
          )}
        </div>
      )}

      {controlsExpanded && !selectedTool && (
        <p className="px-4 pb-3 text-[11px]" style={{ color: 'var(--editor-text-secondary)' }}>
          Chọn công cụ ở thanh dưới để chỉnh thuộc tính — không có panel cố định chiếm không gian canvas.
        </p>
      )}
    </section>
  );
}
