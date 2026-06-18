'use client';

import React, { useEffect, useState, useRef } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import {
  ArrowLeft,
  Layers,
  Loader2,
  AlertTriangle,
  Download,
  PanelLeftClose,
  PanelLeftOpen,
  PanelRightClose,
  PanelRightOpen,
  PackageOpen,
  X,
  Pin,
  PinOff,
  MousePointer2,
  Keyboard,
  ChevronDown,
  Smartphone,
  Globe,
  Ban,
  Trash2,
  Settings,
  Check
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useEditorStore } from '@/store/editor.store';
import { useLayersStore } from '@/store/layers.store';
import { CANVAS_SERIALIZE_PROPS } from '@/store/canvas-serialize.constants';
import { fabricToCloudTemplate } from '@/lib/template-converter';
import { validateTemplateForPublish } from '@/lib/template-validate';
import CanvasWorkspace from '@/components/canvas/CanvasWorkspace';
import LayerPanel from '@/components/canvas/LayerPanel';
import PropertiesPanel from '@/components/canvas/PropertiesPanel';
import AssetSidebar from '@/components/canvas/AssetSidebar';
import MiniMap from '@/components/canvas/MiniMap';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { toast } from 'sonner';

const STORAGE_KEY_LEFT = 'editor_left_panel_width';
const STORAGE_KEY_RIGHT = 'editor_right_panel_width';
const DEFAULT_LEFT_W = 288;
const DEFAULT_RIGHT_W = 320;
const MIN_PANEL_W = 200;
const MAX_PANEL_W = 500;
const AUTOSAVE_MS = 45000;

function formatSavedTime(date: Date): string {
  return date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
}

export default function TemplateEditPage() {
  const { id } = useParams();
  const router = useRouter();

  const [template, setTemplate] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isDirty, setIsDirty] = useState(false);
  const [isPreviewOpen, setIsPreviewOpen] = useState(false);
  const [isActionsOpen, setIsActionsOpen] = useState(false);
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const [lastSavedAt, setLastSavedAt] = useState<Date | null>(null);

  const savingLockRef = useRef(false);
  const handleSaveRef = useRef<(silent?: boolean, status?: 'draft' | 'published', env?: 'debug' | 'release' | 'all') => Promise<boolean>>(async () => false);
  const fabricStateBootstrappedRef = useRef(false);

  // Panel visibility & sizing
  const [isLeftCollapsed, setIsLeftCollapsed] = useState(false);
  const [isRightCollapsed, setIsRightCollapsed] = useState(false);
  const [isAssetDrawerOpen, setIsAssetDrawerOpen] = useState(false);
  const [isAssetPinned, setIsAssetPinned] = useState(false);
  const [leftPanelW, setLeftPanelW] = useState(DEFAULT_LEFT_W);
  const [rightPanelW, setRightPanelW] = useState(DEFAULT_RIGHT_W);

  // Resize state — use refs to avoid stale closures
  const [resizing, setResizing] = useState<'left' | 'right' | null>(null);
  const leftPanelRef = useRef(leftPanelW);
  const rightPanelRef = useRef(rightPanelW);
  const resizeStartX = useRef(0);
  const resizeStartW = useRef(0);
  const resizingRef = useRef<'left' | 'right' | null>(null);

  // Keep refs in sync with state
  useEffect(() => { leftPanelRef.current = leftPanelW; }, [leftPanelW]);
  useEffect(() => { rightPanelRef.current = rightPanelW; }, [rightPanelW]);
  useEffect(() => { resizingRef.current = resizing; }, [resizing]);

  // Status bar state
  const [cursorPos, setCursorPos] = useState({ x: 0, y: 0 });
  const [shortcutOpen, setShortcutOpen] = useState(false);

  const { canvas } = useEditorStore();
  const { layers } = useLayersStore();

  // Load saved panel widths
  useEffect(() => {
    try {
      const savedL = localStorage.getItem(STORAGE_KEY_LEFT);
      const savedR = localStorage.getItem(STORAGE_KEY_RIGHT);
      if (savedL) setLeftPanelW(Math.max(MIN_PANEL_W, Math.min(MAX_PANEL_W, parseInt(savedL))));
      if (savedR) setRightPanelW(Math.max(MIN_PANEL_W, Math.min(MAX_PANEL_W, parseInt(savedR))));
    } catch {}
  }, []);

  // Resize handlers — uses refs to avoid stale closure issues
  const handleResizeStart = (side: 'left' | 'right') => (e: React.MouseEvent) => {
    e.preventDefault();
    setResizing(side);
    resizingRef.current = side;
    resizeStartX.current = e.clientX;
    resizeStartW.current = side === 'left' ? leftPanelRef.current : rightPanelRef.current;
  };

  useEffect(() => {
    if (!resizing) return;

    const handleMove = (e: MouseEvent) => {
      const delta = e.clientX - resizeStartX.current;
      const side = resizingRef.current;
      if (!side) return;
      const newW = Math.max(MIN_PANEL_W, Math.min(MAX_PANEL_W, resizeStartW.current + (side === 'left' ? delta : -delta)));
      if (side === 'left') {
        leftPanelRef.current = newW;
        setLeftPanelW(newW);
      } else {
        rightPanelRef.current = newW;
        setRightPanelW(newW);
      }
    };

    const handleUp = () => {
      const side = resizingRef.current;
      if (side === 'left') {
        try { localStorage.setItem(STORAGE_KEY_LEFT, String(Math.max(MIN_PANEL_W, Math.min(MAX_PANEL_W, leftPanelRef.current)))); } catch {}
      } else if (side === 'right') {
        try { localStorage.setItem(STORAGE_KEY_RIGHT, String(Math.max(MIN_PANEL_W, Math.min(MAX_PANEL_W, rightPanelRef.current)))); } catch {}
      }
      setResizing(null);
    };

    window.addEventListener('mousemove', handleMove);
    window.addEventListener('mouseup', handleUp);
    return () => {
      window.removeEventListener('mousemove', handleMove);
      window.removeEventListener('mouseup', handleUp);
    };
  }, [resizing]);

  // Double-click resize handle to reset
  const handleResizeDoubleClick = (side: 'left' | 'right') => () => {
    if (side === 'left') {
      const w = DEFAULT_LEFT_W;
      leftPanelRef.current = w;
      setLeftPanelW(w);
      try { localStorage.setItem(STORAGE_KEY_LEFT, String(w)); } catch {}
    } else {
      const w = DEFAULT_RIGHT_W;
      rightPanelRef.current = w;
      setRightPanelW(w);
      try { localStorage.setItem(STORAGE_KEY_RIGHT, String(w)); } catch {}
    }
  };

  // Track mouse position on canvas for status bar (debounced via rAF)
  const rafIdRef = useRef<number | null>(null);

  useEffect(() => {
    const handleCanvasMouse = (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      const canvasArea = target.closest('[data-canvas-viewport]');
      if (canvasArea) {
        const fabricCanvas = document.getElementById('fabric-canvas');
        if (!fabricCanvas) return;

        if (rafIdRef.current) cancelAnimationFrame(rafIdRef.current);
        rafIdRef.current = requestAnimationFrame(() => {
          const baseWidth = template?.canvas_data?.canvas?.baseWidth || 1080;
          const baseHeight = template?.canvas_data?.canvas?.baseHeight || 1920;
          const fRect = fabricCanvas.getBoundingClientRect();
          const scaleX = baseWidth / fRect.width;
          const scaleY = baseHeight / fRect.height;
          const x = Math.round((e.clientX - fRect.left) * scaleX);
          const y = Math.round((e.clientY - fRect.top) * scaleY);
          if (x >= 0 && y >= 0 && x <= baseWidth && y <= baseHeight) {
            setCursorPos({ x, y });
          }
        });
      }
    };
    window.addEventListener('mousemove', handleCanvasMouse, { passive: true });
    return () => {
      window.removeEventListener('mousemove', handleCanvasMouse);
      if (rafIdRef.current) cancelAnimationFrame(rafIdRef.current);
    };
  }, [template]);

  const fetchTemplate = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await fetch(`/api/templates/${id}`);
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Failed to load template');
      setTemplate(data.template);
      if (data.template?.updated_at) {
        setLastSavedAt(new Date(data.template.updated_at));
      }
      fabricStateBootstrappedRef.current = Boolean(data.template?.fabric_state);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (id) fetchTemplate();
  }, [id]);

  useEffect(() => {
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      if (isDirty) {
        e.preventDefault();
        e.returnValue = 'Bạn có thay đổi chưa lưu. Bạn có chắc chắn muốn thoát?';
        return e.returnValue;
      }
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [isDirty]);



  const dataURLtoBlob = (dataurl: string) => {
    const arr = dataurl.split(',');
    const mime = arr[0].match(/:(.*?);/)?.[1] || 'image/webp';
    const bstr = atob(arr[1]);
    let n = bstr.length;
    const u8arr = new Uint8Array(n);
    while (n--) u8arr[n] = bstr.charCodeAt(n);
    return new Blob([u8arr], { type: mime });
  };

  const uploadDataUrl = async (dataUrl: string, filename: string, folder: string): Promise<string | null> => {
    const blob = dataURLtoBlob(dataUrl);
    const file = new File([blob], filename, { type: blob.type || 'image/webp' });
    const formData = new FormData();
    formData.append('file', file);
    formData.append('folder', folder);
    formData.append('registerAsset', 'false');
    const uploadRes = await fetch('/api/upload', { method: 'POST', body: formData });
    const uploadData = await uploadRes.json();
    return uploadRes.ok ? uploadData.fileUrl : null;
  };

  const renderBackgroundOnlyDataUrl = () => {
    if (!canvas) return null;
    const hiddenObjects: any[] = [];
    const activeObject = canvas.getActiveObject();

    canvas.discardActiveObject();
    canvas.getObjects().forEach((obj: any) => {
      if (obj._isBackground === true) return;
      if (obj.visible !== false) {
        hiddenObjects.push(obj);
        obj.visible = false;
      }
    });
    canvas.renderAll();

    const dataUrl = canvas.toDataURL({
      format: 'webp',
      quality: 0.9,
      multiplier: 1,
      backgroundColor: canvas.backgroundColor || '#ffffff'
    });

    hiddenObjects.forEach((obj) => { obj.visible = true; });
    if (activeObject) canvas.setActiveObject(activeObject);
    canvas.renderAll();

    return dataUrl;
  };

  const handleSave = async (
    silent = false,
    statusOverride?: 'draft' | 'published',
    envOverride?: 'debug' | 'release' | 'all'
  ): Promise<boolean> => {
    if (!canvas || !template) return false;
    if (savingLockRef.current) return false;

    savingLockRef.current = true;
    setIsSaving(true);
    setError(null);

    const newStatus = statusOverride || template.status;
    const newEnv = envOverride || template.environment || 'all';

    try {
      const baseWidth = template.canvas_data?.canvas?.baseWidth || 1080;
      const baseHeight = template.canvas_data?.canvas?.baseHeight || 1920;

      const activeObject = canvas.getActiveObject();
      canvas.discardActiveObject();
      canvas.renderAll();

      const dataUrl = canvas.toDataURL({
        format: 'webp',
        quality: 0.8,
        multiplier: 0.25,
        backgroundColor: canvas.backgroundColor || '#ffffff'
      });

      if (activeObject) {
        canvas.setActiveObject(activeObject);
        canvas.renderAll();
      }

      const serializedTemplate = fabricToCloudTemplate(
        canvas, baseWidth, baseHeight,
        template.template_id, template.category_id,
        template.title, newStatus
      );

      if (serializedTemplate.metadata) {
        serializedTemplate.metadata.status = newStatus;
        serializedTemplate.metadata.environment = newEnv;
      }

      if (newStatus === 'published') {
        const validation = validateTemplateForPublish(canvas, serializedTemplate);
        if (!validation.valid) {
          if (!silent) {
            toast.error(validation.errors[0] || 'Template không hợp lệ để publish.');
          }
          return false;
        }
      }

      const needsBackgroundUpload = !serializedTemplate.canvas.backgroundUrl;
      const backgroundDataUrl = needsBackgroundUpload ? renderBackgroundOnlyDataUrl() : null;

      let thumbnailUrl = template.thumbnail_url;
      try {
        const [uploadedThumb, uploadedBg] = await Promise.all([
          uploadDataUrl(dataUrl, `thumb_${template.template_id}.webp`, 'thumbnails').catch(() => null),
          backgroundDataUrl
            ? uploadDataUrl(backgroundDataUrl, `bg_${template.template_id}.webp`, 'backgrounds').catch(() => null)
            : Promise.resolve(null),
        ]);
        if (uploadedThumb) thumbnailUrl = uploadedThumb;
        if (uploadedBg) serializedTemplate.canvas.backgroundUrl = uploadedBg;
      } catch (uploadErr) {
        console.warn('Asset upload failed:', uploadErr);
      }

      const fabricState = canvas.toJSON(CANVAS_SERIALIZE_PROPS);

      const res = await fetch(`/api/templates/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: template.title,
          category_id: template.category_id,
          status: newStatus,
          environment: newEnv,
          canvas_data: serializedTemplate,
          fabric_state: fabricState,
          thumbnail_url: thumbnailUrl
        })
      });

      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Failed to save template');

      setTemplate(data.template);
      setIsDirty(false);
      setLastSavedAt(new Date(data.template?.updated_at || Date.now()));
      fabricStateBootstrappedRef.current = true;

      if (!silent) {
        toast.success(
          statusOverride || envOverride
            ? 'Cập nhật trạng thái template thành công!'
            : 'Đã lưu thiết kế và cập nhật ảnh xem trước thành công!'
        );
      }
      return true;
    } catch (err: any) {
      setError(err.message);
      if (!silent) {
        toast.error(`Lỗi lưu thiết kế: ${err.message}`);
      }
      return false;
    } finally {
      savingLockRef.current = false;
      setIsSaving(false);
    }
  };

  handleSaveRef.current = handleSave;

  useEffect(() => {
    if (!isDirty || isSaving || !canvas || !template) return;
    const timer = setTimeout(() => {
      void handleSaveRef.current(true);
    }, AUTOSAVE_MS);
    return () => clearTimeout(timer);
  }, [isDirty, isSaving, canvas, template]);

  const handleBootstrapFabricState = async () => {
    if (fabricStateBootstrappedRef.current || isSaving) return;
    fabricStateBootstrappedRef.current = true;
    const ok = await handleSaveRef.current(true);
    if (!ok) fabricStateBootstrappedRef.current = false;
  };

  const handleDeleteTemplate = async () => {
    try {
      setIsSaving(true);
      setError(null);
      const res = await fetch(`/api/templates/${id}`, {
        method: 'DELETE',
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Failed to delete template');
      toast.success('Xóa template thành công!');
      router.push('/templates');
    } catch (err: any) {
      setError(err.message);
      toast.error(`Lỗi xóa template: ${err.message}`);
    } finally {
      setIsSaving(false);
    }
  };

  const handleExportPNG = () => {
    if (!canvas) { toast.error('Canvas chưa sẵn sàng để xuất!'); return; }
    try {
      const activeObject = canvas.getActiveObject();
      canvas.discardActiveObject();
      canvas.renderAll();
      const dataUrl = canvas.toDataURL({ format: 'png', quality: 1.0 });
      if (activeObject) { canvas.setActiveObject(activeObject); canvas.renderAll(); }
      const link = document.createElement('a');
      link.download = `${template?.title || 'template'}_export.png`;
      link.href = dataUrl;
      link.click();
      toast.success('Đã xuất file PNG thành công!');
    } catch (err: any) {
      toast.error(`Lỗi xuất PNG: ${err.message}`);
    }
  };

  const handleExportWEBP = () => {
    if (!canvas) { toast.error('Canvas chưa sẵn sàng để xuất!'); return; }
    try {
      const activeObject = canvas.getActiveObject();
      canvas.discardActiveObject();
      canvas.renderAll();
      const dataUrl = canvas.toDataURL({ format: 'webp', quality: 0.92 });
      if (activeObject) { canvas.setActiveObject(activeObject); canvas.renderAll(); }
      const link = document.createElement('a');
      link.download = `${template?.title || 'template'}_export.webp`;
      link.href = dataUrl;
      link.click();
      toast.success('Đã xuất file WEBP thành công!');
    } catch (err: any) {
      toast.error(`Lỗi xuất WEBP: ${err.message}`);
    }
  };

  const baseWidth = template?.canvas_data?.canvas?.baseWidth || 1080;
  const baseHeight = template?.canvas_data?.canvas?.baseHeight || 1920;

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center py-40 gap-3 text-slate-400">
        <Loader2 className="w-8 h-8 animate-spin text-indigo-500" />
        <p className="text-sm">Đang tải cấu hình thiết kế...</p>
      </div>
    );
  }

  if (error && !template) {
    return (
      <div className="space-y-6">
        <Link href={`/templates/${id}`} className="flex items-center gap-1 text-xs text-slate-500 hover:text-slate-800">
          <ArrowLeft className="w-4 h-4" /> Quay lại Template
        </Link>
        <div className="p-6 rounded-3xl bg-rose-500/10 border border-rose-500/20 text-rose-400 flex items-start gap-3">
          <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5" />
          <div>
            <p className="font-semibold text-sm">Lỗi tải dữ liệu</p>
            <p className="text-xs mt-0.5">{error}</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className={`h-screen max-h-screen overflow-hidden flex flex-col bg-white text-slate-400 animate-in fade-in duration-300 ${resizing ? 'select-none' : ''}`}>

      {/* Top navbar */}
      <div className="flex items-center justify-between border-b border-slate-200/60 px-4 py-2.5 shrink-0 bg-white/80 backdrop-blur-xl z-30 shadow-sm">
        <div className="flex items-center gap-3">
          <Link href={`/templates/${id}`} className="text-slate-500 hover:text-slate-800 transition-colors cursor-pointer">
            <ArrowLeft className="w-5 h-5" />
          </Link>
          <div className="flex items-center gap-2">
            <Button
              size="icon"
              variant="ghost"
              onClick={() => setIsLeftCollapsed(!isLeftCollapsed)}
              className="text-slate-500 hover:text-slate-800 rounded-lg h-8 w-8"
              title={isLeftCollapsed ? 'Hiện cột trái' : 'Ẩn cột trái'}
            >
              {isLeftCollapsed ? <PanelLeftOpen className="w-4 h-4" /> : <PanelLeftClose className="w-4 h-4" />}
            </Button>
            <div>
              <h1 className="text-base font-bold text-slate-800 leading-tight">{template.title}</h1>
              <span className="text-[10px] text-slate-400 font-mono tracking-wider uppercase">
                {template.category?.name || 'Chưa phân loại'}
              </span>
            </div>
          </div>

          {/* Asset drawer toggle */}
          <div className="h-6 w-px bg-slate-100 mx-1" />
          <Button
            size="sm"
            variant={isAssetDrawerOpen ? 'default' : 'outline'}
            onClick={() => setIsAssetDrawerOpen(!isAssetDrawerOpen)}
            className={`rounded-xl h-8 text-xs font-bold px-3 flex items-center gap-1.5 transition-all cursor-pointer ${
              isAssetDrawerOpen
                ? 'bg-indigo-600 hover:bg-indigo-500 text-white'
                : 'border-slate-200 bg-white text-slate-400 hover:text-slate-800 hover:bg-slate-100'
            }`}
          >
            <PackageOpen className="w-3.5 h-3.5" />
            Assets
          </Button>
        </div>

        <div className="flex items-center gap-2">
          {isDirty && (
            <span className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-amber-500/10 border border-amber-500/20 text-amber-400 text-xs font-semibold select-none">
              <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-pulse" />
              Chưa lưu
            </span>
          )}
          <Button
            size="sm"
            variant="outline"
            onClick={handleExportPNG}
            className="border-slate-200 bg-white text-slate-400 hover:text-slate-800 hover:bg-slate-100 rounded-xl h-8 text-xs font-bold px-3 flex items-center gap-1"
          >
            <Download className="w-3 h-3" /> PNG
          </Button>
          <Button
            size="sm"
            variant="outline"
            onClick={handleExportWEBP}
            className="border-slate-200 bg-white text-slate-400 hover:text-slate-800 hover:bg-slate-100 rounded-xl h-8 text-xs font-bold px-3 flex items-center gap-1"
          >
            <Download className="w-3 h-3" /> WEBP
          </Button>
          <Button
            size="sm"
            variant="outline"
            onClick={() => setIsPreviewOpen(true)}
            className="border-indigo-500/20 bg-indigo-500/10 text-indigo-200 hover:text-slate-800 hover:bg-indigo-500/20 rounded-xl h-8 text-xs font-bold px-3"
          >
            QR App
          </Button>

          {/* Actions Dropdown Menu */}
          <div className="relative">
            <Button
              size="sm"
              onClick={() => setIsActionsOpen(!isActionsOpen)}
              className="bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl h-8 text-xs font-bold px-3 flex items-center gap-1.5 transition-all cursor-pointer"
            >
              Thao tác <ChevronDown className="w-3.5 h-3.5" />
            </Button>
            {isActionsOpen && (
              <>
                <div
                  className="fixed inset-0 z-40"
                  onClick={() => setIsActionsOpen(false)}
                />
                <div
                  className="absolute right-0 mt-2 w-56 bg-white/95 backdrop-blur-xl border border-slate-300/80 rounded-2xl shadow-xl z-50 flex flex-col p-1.5 animate-in fade-in slide-in-from-top-2 duration-200"
                  onClick={(e) => e.stopPropagation()}
                >
                  <button
                    onClick={async () => {
                      setIsActionsOpen(false);
                      await handleSave();
                    }}
                    className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs font-semibold text-slate-400 hover:bg-slate-100 transition-colors cursor-pointer"
                  >
                    <Check className="w-4 h-4 text-indigo-400" /> Lưu Thiết kế (Ctrl+S)
                  </button>

                  <Link href={`/templates/${id}`} className="w-full">
                    <button className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs font-medium text-slate-400 hover:bg-slate-100 transition-colors cursor-pointer">
                      <Settings className="w-4 h-4 text-slate-500" /> Chi tiết & Cấu hình
                    </button>
                  </Link>

                  <div className="h-px bg-slate-200/60 my-1 mx-2" />

                  <button
                    onClick={async () => {
                      setIsActionsOpen(false);
                      const ok = await handleSave(false, 'published', 'debug');
                      if (!ok) return;
                    }}
                    className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-[11px] font-medium text-amber-300 hover:bg-amber-500/10 transition-colors cursor-pointer"
                  >
                    <Smartphone className="w-4 h-4" /> Publish (App Debug)
                  </button>
                  <button
                    onClick={async () => {
                      setIsActionsOpen(false);
                      const ok = await handleSave(false, 'published', 'release');
                      if (!ok) return;
                    }}
                    className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-[11px] font-medium text-emerald-400 hover:bg-emerald-500/10 transition-colors cursor-pointer"
                  >
                    <Globe className="w-4 h-4" /> Publish (App Release)
                  </button>
                  <button
                    onClick={async () => {
                      setIsActionsOpen(false);
                      await handleSave(false, 'draft', 'all');
                    }}
                    className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-[11px] font-medium text-slate-500 hover:bg-slate-100 transition-colors cursor-pointer"
                  >
                    <Ban className="w-4 h-4" /> Thu hồi về Nháp (Draft)
                  </button>

                  <div className="h-px bg-slate-200/60 my-1 mx-2" />

                  <button
                    onClick={() => {
                      setIsActionsOpen(false);
                      setIsDeleteOpen(true);
                    }}
                    className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-[11px] font-bold text-rose-400 hover:bg-rose-500/10 transition-colors cursor-pointer"
                  >
                    <Trash2 className="w-4 h-4" /> Xóa Template
                  </button>
                </div>
              </>
            )}
          </div>

          <span className="text-[10px] uppercase font-bold px-2 py-0.5 rounded-md bg-white border border-slate-200 text-indigo-400 hidden sm:inline">
            Editor
          </span>
          <Button
            size="icon"
            variant="ghost"
            onClick={() => setIsRightCollapsed(!isRightCollapsed)}
            className="text-slate-500 hover:text-slate-800 rounded-lg h-8 w-8 ml-1"
            title={isRightCollapsed ? 'Hiện cột phải' : 'Ẩn cột phải'}
          >
            {isRightCollapsed ? <PanelRightOpen className="w-4 h-4" /> : <PanelRightClose className="w-4 h-4" />}
          </Button>
        </div>
      </div>

      {/* Main editor area */}
      <div className="flex-1 flex min-h-0 w-full relative">

        {/* Layer Icon Strip (always visible when not collapsed) */}
        {!isLeftCollapsed && (
          <div className="shrink-0 bg-white/80 backdrop-blur-xl border-r border-slate-200/60 flex flex-col items-center py-3 gap-1 w-14 z-10 shadow-lg">
            <LayerPanel compact onDirty={() => setIsDirty(true)} />
          </div>
        )}

        {/* Asset Drawer backdrop (click to close when not pinned) */}
        {isAssetDrawerOpen && !isAssetPinned && (
          <div className="fixed inset-0 z-10" onClick={() => setIsAssetDrawerOpen(false)} />
        )}

        {/* Asset Drawer (slides over content) */}
        {isAssetDrawerOpen && (
          <div className="absolute top-0 bottom-0 z-20 animate-in slide-in-from-left-3 duration-200 flex" style={{ width: leftPanelW, left: isLeftCollapsed ? 0 : 56 }}>
            <div className="flex-1 bg-white/95 backdrop-blur-xl border-r border-slate-200/60 shadow-xl flex flex-col h-full">
              {/* Drawer Header */}
              <div className="flex items-center justify-between px-3 py-2.5 border-b border-slate-200 shrink-0">
                <span className="text-xs font-bold text-slate-400 flex items-center gap-1.5">
                  <PackageOpen className="w-3.5 h-3.5 text-indigo-400" /> Thư viện
                </span>
                <div className="flex items-center gap-1">
                  <Button
                    size="icon"
                    variant="ghost"
                    onClick={() => setIsAssetPinned(!isAssetPinned)}
                    className={`w-6 h-6 rounded-md cursor-pointer ${isAssetPinned ? 'text-indigo-400 bg-indigo-500/10' : 'text-slate-400 hover:text-slate-800'}`}
                    title={isAssetPinned ? 'Bỏ ghim' : 'Ghim drawer'}
                  >
                    {isAssetPinned ? <Pin className="w-3 h-3" /> : <PinOff className="w-3 h-3" />}
                  </Button>
                  <Button
                    size="icon"
                    variant="ghost"
                    onClick={() => setIsAssetDrawerOpen(false)}
                    className="w-6 h-6 rounded-md text-slate-400 hover:text-slate-800 cursor-pointer"
                  >
                    <X className="w-3.5 h-3.5" />
                  </Button>
                </div>
              </div>
              {/* Drawer Content */}
              <div className="flex-1 min-h-0 overflow-y-auto p-3">
                <AssetSidebar
                  categoryId={template?.category_id || ''}
                  onDirty={() => setIsDirty(true)}
                />
              </div>
            </div>
            {/* Resize handle */}
            <div
              className="w-1.5 cursor-col-resize hover:bg-indigo-500/50 active:bg-indigo-500 transition-colors shrink-0 relative group"
              onMouseDown={handleResizeStart('left')}
              onDoubleClick={handleResizeDoubleClick('left')}
            >
              <div className="absolute inset-y-0 left-1/2 -translate-x-1/2 w-0.5 bg-slate-700 group-hover:bg-indigo-500 transition-colors" />
            </div>
          </div>
        )}

        {/* Center: Canvas Workspace */}
        <div className="flex-1 min-w-0 h-full relative transition-all duration-300">
          <CanvasWorkspace
            template={template}
            onSave={() => { void handleSave(); }}
            isSaving={isSaving}
            setIsDirty={setIsDirty}
            onLoadedWithoutFabricState={() => { void handleBootstrapFabricState(); }}
          />
          {/* MiniMap overlay */}
          <MiniMap />
        </div>

        {/* Right Panel Resize Handle */}
        {!isRightCollapsed && (
          <div
            className="w-1.5 cursor-col-resize hover:bg-indigo-500/50 active:bg-indigo-500 transition-colors shrink-0 relative group z-10"
            onMouseDown={handleResizeStart('right')}
            onDoubleClick={handleResizeDoubleClick('right')}
          >
            <div className="absolute inset-y-0 left-1/2 -translate-x-1/2 w-0.5 bg-slate-700 group-hover:bg-indigo-500 transition-colors" />
          </div>
        )}

        {/* Right: Properties Panel */}
        {!isRightCollapsed && (
          <div className="shrink-0 bg-white/95 backdrop-blur-xl border-l border-slate-200/60 h-full min-h-0 shadow-xl z-10 flex flex-col" style={{ width: rightPanelW }}>
            <div className="flex-1 min-h-0 overflow-hidden p-4">
              <PropertiesPanel onDirty={() => setIsDirty(true)} />
            </div>
          </div>
        )}
      </div>

      {/* Enhanced Status Bar */}
      <div className="h-7 bg-white/80 backdrop-blur-xl border-t border-slate-200/60 px-3 flex items-center justify-between text-[10px] text-slate-400 shrink-0 z-10 select-none shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.1)]">
        <div className="flex items-center gap-3">
          <span className="flex items-center gap-1">
            <span className="text-slate-400">Canvas:</span>
            <span className="font-mono text-slate-500">{baseWidth}×{baseHeight}</span>
          </span>
          <span className="text-slate-400">|</span>
          <span className="flex items-center gap-1">
            <MousePointer2 className="w-3 h-3 text-slate-400" />
            <span className="font-mono text-slate-500">X:{cursorPos.x} Y:{cursorPos.y}</span>
          </span>
          <span className="text-slate-400">|</span>
          <span className="flex items-center gap-1">
            <Layers className="w-3 h-3 text-slate-400" />
            <span className="text-slate-500">{layers.length} layers</span>
          </span>
        </div>
        <div className="flex items-center gap-2">
          {isSaving ? (
            <span className="text-indigo-400 flex items-center gap-1.5">
              <Loader2 className="w-3 h-3 animate-spin text-indigo-400" />
              Đang lưu...
            </span>
          ) : isDirty ? (
            <>
              <span className="text-amber-400 flex items-center gap-1">
                <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-pulse" />
                Chưa lưu
              </span>
              <span className="text-amber-500/80 hidden sm:inline">· Mobile: chưa cập nhật</span>
            </>
          ) : (
            <>
              <span className="text-emerald-400 flex items-center gap-1">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                Đã lưu{lastSavedAt ? ` · ${formatSavedTime(lastSavedAt)}` : ''}
              </span>
              <span className="text-emerald-500/80 hidden sm:inline">· Mobile: đã sync</span>
            </>
          )}
          <span className="text-slate-400">|</span>
          <button
            onClick={() => setShortcutOpen(!shortcutOpen)}
            className="flex items-center gap-1 hover:text-slate-400 transition-colors cursor-pointer"
          >
            <Keyboard className="w-3 h-3" />
            Shortcuts
          </button>
        </div>
      </div>

      {/* Keyboard Shortcuts Popover */}
      {shortcutOpen && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setShortcutOpen(false)} />
          <div className="absolute bottom-8 right-4 z-50 w-64 bg-white border border-slate-300 rounded-2xl shadow-xl p-4 animate-in slide-in-from-bottom-2 duration-200">
            <h4 className="text-xs font-bold text-slate-400 mb-2 flex items-center gap-1.5">
              <Keyboard className="w-3.5 h-3.5 text-indigo-400" /> Phím tắt
            </h4>
            <div className="space-y-1.5">
              {[
                ['Ctrl+S', 'Lưu'],
                ['Ctrl+Z', 'Hoàn tác'],
                ['Ctrl+Y', 'Làm lại'],
                ['Ctrl+0', 'Zoom vừa khung'],
                ['Ctrl+1', 'Zoom 100%'],
                ['Ctrl+C', 'Sao chép layer'],
                ['Ctrl+V', 'Dán layer'],
                ['Ctrl+Shift+C', 'Sao chép kiểu'],
                ['Ctrl+Shift+V', 'Dán kiểu'],
                ['Ctrl+G', 'Nhóm (Group)'],
                ['Ctrl+Shift+G', 'Rã nhóm'],
                ['Ctrl+D', 'Nhân đôi'],
                ['Delete', 'Xóa layer'],
                ['←↑↓→', 'Di chuyển 1px'],
                ['Shift+←↑↓→', 'Di chuyển 10px'],
                ['Esc', 'Bỏ chọn'],
              ].map(([key, desc]) => (
                <div key={key} className="flex items-center justify-between text-[10px]">
                  <kbd className="px-1.5 py-0.5 rounded-md bg-slate-100 text-slate-400 font-mono text-[9px] border border-slate-300">{key}</kbd>
                  <span className="text-slate-500">{desc}</span>
                </div>
              ))}
            </div>
          </div>
        </>
      )}

      {/* QR Code Preview Dialog */}
      <Dialog open={isPreviewOpen} onOpenChange={setIsPreviewOpen}>
        <DialogContent className="bg-white border border-slate-200 text-slate-800 rounded-2xl sm:max-w-sm">
          <DialogHeader>
            <DialogTitle className="text-lg font-bold text-slate-800 text-center">Quét QR Code để xem thử</DialogTitle>
            <DialogDescription className="text-xs text-slate-500 text-center">
              Mở camera trên thiết bị Android hoặc ứng dụng quét mã QR để bắt đầu xem thử trực tiếp mẫu thiết kế này.
            </DialogDescription>
          </DialogHeader>
          <div className="py-6 flex flex-col items-center justify-center space-y-4">
            <div className="bg-white p-3 rounded-2xl">
              <img
                src={`https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(`quickedit://preview?templateId=${template?.template_id || template?.id}`)}`}
                alt="QR Code Preview"
                className="w-[200px] h-[200px]"
              />
            </div>
            <div className="text-center">
              <p className="text-xs font-bold text-slate-400">Deep Link URL:</p>
              <p className="text-[10px] font-mono text-slate-500 select-all mt-1 bg-white px-3 py-1.5 rounded-lg break-all">
                quickedit://preview?templateId={template?.template_id || template?.id}
              </p>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={isDeleteOpen} onOpenChange={setIsDeleteOpen}>
        <DialogContent className="bg-white border border-slate-200 text-slate-800 rounded-2xl sm:max-w-sm">
          <DialogHeader>
            <DialogTitle className="text-lg font-bold text-slate-800">Xóa template?</DialogTitle>
            <DialogDescription className="text-xs text-slate-500">
              Bạn có chắc chắn muốn xóa template này? Hành động này không thể hoàn tác.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsDeleteOpen(false)} disabled={isSaving}>
              Hủy
            </Button>
            <Button
              variant="destructive"
              disabled={isSaving}
              onClick={async () => {
                await handleDeleteTemplate();
                setIsDeleteOpen(false);
              }}
            >
              {isSaving ? <Loader2 className="w-4 h-4 animate-spin" /> : 'Xóa template'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
