'use client';

import React from 'react';
import {
  ImageIcon,
  Sticker,
  Type,
  Shapes,
  RotateCw,
  Sun,
  Droplets,
  Crop,
  Copy,
  Trash2,
} from 'lucide-react';
import { EDITOR_MOTION } from '@/lib/editor-tokens';
import { useEditorUiStore, type EditorToolId } from '@/store/editor-ui.store';
import { useLayersStore } from '@/store/layers.store';
import { resolveLayerType } from '@/lib/canvas-object-props';
import { t } from '@/i18n/editor';

const TOOLS: { id: EditorToolId; labelKey: Parameters<typeof t>[0]; icon: React.ElementType; instant?: boolean }[] = [
  { id: 'replace', labelKey: 'studio_tool_replace', icon: ImageIcon },
  { id: 'sticker', labelKey: 'studio_tool_sticker', icon: Sticker },
  { id: 'label', labelKey: 'studio_tool_label', icon: Type },
  { id: 'shape', labelKey: 'studio_tool_shape', icon: Shapes },
  { id: 'rotate', labelKey: 'studio_tool_rotateflip', icon: RotateCw },
  { id: 'shadow', labelKey: 'studio_tool_shadow', icon: Sun },
  { id: 'transparency', labelKey: 'studio_tool_transparency', icon: Droplets },
  { id: 'crop', labelKey: 'studio_tool_crop', icon: Crop },
  { id: 'duplicate', labelKey: 'studio_tool_duplicate', icon: Copy, instant: true },
  { id: 'delete', labelKey: 'studio_tool_delete', icon: Trash2, instant: true },
];

interface BottomToolDockProps {
  canReplaceImage?: boolean;
  labelLayerActive?: boolean;
  toolsLocked?: boolean;
}

export default function BottomToolDock({
  canReplaceImage = true,
  labelLayerActive = false,
  toolsLocked = false,
}: BottomToolDockProps) {
  const selectedTool = useEditorUiStore((s) => s.selectedTool);
  const toggleTool = useEditorUiStore((s) => s.toggleTool);
  const canvasActions = useEditorUiStore((s) => s.canvasActions);
  const setAssetDrawerOpen = useEditorUiStore((s) => s.setAssetDrawerOpen);
  const activeObjectProps = useLayersStore((s) => s.activeObjectProps);

  const isTextLayerSelected =
    activeObjectProps?.layerType === 'TEXT' ||
    (activeObjectProps && resolveLayerType(activeObjectProps) === 'TEXT');

  const handleToolClick = (tool: (typeof TOOLS)[number]) => {
    if (tool.id === 'replace') {
      canvasActions.openReplace?.();
      return;
    }
    if (tool.id === 'sticker') {
      setAssetDrawerOpen(true);
      toggleTool('sticker');
      return;
    }
    if (tool.id === 'label') {
      if (!isTextLayerSelected) canvasActions.addText?.();
      toggleTool('label');
      return;
    }
    if (tool.id === 'shape') {
      toggleTool('shape');
      return;
    }
    if (tool.id === 'crop') {
      canvasActions.openCrop?.();
      toggleTool('crop');
      return;
    }
    if (tool.id === 'duplicate') {
      canvasActions.duplicateSelection?.();
      return;
    }
    if (tool.id === 'delete') {
      canvasActions.deleteSelection?.();
      return;
    }
    if (tool.instant) return;
    toggleTool(tool.id);
  };

  const isEnabled = (toolId: EditorToolId) => {
    if (toolId === 'replace') return canReplaceImage;
    if (toolId === 'crop') return !toolsLocked && !labelLayerActive;
    if (labelLayerActive) return true;
    return !toolsLocked;
  };

  const isSelected = (toolId: EditorToolId) =>
    toolId !== 'duplicate' && toolId !== 'delete' && selectedTool === toolId;

  return (
    <nav
      className="shrink-0 border-t overflow-x-auto no-scrollbar"
      style={{
        height: 'var(--editor-bottom-dock-h)',
        background: 'var(--editor-dock-bg)',
        borderColor: 'var(--editor-border)',
      }}
      aria-label="Editor tools"
    >
      <div className="flex items-center gap-1 px-2 py-2.5 min-w-max h-full">
        {TOOLS.map((tool) => {
          const Icon = tool.icon;
          const selected = isSelected(tool.id);
          const enabled = isEnabled(tool.id);
          const label = t(tool.labelKey);
          return (
            <ToolButton
              key={tool.id}
              label={label}
              selected={selected}
              enabled={enabled}
              onClick={() => enabled && handleToolClick(tool)}
            >
              <Icon className="w-5 h-5" />
            </ToolButton>
          );
        })}
      </div>
    </nav>
  );
}

function ToolButton({
  children,
  label,
  selected,
  enabled,
  onClick,
}: {
  children: React.ReactNode;
  label: string;
  selected: boolean;
  enabled: boolean;
  onClick: () => void;
}) {
  const [pressed, setPressed] = React.useState(false);

  return (
    <button
      type="button"
      aria-label={label}
      aria-pressed={selected}
      disabled={!enabled}
      onClick={onClick}
      onPointerDown={() => setPressed(true)}
      onPointerUp={() => setPressed(false)}
      onPointerLeave={() => setPressed(false)}
      className="relative flex flex-col items-center justify-center gap-0.5 min-w-[56px] h-12 rounded-2xl transition-all disabled:opacity-35"
      style={{
        transform: pressed ? `scale(${EDITOR_MOTION.pressScale})` : selected ? `scale(${EDITOR_MOTION.selectionScale})` : 'scale(1)',
        transition: `transform 150ms ${EDITOR_MOTION.springEmphasized}, background 120ms ease`,
        color: selected ? 'var(--editor-accent)' : 'var(--editor-text-secondary)',
        background: selected ? 'var(--editor-accent-soft)' : 'transparent',
      }}
    >
      {children}
      <span className="text-[9px] font-semibold leading-none">{label}</span>
      {selected && (
        <span
          className="absolute bottom-1 left-1/2 -translate-x-1/2 h-1 w-5 rounded-full"
          style={{ background: 'var(--editor-accent)' }}
        />
      )}
    </button>
  );
}
