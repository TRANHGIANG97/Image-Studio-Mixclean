'use client';

import React, { useEffect, useRef, useState } from 'react';
import { 
  Canvas, 
  FabricImage, 
  IText, 
  Shadow,
  Rect,
  Circle,
  Triangle,
  ActiveSelection,
  FabricObject,
  Point,
  Control,
  controlsUtils,
  Line,
  Polygon,
  Path
} from 'fabric';

// Cấu hình CORS mặc định cho các đối tượng ảnh trong Fabric v6/v7
if (typeof window !== 'undefined' && FabricImage) {
  (FabricImage as any).ownDefaults = (FabricImage as any).ownDefaults || {};
  (FabricImage as any).ownDefaults.crossOrigin = 'anonymous';
}

// Helper functions for Phase H Vector Shapes
const getStarPoints = (outerRadius = 100, innerRadius = 40): { x: number; y: number }[] => {
  const points = [];
  const spikes = 5;
  let rot = (Math.PI / 2) * 3;
  const step = Math.PI / spikes;

  for (let i = 0; i < spikes; i++) {
    points.push({
      x: Math.cos(rot) * outerRadius,
      y: Math.sin(rot) * outerRadius
    });
    rot += step;

    points.push({
      x: Math.cos(rot) * innerRadius,
      y: Math.sin(rot) * innerRadius
    });
    rot += step;
  }
  return points;
};

const getDiamondPoints = (size = 100): { x: number; y: number }[] => {
  return [
    { x: 0, y: -size }, // Top
    { x: size, y: 0 },  // Right
    { x: 0, y: size },  // Bottom
    { x: -size, y: 0 }  // Left
  ];
};

const getHexagonPoints = (size = 100): { x: number; y: number }[] => {
  const points = [];
  for (let i = 0; i < 6; i++) {
    const angle = (Math.PI / 3) * i;
    points.push({
      x: Math.cos(angle) * size,
      y: Math.sin(angle) * size
    });
  }
  return points;
};

const arrowPath = "M -100 -20 L 20 -20 L 20 -60 L 100 0 L 20 60 L 20 20 L -100 20 Z";

const getCompositeOperation = (blendMode?: string | null): GlobalCompositeOperation => {
  if (!blendMode) return 'source-over';
  const mode = blendMode.toLowerCase();
  switch (mode) {
    case 'normal':
      return 'source-over';
    case 'multiply':
      return 'multiply';
    case 'screen':
      return 'screen';
    case 'overlay':
      return 'overlay';
    case 'darken':
      return 'darken';
    case 'lighten':
      return 'lighten';
    case 'color-dodge':
      return 'color-dodge';
    case 'color-burn':
      return 'color-burn';
    case 'hard-light':
      return 'hard-light';
    case 'soft-light':
      return 'soft-light';
    case 'difference':
      return 'difference';
    case 'exclusion':
      return 'exclusion';
    case 'hue':
      return 'hue';
    case 'saturation':
      return 'saturation';
    case 'color':
      return 'color';
    case 'luminosity':
      return 'luminosity';
    case 'linear-dodge':
      return 'lighter';
    case 'linear-burn':
      return 'multiply';
    default:
      return 'source-over';
  }
};

// Customize active selection bounding box styling globally (Canva-like style)
FabricObject.ownDefaults.borderColor = '#6366f1';
FabricObject.ownDefaults.borderScaleFactor = 2.5; // Viền dày vừa phải giúp thiết kế thanh thoát hơn
FabricObject.ownDefaults.cornerColor = '#ffffff';
FabricObject.ownDefaults.cornerStrokeColor = '#6366f1'; // Border around corners
FabricObject.ownDefaults.cornerSize = 32; // Giảm kích thước núm đi một nửa (từ 65 xuống 32)
FabricObject.ownDefaults.touchCornerSize = 48; // Giảm vùng chạm tương ứng để vừa vặn (48px)
FabricObject.ownDefaults.cornerStyle = 'circle'; // Rounded Canva style corners
FabricObject.ownDefaults.transparentCorners = false; // Solid filled corners
FabricObject.ownDefaults.borderOpacityWhenMoving = 0.8;

// Custom corner radius control (Canva style corner rounding handle)
const rxPositionHandler = (dim: any, finalMatrix: any, fabricObject: any) => {
  const x = -dim.x / 2 + (fabricObject.rx || 0);
  const y = -dim.y / 2 + (fabricObject.ry || 0);
  return new Point(x, y).transform(finalMatrix);
};

const rxActionHandler = (eventData: any, transform: any, x: number, y: number) => {
  const { target } = transform;
  
  const localPoint = controlsUtils.getLocalPoint(
    transform,
    'center',
    'center',
    x,
    y
  );
  
  const maxRadius = Math.min(target.width, target.height) / 2;
  const newRx = Math.max(0, Math.min(maxRadius, target.width / 2 + localPoint.x));
  const newRy = Math.max(0, Math.min(maxRadius, target.height / 2 + localPoint.y));
  
  const radius = Math.max(newRx, newRy);
  
  if (target.rx !== radius || target.ry !== radius) {
    target.set({ rx: radius, ry: radius });
    return true; // render canvas
  }
  return false;
};

const renderCornerRadiusControl = (ctx: any, left: number, top: number, styleOverride: any, fabricObject: any) => {
  ctx.save();
  ctx.beginPath();
  ctx.arc(left, top, 6, 0, 2 * Math.PI); // Draw 6px radius circle (12px diameter)
  ctx.fillStyle = '#ffffff';
  ctx.fill();
  ctx.lineWidth = 1.5;
  ctx.strokeStyle = '#6366f1'; // Indigo outline
  ctx.stroke();
  ctx.restore();
};

// Add to Rect prototype controls
if (Rect && Rect.prototype && Rect.prototype.controls) {
  Rect.prototype.controls.cornerRadius = new Control({
    x: -0.5,
    y: -0.5,
    positionHandler: rxPositionHandler,
    actionHandler: rxActionHandler,
    cursorStyle: 'pointer',
    actionName: 'cornerRadius',
    render: renderCornerRadiusControl,
  });
}
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
  Copy,
  Clipboard,
  Trash2,
  Layers,
  ChevronUp,
  ChevronDown,
  Lock,
  Unlock,
  FlipHorizontal,
  FlipVertical
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useEditorStore } from '@/store/editor.store';
import EditorToolbar from './toolbar/EditorToolbar';
import { useKeyboardShortcuts } from '@/hooks/useKeyboardShortcuts';
import { useLayersStore } from '@/store/layers.store';
import { useCropStore } from '@/store/crop.store';
import { argbIntToRgba } from '@/lib/template-converter';
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
import { ensureFontLoaded } from '@/lib/font-loader';
import Ruler from './Ruler';


interface DroppedAsset {
  id?: string;
  name?: string;
  folder?: string;
  file_url?: string;
  fileUrl?: string;
}

interface CanvasWorkspaceProps {
  template: any;
  onSave: () => void;
  isSaving: boolean;
  setIsDirty: (dirty: boolean) => void;
}

// Module-level variable to serialize canvas initialization and disposal across strict mode double mounts
let activeDisposalPromise: Promise<void> | null = null;

export default function CanvasWorkspace({ template, onSave, isSaving, setIsDirty }: CanvasWorkspaceProps) {
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
    activeObjectProps,
  } = useLayersStore();
  const {
    request: cropRequest,
    openCrop,
    closeCrop,
  } = useCropStore();

  const [zoom, setZoom] = useState(1);
  const [loadingLayers, setLoadingLayers] = useState(true);

  // Keyboard shortcuts (Ctrl+S, Ctrl+Z, Delete, arrows, etc.)
  useKeyboardShortcuts({
    onSave,
    setIsDirty,
    onZoomFit: calculateFitZoom,
    onZoom100: () => setZoom(1),
  });

  // States for snapping, grid, image dialog, dropdown, and custom context menu
  const [guideX, setGuideX] = useState<number | null>(null);
  const [guideY, setGuideY] = useState<number | null>(null);
  const [showGrid, setShowGrid] = useState(false);
  const [snappingEnabled, setSnappingEnabled] = useState(true);
  const [isImageUrlOpen, setIsImageUrlOpen] = useState(false);
  const [imageUrl, setImageUrl] = useState('');
  const [isShapeDropdownOpen, setIsShapeDropdownOpen] = useState(false);
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number; visible: boolean } | null>(null);

  // New States: Guides and Floating Toolbar
  const [guides, setGuides] = useState<{ type: 'h' | 'v'; position: number }[]>([]);
  const [toolbarPosition, setToolbarPosition] = useState<{ left: number; top: number; visible: boolean } | null>(null);

  const guidesRef = useRef(guides);
  useEffect(() => {
    guidesRef.current = guides;
  }, [guides]);

  const addGuide = (type: 'h' | 'v', position: number) => {
    setGuides((prev) => {
      if (prev.some((g) => g.type === type && g.position === position)) return prev;
      return [...prev, { type, position }];
    });
  };

  const removeGuide = (index: number) => {
    setGuides((prev) => prev.filter((_, i) => i !== index));
  };

  const updateFloatingToolbar = (canvasInstance: any) => {
    if (!canvasInstance) return;
    const activeObj = canvasInstance.getActiveObject();
    if (!activeObj || activeObj._isBackground) {
      setToolbarPosition(null);
      return;
    }
    const rect = activeObj.getBoundingRect();
    const top = Math.max(10, rect.top - 52);
    const left = rect.left + rect.width / 2;
    setToolbarPosition({ left, top, visible: true });
  };

  // Space drag panning states
  const [isSpacePressed, setIsSpacePressed] = useState(false);
  const isSpacePressedRef = useRef(false);
  const isDraggingViewportRef = useRef(false);
  const startDragCoordsRef = useRef({ x: 0, y: 0, scrollLeft: 0, scrollTop: 0 });

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.code === 'Space') {
        const activeEl = document.activeElement;
        const isTyping =
          activeEl?.tagName === 'INPUT' ||
          activeEl?.tagName === 'TEXTAREA' ||
          (activeEl as any)?.isContentEditable ||
          fabricCanvasRef.current?.getActiveObject()?.isEditing;

        if (isTyping) return;
        
        e.preventDefault();
        if (!isSpacePressedRef.current) {
          isSpacePressedRef.current = true;
          setIsSpacePressed(true);
          if (workspaceRef.current) {
            workspaceRef.current.style.cursor = 'grab';
          }
        }
      }
    };

    const handleKeyUp = (e: KeyboardEvent) => {
      if (e.code === 'Space') {
        isSpacePressedRef.current = false;
        setIsSpacePressed(false);
        if (workspaceRef.current) {
          workspaceRef.current.style.cursor = '';
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    window.addEventListener('keyup', handleKeyUp);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      window.removeEventListener('keyup', handleKeyUp);
    };
  }, []);

  // Sync canvas object selection state when space pressed
  useEffect(() => {
    const canvasInstance = fabricCanvasRef.current;
    if (!canvasInstance) return;
    if (isSpacePressed) {
      canvasInstance.selection = false;
      canvasInstance.forEachObject((obj: any) => {
        obj.evented = false;
      });
    } else {
      canvasInstance.selection = true;
      canvasInstance.forEachObject((obj: any) => {
        const isLocked = obj.lockMovementX === true;
        obj.evented = !isLocked;
      });
    }
    canvasInstance.renderAll();
  }, [isSpacePressed]);

  const handleViewportMouseDown = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!isSpacePressedRef.current || !workspaceRef.current) return;
    isDraggingViewportRef.current = true;
    workspaceRef.current.style.cursor = 'grabbing';
    startDragCoordsRef.current = {
      x: e.clientX,
      y: e.clientY,
      scrollLeft: workspaceRef.current.scrollLeft,
      scrollTop: workspaceRef.current.scrollTop
    };
  };

  const handleViewportMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!isDraggingViewportRef.current || !workspaceRef.current) return;
    const dx = e.clientX - startDragCoordsRef.current.x;
    const dy = e.clientY - startDragCoordsRef.current.y;
    workspaceRef.current.scrollLeft = startDragCoordsRef.current.scrollLeft - dx;
    workspaceRef.current.scrollTop = startDragCoordsRef.current.scrollTop - dy;
  };

  const handleViewportMouseUp = () => {
    if (isDraggingViewportRef.current) {
      isDraggingViewportRef.current = false;
      if (workspaceRef.current) {
        workspaceRef.current.style.cursor = isSpacePressedRef.current ? 'grab' : '';
      }
    }
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

  // Ref to track snapping state inside Fabric callbacks
  const snappingEnabledRef = useRef(true);
  useEffect(() => {
    snappingEnabledRef.current = snappingEnabled;
  }, [snappingEnabled]);

  const workspaceRef = useRef<HTMLDivElement>(null);

  const baseWidth = template.canvas_data?.canvas?.baseWidth || 1080;
  const baseHeight = template.canvas_data?.canvas?.baseHeight || 1920;

  // Tự động tính lại zoom khi kích thước container thay đổi (ResizeObserver)
  useEffect(() => {
    if (!workspaceRef.current) return;
    const observer = new ResizeObserver(() => {
      calculateFitZoom();
    });
    observer.observe(workspaceRef.current);
    return () => {
      observer.disconnect();
    };
  }, [baseWidth, baseHeight]);

  // Sync Layers list from Fabric to Zustand Store
  const syncLayersList = (canvasInstance: any) => {
    const objects = canvasInstance.getObjects();
    const updatedLayers = objects
      .filter((obj: any) => obj._isBackground !== true)
      .map((obj: any) => {
        if (!obj.layerId) {
          obj.layerId = `layer_${Math.random().toString(36).substring(2, 11)}`;
        }
        return {
          id: obj.layerId,
          name: obj.layerName || 'Layer',
          type: obj.layerType || 'DECORATION',
          visible: obj.visible !== false,
          locked: obj.lockMovementX === true
        };
      });
    setLayers(updatedLayers);
  };

  // Zoom manipulation calculations
  function calculateFitZoom() {
    if (!workspaceRef.current) return;
    const containerHeight = workspaceRef.current.clientHeight - 48; // padding offset
    const containerWidth = workspaceRef.current.clientWidth - 48;
    if (containerHeight <= 0 || containerWidth <= 0) return;

    const fitZoomH = containerHeight / baseHeight;
    const fitZoomW = containerWidth / baseWidth;
    const fitZoom = Math.min(fitZoomH, fitZoomW); // Fit inside both dimensions
    
    setZoom(parseFloat(Math.max(0.01, fitZoom).toFixed(2)));
  }

  const createLayerId = () => {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return `layer_${crypto.randomUUID()}`;
    }
    return `layer_${Math.random().toString(36).substring(2, 15)}`;
  };

  const addImageLayerFromUrl = async (url: string, layerName: string) => {
    const fabricCanvas = fabricCanvasRef.current;
    if (!fabricCanvas) return;

    try {
      const img = await FabricImage.fromURL(url, { crossOrigin: 'anonymous' });
      img.set({
        left: baseWidth / 2,
        top: baseHeight / 2,
        originX: 'center',
        originY: 'center',
      });

      (img as any).layerId = createLayerId();
      (img as any).layerType = 'IMAGE';
      (img as any).layerName = layerName;
      (img as any).src = url;
      (img as any).defaultImageUrl = url;

      if (img.width && img.width > baseWidth * 0.8) {
        img.scaleToWidth(baseWidth * 0.8);
      } else if (img.height && img.height > baseHeight * 0.8) {
        img.scaleToHeight(baseHeight * 0.8);
      }

      fabricCanvas.add(img);
      fabricCanvas.setActiveObject(img);
      fabricCanvas.renderAll();
      pushState();
      setIsDirty(true);
      syncLayersList(fabricCanvas);
      useLayersStore.getState().setActiveObjectId((img as any).layerId);
      useLayersStore.getState().setActiveObjectProps({
        left: Math.round(img.left),
        top: Math.round(img.top),
        scaleX: img.scaleX,
        scaleY: img.scaleY,
        angle: img.angle,
        opacity: img.opacity,
        flipX: img.flipX,
        flipY: img.flipY,
        layerId: (img as any).layerId,
        layerType: 'IMAGE',
        layerName,
        src: url,
        defaultImageUrl: url,
        cropRatio: null,
        fill: null,
        rx: 0,
        ry: 0,
        stroke: null,
        strokeWidth: 0,
        imageFilters: {},
        shadow: null
      });
      toast.success('Đã tạo layer mới từ vùng cắt!');
    } catch (err) {
      console.error('Failed to add crop layer:', err);
      toast.error('Lỗi khi chèn layer mới vào canvas');
    }
  };

  const replaceImageLayerFromUrl = async (url: string) => {
    const fabricCanvas = fabricCanvasRef.current;
    if (!fabricCanvas) return;

    const activeObj = fabricCanvas.getActiveObject() as any;
    if (!activeObj || (activeObj.type !== 'image' && activeObj.layerType !== 'IMAGE')) {
      toast.error('Không tìm thấy layer ảnh đang chọn để thay thế.');
      return;
    }

    try {
      const newImg = await FabricImage.fromURL(url, { crossOrigin: 'anonymous' });
      // Preserve position and scale from the original object
      newImg.set({
        left: activeObj.left,
        top: activeObj.top,
        originX: activeObj.originX || 'left',
        originY: activeObj.originY || 'top',
        scaleX: activeObj.scaleX,
        scaleY: activeObj.scaleY,
        angle: activeObj.angle,
        opacity: activeObj.opacity,
        flipX: activeObj.flipX,
        flipY: activeObj.flipY,
      });

      (newImg as any).layerId = activeObj.layerId;
      (newImg as any).layerType = activeObj.layerType;
      (newImg as any).layerName = activeObj.layerName;
      (newImg as any).src = url;
      (newImg as any).defaultImageUrl = url;

      fabricCanvas.remove(activeObj);
      fabricCanvas.add(newImg);
      fabricCanvas.setActiveObject(newImg);
      fabricCanvas.renderAll();
      pushState();
      setIsDirty(true);
      syncLayersList(fabricCanvas);
      toast.success('Đã thay thế ảnh layer thành công!');
    } catch (err) {
      console.error('Failed to replace image layer:', err);
      toast.error('Lỗi khi thay thế ảnh. Vui lòng thử lại.');
    }
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

  // Helper to load layers onto Fabric canvas
  const loadTemplateLayers = async (canvasInstance: any) => {
    setLoadingLayers(true);

    const applyShadowIfPresent = (obj: any, payload: any) => {
      if (!payload) return;
      const hasShadow = (payload.shadowDistance !== undefined && payload.shadowDistance > 0) ||
                        (payload.shadowIntensity !== undefined && payload.shadowIntensity > 0.05) ||
                        (payload.shadowBlur !== undefined && payload.shadowBlur > 0);

      if (hasShadow) {
        const distance = payload.shadowDistance || 0;
        const angle = payload.shadowAngle || 45;
        const angleRad = (angle * Math.PI) / 180;
        const offsetX = Math.round(distance * Math.cos(angleRad));
        const offsetY = Math.round(distance * Math.sin(angleRad));

        const { rgba } = argbIntToRgba(payload.shadowColorArgb || -16777216, payload.shadowIntensity !== undefined ? payload.shadowIntensity : 0.4);

        obj.set('shadow', new Shadow({
          color: rgba,
          blur: payload.shadowBlur !== undefined ? payload.shadowBlur : 15,
          offsetX,
          offsetY,
        }));
      }
    };

    // Check if fabric_state exists in template (from previous builder session)
    if (template.fabric_state) {
      try {
        const state = typeof template.fabric_state === 'string'
          ? JSON.parse(template.fabric_state)
          : template.fabric_state;
        
        // Prevent tainted canvas: inject crossOrigin into serialized state objects
        if (state && state.objects) {
          state.objects.forEach((obj: any) => {
            if (obj.type === 'image' || obj.type === 'FabricImage' || obj.src) {
              obj.crossOrigin = 'anonymous';
            }
          });
        }
        
        if (canvasInstance.disposed) return;
        await canvasInstance.loadFromJSON(state);
        if (canvasInstance.disposed) return;
        if (!canvasInstance.backgroundColor) {
          canvasInstance.backgroundColor = '#ffffff';
        }

        // Restore template background image if missing in fabric_state
        const canvasData = template.canvas_data;
        if (!canvasInstance.backgroundImage && canvasData?.canvas?.backgroundUrl) {
          const bgImg = await FabricImage.fromURL(canvasData.canvas.backgroundUrl, {
            crossOrigin: 'anonymous'
          });
          if (!canvasInstance.disposed) {
            const scaleX = baseWidth / (bgImg.width || baseWidth);
            const scaleY = baseHeight / (bgImg.height || baseHeight);
            const bgScale = Math.max(scaleX, scaleY);

            bgImg.set({
              originX: 'left',
              originY: 'top',
              left: 0,
              top: 0,
              scaleX: bgScale,
              scaleY: bgScale,
              selectable: false,
              evented: false,
              hasControls: false,
              hasBorders: false,
            });
            (bgImg as any)._isBackground = true;
            (bgImg as any).layerId = 'background_layer';
            (bgImg as any).layerName = 'Background';
            canvasInstance.backgroundImage = bgImg;
          }
        }

        // Load fonts for all text objects loaded from JSON
        if (typeof document !== 'undefined') {
          canvasInstance.getObjects().forEach((obj: any) => {
            if ((obj.type === 'i-text' || obj.layerType === 'TEXT') && obj.fontFamily) {
              ensureFontLoaded(obj.fontFamily).then(() => {
                document.fonts.load(`12px "${obj.fontFamily}"`).then(() => {
                  canvasInstance.renderAll();
                }).catch(() => {});
              });
            }
          });
        }

        canvasInstance.renderAll();
        syncLayersList(canvasInstance);
        useEditorStore.getState().clearHistory();
        useEditorStore.getState().pushState();
        setLoadingLayers(false);
        return;
      } catch (err) {
        if (!canvasInstance.disposed) {
          console.error('Failed to load canvas from fabric_state, falling back to canvas_data:', err);
        }
      }
    }

    const canvasData = template.canvas_data;
    if (!canvasData) {
      setLoadingLayers(false);
      return;
    }

    try {
      // Load Background Image – use setBackgroundImage to avoid duplicate render
      if (canvasData.canvas?.backgroundUrl) {
        if (canvasInstance.disposed) return;
        const bgImg = await FabricImage.fromURL(canvasData.canvas.backgroundUrl, {
          crossOrigin: 'anonymous'
        });
        if (canvasInstance.disposed) return;

        // Scale to fill the full canvas area
        const scaleX = baseWidth  / (bgImg.width  || baseWidth);
        const scaleY = baseHeight / (bgImg.height || baseHeight);
        const bgScale = Math.max(scaleX, scaleY);

        bgImg.set({
          originX: 'left',
          originY: 'top',
          left: 0,
          top: 0,
          scaleX: bgScale,
          scaleY: bgScale,
          selectable: false,
          evented: false,
          hasControls: false,
          hasBorders: false,
        });
        (bgImg as any)._isBackground = true;
        (bgImg as any).layerId = 'background_layer';
        (bgImg as any).layerName = 'Background';

        // Fabric v7: set backgroundImage directly (setBackgroundImage removed in v7)
        canvasInstance.backgroundImage = bgImg;
        canvasInstance.renderAll();
      }

      // Load Layers in zIndex order
      const layers = [...(canvasData.layers || [])].sort((a, b) => a.zIndex - b.zIndex);
      
      for (const layer of layers) {
        const transform = layer.transform;
        const payload = layer.payload;

        const centerX = transform.anchorX * baseWidth;
        const centerY = transform.anchorY * baseHeight;
        const scale = transform.scale;
        const rotation = transform.rotation;
        const isVisible = payload.visible !== false;
        const isLocked = payload.locked === true;

        if (layer.type === 'TEXT') {
          const textFill = payload.textColorArgb !== undefined && payload.textColorArgb !== null
            ? argbIntToRgba(payload.textColorArgb).rgba
            : (payload.fill || '#1e1b4b');
          const textObj = new IText(payload.text || 'Text Layer', {
            left: centerX,
            top: centerY,
            originX: 'center',
            originY: 'center',
            fontFamily: payload.font || 'Outfit',
            fontSize: payload.fontSize || 60,
            fill: textFill,
            scaleX: scale,
            scaleY: scale,
            angle: rotation,
            opacity: payload.alpha !== undefined ? payload.alpha : 1.0,
            hasControls: true,
            hasBorders: true,
            fontWeight: payload.fontWeight || 'normal',
            fontStyle: payload.fontStyle || 'normal',
            underline: payload.underline || false,
            textAlign: payload.textAlign || 'left',
            lineHeight: payload.lineHeight || 1.16,
            charSpacing: payload.charSpacing || 0,
            textBackgroundColor: payload.textBackgroundColor || null,
            linethrough: payload.linethrough || false,
            textTransform: payload.textTransform || 'none',
            visible: isVisible,
            selectable: !isLocked,
            evented: !isLocked,
            _originalText: payload._originalText || payload.text || '',
            stroke: payload.stroke || null,
            strokeWidth: payload.strokeWidth || 0,
            strokeDashArray: payload.strokeDashArray || null,
            globalCompositeOperation: getCompositeOperation(payload.blendMode),
          });

          (textObj as any).layerId = layer.layerId;
          (textObj as any).layerType = 'TEXT';
          (textObj as any).layerName = layer.name || 'Text';
          (textObj as any).groupPath = payload.groupPath || null;
          (textObj as any).sourceKind = payload.sourceKind || null;
          (textObj as any).blendMode = payload.blendMode || 'normal';
          
          applyShadowIfPresent(textObj, payload);
          canvasInstance.add(textObj);

          if (payload.font && typeof document !== 'undefined') {
            ensureFontLoaded(payload.font).then(() => {
              document.fonts.load(`12px "${payload.font}"`).then(() => {
                canvasInstance.renderAll();
              }).catch(() => {});
            });
          }
        } else {
          // Check if it's a vector shape first
          const shapeType = payload.shapeType;
          if (shapeType) {
            try {
              let shapeObj: any;
              const commonShapeProps = {
                left: centerX,
                top: centerY,
                originX: 'center' as const,
                originY: 'center' as const,
                scaleX: scale,
                scaleY: scale,
                angle: rotation,
                opacity: payload.alpha !== undefined ? payload.alpha : 1.0,
                visible: isVisible,
                selectable: !isLocked,
                evented: !isLocked,
                fill: payload.fillColor || '#6366f1',
                hasControls: true,
                hasBorders: true,
                stroke: payload.stroke || null,
                strokeWidth: payload.strokeWidth || 0,
                strokeDashArray: payload.strokeDashArray || null,
                globalCompositeOperation: getCompositeOperation(payload.blendMode),
              };

              if (shapeType === 'rect') {
                shapeObj = new Rect({
                  ...commonShapeProps,
                  width: payload.baseWidth || 200,
                  height: payload.baseHeight || 200,
                  rx: payload.rx || 0,
                  ry: payload.ry || 0,
                });
              } else if (shapeType === 'circle') {
                shapeObj = new Circle({
                  ...commonShapeProps,
                  radius: (payload.baseWidth || 200) / 2,
                });
              } else if (shapeType === 'triangle') {
                shapeObj = new Triangle({
                  ...commonShapeProps,
                  width: payload.baseWidth || 200,
                  height: payload.baseHeight || 200,
                });
              } else if (shapeType === 'line') {
                shapeObj = new Line([-100, 0, 100, 0], {
                  ...commonShapeProps,
                  stroke: payload.fillColor || '#6366f1',
                  strokeWidth: payload.strokeWidth || 6,
                });
              } else if (shapeType === 'star') {
                shapeObj = new Polygon(getStarPoints(100, 40), {
                  ...commonShapeProps,
                  width: payload.baseWidth || 200,
                  height: payload.baseHeight || 200,
                });
              } else if (shapeType === 'diamond') {
                shapeObj = new Polygon(getDiamondPoints(100), {
                  ...commonShapeProps,
                  width: payload.baseWidth || 200,
                  height: payload.baseHeight || 200,
                });
              } else if (shapeType === 'hexagon') {
                shapeObj = new Polygon(getHexagonPoints(100), {
                  ...commonShapeProps,
                  width: payload.baseWidth || 200,
                  height: payload.baseHeight || 200,
                });
              } else if (shapeType === 'arrow') {
                shapeObj = new Path(arrowPath, {
                  ...commonShapeProps,
                  width: payload.baseWidth || 200,
                  height: payload.baseHeight || 120,
                });
              }

              if (shapeObj) {
                shapeObj.layerId = layer.layerId;
                shapeObj.layerType = layer.type || 'DECORATION';
                shapeObj.layerName = layer.name || 'Shape';
                shapeObj.shapeSubtype = shapeType;
                shapeObj.groupPath = payload.groupPath || null;
                shapeObj.sourceKind = payload.sourceKind || null;
                (shapeObj as any).blendMode = payload.blendMode || 'normal';
                applyShadowIfPresent(shapeObj, payload);
                canvasInstance.add(shapeObj);
                continue;
              }
            } catch (err) {
              console.error('Failed to load vector shape layer:', err);
            }
          }

          // Load Image / Decoration
          const imgUrl = payload.imageUrl || payload.defaultImageUrl;
          if (imgUrl) {
            try {
              if (canvasInstance.disposed) return;
              const imgObj = await FabricImage.fromURL(imgUrl, {
                crossOrigin: 'anonymous'
              });
              if (canvasInstance.disposed) return;

                imgObj.set({
                  left: centerX,
                  top: centerY,
                  originX: 'center',
                  originY: 'center',
                  scaleX: scale,
                  scaleY: scale,
                  angle: rotation,
                  opacity: payload.alpha !== undefined ? payload.alpha : 1.0,
                  flipX: payload.flippedH || false,
                  flipY: payload.flippedV || false,
                  visible: isVisible,
                  selectable: !isLocked,
                  evented: !isLocked,
                  hasControls: true,
                  hasBorders: true,
                  stroke: payload.stroke || null,
                  strokeWidth: payload.strokeWidth || 0,
                  strokeDashArray: payload.strokeDashArray || null,
                  globalCompositeOperation: getCompositeOperation(payload.blendMode),
                });

              (imgObj as any).layerId = layer.layerId;
              (imgObj as any).layerType = layer.type || 'DECORATION';
              (imgObj as any).layerName = layer.name || 'Image';
              (imgObj as any).src = payload.imageUrl;
              (imgObj as any).defaultImageUrl = payload.defaultImageUrl;
              (imgObj as any).cropRatio = payload.cropRatio || null;
              (imgObj as any).groupPath = payload.groupPath || null;
              (imgObj as any).sourceKind = payload.sourceKind || null;
              (imgObj as any).blendMode = payload.blendMode || 'normal';

              // Apply shadow if present
              applyShadowIfPresent(imgObj, payload);

              // Apply image filters if present
              if (payload.imageFilters) {
                if (canvasInstance.disposed) return;
                const { filters } = await import('fabric');
                if (canvasInstance.disposed) return;
                imgObj.filters = [];
                const filterState = payload.imageFilters || {};
                if (filterState.brightness !== undefined && filterState.brightness !== 0) {
                  imgObj.filters.push(new filters.Brightness({ brightness: filterState.brightness }));
                }
                if (filterState.contrast !== undefined && filterState.contrast !== 0) {
                  imgObj.filters.push(new filters.Contrast({ contrast: filterState.contrast }));
                }
                if (filterState.saturation !== undefined && filterState.saturation !== 0) {
                  imgObj.filters.push(new filters.Saturation({ saturation: filterState.saturation }));
                }
                if (filterState.blur !== undefined && filterState.blur !== 0) {
                  imgObj.filters.push(new filters.Blur({ blur: filterState.blur }));
                }
                if (filterState.grayscale) {
                  imgObj.filters.push(new filters.Grayscale());
                }
                if (filterState.sepia) {
                  imgObj.filters.push(new filters.Sepia());
                }
                if (filterState.toneColor) {
                  if (filterState.toneGrayscale) {
                    imgObj.filters.push(new filters.Grayscale());
                  }
                  imgObj.filters.push(new filters.BlendColor({
                    color: filterState.toneColor,
                    mode: 'multiply',
                    alpha: typeof filterState.toneAlpha === 'number' ? filterState.toneAlpha : 1
                  }));
                }
                imgObj.applyFilters();
                (imgObj as any).imageFilters = payload.imageFilters;
              }

              canvasInstance.add(imgObj);
            } catch (imgLoadErr) {
              console.error(`Failed to load image layer: ${imgUrl}`, imgLoadErr);
            }
          }
        }
      }

      canvasInstance.renderAll();
      syncLayersList(canvasInstance);
      useEditorStore.getState().clearHistory();
      useEditorStore.getState().pushState();
    } catch (err) {
      console.error('Error rendering template layers:', err);
    } finally {
      setLoadingLayers(false);
    }
  };

  const syncActiveObjectProps = (activeObj: any) => {
    if (!activeObj) {
      setActiveObjectProps(null);
      return;
    }
    const props = {
      left: Math.round(activeObj.left),
      top: Math.round(activeObj.top),
      scaleX: activeObj.scaleX,
      scaleY: activeObj.scaleY,
      angle: activeObj.angle,
      opacity: activeObj.opacity,
      flipX: activeObj.flipX,
      flipY: activeObj.flipY,
      layerId: activeObj.layerId,
      layerType: activeObj.layerType,
      layerName: activeObj.layerName,
      text: activeObj.text,
      fontFamily: activeObj.fontFamily,
      src: activeObj.src,
      defaultImageUrl: activeObj.defaultImageUrl,
      cropRatio: activeObj.cropRatio || null,
      fill: activeObj.fill || null,
      fontSize: activeObj.fontSize || null,
      fontWeight: activeObj.fontWeight || 'normal',
      fontStyle: activeObj.fontStyle || 'normal',
      underline: activeObj.underline || false,
      textAlign: activeObj.textAlign || 'left',
      lineHeight: activeObj.lineHeight || 1.16,
      charSpacing: activeObj.charSpacing || 0,
      rx: activeObj.rx || 0,
      ry: activeObj.ry || 0,
      stroke: activeObj.stroke || null,
      strokeWidth: activeObj.strokeWidth || 0,
      strokeDashArray: activeObj.strokeDashArray || null,
      imageFilters: activeObj.imageFilters || {},
      textBackgroundColor: activeObj.textBackgroundColor || null,
      linethrough: activeObj.linethrough || false,
      textTransform: activeObj.textTransform || 'none',
      _originalText: activeObj._originalText || activeObj.text || '',
      blendMode: activeObj.blendMode || 'normal',
      globalCompositeOperation: activeObj.globalCompositeOperation || 'source-over',
      shadow: activeObj.shadow ? {
        color: activeObj.shadow.color,
        blur: activeObj.shadow.blur,
        offsetX: activeObj.shadow.offsetX,
        offsetY: activeObj.shadow.offsetY
      } : null
    };
    setActiveObjectProps(props);
  };

  // 1. Initialize Fabric.js Canvas
  useEffect(() => {
    if (!canvasRef.current || !containerRef.current) return;

    let fabricCanvas: any = null;
    let isAborted = false;

    const handleUpdateToolbar = () => {
      updateFloatingToolbar(fabricCanvas || fabricCanvasRef.current);
    };

    // Bind Event Listeners
    const onSelect = (e: any) => {
      const currentCanvas = fabricCanvas || fabricCanvasRef.current;
      if (!currentCanvas) return;
      const selectedObj = e.selected?.[0] || currentCanvas.getActiveObject();
      if (!selectedObj) return;

      const objAny = selectedObj as any;
      setActiveObjectId(objAny.layerId || null);
      syncActiveObjectProps(selectedObj);
    };

    const onClear = () => {
      setActiveObjectId(null);
      setActiveObjectProps(null);
    };

    const onModify = () => {
      pushState();
      setIsDirty(true);
      const currentCanvas = fabricCanvas || fabricCanvasRef.current;
      if (currentCanvas) {
        syncLayersList(currentCanvas);
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

    const handleObjectMoving = (e: any) => {
      if (!snappingEnabledRef.current) return;
      const obj = e.target;
      if (!obj) return;

      const tolerance = 15; // pixels
      let snapX: number | null = null;
      let snapY: number | null = null;

      const objCenter = obj.getCenterPoint();
      const objWidth = obj.width * obj.scaleX;
      const objHeight = obj.height * obj.scaleY;

      const centerX = baseWidth / 2;
      const centerY = baseHeight / 2;

      if (Math.abs(objCenter.x - centerX) < tolerance) {
        snapX = centerX;
        obj.setPositionByOrigin({ x: centerX, y: objCenter.y }, 'center', 'center');
      }

      if (Math.abs(objCenter.y - centerY) < tolerance) {
        snapY = centerY;
        obj.setPositionByOrigin({ x: snapX !== null ? centerX : objCenter.x, y: centerY }, 'center', 'center');
      }

      const objLeft = objCenter.x - objWidth / 2;
      const objRight = objCenter.x + objWidth / 2;
      const objTop = objCenter.y - objHeight / 2;
      const objBottom = objCenter.y + objHeight / 2;

      if (snapX === null) {
        if (Math.abs(objLeft - 0) < tolerance) {
          snapX = objWidth / 2;
          obj.setPositionByOrigin({ x: snapX, y: objCenter.y }, 'center', 'center');
          snapX = 0;
        } else if (Math.abs(objRight - baseWidth) < tolerance) {
          snapX = baseWidth - objWidth / 2;
          obj.setPositionByOrigin({ x: snapX, y: objCenter.y }, 'center', 'center');
          snapX = baseWidth;
        }
      }

      if (snapY === null) {
        if (Math.abs(objTop - 0) < tolerance) {
          snapY = objHeight / 2;
          obj.setPositionByOrigin({ x: objCenter.x, y: snapY }, 'center', 'center');
          snapY = 0;
        } else if (Math.abs(objBottom - baseHeight) < tolerance) {
          snapY = baseHeight - objHeight / 2;
          obj.setPositionByOrigin({ x: objCenter.x, y: snapY }, 'center', 'center');
          snapY = baseHeight;
        }
      }

      // Snap to user guides
      const currentGuides = guidesRef.current || [];
      currentGuides.forEach((guide) => {
        const curCenter = obj.getCenterPoint();
        if (guide.type === 'v') {
          const curLeft = curCenter.x - objWidth / 2;
          const curRight = curCenter.x + objWidth / 2;
          
          if (Math.abs(curCenter.x - guide.position) < tolerance) {
            snapX = guide.position;
            obj.setPositionByOrigin({ x: guide.position, y: curCenter.y }, 'center', 'center');
          } else if (Math.abs(curLeft - guide.position) < tolerance) {
            snapX = guide.position;
            obj.setPositionByOrigin({ x: guide.position + objWidth / 2, y: curCenter.y }, 'center', 'center');
          } else if (Math.abs(curRight - guide.position) < tolerance) {
            snapX = guide.position;
            obj.setPositionByOrigin({ x: guide.position - objWidth / 2, y: curCenter.y }, 'center', 'center');
          }
        } else if (guide.type === 'h') {
          const curTop = curCenter.y - objHeight / 2;
          const curBottom = curCenter.y + objHeight / 2;

          if (Math.abs(curCenter.y - guide.position) < tolerance) {
            snapY = guide.position;
            obj.setPositionByOrigin({ x: curCenter.x, y: guide.position }, 'center', 'center');
          } else if (Math.abs(curTop - guide.position) < tolerance) {
            snapY = guide.position;
            obj.setPositionByOrigin({ x: curCenter.x, y: guide.position + objHeight / 2 }, 'center', 'center');
          } else if (Math.abs(curBottom - guide.position) < tolerance) {
            snapY = guide.position;
            obj.setPositionByOrigin({ x: curCenter.x, y: guide.position - objHeight / 2 }, 'center', 'center');
          }
        }
      });

      setGuideX(snapX);
      setGuideY(snapY);
    };

    const clearGuides = () => {
      setGuideX(null);
      setGuideY(null);
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
        updateFloatingToolbar(currentCanvas);
      }
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
      
      newCanvas.on('object:moving', handleObjectMoving);
      newCanvas.on('object:modified', clearGuides);
      newCanvas.on('mouse:up', clearGuides);
      newCanvas.on('selection:cleared', clearGuides);

      newCanvas.on('selection:created', handleUpdateToolbar);
      newCanvas.on('selection:updated', handleUpdateToolbar);
      newCanvas.on('selection:cleared', handleUpdateToolbar);
      newCanvas.on('object:moving', handleUpdateToolbar);
      newCanvas.on('object:scaling', handleUpdateToolbar);
      newCanvas.on('object:rotating', handleUpdateToolbar);
      newCanvas.on('object:modified', handleUpdateToolbar);

      // Initial Zoom
      calculateFitZoom();

      // Load layers
      loadTemplateLayers(newCanvas);

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
      
      canvasInstance.off('object:moving', handleObjectMoving);
      canvasInstance.off('object:modified', clearGuides);
      canvasInstance.off('mouse:up', clearGuides);
      canvasInstance.off('selection:cleared', clearGuides);

      canvasInstance.off('selection:created', handleUpdateToolbar);
      canvasInstance.off('selection:updated', handleUpdateToolbar);
      canvasInstance.off('selection:cleared', handleUpdateToolbar);
      canvasInstance.off('object:moving', handleUpdateToolbar);
      canvasInstance.off('object:scaling', handleUpdateToolbar);
      canvasInstance.off('object:rotating', handleUpdateToolbar);
      canvasInstance.off('object:modified', handleUpdateToolbar);

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

  const handleZoom = (factor: number) => {
    setZoom((prev) => {
      const val = parseFloat((prev * factor).toFixed(2));
      return Math.max(0.1, Math.min(val, 3.0));
    });
  };

  const addTextToCanvas = () => {
    const fabricCanvas = fabricCanvasRef.current;
    if (!fabricCanvas) return;

    const textObj = new IText('Nhập chữ...', {
      left: baseWidth / 2,
      top: baseHeight / 2,
      originX: 'center',
      originY: 'center',
      fontFamily: 'Outfit, sans-serif',
      fontSize: 80,
      fill: '#6366f1',
      hasControls: true,
      hasBorders: true,
      selectable: true,
      fontWeight: 'normal',
      fontStyle: 'normal',
      underline: false,
      textAlign: 'left',
      lineHeight: 1.16,
      charSpacing: 0,
    });

    (textObj as any).layerId = `layer_${Date.now()}`;
    (textObj as any).layerType = 'TEXT';
    (textObj as any).layerName = 'Text Layer';

    fabricCanvas.add(textObj);
    fabricCanvas.setActiveObject(textObj);
    fabricCanvas.renderAll();
    pushState();
    setIsDirty(true);
    syncLayersList(fabricCanvas);
    toast.success('Đã thêm layer chữ!');
  };

  const addShapeToCanvas = (type: 'rect' | 'circle' | 'triangle' | 'line' | 'star' | 'arrow' | 'diamond' | 'hexagon') => {
    const fabricCanvas = fabricCanvasRef.current;
    if (!fabricCanvas) return;

    let shape: any;
    const commonProps = {
      left: baseWidth / 2,
      top: baseHeight / 2,
      originX: 'center' as const,
      originY: 'center' as const,
      fill: '#6366f1',
      hasControls: true,
      hasBorders: true,
      selectable: true,
    };

    if (type === 'rect') {
      shape = new Rect({ ...commonProps, width: 200, height: 200 });
    } else if (type === 'circle') {
      shape = new Circle({ ...commonProps, radius: 100 });
    } else if (type === 'triangle') {
      shape = new Triangle({ ...commonProps, width: 200, height: 200 });
    } else if (type === 'line') {
      shape = new Line([-100, 0, 100, 0], {
        ...commonProps,
        stroke: '#6366f1',
        strokeWidth: 6,
      });
    } else if (type === 'star') {
      shape = new Polygon(getStarPoints(100, 40), {
        ...commonProps,
        width: 200,
        height: 200,
      });
    } else if (type === 'diamond') {
      shape = new Polygon(getDiamondPoints(100), {
        ...commonProps,
        width: 200,
        height: 200,
      });
    } else if (type === 'hexagon') {
      shape = new Polygon(getHexagonPoints(100), {
        ...commonProps,
        width: 200,
        height: 200,
      });
    } else if (type === 'arrow') {
      shape = new Path(arrowPath, {
        ...commonProps,
        width: 200,
        height: 120,
      });
    }

    shape.layerId = `layer_${Date.now()}`;
    shape.layerType = 'DECORATION';
    shape.shapeSubtype = type;
    
    const shapeNames: Record<string, string> = {
      rect: 'Hình chữ nhật',
      circle: 'Hình tròn',
      triangle: 'Hình tam giác',
      line: 'Đường thẳng',
      star: 'Hình ngôi sao',
      diamond: 'Hình thoi',
      hexagon: 'Hình lục giác',
      arrow: 'Hình mũi tên',
    };
    shape.layerName = `${shapeNames[type] || 'Shape'} Layer`;

    fabricCanvas.add(shape);
    fabricCanvas.setActiveObject(shape);
    fabricCanvas.renderAll();
    pushState();
    setIsDirty(true);
    syncLayersList(fabricCanvas);
    toast.success(`Đã thêm hình dạng: ${shapeNames[type] || type}`);
  };

  const addImageFromUrl = async (url: string) => {
    if (!url) return;
    const fabricCanvas = fabricCanvasRef.current;
    if (!fabricCanvas) return;

    try {
      const img = await FabricImage.fromURL(url, {
        crossOrigin: 'anonymous'
      });

      img.set({
        left: baseWidth / 2,
        top: baseHeight / 2,
        originX: 'center',
        originY: 'center',
      });

      (img as any).layerId = `layer_${Date.now()}`;
      (img as any).layerType = 'IMAGE';
      (img as any).layerName = 'Image Layer';
      (img as any).src = url;
      (img as any).defaultImageUrl = url;

      if (img.width && img.width > baseWidth * 0.5) {
        img.scaleToWidth(baseWidth * 0.5);
      }

      fabricCanvas.add(img);
      fabricCanvas.setActiveObject(img);
      fabricCanvas.renderAll();
      pushState();
      setIsDirty(true);
      syncLayersList(fabricCanvas);
      setImageUrl('');
      setIsImageUrlOpen(false);
      toast.success('Đã thêm ảnh từ URL!');
    } catch (err: any) {
      toast.error('Không thể chèn ảnh từ URL này. Hãy kiểm tra lại liên kết.');
    }
  };

  const addDroppedImageToCanvas = async (asset: DroppedAsset, x: number, y: number) => {
    const fabricCanvas = fabricCanvasRef.current;
    if (!fabricCanvas) return;

    const url = asset.file_url || asset.fileUrl;
    if (!url) return;

    try {
      const img = await FabricImage.fromURL(url, {
        crossOrigin: 'anonymous'
      });

      img.set({
        left: x,
        top: y,
        originX: 'center',
        originY: 'center',
      });

      (img as any).layerId = `layer_${Date.now()}`;
      (img as any).layerType = asset.folder === 'backgrounds' ? 'DECORATION' : 'IMAGE';
      (img as any).layerName = asset.name || 'Dropped Asset';
      (img as any).src = url;
      (img as any).defaultImageUrl = url;

      if (img.width && img.width > baseWidth * 0.5) {
        img.scaleToWidth(baseWidth * 0.5);
      }

      fabricCanvas.add(img);
      fabricCanvas.setActiveObject(img);
      fabricCanvas.renderAll();
      pushState();
      setIsDirty(true);
      syncLayersList(fabricCanvas);
    } catch (err) {
      console.error('Failed to add dropped asset:', err);
      toast.error('Không thể thả ảnh vào canvas. Vui lòng thử lại.');
    }
  };

  const handleCanvasDrop = async (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();

    const fabricCanvas = fabricCanvasRef.current;
    if (!fabricCanvas || !canvasRef.current) return;

    const raw = event.dataTransfer.getData('application/json') || event.dataTransfer.getData('text/plain');
    if (!raw) return;

    let asset: DroppedAsset | null = null;
    try {
      asset = JSON.parse(raw);
    } catch {
      asset = { file_url: raw };
    }

    if (!asset) return;

    const rect = canvasRef.current.getBoundingClientRect();
    if (!rect.width || !rect.height) return;

    const canvasWidth = fabricCanvas.getWidth() || baseWidth;
    const canvasHeight = fabricCanvas.getHeight() || baseHeight;
    const x = ((event.clientX - rect.left) / rect.width) * canvasWidth;
    const y = ((event.clientY - rect.top) / rect.height) * canvasHeight;

    // Detect if we dropped over an existing image layer
    const pointer = new Point(x, y);
    const objects = fabricCanvas.getObjects();
    let targetImageObj: any = null;

    for (let i = objects.length - 1; i >= 0; i--) {
      const obj = objects[i];
      if (obj._isBackground) continue;
      if (obj.type === 'image' || (obj as any).layerType === 'IMAGE') {
        if (obj.containsPoint(pointer)) {
          targetImageObj = obj;
          break;
        }
      }
    }

    if (targetImageObj) {
      const url = asset.file_url || asset.fileUrl;
      if (url) {
        let loadingToast: any = null;
        try {
          loadingToast = toast.loading('Đang thay thế hình ảnh layer...');
          const newImg = await FabricImage.fromURL(url, { crossOrigin: 'anonymous' });
          targetImageObj.setElement(newImg._element || newImg.getElement());
          targetImageObj.set({
            src: url,
            defaultImageUrl: url,
          });
          targetImageObj.applyFilters();
          fabricCanvas.renderAll();

          // Sync active properties if this is the active object
          const activeObjId = useLayersStore.getState().activeObjectId;
          if (targetImageObj.layerId === activeObjId) {
            useLayersStore.getState().updateActiveObject({
              src: url,
              defaultImageUrl: url,
            });
          }

          pushState();
          setIsDirty(true);
          syncLayersList(fabricCanvas);
          toast.dismiss(loadingToast);
          toast.success('Đã thay thế ảnh layer thành công!');
          return;
        } catch (err) {
          console.error(err);
          if (loadingToast) toast.dismiss(loadingToast);
          toast.error('Lỗi khi thay thế ảnh layer.');
          return;
        }
      }
    }

    await addDroppedImageToCanvas(asset, x, y);
  };

  return (
    <div className="flex flex-col h-full bg-slate-100 overflow-hidden relative" ref={containerRef}>
      
      {/* Toolbar Component */}
      <EditorToolbar
        zoom={zoom}
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
        className="flex-1 min-h-0 overflow-auto flex items-center justify-center bg-slate-100 relative"
        style={{ 
          padding: '48px', // Increased padding to fit rulers nicely
          backgroundImage: 'radial-gradient(circle at 2px 2px, rgba(99, 102, 241, 0.1) 1px, transparent 0)',
          backgroundSize: '24px 24px',
        }}
        onDragOver={(e) => e.preventDefault()}
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

            {/* Quick Action Contextual Floating Toolbar */}
            {toolbarPosition?.visible && (
              <div
                style={{
                  position: 'absolute',
                  left: `${toolbarPosition.left}px`,
                  top: `${toolbarPosition.top}px`,
                  transform: 'translateX(-50%)',
                  zIndex: 40,
                }}
                className="flex items-center gap-1 p-1 bg-white/95 border border-slate-200/90 rounded-2xl shadow-xl backdrop-blur-md animate-in fade-in slide-in-from-top-1 duration-150 pointer-events-auto"
              >
                <Button
                  size="icon"
                  variant="ghost"
                  onClick={() => {
                    const canvasInstance = fabricCanvasRef.current;
                    const activeObj = canvasInstance?.getActiveObject();
                    if (activeObj) {
                      activeObj.clone().then((cloned: any) => {
                        canvasInstance.discardActiveObject();
                        cloned.set({
                          left: activeObj.left + 30,
                          top: activeObj.top + 30,
                          layerId: `layer_${Date.now()}`,
                          layerName: `${activeObj.layerName || 'Layer'} Copy`,
                          selectable: true,
                        });
                        canvasInstance.add(cloned);
                        canvasInstance.setActiveObject(cloned);
                        canvasInstance.renderAll();
                        pushState();
                        setIsDirty(true);
                        syncLayersList(canvasInstance);
                        updateFloatingToolbar(canvasInstance);
                        toast.success('Đã nhân đôi đối tượng!');
                      });
                    }
                  }}
                  className="w-7 h-7 hover:bg-slate-100 hover:text-indigo-400 text-slate-400 rounded-xl transition-all"
                  title="Nhân đôi (Ctrl+D)"
                >
                  <Copy className="w-3.5 h-3.5" />
                </Button>

                <Button
                  size="icon"
                  variant="ghost"
                  onClick={() => {
                    copyToClipboard();
                  }}
                  className="w-7 h-7 hover:bg-slate-100 hover:text-indigo-400 text-slate-400 rounded-xl transition-all"
                  title="Sao chép (Ctrl+C)"
                >
                  <Clipboard className="w-3.5 h-3.5" />
                </Button>

                <Button
                  size="icon"
                  variant="ghost"
                  onClick={() => {
                    const canvasInstance = fabricCanvasRef.current;
                    const activeObj = canvasInstance?.getActiveObject();
                    if (activeObj) {
                      canvasInstance.bringForward(activeObj);
                      canvasInstance.renderAll();
                      pushState();
                      setIsDirty(true);
                      syncLayersList(canvasInstance);
                      toast.success('Đã đưa lên 1 lớp!');
                    }
                  }}
                  className="w-7 h-7 hover:bg-slate-100 hover:text-indigo-400 text-slate-400 rounded-xl transition-all"
                  title="Đưa lên 1 lớp"
                >
                  <ChevronUp className="w-3.5 h-3.5" />
                </Button>

                <Button
                  size="icon"
                  variant="ghost"
                  onClick={() => {
                    const canvasInstance = fabricCanvasRef.current;
                    const activeObj = canvasInstance?.getActiveObject();
                    if (activeObj) {
                      const objects = canvasInstance.getObjects();
                      const bgObj = objects.find((o: any) => o._isBackground);
                      const activeIndex = objects.indexOf(activeObj);
                      if (bgObj && activeIndex <= objects.indexOf(bgObj) + 1) {
                        toast.warning('Đối tượng đã ở dưới cùng sát ảnh nền.');
                      } else {
                        canvasInstance.sendBackwards(activeObj);
                        canvasInstance.renderAll();
                        pushState();
                        setIsDirty(true);
                        syncLayersList(canvasInstance);
                        toast.success('Đã giảm xuống 1 lớp!');
                      }
                    }
                  }}
                  className="w-7 h-7 hover:bg-slate-100 hover:text-indigo-400 text-slate-400 rounded-xl transition-all"
                  title="Đưa xuống 1 lớp"
                >
                  <ChevronDown className="w-3.5 h-3.5" />
                </Button>

                <Button
                  size="icon"
                  variant="ghost"
                  onClick={() => {
                    const canvasInstance = fabricCanvasRef.current;
                    const activeObj = canvasInstance?.getActiveObject();
                    if (activeObj) {
                      activeObj.set({ flipX: !activeObj.flipX });
                      canvasInstance.renderAll();
                      pushState();
                      setIsDirty(true);
                      syncActiveObjectProps(activeObj);
                      toast.success('Đã lật ngang đối tượng!');
                    }
                  }}
                  className="w-7 h-7 hover:bg-slate-100 hover:text-indigo-400 text-slate-400 rounded-xl transition-all"
                  title="Lật ngang"
                >
                  <FlipHorizontal className="w-3.5 h-3.5" />
                </Button>

                <Button
                  size="icon"
                  variant="ghost"
                  onClick={() => {
                    const canvasInstance = fabricCanvasRef.current;
                    const activeObj = canvasInstance?.getActiveObject();
                    if (activeObj) {
                      activeObj.set({ flipY: !activeObj.flipY });
                      canvasInstance.renderAll();
                      pushState();
                      setIsDirty(true);
                      syncActiveObjectProps(activeObj);
                      toast.success('Đã lật dọc đối tượng!');
                    }
                  }}
                  className="w-7 h-7 hover:bg-slate-100 hover:text-indigo-400 text-slate-400 rounded-xl transition-all"
                  title="Lật dọc"
                >
                  <FlipVertical className="w-3.5 h-3.5" />
                </Button>

                <Button
                  size="icon"
                  variant="ghost"
                  onClick={() => {
                    const canvasInstance = fabricCanvasRef.current;
                    const activeObj = canvasInstance?.getActiveObject();
                    if (activeObj) {
                      const isLocked = activeObj.lockMovementX === true;
                      activeObj.set({
                        lockMovementX: !isLocked,
                        lockMovementY: !isLocked,
                        lockScalingX: !isLocked,
                        lockScalingY: !isLocked,
                        lockRotation: !isLocked,
                        hasControls: isLocked,
                      });
                      canvasInstance.renderAll();
                      pushState();
                      setIsDirty(true);
                      syncActiveObjectProps(activeObj);
                      syncLayersList(canvasInstance);
                      toast.success(isLocked ? 'Đã mở khóa đối tượng!' : 'Đã khóa đối tượng!');
                    }
                  }}
                  className="w-7 h-7 hover:bg-slate-100 hover:text-indigo-400 text-slate-400 rounded-xl transition-all"
                  title={fabricCanvasRef.current?.getActiveObject()?.lockMovementX === true ? 'Mở khóa' : 'Khóa'}
                >
                  {fabricCanvasRef.current?.getActiveObject()?.lockMovementX === true ? (
                    <Lock className="w-3.5 h-3.5 text-amber-500" />
                  ) : (
                    <Unlock className="w-3.5 h-3.5" />
                  )}
                </Button>

                <div className="w-px h-4 bg-slate-100 mx-0.5" />

                <Button
                  size="icon"
                  variant="ghost"
                  onClick={() => {
                    const canvasInstance = fabricCanvasRef.current;
                    const activeObj = canvasInstance?.getActiveObject();
                    if (activeObj) {
                      canvasInstance.remove(activeObj);
                      canvasInstance.discardActiveObject();
                      canvasInstance.renderAll();
                      pushState();
                      setIsDirty(true);
                      syncLayersList(canvasInstance);
                      toast.success('Đã xóa đối tượng!');
                    }
                  }}
                  className="w-7 h-7 hover:bg-rose-500/20 text-rose-400 rounded-xl transition-all"
                  title="Xóa đối tượng (Delete)"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                </Button>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Custom Context Menu */}
      {contextMenu?.visible && (
        <div
          style={{
            position: 'absolute',
            left: `${contextMenu.x}px`,
            top: `${contextMenu.y}px`,
            zIndex: 100
          }}
          className="w-56 p-1.5 bg-white/95 backdrop-blur-md border border-slate-200/80 rounded-2xl shadow-xl animate-in fade-in zoom-in-95 duration-100 flex flex-col select-none text-slate-400"
          onClick={(e) => e.stopPropagation()} // Tránh đóng menu ngay khi bấm bên trong
        >
          {/* Nếu click trúng hoặc đang chọn một đối tượng (không phải background) */}
          {fabricCanvasRef.current?.getActiveObject() && !fabricCanvasRef.current?.getActiveObject()?._isBackground ? (
            <>
              <button
                onClick={() => {
                  copyToClipboard();
                  setContextMenu(null);
                }}
                className="flex items-center justify-between px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
              >
                <span className="flex items-center gap-2">
                  <Copy className="w-3.5 h-3.5" /> Sao chép
                </span>
                <span className="text-[10px] text-slate-500 font-mono">Ctrl+C</span>
              </button>
              
              <button
                onClick={() => {
                  const canvasInstance = fabricCanvasRef.current;
                  const activeObj = canvasInstance?.getActiveObject();
                  if (activeObj) {
                    activeObj.clone().then((cloned: any) => {
                      canvasInstance.discardActiveObject();
                      cloned.set({
                        left: activeObj.left + 30,
                        top: activeObj.top + 30,
                        layerId: `layer_${Date.now()}`,
                        layerName: `${activeObj.layerName || 'Layer'} Copy`,
                        selectable: true,
                      });
                      canvasInstance.add(cloned);
                      canvasInstance.setActiveObject(cloned);
                      canvasInstance.renderAll();
                      pushState();
                      setIsDirty(true);
                      syncLayersList(canvasInstance);
                      toast.success('Đã nhân đôi layer!');
                    });
                  }
                  setContextMenu(null);
                }}
                className="flex items-center justify-between px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
              >
                <span className="flex items-center gap-2">
                  <Copy className="w-3.5 h-3.5" /> Nhân đôi
                </span>
                <span className="text-[10px] text-slate-500 font-mono">Ctrl+D</span>
              </button>

              <button
                onClick={() => {
                  const canvasInstance = fabricCanvasRef.current;
                  const activeObj = canvasInstance?.getActiveObject();
                  if (activeObj) {
                    canvasInstance.remove(activeObj);
                    canvasInstance.discardActiveObject();
                    canvasInstance.renderAll();
                    pushState();
                    setIsDirty(true);
                    syncLayersList(canvasInstance);
                    toast.success('Đã xóa layer!');
                  }
                  setContextMenu(null);
                }}
                className="flex items-center justify-between px-3 py-1.5 hover:bg-rose-500/10 hover:text-rose-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
              >
                <span className="flex items-center gap-2">
                  <Trash2 className="w-3.5 h-3.5" /> Xóa
                </span>
                <span className="text-[10px] text-rose-500/50 font-mono">Del</span>
              </button>

              {/* Tính năng Nhóm & Rã nhóm */}
              {fabricCanvasRef.current?.getActiveObject()?.type === 'activeselection' && (
                <button
                  onClick={() => {
                    groupSelection();
                    setIsDirty(true);
                    setContextMenu(null);
                  }}
                  className="flex items-center justify-between px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
                >
                  <span className="flex items-center gap-2">
                    <Layers className="w-3.5 h-3.5" /> Nhóm lại (Group)
                  </span>
                  <span className="text-[10px] text-slate-500 font-mono">Ctrl+G</span>
                </button>
              )}

              {fabricCanvasRef.current?.getActiveObject()?.type === 'group' && (
                <button
                  onClick={() => {
                    ungroupSelection();
                    setIsDirty(true);
                    setContextMenu(null);
                  }}
                  className="flex items-center justify-between px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
                >
                  <span className="flex items-center gap-2">
                    <Layers className="w-3.5 h-3.5" /> Rã nhóm (Ungroup)
                  </span>
                  <span className="text-[10px] text-slate-500 font-mono">Ctrl+Shift+G</span>
                </button>
              )}

              <div className="my-1 border-t border-slate-200/60" />

              {/* Sắp xếp thứ tự các layer */}
              <button
                onClick={() => {
                  const canvasInstance = fabricCanvasRef.current;
                  const activeObj = canvasInstance?.getActiveObject();
                  if (activeObj) {
                    canvasInstance.bringToFront(activeObj);
                    canvasInstance.renderAll();
                    pushState();
                    setIsDirty(true);
                    syncLayersList(canvasInstance);
                    toast.success('Đã chuyển lên trên cùng!');
                  }
                  setContextMenu(null);
                }}
                className="flex items-center gap-2 px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
              >
                <ChevronUp className="w-3.5 h-3.5" /> Đưa lên trên cùng
              </button>

              <button
                onClick={() => {
                  const canvasInstance = fabricCanvasRef.current;
                  const activeObj = canvasInstance?.getActiveObject();
                  if (activeObj) {
                    canvasInstance.bringForward(activeObj);
                    canvasInstance.renderAll();
                    pushState();
                    setIsDirty(true);
                    syncLayersList(canvasInstance);
                  }
                  setContextMenu(null);
                }}
                className="flex items-center gap-2 px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
              >
                <ChevronUp className="w-3.5 h-3.5" /> Tăng 1 lớp
              </button>

              <button
                onClick={() => {
                  const canvasInstance = fabricCanvasRef.current;
                  const activeObj = canvasInstance?.getActiveObject();
                  if (activeObj) {
                    const objects = canvasInstance.getObjects();
                    const bgObj = objects.find((o: any) => o._isBackground);
                    const activeIndex = objects.indexOf(activeObj);
                    if (bgObj && activeIndex <= objects.indexOf(bgObj) + 1) {
                      // Không giảm thêm lớp nếu đã nằm ngay trên ảnh nền background
                    } else {
                      canvasInstance.sendBackwards(activeObj);
                    }
                    canvasInstance.renderAll();
                    pushState();
                    setIsDirty(true);
                    syncLayersList(canvasInstance);
                  }
                  setContextMenu(null);
                }}
                className="flex items-center gap-2 px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
              >
                <ChevronDown className="w-3.5 h-3.5" /> Giảm 1 lớp
              </button>

              <button
                onClick={() => {
                  const canvasInstance = fabricCanvasRef.current;
                  const activeObj = canvasInstance?.getActiveObject();
                  if (activeObj) {
                    const objects = canvasInstance.getObjects();
                    const bgObj = objects.find((o: any) => o._isBackground);
                    if (bgObj) {
                      const bgIndex = objects.indexOf(bgObj);
                      activeObj.moveTo(bgIndex + 1);
                    } else {
                      canvasInstance.sendToBack(activeObj);
                    }
                    canvasInstance.renderAll();
                    pushState();
                    setIsDirty(true);
                    syncLayersList(canvasInstance);
                    toast.success('Đã chuyển xuống dưới cùng!');
                  }
                  setContextMenu(null);
                }}
                className="flex items-center gap-2 px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
              >
                <ChevronDown className="w-3.5 h-3.5" /> Đưa xuống dưới cùng
              </button>
            </>
          ) : (
            <>
              {/* Menu khoảng trống (Empty click) */}
              <button
                disabled={!clipboard}
                onClick={() => {
                  pasteFromClipboard();
                  setIsDirty(true);
                  setContextMenu(null);
                }}
                className="flex items-center justify-between px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent"
              >
                <span className="flex items-center gap-2">
                  <Clipboard className="w-3.5 h-3.5" /> Dán đối tượng
                </span>
                <span className="text-[10px] text-slate-500 font-mono">Ctrl+V</span>
              </button>

              <div className="my-1 border-t border-slate-200/60" />

              <button
                onClick={() => {
                  addTextToCanvas();
                  setContextMenu(null);
                }}
                className="flex items-center gap-2 px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
              >
                <Type className="w-3.5 h-3.5" /> Thêm văn bản
              </button>

              <button
                onClick={() => {
                  addShapeToCanvas('rect');
                  setContextMenu(null);
                }}
                className="flex items-center gap-2 px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
              >
                <Layers className="w-3.5 h-3.5" /> Thêm hình vuông
              </button>

              <button
                onClick={() => {
                  addShapeToCanvas('circle');
                  setContextMenu(null);
                }}
                className="flex items-center gap-2 px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
              >
                <Layers className="w-3.5 h-3.5" /> Thêm hình tròn
              </button>

              <button
                onClick={() => {
                  setIsImageUrlOpen(true);
                  setContextMenu(null);
                }}
                className="flex items-center gap-2 px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
              >
                <Link className="w-3.5 h-3.5" /> Chèn ảnh từ URL
              </button>
            </>
          )}
        </div>
      )}

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
