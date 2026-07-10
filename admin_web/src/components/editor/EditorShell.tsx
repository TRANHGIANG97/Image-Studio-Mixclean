'use client';

import React from 'react';
import EditorTopBar from '@/components/editor/EditorTopBar';
import BottomToolDock from '@/components/editor/BottomToolDock';
import ContextPanel from '@/components/editor/ContextPanel';
import LayerRailOverlay from '@/components/editor/LayerRailOverlay';
import CanvasWorkspace from '@/components/canvas/CanvasWorkspace';
import { useLayersStore } from '@/store/layers.store';
import { resolveLayerType } from '@/lib/canvas-object-props';

export interface EditorShellProps {
  template: any;
  templateRouteId: string;
  isSaving: boolean;
  isDirty: boolean;
  lastSavedAt: Date | null;
  layerErrors: string[];
  missingFonts: string[];
  cursorPos: { x: number; y: number };
  onSave: () => void;
  onReloadThumbnail: () => void;
  onExportPNG: () => void;
  onExportWEBP: () => void;
  onPreview: () => void;
  onPublishDebug: () => void;
  onPublishRelease: () => void;
  onRevertDraft: () => void;
  onDelete: () => void;
  onDirty: () => void;
  onLayerLoadError: (err: string) => void;
  onBootstrapFabricState: () => void;
  onDismissLayerErrors: () => void;
  onDismissMissingFonts: () => void;
  formatSavedTime: (date: Date) => string;
  children?: React.ReactNode;
}

export default function EditorShell({
  template,
  templateRouteId,
  isSaving,
  isDirty,
  lastSavedAt,
  layerErrors,
  missingFonts,
  cursorPos,
  onSave,
  onReloadThumbnail,
  onExportPNG,
  onExportWEBP,
  onPreview,
  onPublishDebug,
  onPublishRelease,
  onRevertDraft,
  onDelete,
  onDirty,
  onLayerLoadError,
  onBootstrapFabricState,
  onDismissLayerErrors,
  onDismissMissingFonts,
  formatSavedTime,
  children,
}: EditorShellProps) {
  const { layers, activeObjectProps } = useLayersStore();
  const baseWidth = template?.canvas_data?.canvas?.baseWidth || 1080;
  const baseHeight = template?.canvas_data?.canvas?.baseHeight || 1920;

  return (
    <div className="h-screen max-h-screen overflow-hidden flex flex-col editor-workspace animate-in fade-in duration-300">
      <EditorTopBar
        templateId={templateRouteId}
        title={template.title}
        categoryName={
          (template.categories as { name?: string } | null)?.name ||
          template.category?.name
        }
        isDirty={isDirty}
        isSaving={isSaving}
        lastSavedAt={lastSavedAt}
        onSave={onSave}
        onReloadThumbnail={onReloadThumbnail}
        onExportPNG={onExportPNG}
        onExportWEBP={onExportWEBP}
        onPreview={onPreview}
        onPublishDebug={onPublishDebug}
        onPublishRelease={onPublishRelease}
        onRevertDraft={onRevertDraft}
        onDelete={onDelete}
        formatSavedTime={formatSavedTime}
      />

      {missingFonts.length > 0 && (
        <WarningBar tone="amber" onDismiss={onDismissMissingFonts}>
          Cảnh báo phông chữ: <strong>{missingFonts.join(', ')}</strong>
        </WarningBar>
      )}

      {layerErrors.length > 0 && (
        <WarningBar tone="rose" onDismiss={onDismissLayerErrors}>
          Lỗi tải layer — không nên lưu để tránh mất dữ liệu. F5 để thử lại.
        </WarningBar>
      )}

      <div className="flex-1 flex flex-col min-h-0 relative">
        <div className="flex-1 min-h-0 relative">
          <CanvasWorkspace
            template={template}
            onSave={onSave}
            isSaving={isSaving}
            setIsDirty={onDirty}
            onLoadedWithoutFabricState={onBootstrapFabricState}
            onLayerLoadError={onLayerLoadError}
            layoutMode="v2"
          />
          <LayerRailOverlay onDirty={onDirty} />
        </div>

        <ContextPanel categoryId={template?.category_id || ''} onDirty={onDirty} />
        <BottomToolDock
          labelLayerActive={
            activeObjectProps?.layerType === 'TEXT' ||
            (activeObjectProps ? resolveLayerType(activeObjectProps) === 'TEXT' : false)
          }
        />
      </div>

      <footer
        className="h-7 border-t px-3 flex items-center justify-between text-[10px] shrink-0 select-none"
        style={{ borderColor: 'var(--editor-border)', background: 'var(--editor-panel-bg)', color: 'var(--editor-text-secondary)' }}
      >
        <span className="font-mono">
          {baseWidth}×{baseHeight} · X:{cursorPos.x} Y:{cursorPos.y} · {layers.length} layers
        </span>
        <span>
          {isSaving ? 'Đang lưu...' : isDirty ? 'Chưa lưu' : 'Đã sync'}
        </span>
      </footer>

      {children}
    </div>
  );
}

function WarningBar({
  children,
  tone,
  onDismiss,
}: {
  children: React.ReactNode;
  tone: 'amber' | 'rose';
  onDismiss: () => void;
}) {
  const styles =
    tone === 'amber'
      ? 'bg-amber-50 border-amber-200/60 text-amber-800'
      : 'bg-rose-50 border-rose-200/60 text-rose-800';
  return (
    <div className={`border-b px-4 py-2 shrink-0 flex items-center justify-between text-xs z-20 ${styles}`}>
      <span>{children}</span>
      <button type="button" onClick={onDismiss} className="font-bold px-1.5 py-0.5 rounded hover:bg-black/5">
        Đóng
      </button>
    </div>
  );
}
