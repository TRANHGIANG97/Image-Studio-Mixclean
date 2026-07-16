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
  RefreshCw,
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
import { prepareTemplateForSave } from '@/lib/template-persist';
import { isEditorV2LayoutEnabled } from '@/lib/editor-feature-flags';
import EditorShell from '@/components/editor/EditorShell';
import EditorLoadingShimmer from '@/components/editor/EditorLoadingShimmer';
import { validateTemplateForPublish } from '@/lib/template-validate';
import { uploadInlineImageLayers, dataUrlToBlob } from '@/lib/canvas-upload';
import CanvasWorkspace from '@/components/canvas/CanvasWorkspace';
import LayerPanel from '@/components/canvas/LayerPanel';
import PropertiesPanel from '@/components/canvas/PropertiesPanel';
import AssetSidebar from '@/components/canvas/AssetSidebar';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { toast } from 'sonner';
import { fetchFontsManifest, injectFontFace, checkGoogleFont, injectGoogleFont } from '@/lib/fonts-manifest';

const STORAGE_KEY_LEFT = 'editor_left_panel_width';
const STORAGE_KEY_RIGHT = 'editor_right_panel_width';
const STORAGE_KEY_LAYER = 'editor_layer_panel_width';
const DEFAULT_LEFT_W = 288;
const DEFAULT_RIGHT_W = 320;
const DEFAULT_LAYER_W = 340;
const MIN_PANEL_W = 200;
const MAX_PANEL_W = 500;
const MIN_LAYER_W = 280;
const MAX_LAYER_W = 440;
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
  const [missingFonts, setMissingFonts] = useState<string[]>([]);
  const [layerErrors, setLayerErrors] = useState<string[]>([]);
  const [v2Layout, setV2Layout] = useState(false);

  useEffect(() => {
    setV2Layout(isEditorV2LayoutEnabled());
  }, []);
  const saveBlocked = layerErrors.length > 0;

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
  const [layerPanelW, setLayerPanelW] = useState(DEFAULT_LAYER_W);

  // Resize state — use refs to avoid stale closures
  const [resizing, setResizing] = useState<'left' | 'right' | 'layer' | null>(null);
  const leftPanelRef = useRef(leftPanelW);
  const rightPanelRef = useRef(rightPanelW);
  const layerPanelRef = useRef(layerPanelW);
  const resizeStartX = useRef(0);
  const resizeStartW = useRef(0);
  const resizingRef = useRef<'left' | 'right' | 'layer' | null>(null);

  // Keep refs in sync with state
  useEffect(() => { leftPanelRef.current = leftPanelW; }, [leftPanelW]);
  useEffect(() => { rightPanelRef.current = rightPanelW; }, [rightPanelW]);
  useEffect(() => { layerPanelRef.current = layerPanelW; }, [layerPanelW]);
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
      const savedLayer = localStorage.getItem(STORAGE_KEY_LAYER);
      if (savedL) setLeftPanelW(Math.max(MIN_PANEL_W, Math.min(MAX_PANEL_W, parseInt(savedL))));
      if (savedR) setRightPanelW(Math.max(MIN_PANEL_W, Math.min(MAX_PANEL_W, parseInt(savedR))));
      if (savedLayer) setLayerPanelW(Math.max(MIN_LAYER_W, Math.min(MAX_LAYER_W, parseInt(savedLayer))));
    } catch {}
  }, []);

  // Resize handlers — uses refs to avoid stale closure issues
  const handleResizeStart = (side: 'left' | 'right' | 'layer') => (e: React.MouseEvent) => {
    e.preventDefault();
    setResizing(side);
    resizingRef.current = side;
    resizeStartX.current = e.clientX;
    resizeStartW.current =
      side === 'left'
        ? leftPanelRef.current
        : side === 'right'
          ? rightPanelRef.current
          : layerPanelRef.current;
  };

  useEffect(() => {
    if (!resizing) return;

    const handleMove = (e: MouseEvent) => {
      const delta = e.clientX - resizeStartX.current;
      const side = resizingRef.current;
      if (!side) return;
      const newW = Math.max(
        side === 'layer' ? MIN_LAYER_W : MIN_PANEL_W,
        Math.min(
          side === 'layer' ? MAX_LAYER_W : MAX_PANEL_W,
          resizeStartW.current + (side === 'right' ? -delta : delta)
        )
      );
      if (side === 'left') {
        leftPanelRef.current = newW;
        setLeftPanelW(newW);
      } else if (side === 'right') {
        rightPanelRef.current = newW;
        setRightPanelW(newW);
      } else {
        layerPanelRef.current = newW;
        setLayerPanelW(newW);
      }
    };

    const handleUp = () => {
      const side = resizingRef.current;
      if (side === 'left') {
        try { localStorage.setItem(STORAGE_KEY_LEFT, String(Math.max(MIN_PANEL_W, Math.min(MAX_PANEL_W, leftPanelRef.current)))); } catch {}
      } else if (side === 'right') {
        try { localStorage.setItem(STORAGE_KEY_RIGHT, String(Math.max(MIN_PANEL_W, Math.min(MAX_PANEL_W, rightPanelRef.current)))); } catch {}
      } else if (side === 'layer') {
        try { localStorage.setItem(STORAGE_KEY_LAYER, String(Math.max(MIN_LAYER_W, Math.min(MAX_LAYER_W, layerPanelRef.current)))); } catch {}
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
  const handleResizeDoubleClick = (side: 'left' | 'right' | 'layer') => () => {
    if (side === 'left') {
      const w = DEFAULT_LEFT_W;
      leftPanelRef.current = w;
      setLeftPanelW(w);
      try { localStorage.setItem(STORAGE_KEY_LEFT, String(w)); } catch {}
    } else if (side === 'right') {
      const w = DEFAULT_RIGHT_W;
      rightPanelRef.current = w;
      setRightPanelW(w);
      try { localStorage.setItem(STORAGE_KEY_RIGHT, String(w)); } catch {}
    } else {
      const w = DEFAULT_LAYER_W;
      layerPanelRef.current = w;
      setLayerPanelW(w);
      try { localStorage.setItem(STORAGE_KEY_LAYER, String(w)); } catch {}
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
      setLayerErrors([]);
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
    if (!template || !template.canvas_data) return;

    const checkAndLoadTemplateFonts = async () => {
      try {
        const layers = template.canvas_data.layers || [];
        const textLayers = layers.filter((l: any) => l.type === 'TEXT');
        const uniqueFonts = Array.from(new Set(textLayers.map((l: any) => l.payload?.font).filter(Boolean))) as string[];

        if (uniqueFonts.length === 0) return;

        const manifest = await fetchFontsManifest();
        const systemFonts = (manifest.system_fonts || []).map(f => f.toLowerCase());
        const customFonts = (manifest.fonts || []).map(f => f.family_slug.toLowerCase());
        const customFontsNames = (manifest.fonts || []).map(f => f.name.toLowerCase());

        const missing: string[] = [];

        await Promise.all(uniqueFonts.map(async (fontName) => {
          const normalized = fontName.trim().toLowerCase();
          const isLocal = systemFonts.includes(normalized) || 
                          customFonts.includes(normalized) || 
                          customFontsNames.includes(normalized);

          if (isLocal) {
            const entry = manifest.fonts.find(f => f.family_slug.toLowerCase() === normalized || f.name.toLowerCase() === normalized);
            if (entry) {
              injectFontFace(entry);
            }
            return;
          }

          const isGoogle = await checkGoogleFont(fontName);
          if (isGoogle) {
            injectGoogleFont(fontName);
            return;
          }

          missing.push(fontName);
        }));

        setMissingFonts(missing);
        if (missing.length > 0) {
          toast.warning(`Thiếu phông chữ: các font "${missing.join(', ')}" không được cài đặt trong hệ thống và Google Fonts. Chữ trên thiết kế có thể bị lệch.`);
        }
      } catch (err) {
        console.error('Failed to analyze template fonts in editor:', err);
      }
    };

    checkAndLoadTemplateFonts();
  }, [template]);

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



  const dataURLtoBlob = (dataurl: string) => dataUrlToBlob(dataurl);

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

  const renderThumbnailDataUrl = () => {
    if (!canvas) return null;
    return canvas.toDataURL({
      format: 'webp',
      quality: 0.8,
      multiplier: 0.25,
      backgroundColor: canvas.backgroundColor || '#ffffff'
    });
  };

  const handleSave = async (
    silent = false,
    statusOverride?: 'draft' | 'published',
    envOverride?: 'debug' | 'release' | 'all'
  ): Promise<boolean> => {
    if (!canvas || !template) return false;
    if (savingLockRef.current) return false;

    // Block all saves while layer assets failed to load (W2 — no destructive confirm)
    if (layerErrors.length > 0) {
      if (!silent) {
        toast.error(
          'Không thể lưu: một số layer chưa tải xong. Hãy F5 tải lại trang trước khi lưu.'
        );
      }
      return false;
    }

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

      const dataUrl = renderThumbnailDataUrl();
      if (!dataUrl) {
        throw new Error('Không thể tạo thumbnail cho template.');
      }

      if (activeObject) {
        canvas.setActiveObject(activeObject);
        canvas.renderAll();
      }

      await uploadInlineImageLayers(canvas, template.template_id);

      const prepared = prepareTemplateForSave(canvas, {
        canvasBaseWidth: baseWidth,
        canvasBaseHeight: baseHeight,
        templateId: template.template_id,
        categoryId: template.category_id,
        title: template.title,
        status: newStatus,
        thumbnailUrl: template.thumbnail_url,
        silent,
      });

      const serializedTemplate = prepared.template;

      if (prepared.driftWarnings.length > 0) {
        console.warn('[save] Fabric ↔ cloud drift:', prepared.driftWarnings);
      }
      if (prepared.layerCountDiff) {
        console.warn('[save] Layer count diff:', prepared.layerCountDiff);
      }
      if (prepared.parityGaps.length > 0) {
        console.warn('[save] Mobile parity gaps:', prepared.parityGaps);
      }

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
        if (validation.warnings.length > 0) {
          console.warn('[publish] Cảnh báo data contract:', validation.warnings);
          if (!silent) {
            toast.warning(
              `${validation.warnings.length} cảnh báo khi kiểm tra template — giá trị lệch đã được tự chỉnh. Xem console để biết chi tiết.`
            );
          }
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
        if (serializedTemplate.metadata) {
          serializedTemplate.metadata.thumbnailUrl = thumbnailUrl;
        }
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
            : 'Đã lưu template thành công!',
          {
            duration: 4500,
            description: statusOverride || envOverride
              ? 'Trạng thái publish/draft đã được cập nhật trên server.'
              : 'Thiết kế và ảnh xem trước đã được lưu trên server.',
          }
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
    if (!isDirty || isSaving || saveBlocked || !canvas || !template) return;
    const timer = setTimeout(() => {
      void handleSaveRef.current(true);
    }, AUTOSAVE_MS);
    return () => clearTimeout(timer);
  }, [isDirty, isSaving, saveBlocked, canvas, template]);

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

  const handleReloadThumbnail = async () => {
    if (!canvas || !template || isSaving) return;
    const ok = await handleSaveRef.current(true);
    if (ok) {
      toast.success('Đã tải lại thumbnail thành công!');
    }
  };

  const waitForSaveUnlock = async (maxMs = 20000): Promise<boolean> => {
    const started = Date.now();
    while (savingLockRef.current) {
      if (Date.now() - started > maxMs) return false;
      await new Promise((resolve) => setTimeout(resolve, 120));
    }
    return true;
  };

  const downloadBlobFile = (blob: Blob, filename: string) => {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.download = filename;
    link.href = url;
    link.rel = 'noopener';
    document.body.appendChild(link);
    link.click();
    link.remove();
    // Keep object URL briefly so the browser can start the download before revoke.
    window.setTimeout(() => URL.revokeObjectURL(url), 1500);
  };

  const exportCanvasImage = async (format: 'png' | 'webp') => {
    if (!canvas) {
      toast.error('Canvas chưa sẵn sàng để xuất!');
      return;
    }

    const label = format.toUpperCase();
    const quality = format === 'png' ? 1.0 : 0.92;
    const toastId = toast.loading(`Đang lưu template và xuất ${label}...`);
    const hadUnsavedChanges = isDirty;
    let didSave = false;

    try {
      // Persist before export. Wait out autosave lock so we don't race mid-save.
      const unlocked = await waitForSaveUnlock();
      if (!unlocked && hadUnsavedChanges) {
        toast.error(`Đang lưu template — thử xuất ${label} lại sau vài giây.`, {
          id: toastId,
          duration: 6500,
        });
        return;
      }

      if (unlocked) {
        const saved = await handleSaveRef.current(true);
        if (saved) {
          didSave = true;
        } else if (hadUnsavedChanges) {
          toast.error(`Không thể lưu template trước khi xuất ${label}.`, {
            id: toastId,
            duration: 6500,
          });
          return;
        }
      }

      const activeObject = canvas.getActiveObject();
      canvas.discardActiveObject();
      canvas.renderAll();
      const dataUrl = canvas.toDataURL({ format, quality });
      if (activeObject) {
        canvas.setActiveObject(activeObject);
        canvas.renderAll();
      }

      const filename = `${template?.title || 'template'}_export.${format}`;
      // Use blob:// download — data: URLs can navigate the SPA away and kill toasts.
      downloadBlobFile(dataUrlToBlob(dataUrl), filename);

      // Fresh toast (not only id-replace) so success is unmistakable after long saves.
      toast.dismiss(toastId);
      toast.success(
        didSave || hadUnsavedChanges
          ? 'Đã lưu template thành công!'
          : `Đã xuất file ${label} thành công!`,
        {
          duration: 7000,
          description: didSave || hadUnsavedChanges
            ? `Đã xuất file ${label}: ${filename}`
            : `File ${filename} đã được tải xuống.`,
        }
      );
    } catch (err: any) {
      toast.error(
        didSave
          ? `Đã lưu template nhưng lỗi xuất ${label}: ${err.message}`
          : `Lỗi xuất ${label}: ${err.message}`,
        { id: toastId, duration: 6500 }
      );
    }
  };

  const handleExportPNG = () => {
    void exportCanvasImage('png');
  };

  const handleExportWEBP = () => {
    void exportCanvasImage('webp');
  };

  const baseWidth = template?.canvas_data?.canvas?.baseWidth || 1080;
  const baseHeight = template?.canvas_data?.canvas?.baseHeight || 1920;

  if (loading) {
    if (v2Layout) return <EditorLoadingShimmer />;
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

  const editorDialogs = (
    <>
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
    </>
  );

  if (v2Layout) {
    return (
      <EditorShell
        template={template}
        templateRouteId={String(id)}
        isSaving={isSaving}
        isDirty={isDirty}
        lastSavedAt={lastSavedAt}
        layerErrors={layerErrors}
        missingFonts={missingFonts}
        cursorPos={cursorPos}
        onSave={() => { void handleSave(); }}
        onReloadThumbnail={handleReloadThumbnail}
        onExportPNG={handleExportPNG}
        onExportWEBP={handleExportWEBP}
        onPreview={() => setIsPreviewOpen(true)}
        onPublishDebug={async () => { await handleSave(false, 'published', 'debug'); }}
        onPublishRelease={async () => { await handleSave(false, 'published', 'release'); }}
        onRevertDraft={async () => { await handleSave(false, 'draft', 'all'); }}
        onDelete={() => setIsDeleteOpen(true)}
        onDirty={() => setIsDirty(true)}
        onLayerLoadError={(err) => {
          setLayerErrors((prev) => (prev.includes(err) ? prev : [...prev, err]));
        }}
        onBootstrapFabricState={() => { void handleBootstrapFabricState(); }}
        onDismissLayerErrors={() => setLayerErrors([])}
        onDismissMissingFonts={() => setMissingFonts([])}
        formatSavedTime={formatSavedTime}
      >
        {editorDialogs}
      </EditorShell>
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
                {(template.categories as { name?: string } | null)?.name || template.category?.name || 'Chưa phân loại'}
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
            onClick={handleReloadThumbnail}
            disabled={isSaving}
            className="border-slate-200 bg-white text-slate-400 hover:text-slate-800 hover:bg-slate-100 rounded-xl h-8 text-xs font-bold px-3 flex items-center gap-1"
          >
            <RefreshCw className="w-3 h-3" /> Tải lại thumbnail
          </Button>
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
                    disabled={saveBlocked || isSaving}
                    className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs font-semibold text-slate-400 hover:bg-slate-100 transition-colors cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent"
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
                    disabled={saveBlocked || isSaving}
                    className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-[11px] font-medium text-amber-300 hover:bg-amber-500/10 transition-colors cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    <Smartphone className="w-4 h-4" /> Publish (App Debug)
                  </button>
                  <button
                    onClick={async () => {
                      setIsActionsOpen(false);
                      const ok = await handleSave(false, 'published', 'release');
                      if (!ok) return;
                    }}
                    disabled={saveBlocked || isSaving}
                    className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-[11px] font-medium text-emerald-400 hover:bg-emerald-500/10 transition-colors cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    <Globe className="w-4 h-4" /> Publish (App Release)
                  </button>
                  <button
                    onClick={async () => {
                      setIsActionsOpen(false);
                      await handleSave(false, 'draft', 'all');
                    }}
                    disabled={saveBlocked || isSaving}
                    className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-[11px] font-medium text-slate-500 hover:bg-slate-100 transition-colors cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
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

      {/* Missing Fonts Warning Bar */}
      {missingFonts.length > 0 && (
        <div className="bg-amber-50 border-b border-amber-200/60 px-4 py-2 shrink-0 flex items-center justify-between z-20 text-amber-800 text-xs">
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-amber-500 shrink-0 animate-bounce" />
            <span>
              Cảnh báo: Thiết kế này sử dụng phông chữ chưa được hỗ trợ: <strong className="font-bold">{missingFonts.join(', ')}</strong>. Hãy đổi font hoặc thêm font vào hệ thống để tránh lỗi hiển thị trên thiết bị.
            </span>
          </div>
          <button 
            onClick={() => setMissingFonts([])} 
            className="text-amber-500 hover:text-amber-700 font-bold px-1.5 py-0.5 hover:bg-amber-100 rounded transition-colors cursor-pointer"
          >
            Đóng
          </button>
        </div>
      )}

      {/* Layer Loading Errors Warning Bar */}
      {layerErrors.length > 0 && (
        <div className="bg-rose-50 border-b border-rose-200/60 px-4 py-2.5 shrink-0 flex items-center z-20 text-rose-800 text-xs">
          <div className="flex items-start gap-2">
            <AlertTriangle className="w-4 h-4 text-rose-500 shrink-0 mt-0.5 animate-pulse" />
            <div className="space-y-1">
              <span className="font-bold text-rose-700 block">Lỗi tải tài nguyên Layer — lưu bị chặn:</span>
              <ul className="list-disc list-inside space-y-0.5 pl-1 text-[11px] text-rose-600">
                {layerErrors.map((err, idx) => (
                  <li key={idx}>{err}</li>
                ))}
              </ul>
              <span className="text-[10px] text-rose-500 font-semibold block pt-1">
                Vui lòng F5 tải lại trang. Lưu thủ công, autosave và publish đều bị vô hiệu cho đến khi lỗi được khắc phục.
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Main editor area */}
      <div className="flex-1 flex min-h-0 w-full relative">

        {/* Layer panel (resizable) */}
        {!isLeftCollapsed && (
          <>
            <div
              className="shrink-0 bg-white/80 backdrop-blur-xl border-r border-slate-200/60 flex flex-col min-h-0 z-10 shadow-lg"
              style={{ width: layerPanelW }}
            >
              <div className="flex items-center justify-between px-3 py-2 border-b border-slate-200/60 shrink-0">
                <span className="text-xs font-bold text-slate-500 flex items-center gap-1.5">
                  <Layers className="w-3.5 h-3.5 text-indigo-400" /> Layers
                </span>
                <span className="text-[10px] text-slate-400">{layers.length}</span>
              </div>
              <div className="flex-1 min-h-0 overflow-hidden p-3">
                <LayerPanel onDirty={() => setIsDirty(true)} />
              </div>
            </div>
            <div
              className="w-1.5 cursor-col-resize hover:bg-indigo-500/50 active:bg-indigo-500 transition-colors shrink-0 relative group z-10"
              onMouseDown={handleResizeStart('layer')}
              onDoubleClick={handleResizeDoubleClick('layer')}
            >
              <div className="absolute inset-y-0 left-1/2 -translate-x-1/2 w-0.5 bg-slate-300 group-hover:bg-indigo-500 transition-colors" />
            </div>
          </>
        )}

        {/* Asset Drawer backdrop (click to close when not pinned) */}
        {isAssetDrawerOpen && !isAssetPinned && (
          <div className="fixed inset-0 z-40" onClick={() => setIsAssetDrawerOpen(false)} />
        )}

        {/* Asset Drawer (slides over content) */}
        {isAssetDrawerOpen && (
          <div className="absolute top-0 bottom-0 z-40 animate-in slide-in-from-left-3 duration-200 flex" style={{ width: leftPanelW, left: isLeftCollapsed ? 0 : layerPanelW + 6 }}>
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
            onLayerLoadError={(err) => {
              setLayerErrors(prev => {
                if (prev.includes(err)) return prev;
                return [...prev, err];
              });
            }}
          />
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
                ['Ctrl+0', 'Zoom 100% (cao vừa khung)'],
                ['Ctrl+1', 'Zoom 100% (cao vừa khung)'],
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

      {editorDialogs}
    </div>
  );
}
