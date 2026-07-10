'use client';

import React from 'react';
import Link from 'next/link';
import {
  ArrowLeft,
  Layers,
  Loader2,
  Download,
  RefreshCw,
  Undo,
  Redo,
  ChevronDown,
  Check,
  Smartphone,
  Globe,
  Ban,
  Trash2,
  Settings,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useEditorStore } from '@/store/editor.store';
import { useEditorUiStore } from '@/store/editor-ui.store';
import { isEditorV2LayoutEnabled, setEditorV2LayoutEnabled } from '@/lib/editor-feature-flags';

interface EditorTopBarProps {
  templateId: string;
  title: string;
  categoryName?: string;
  isDirty: boolean;
  isSaving: boolean;
  lastSavedAt: Date | null;
  onSave: () => void;
  onReloadThumbnail: () => void;
  onExportPNG: () => void;
  onExportWEBP: () => void;
  onPreview: () => void;
  onPublishDebug: () => void;
  onPublishRelease: () => void;
  onRevertDraft: () => void;
  onDelete: () => void;
  formatSavedTime: (date: Date) => string;
}

export default function EditorTopBar({
  templateId,
  title,
  categoryName,
  isDirty,
  isSaving,
  lastSavedAt,
  onSave,
  onReloadThumbnail,
  onExportPNG,
  onExportWEBP,
  onPreview,
  onPublishDebug,
  onPublishRelease,
  onRevertDraft,
  onDelete,
  formatSavedTime,
}: EditorTopBarProps) {
  const { undo, redo, undoStack, redoStack } = useEditorStore();
  const toggleLayerRail = useEditorUiStore((s) => s.toggleLayerRail);
  const [actionsOpen, setActionsOpen] = React.useState(false);
  const [v2Layout, setV2Layout] = React.useState(false);

  React.useEffect(() => {
    setV2Layout(isEditorV2LayoutEnabled());
  }, []);

  const toggleV2Layout = () => {
    const next = !v2Layout;
    setEditorV2LayoutEnabled(next);
    setV2Layout(next);
    window.location.reload();
  };

  return (
    <header
      className="flex items-center justify-between border-b px-4 py-2 shrink-0 z-30 shadow-sm backdrop-blur-xl"
      style={{
        height: 'var(--editor-topbar-h)',
        borderColor: 'var(--editor-border)',
        background: 'var(--editor-panel-bg)',
      }}
    >
      <div className="flex items-center gap-3 min-w-0">
        <Link
          href={`/templates/${templateId}`}
          className="transition-colors shrink-0"
          style={{ color: 'var(--editor-text-secondary)' }}
        >
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div className="min-w-0">
          <h1
            className="text-sm font-bold leading-tight truncate"
            style={{ color: 'var(--editor-text-primary)' }}
          >
            {title}
          </h1>
          <span className="text-[10px] font-mono tracking-wider uppercase" style={{ color: 'var(--editor-text-secondary)' }}>
            {categoryName || 'Chưa phân loại'}
          </span>
        </div>

        <div className="h-6 w-px mx-1" style={{ background: 'var(--editor-border)' }} />

        <Button
          size="icon"
          variant="ghost"
          onClick={undo}
          disabled={undoStack.length < 2}
          className="h-8 w-8 rounded-lg disabled:opacity-30"
          title="Hoàn tác"
        >
          <Undo className="w-4 h-4" />
        </Button>
        <Button
          size="icon"
          variant="ghost"
          onClick={redo}
          disabled={redoStack.length === 0}
          className="h-8 w-8 rounded-lg disabled:opacity-30"
          title="Làm lại"
        >
          <Redo className="w-4 h-4" />
        </Button>

        <Button
          size="sm"
          variant="outline"
          onClick={toggleV2Layout}
          className="rounded-xl h-8 text-[10px] font-bold px-2.5 hidden md:flex"
          style={{
            borderColor: v2Layout ? 'var(--editor-accent)' : 'var(--editor-border)',
            color: v2Layout ? 'var(--editor-accent)' : 'var(--editor-text-secondary)',
          }}
          title="Canvas-first editor layout (V2)"
        >
          V2 {v2Layout ? 'ON' : 'OFF'}
        </Button>

        <Button
          size="sm"
          variant="outline"
          onClick={toggleLayerRail}
          className="rounded-xl h-8 text-xs font-bold px-3 gap-1.5"
          style={{ borderColor: 'var(--editor-border)' }}
        >
          <Layers className="w-3.5 h-3.5" style={{ color: 'var(--editor-accent)' }} />
          Layers
        </Button>
      </div>

      <div className="flex items-center gap-2 shrink-0">
        {isDirty && (
          <span className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-amber-500/10 border border-amber-500/20 text-amber-600 text-xs font-semibold">
            <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-pulse" />
            Chưa lưu
          </span>
        )}
        {!isDirty && lastSavedAt && (
          <span className="text-[10px] hidden md:inline" style={{ color: 'var(--editor-text-secondary)' }}>
            Đã lưu · {formatSavedTime(lastSavedAt)}
          </span>
        )}
        {isSaving && (
          <span className="text-xs flex items-center gap-1" style={{ color: 'var(--editor-accent)' }}>
            <Loader2 className="w-3 h-3 animate-spin" />
            Đang lưu...
          </span>
        )}

        <Button size="sm" variant="outline" onClick={onReloadThumbnail} disabled={isSaving} className="rounded-xl h-8 text-xs px-3 hidden lg:flex">
          <RefreshCw className="w-3 h-3 mr-1" /> Thumbnail
        </Button>
        <Button size="sm" variant="outline" onClick={onExportPNG} className="rounded-xl h-8 text-xs px-3">
          <Download className="w-3 h-3 mr-1" /> PNG
        </Button>
        <Button size="sm" variant="outline" onClick={onExportWEBP} className="rounded-xl h-8 text-xs px-3 hidden sm:flex">
          <Download className="w-3 h-3 mr-1" /> WEBP
        </Button>
        <Button size="sm" variant="outline" onClick={onPreview} className="rounded-xl h-8 text-xs px-3 editor-accent-soft-bg" style={{ color: 'var(--editor-accent)' }}>
          QR App
        </Button>

        <div className="relative">
          <Button
            size="sm"
            onClick={() => setActionsOpen(!actionsOpen)}
            className="rounded-xl h-8 text-xs font-bold px-3 editor-accent-bg text-white"
          >
            Thao tác <ChevronDown className="w-3.5 h-3.5 ml-1" />
          </Button>
          {actionsOpen && (
            <>
              <div className="fixed inset-0 z-40" onClick={() => setActionsOpen(false)} />
              <div className="absolute right-0 mt-2 w-56 border rounded-2xl shadow-xl z-50 flex flex-col p-1.5 animate-in fade-in slide-in-from-top-2 duration-200"
                style={{ background: 'var(--editor-panel-bg)', borderColor: 'var(--editor-border)' }}
              >
                <button onClick={() => { setActionsOpen(false); onSave(); }} className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs font-semibold hover:bg-slate-100">
                  <Check className="w-4 h-4" style={{ color: 'var(--editor-accent)' }} /> Lưu (Ctrl+S)
                </button>
                <Link href={`/templates/${templateId}`} className="w-full">
                  <button className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs hover:bg-slate-100">
                    <Settings className="w-4 h-4" /> Chi tiết & Cấu hình
                  </button>
                </Link>
                <div className="h-px bg-slate-200/60 my-1 mx-2" />
                <button onClick={() => { setActionsOpen(false); onPublishDebug(); }} className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-[11px] text-amber-600 hover:bg-amber-500/10">
                  <Smartphone className="w-4 h-4" /> Publish (Debug)
                </button>
                <button onClick={() => { setActionsOpen(false); onPublishRelease(); }} className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-[11px] text-emerald-600 hover:bg-emerald-500/10">
                  <Globe className="w-4 h-4" /> Publish (Release)
                </button>
                <button onClick={() => { setActionsOpen(false); onRevertDraft(); }} className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-[11px] hover:bg-slate-100">
                  <Ban className="w-4 h-4" /> Thu hồi Draft
                </button>
                <div className="h-px bg-slate-200/60 my-1 mx-2" />
                <button onClick={() => { setActionsOpen(false); onDelete(); }} className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-[11px] font-bold text-rose-500 hover:bg-rose-500/10">
                  <Trash2 className="w-4 h-4" /> Xóa Template
                </button>
              </div>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
