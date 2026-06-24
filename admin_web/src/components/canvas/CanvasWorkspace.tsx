'use client';

import React, { useEffect, useRef, useState } from 'react';
import { Canvas, IText } from 'fabric';
import { initFabricEditorDefaults } from '@/lib/fabric-setup';
import { loadTemplateIntoCanvas } from '@/lib/fabric-template-loader';
import { CanvasContextMenu } from './CanvasContextMenu';
import { useCanvasSnapping } from '@/hooks/useCanvasSnapping';
import { useCanvasViewport } from '@/hooks/useCanvasViewport';
import { extractActiveObjectProps } from '@/lib/canvas-object-props';
import { injectFontFace, invalidateFontsManifestCache } from '@/lib/fonts-manifest';

initFabricEditorDefaults();

import {
  ZoomIn,
  ZoomOut,
  Maximize,
  Undo,
  Redo,
  Save,
  Loader2,
  Type,
  Grid,
  Sparkles,
  Link,
  Trash2,
  Layers,
  ImagePlus,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useEditorStore } from '@/store/editor.store';
import EditorToolbar from './toolbar/EditorToolbar';
import { useKeyboardShortcuts } from '@/hooks/useKeyboardShortcuts';
import { useLayersStore } from '@/store/layers.store';
import { useCropStore } from '@/store/crop.store';
import { toast } from 'sonner';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import CropImageModal from './properties/CropImageModal';
import {
  type DroppedAsset,
  type ShapeSubtype,
  addTextToCanvas as factoryAddText,
  addShapeToCanvas as factoryAddShape,
  addImageFromUrl as factoryAddImageFromUrl,
  addImageLayerFromUrl as factoryAddImageLayer,
  replaceImageLayerFromUrl as factoryReplaceImage,
  addDroppedImageToCanvas as factoryAddDroppedImage,
  findImageLayerAtPoint,
  replaceDroppedImageOnLayer,
  uploadCanvasImageFile,
  getDroppedImageFiles,
  ensureRemoteImageUrl,
  uploadInlineImageUrl,
  isInlineImageUrl,
  canvasCoordsFromDropEvent,
  createLayerId,
} from '@/lib/canvas-factory';
import Ruler from './Ruler';

interface CanvasWorkspaceProps {
  template: any;
  onSave: () => void;
  isSaving: boolean;
  setIsDirty: (dirty: boolean) => void;
  /** Fired once when template was loaded from canvas_data only (no fabric_state). */
  onLoadedWithoutFabricState?: () => void;
  onLayerLoadError?: (error: string) => void;
}

// Module-level variable to serialize canvas initialization and disposal across strict mode double mounts
let activeDisposalPromise: Promise<void> | null = null;

export default function CanvasWorkspace({ template, onSave, isSaving, setIsDirty, onLoadedWithoutFabricState, onLayerLoadError }: CanvasWorkspaceProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const fabricCanvasRef = useRef<any>(null);

  const {
    setCanvas,
    pushState,
    undo,
    redo,
    undoStack,
    redoStack,
    copyToClipboard,
    pasteFromClipboard,
    groupSelection,
    ungroupSelection,
    clipboard,
  } = useEditorStore();

  const {
    setActiveObjectId,
    setActiveObjectProps,
    setLayers,
    syncLayersFromCanvas,
    activeObjectProps,
  } = useLayersStore();
  const {
    request: cropRequest,
    openCrop,
    closeCrop,
  } = useCropStore();

  const [loadingLayers, setLoadingLayers] = useState(true);
  const workspaceRef = useRef<HTMLDivElement>(null);

  const baseWidth = template.canvas_data?.canvas?.baseWidth || 1080;
  const baseHeight = template.canvas_data?.canvas?.baseHeight || 1920;

  const {
    zoom,
    zoomPercent,
    exceedsFitHeight,
    calculateFitZoom,
    resetZoomTo100,
    handleZoom,
    handleViewportMouseDown,
    handleViewportMouseMove,
    handleViewportMouseUp,
  } = useCanvasViewport(workspaceRef, fabricCanvasRef, baseWidth, baseHeight);

  useEffect(() => {
    if (!exceedsFitHeight && workspaceRef.current) {
      workspaceRef.current.scrollTop = 0;
    }
  }, [exceedsFitHeight, zoom]);

  useKeyboardShortcuts({
    onSave,
    setIsDirty,
    onZoomFit: () => calculateFitZoom(true),
    onZoom100: resetZoomTo100,
  });

  const [showGrid, setShowGrid] = useState(false);
  const [isImageUrlOpen, setIsImageUrlOpen] = useState(false);
  const [imageUrl, setImageUrl] = useState('');
  const [isShapeDropdownOpen, setIsShapeDropdownOpen] = useState(false);
  const [isFileDragOver, setIsFileDragOver] = useState(false);
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number; visible: boolean } | null>(null);

  const [guides, setGuides] = useState<{ type: 'h' | 'v'; position: number }[]>([]);
  const guidesRef = useRef(guides);
  useEffect(() => {
    guidesRef.current = guides;
  }, [guides]);

  const {
    snappingEnabled,
    setSnappingEnabled,
    guideX,
    guideY,
    handleObjectMoving,
    clearGuides,
  } = useCanvasSnapping(baseWidth, baseHeight, guidesRef);

  const addGuide = (type: 'h' | 'v', position: number) => {
    setGuides((prev) => {
      if (prev.some((g) => g.type === type && g.position === position)) return prev;
      return [...prev, { type, position }];
    });
  };

  const removeGuide = (index: number) => {
    setGuides((prev) => prev.filter((_, i) => i !== index));
  };

  const syncActiveObjectProps = (activeObj: any) => {
    setActiveObjectProps(extractActiveObjectProps(activeObj));
  };

  // Determine if there is an image layer or background image as crop target
  const hasImageTarget = !!(activeObjectProps?.layerType === 'IMAGE' || activeObjectProps?.layerType === 'image'
    || (fabricCanvasRef.current?.backgroundImage));

  const handleContextMenu = (e: React.MouseEvent) => {
    e.preventDefault();
    const canvasInstance = fabricCanvasRef.current;
    if (!canvasInstance) return;

    const rect = workspaceRef.current?.getBoundingClientRect();
    if (!rect) return;

    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    const target = canvasInstance.findTarget(e);
    if (target && !target._isBackground) {
      canvasInstance.setActiveObject(target);
      canvasInstance.renderAll();
      setActiveObjectId(target.layerId || null);
    } else if (!target) {
      canvasInstance.discardActiveObject();
      canvasInstance.renderAll();
      setActiveObjectId(null);
    }

    setContextMenu({
      x,
      y,
      visible: true
    });
  };

  const commitCanvasAdd = (canvas: any, activeObject?: any) => {
    if (activeObject) {
      canvas.setActiveObject(activeObject);
    }
    canvas.renderAll();
    pushState();
    setIsDirty(true);
    syncLayersFromCanvas(canvas);
  };

  const factoryHandlers = { onCommit: commitCanvasAdd };
  const layerSelectionHandlers = { setActiveObjectId, setActiveObjectProps };

  const addTextToCanvas = () => {
    const canvas = fabricCanvasRef.current;
    if (!canvas) return;
    factoryAddText(canvas, baseWidth, baseHeight, factoryHandlers);
  };

  const addShapeToCanvas = (type: ShapeSubtype) => {
    const canvas = fabricCanvasRef.current;
    if (!canvas) return;
    factoryAddShape(canvas, type, baseWidth, baseHeight, factoryHandlers);
  };

  const addImageFromUrl = async (url: string) => {
    const canvas = fabricCanvasRef.current;
    if (!canvas) return;
    const ok = await factoryAddImageFromUrl(canvas, url, baseWidth, baseHeight, factoryHandlers);
    if (ok) {
      setImageUrl('');
      setIsImageUrlOpen(false);
    }
  };

  const addImageLayerFromUrl = async (url: string, layerName: string) => {
    const canvas = fabricCanvasRef.current;
    if (!canvas) return;
    await factoryAddImageLayer(
      canvas,
      url,
      layerName,
      baseWidth,
      baseHeight,
      factoryHandlers,
      layerSelectionHandlers
    );
  };

  const replaceImageLayerFromUrl = async (url: string) => {
    const canvas = fabricCanvasRef.current;
    if (!canvas) return;
    await factoryReplaceImage(canvas, url, factoryHandlers);
  };

  const addDroppedImageToCanvas = async (asset: DroppedAsset, x: number, y: number) => {
    const canvas = fabricCanvasRef.current;
    if (!canvas) return;
    await factoryAddDroppedImage(canvas, asset, x, y, baseWidth, baseHeight, factoryHandlers);
  };

  const openCropForCurrentTarget = () => {
    const fabricCanvas = fabricCanvasRef.current;
    if (!fabricCanvas) return;

    const activeObj = fabricCanvas.getActiveObject() as any;
    const activeIsImage = activeObj && (activeObj.type === 'image' || activeObj.layerType === 'IMAGE');
    const backgroundUrl = fabricCanvas.backgroundImage ? getBackgroundImageUrl(fabricCanvas) : '';

    if (activeIsImage) {
      const imageUrlValue = activeObj.src || activeObj.defaultImageUrl || activeObj._originalElement?.src || '';
      if (!imageUrlValue) {
        toast.error('Không tìm thấy nguồn ảnh của layer này.');
        return;
      }
      openCrop({
        imageUrl: imageUrlValue,
        sourceName: activeObj.layerName || 'Layer ảnh',
        initialRatio: activeObj.cropRatio || 'FREE',
      });
      return;
    }

    if (backgroundUrl) {
      openCrop({
        imageUrl: backgroundUrl,
        sourceName: 'Nền',
        initialRatio: 'FREE',
      });
      return;
    }

    toast.error('Chọn một layer ảnh hoặc có ảnh nền để cắt.');
  };

  const getBackgroundImageUrl = (canvasInstance: any) => {
    const bg = canvasInstance?.backgroundImage;
    return bg ? (bg.src || bg._originalElement?.src || bg.defaultImageUrl || '') : '';
  };

  // 1. Initialize Fabric.js Canvas
  useEffect(() => {
    if (!canvasRef.current || !containerRef.current) return;

    let fabricCanvas: any = null;
    let isAborted = false;

    let lastActiveTextObj: any = null;

    const onSelect = (e: any) => {
      const currentCanvas = fabricCanvas || fabricCanvasRef.current;
      if (!currentCanvas) return;
      const selectedObj = e.selected?.[0] || currentCanvas.getActiveObject();
      if (!selectedObj) return;

      const objAny = selectedObj as any;
      setActiveObjectId(objAny.layerId || null);
      syncActiveObjectProps(selectedObj);

      if (objAny.type === 'i-text' || objAny.layerType === 'TEXT') {
        lastActiveTextObj = objAny;
      } else {
        lastActiveTextObj = null;
      }
    };

    const onClear = () => {
      setActiveObjectId(null);
      setActiveObjectProps(null);
    };

    const onMouseDown = (options: any) => {
      const currentCanvas = fabricCanvas || fabricCanvasRef.current;
      if (!currentCanvas) return;
      const pointer = currentCanvas.getScenePoint(options.e);
      const prevActive = lastActiveTextObj;
      if (prevActive && prevActive.canvas === currentCanvas && prevActive.containsPoint(pointer)) {
        // Wrap in a setTimeout to avoid disrupting Fabric's synchronous event handling (which causes the mouse to stick/drag selection marquee)
        setTimeout(() => {
          if (currentCanvas.disposed) return;
          if (currentCanvas.getActiveObject() !== prevActive) {
            currentCanvas.setActiveObject(prevActive);
          }
          
          if (options.e?.detail === 2 && typeof prevActive.enterEditing === 'function') {
            prevActive.enterEditing();
            if (prevActive.hiddenTextarea) {
              prevActive.hiddenTextarea.focus({ preventScroll: true });
            }
          }
          currentCanvas.renderAll();
        }, 50);
      }
    };

    const onModify = () => {
      pushState();
      setIsDirty(true);
      const currentCanvas = fabricCanvas || fabricCanvasRef.current;
      if (currentCanvas) {
        syncLayersFromCanvas(currentCanvas);
        const activeObj = currentCanvas.getActiveObject();
        if (activeObj) {
          syncActiveObjectProps(activeObj);
        }
      }
    };

    const onTextEditingEntered = (e: any) => {
      const obj = e.target;
      if (obj && obj.hiddenTextarea) {
        try {
          obj.hiddenTextarea.style.position = 'fixed';
          obj.hiddenTextarea.style.top = '20px';
          obj.hiddenTextarea.style.left = '20px';
          obj.hiddenTextarea.style.width = '1px';
          obj.hiddenTextarea.style.height = '1px';
          obj.hiddenTextarea.style.opacity = '0';
          obj.hiddenTextarea.style.pointerEvents = 'none';

          const originalFocus = obj.hiddenTextarea.focus;
          if (originalFocus && !(originalFocus as any)._isOverridden) {
            const fn = function(this: any, options: any) {
              originalFocus.call(this, { ...options, preventScroll: true });
            };
            (fn as any)._isOverridden = true;
            obj.hiddenTextarea.focus = fn;
          }
        } catch (err) {
          console.warn('Failed to configure hiddenTextarea:', err);
        }
      }

      const scrollX = window.scrollX || window.pageXOffset;
      const scrollY = window.scrollY || window.pageYOffset;
      const viewport = workspaceRef.current;
      const vScrollLeft = viewport ? viewport.scrollLeft : 0;
      const vScrollTop = viewport ? viewport.scrollTop : 0;
      setTimeout(() => {
        window.scrollTo(scrollX, scrollY);
        if (viewport) {
          viewport.scrollLeft = vScrollLeft;
          viewport.scrollTop = vScrollTop;
        }
      }, 0);
    };

    const onTextChanged = (e: any) => {
      const obj = e.target;
      const currentCanvas = fabricCanvas || fabricCanvasRef.current;
      if (!obj || !currentCanvas) return;

      if (obj.textTransform === 'uppercase') {
        const upper = obj.text.toUpperCase();
        if (obj.text !== upper) {
          obj.set('text', upper);
          currentCanvas.renderAll();
        }
        obj._originalText = upper;
      }

      // Auto-fit font size if text wraps and height exceeds maxHeight
      if (obj.autoFit && obj.maxHeight) {
        let step = 0;
        while (obj.height > obj.maxHeight && obj.fontSize > 8 && step < 100) {
          obj.set('fontSize', obj.fontSize - 1);
          if (obj.type === 'textbox') {
            obj.initDimensions();
          }
          step++;
        }
        currentCanvas.renderAll();
        syncActiveObjectProps(obj);
      }

      syncLayersFromCanvas(currentCanvas);
    };

    // Unified initialization task
    const init = async () => {
      try {
        if (activeDisposalPromise) {
          await activeDisposalPromise;
        }
      } catch (err) {
        console.warn('Error waiting for previous canvas disposal:', err);
      }

      if (isAborted || !canvasRef.current) return;

      const newCanvas = new Canvas(canvasRef.current, {
        width: baseWidth,
        height: baseHeight,
        backgroundColor: '#ffffff',
        preserveObjectStacking: true,
      });

      (newCanvas as any).disposed = false;
      fabricCanvas = newCanvas;
      fabricCanvasRef.current = newCanvas;
      setCanvas(newCanvas);

      // Event bindings
      newCanvas.on('selection:created', onSelect);
      newCanvas.on('selection:updated', onSelect);
      newCanvas.on('selection:cleared', onClear);
      newCanvas.on('object:modified', onModify);
      newCanvas.on('text:editing:entered', onTextEditingEntered);
      newCanvas.on('text:changed', onTextChanged);
      newCanvas.on('mouse:down', onMouseDown);
      
      newCanvas.on('object:moving', handleObjectMoving);
      newCanvas.on('object:modified', clearGuides);
      newCanvas.on('mouse:up', clearGuides);
      newCanvas.on('selection:cleared', clearGuides);

      // Initial zoom (fit to viewport)
      calculateFitZoom(true);

      // Load layers
      loadTemplateIntoCanvas({
        canvasInstance: newCanvas,
        template,
        baseWidth,
        baseHeight,
        syncLayersFromCanvas,
        setLoadingLayers,
        onLoadedWithoutFabricState,
        onLayerLoadError,
      });

      if (isAborted) {
        cleanUpInstance(newCanvas);
      }
    };

    const cleanUpInstance = (canvasInstance: any) => {
      canvasInstance.off('selection:created', onSelect);
      canvasInstance.off('selection:updated', onSelect);
      canvasInstance.off('selection:cleared', onClear);
      canvasInstance.off('object:modified', onModify);
      canvasInstance.off('text:editing:entered', onTextEditingEntered);
      canvasInstance.off('text:changed', onTextChanged);
      canvasInstance.off('mouse:down', onMouseDown);
      
      canvasInstance.off('object:moving', handleObjectMoving);
      canvasInstance.off('object:modified', clearGuides);
      canvasInstance.off('mouse:up', clearGuides);
      canvasInstance.off('selection:cleared', clearGuides);

      activeDisposalPromise = canvasInstance.dispose();
      (canvasInstance as any).disposed = true;
      
      if (fabricCanvasRef.current === canvasInstance) {
        fabricCanvasRef.current = null;
        setCanvas(null);
        setActiveObjectId(null);
        setActiveObjectProps(null);
        setLayers([]);
      }
    };

    init();

    return () => {
      isAborted = true;
      if (fabricCanvas) {
        cleanUpInstance(fabricCanvas);
      }
    };
  }, []);

  const handleCanvasDrop = async (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setIsFileDragOver(false);

    const fabricCanvas = fabricCanvasRef.current;
    if (!fabricCanvas || !canvasRef.current || loadingLayers) return;

    const { x, y } = canvasCoordsFromDropEvent(
      event,
      canvasRef.current,
      fabricCanvas,
      baseWidth,
      baseHeight,
    );

    // Xử lý kéo thả file font (.ttf, .otf, .woff)
    const getDroppedFontFiles = (dt: DataTransfer): File[] => {
      const fromFiles = Array.from(dt.files).filter((file) => /\.(ttf|otf|woff)$/i.test(file.name));
      if (fromFiles.length > 0) return fromFiles;
 
      const fromItems: File[] = [];
      for (const item of Array.from(dt.items || [])) {
        if (item.kind !== 'file') continue;
        const file = item.getAsFile();
        if (file && /\.(ttf|otf|woff)$/i.test(file.name)) fromItems.push(file);
      }
      return fromItems;
    };
 
    const droppedFontFiles = getDroppedFontFiles(event.dataTransfer);
    if (droppedFontFiles.length > 0) {
      for (let i = 0; i < droppedFontFiles.length; i++) {
        const file = droppedFontFiles[i];
        const toastId = toast.loading(`Đang tải lên font "${file.name}"...`);
        try {
          const baseName = file.name.replace(/\.[^/.]+$/, '');
          const fontName = baseName.replace(/[-_]/g, ' ');
          const familySlug = baseName.toLowerCase().replace(/[^a-z0-9]/g, '');
 
          const formData = new FormData();
          formData.append('file', file);
          formData.append('name', fontName);
          formData.append('family_slug', familySlug);
          formData.append('style', 'Chưa phân loại');
 
          const res = await fetch('/api/v1/fonts', {
            method: 'POST',
            body: formData,
          });
 
          const data = await res.json();
          if (!res.ok) throw new Error(data.error || 'Tải font lên thất bại');
 
          const fontEntry = data.font;
          if (fontEntry && fontEntry.font_url) {
            injectFontFace(fontEntry);
            invalidateFontsManifestCache();
            await document.fonts.load(`12px "${fontEntry.family_slug}"`);
 
            const activeObj = fabricCanvas.getActiveObject() as any;
            const isText = activeObj && (activeObj.type === 'i-text' || activeObj.layerType === 'TEXT');
            if (isText) {
              activeObj.set({ fontFamily: fontEntry.family_slug });
              fabricCanvas.renderAll();
              pushState();
              setIsDirty(true);
              syncLayersFromCanvas(fabricCanvas);
              useLayersStore.getState().setActiveObjectProps(extractActiveObjectProps(activeObj));
              toast.success(`Đã áp dụng font "${fontEntry.name}" cho layer chữ đang chọn!`, { id: toastId });
            } else {
              const textObj = new IText('Nhập chữ...', {
                left: x + i * 24,
                top: y + i * 24,
                originX: 'center',
                originY: 'center',
                fontFamily: fontEntry.family_slug,
                fontSize: 80,
                fill: '#6366f1',
                hasControls: true,
                hasBorders: true,
                selectable: true,
                padding: 20,
                objectCaching: false,
              });
              (textObj as any).layerId = createLayerId();
              (textObj as any).layerType = 'TEXT';
              (textObj as any).layerName = `Chữ: ${fontEntry.name}`;
              fabricCanvas.add(textObj);
              commitCanvasAdd(fabricCanvas, textObj);
              toast.success(`Đã thêm layer chữ mới sử dụng font "${fontEntry.name}"!`, { id: toastId });
            }
          } else {
            throw new Error('Phản hồi từ server không hợp lệ');
          }
        } catch (err: any) {
          console.error('Failed to upload dropped font:', err);
          toast.error(`Không thể tải lên font "${file.name}": ${err.message || 'Lỗi chưa xác định'}`, { id: toastId });
        }
      }
      return;
    }

    const droppedFiles = getDroppedImageFiles(event.dataTransfer);
    if (droppedFiles.length > 0) {
      for (let i = 0; i < droppedFiles.length; i++) {
        const file = droppedFiles[i];
        const toastId = toast.loading(`Đang tải lên "${file.name}"...`);
        try {
          const url = await uploadCanvasImageFile(file);
          if (!url) {
            toast.error(`Không thể tải lên "${file.name}". Kiểm tra đăng nhập và thử lại.`, { id: toastId });
            continue;
          }
          const layerName = file.name.replace(/\.[^.]+$/, '') || 'Ảnh';
          await addDroppedImageToCanvas(
            { file_url: url, name: layerName, folder: 'template-layers' },
            x + i * 24,
            y + i * 24,
          );
          toast.dismiss(toastId);
        } catch (err) {
          console.error('Failed to drop image file:', err);
          toast.error(`Lỗi khi thêm "${file.name}"`, { id: toastId });
        }
      }
      return;
    }

    const raw = event.dataTransfer.getData('application/json') || event.dataTransfer.getData('text/plain');
    if (!raw) return;

    // Some drag sources (browser image, screenshot tools) only provide a data URL string.
    if (isInlineImageUrl(raw)) {
      const toastId = toast.loading('Đang tải ảnh lên...');
      try {
        const uploaded = await uploadInlineImageUrl(raw, `drop_${Date.now()}.png`, 'template-layers');
        if (!uploaded) {
          toast.error('Không thể tải ảnh lên server.', { id: toastId });
          return;
        }
        await addDroppedImageToCanvas(
          { file_url: uploaded, name: 'Ảnh', folder: 'template-layers' },
          x,
          y,
        );
        toast.dismiss(toastId);
      } catch (err) {
        console.error('Failed to drop inline image:', err);
        toast.error('Không thể thêm ảnh vào canvas.', { id: toastId });
      }
      return;
    }

    let asset: DroppedAsset | null = null;
    try {
      asset = JSON.parse(raw);
    } catch {
      asset = { file_url: raw };
    }

    if (!asset) return;

    const assetUrl = asset.file_url || asset.fileUrl;
    if (assetUrl) {
      const remoteUrl = await ensureRemoteImageUrl(
        assetUrl,
        `asset_${Date.now()}.png`,
        asset.folder || 'template-layers',
      );
      if (!remoteUrl) {
        toast.error('Không thể tải ảnh lên server.');
        return;
      }
      asset = { ...asset, file_url: remoteUrl, fileUrl: remoteUrl };
    }

    const targetImageObj = findImageLayerAtPoint(fabricCanvas, x, y);
    if (targetImageObj) {
      const url = asset.file_url || asset.fileUrl;
      if (url) {
        const replaced = await replaceDroppedImageOnLayer(
          fabricCanvas,
          targetImageObj,
          url,
          factoryHandlers,
          (imageUrl) => {
            if (targetImageObj.layerId === useLayersStore.getState().activeObjectId) {
              useLayersStore.getState().updateActiveObject({
                src: imageUrl,
                defaultImageUrl: imageUrl,
              });
            }
          }
        );
        if (replaced) return;
      }
    }

    await addDroppedImageToCanvas(asset, x, y);
  };

  const handleCanvasDragOver = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'copy';
    if (Array.from(event.dataTransfer.types).includes('Files')) {
      setIsFileDragOver(true);
    }
  };

  const handleCanvasDragLeave = (event: React.DragEvent<HTMLDivElement>) => {
    if (!event.currentTarget.contains(event.relatedTarget as Node)) {
      setIsFileDragOver(false);
    }
  };

  return (
    <div className="flex flex-col h-full bg-slate-100 overflow-hidden relative" ref={containerRef}>
      
      {/* Toolbar Component */}
      <EditorToolbar
        zoomPercent={zoomPercent}
        onZoomIn={() => handleZoom(1.1)}
        onZoomOut={() => handleZoom(0.9)}
        onUndo={undo}
        onRedo={redo}
        undoStack={undoStack}
        redoStack={redoStack}
        onAddText={addTextToCanvas}
        onAddImage={() => setIsImageUrlOpen(true)}
        onCrop={openCropForCurrentTarget}
        hasImageTarget={hasImageTarget}
        showGrid={showGrid}
        onToggleGrid={() => setShowGrid(!showGrid)}
        snappingEnabled={snappingEnabled}
        onToggleSnapping={() => setSnappingEnabled(!snappingEnabled)}
        onShapeSelect={addShapeToCanvas}
      />

      {/* Loading Overlay */}
      {loadingLayers && (
        <div className="absolute inset-0 bg-slate-100/80 backdrop-blur-sm flex flex-col items-center justify-center gap-2 z-20">
          <Loader2 className="w-8 h-8 animate-spin text-indigo-500" />
          <p className="text-xs text-slate-500">Đang khởi tạo các layers...</p>
        </div>
      )}

      {isFileDragOver && !loadingLayers && (
        <div className="absolute inset-0 z-30 pointer-events-none flex items-center justify-center bg-indigo-500/10 border-2 border-dashed border-indigo-400/70 m-3 rounded-2xl">
          <div className="flex flex-col items-center gap-2 px-6 py-4 bg-white/90 rounded-2xl shadow-lg border border-indigo-200/80">
            <ImagePlus className="w-8 h-8 text-indigo-500" />
            <p className="text-sm font-semibold text-slate-700">Thả ảnh hoặc file Font chữ tại đây</p>
            <p className="text-[11px] text-slate-400">Ảnh (PNG, JPG, WebP...) hoặc Font (.ttf, .otf, .woff)</p>
          </div>
        </div>
      )}

      {cropRequest && (
        <CropImageModal
          isOpen={true}
          onClose={closeCrop}
          imageUrl={cropRequest.imageUrl}
          initialRatio={cropRequest.initialRatio || 'FREE'}
          onCropComplete={async (newImageUrl) => {
            await addImageLayerFromUrl(newImageUrl, `Cắt từ ${cropRequest.sourceName}`);
            closeCrop();
          }}
          onCropReplace={async (newImageUrl) => {
            await replaceImageLayerFromUrl(newImageUrl);
            closeCrop();
          }}
        />
      )}

      {/* Editor Center Container */}
      <div
        data-canvas-viewport
        ref={workspaceRef}
        className={`flex-1 min-h-0 flex bg-slate-100 relative overflow-x-auto ${
          exceedsFitHeight ? 'overflow-y-auto' : 'overflow-y-hidden'
        }`}
        style={{ 
          padding: '48px', // Increased padding to fit rulers nicely
          backgroundImage: 'radial-gradient(circle at 2px 2px, rgba(99, 102, 241, 0.1) 1px, transparent 0)',
          backgroundSize: '24px 24px',
        }}
        onDragOver={handleCanvasDragOver}
        onDragLeave={handleCanvasDragLeave}
        onDrop={handleCanvasDrop}
        onContextMenu={handleContextMenu}
        onClick={() => { if (contextMenu?.visible) setContextMenu(null); }}
        onMouseDown={handleViewportMouseDown}
        onMouseMove={handleViewportMouseMove}
        onMouseUp={handleViewportMouseUp}
        onMouseLeave={handleViewportMouseUp}
      >
        {/* Outer box: sized to the scaled canvas + rulers so scrollbars appear correctly */}
        <div
          style={{
            width: `${baseWidth * zoom + 20}px`,
            height: `${baseHeight * zoom + 20}px`,
            flexShrink: 0,
            position: 'relative',
            margin: 'auto',
          }}
        >
          {/* Corner Spacer */}
          <div 
            style={{ width: '20px', height: '20px' }}
            className="absolute top-0 left-0 bg-slate-100 border-r border-b border-slate-200/80 z-35" 
          />

          {/* Horizontal Ruler */}
          <div 
            style={{ left: '20px', top: 0, width: `${baseWidth * zoom}px`, height: '20px' }} 
            className="absolute z-35 overflow-hidden"
          >
            <Ruler type="horizontal" zoom={zoom} baseSize={baseWidth} onAddGuide={(pos) => addGuide('v', pos)} />
          </div>

          {/* Vertical Ruler */}
          <div 
            style={{ left: 0, top: '20px', width: '20px', height: `${baseHeight * zoom}px` }} 
            className="absolute z-35 overflow-hidden"
          >
            <Ruler type="vertical" zoom={zoom} baseSize={baseHeight} onAddGuide={(pos) => addGuide('h', pos)} />
          </div>

          {/* Inner box: native canvas size, scaled via CSS transform */}
          <div
            style={{
              width:  `${baseWidth}px`,
              height: `${baseHeight}px`,
              position: 'absolute',
              top: '20px',
              left: '20px',
              transformOrigin: 'top left',
              transform: `scale(${zoom})`,
              transition: 'transform 0.15s ease-out',
            }}
            className="shadow-xl ring-1 ring-slate-300 bg-white"
          >
            {/* Fabric.js canvas element */}
            <canvas ref={canvasRef} id="fabric-canvas" />

            {/* Smart Snapping Guides and Grid Overlay */}
            <svg className="absolute inset-0 pointer-events-none w-full h-full z-30">
              {showGrid && (
                <defs>
                  <pattern id="grid-pattern" width="50" height="50" patternUnits="userSpaceOnUse">
                    <path d="M 50 0 L 0 0 0 50" fill="none" stroke="rgba(99, 102, 241, 0.07)" strokeWidth="1" />
                  </pattern>
                </defs>
              )}
              {showGrid && (
                <rect width="100%" height="100%" fill="url(#grid-pattern)" />
              )}
              {guideX !== null && (
                <line x1={guideX} y1={0} x2={guideX} y2={baseHeight} stroke="#6366f1" strokeWidth={1.5} strokeDasharray="5,5" />
              )}
              {guideY !== null && (
                <line x1={0} y1={guideY} x2={baseWidth} y2={guideY} stroke="#6366f1" strokeWidth={1.5} strokeDasharray="5,5" />
              )}
            </svg>

            {/* User guides */}
            {guides.map((guide, idx) => (
              <div
                key={idx}
                style={{
                  position: 'absolute',
                  left: guide.type === 'v' ? `${guide.position}px` : 0,
                  top: guide.type === 'h' ? `${guide.position}px` : 0,
                  width: guide.type === 'v' ? '6px' : '100%',
                  height: guide.type === 'h' ? '6px' : '100%',
                  marginLeft: guide.type === 'v' ? '-3px' : 0,
                  marginTop: guide.type === 'h' ? '-3px' : 0,
                  cursor: guide.type === 'v' ? 'ew-resize' : 'ns-resize',
                  zIndex: 35,
                }}
                onDoubleClick={() => removeGuide(idx)}
                title="Nhấp đúp chuột để xóa đường gióng"
                className="group flex items-center justify-center pointer-events-auto"
              >
                <div 
                  className="w-full h-full bg-blue-500/10 group-hover:bg-blue-500/30 transition-colors flex items-center justify-center"
                  style={{
                    borderStyle: 'dashed',
                    borderWidth: guide.type === 'v' ? '0 0 0 1px' : '1px 0 0 0',
                    borderColor: '#3b82f6',
                  }}
                />
              </div>
            ))}

          </div>
        </div>
      </div>

      <CanvasContextMenu
        menu={contextMenu}
        fabricCanvasRef={fabricCanvasRef}
        clipboard={clipboard}
        onClose={() => setContextMenu(null)}
        onDirty={() => setIsDirty(true)}
        onCopy={copyToClipboard}
        onPaste={pasteFromClipboard}
        onGroup={groupSelection}
        onUngroup={ungroupSelection}
        syncLayersFromCanvas={syncLayersFromCanvas}
        onAddText={addTextToCanvas}
        onAddShape={addShapeToCanvas}
        onOpenImageUrlDialog={() => setIsImageUrlOpen(true)}
      />

      {/* Dialog Chèn Ảnh từ URL */}
      <Dialog open={isImageUrlOpen} onOpenChange={setIsImageUrlOpen}>
        <DialogContent className="bg-white border border-slate-200 text-slate-800 rounded-2xl sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="text-lg font-bold text-slate-800 flex items-center gap-2">
              <Link className="w-5 h-5 text-indigo-400" />
              Chèn ảnh từ URL
            </DialogTitle>
          </DialogHeader>
          <div className="py-4 space-y-3">
            <p className="text-xs text-slate-500">
              Nhập liên kết hình ảnh (PNG, JPG, WEBP, SVG) trực tuyến để thêm vào mẫu thiết kế:
            </p>
            <Input
              type="text"
              placeholder="https://example.com/image.png"
              value={imageUrl}
              onChange={(e) => setImageUrl(e.target.value)}
              className="bg-slate-100 border-slate-200 text-slate-800 rounded-xl focus:ring-indigo-500"
            />
          </div>
          <DialogFooter className="gap-2">
            <Button
              variant="outline"
              onClick={() => { setIsImageUrlOpen(false); setImageUrl(''); }}
              className="border-slate-200 hover:bg-slate-100 hover:text-slate-800 rounded-xl text-slate-400"
            >
              Hủy
            </Button>
            <Button
              onClick={() => addImageFromUrl(imageUrl)}
              className="bg-indigo-600 hover:bg-indigo-500 rounded-xl text-white font-bold cursor-pointer"
            >
              Chèn ảnh
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
