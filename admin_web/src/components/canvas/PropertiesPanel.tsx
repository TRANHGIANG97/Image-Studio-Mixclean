'use client';

import React, { useState, useCallback } from 'react';
import { ActiveSelection } from 'fabric';
import {
  Sliders, Trash2, FlipHorizontal, FlipVertical,
  Maximize2, AlignHorizontalSpaceAround, Settings,
  ChevronRight, EyeOff, Image as ImageIcon, Palette,
  Bold, Italic, Underline, AlignLeft, AlignCenter, AlignRight,
  Strikethrough, ChevronDown, Pipette, Copy, Crop as CropIcon, MoonStar, Sparkles, RefreshCw
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useEditorStore } from '@/store/editor.store';
import { useLayersStore } from '@/store/layers.store';
import { useCropStore } from '@/store/crop.store';
import { toast } from 'sonner';
import { CollapsibleSection } from './properties/CollapsibleSection';
import { useRecentColors, PRESET_COLORS } from './properties/useRecentColors';
import { getGradientAngle, rgbaToHex, hexToRgba } from './properties/color-utils';
import { ensureFontLoaded } from '@/lib/font-loader';
import { removeImageBackground } from '@/lib/background-remover';


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

// ==================== Quick Color Palette ====================

// ==================== Main Component ====================
export default function PropertiesPanel() {
  const { canvas, pushState, copyToClipboard, copyStyle, pasteStyle, copiedStyle } = useEditorStore();
  const { activeObjectId, activeObjectProps, updateActiveObject } = useLayersStore();
  const { openCrop } = useCropStore();
  const [dynamicFonts, setDynamicFonts] = useState<{ name: string; family_slug: string; font_url: string; style?: string }[]>([]);
  const [bgUrlInput, setBgUrlInput] = useState('');
  const { recentColors, addRecentColor } = useRecentColors();

  const [customPresets, setCustomPresets] = useState<{ id: string; name: string; fontFamily: string; fontSize: number; fontWeight: string; fontStyle: string; fill: string }[]>([]);

  React.useEffect(() => {
    if (typeof window !== 'undefined') {
      const stored = localStorage.getItem('custom_typography_presets');
      if (stored) {
        try {
          setCustomPresets(JSON.parse(stored));
        } catch (e) {
          console.warn('Failed to parse custom typography presets');
        }
      }
    }
  }, []);

  const saveCurrentAsPreset = () => {
    if (!activeObjectProps) return;
    const name = prompt('Nhập tên cho Preset của bạn:', `Preset ${customPresets.length + 1}`);
    if (!name) return;

    const newPreset = {
      id: `preset_${Date.now()}`,
      name,
      fontFamily: activeObjectProps.fontFamily || 'Outfit',
      fontSize: activeObjectProps.fontSize || 32,
      fontWeight: activeObjectProps.fontWeight || 'normal',
      fontStyle: activeObjectProps.fontStyle || 'normal',
      fill: activeObjectProps.fill || '#6366f1',
    };

    const updated = [...customPresets, newPreset];
    setCustomPresets(updated);
    localStorage.setItem('custom_typography_presets', JSON.stringify(updated));
    toast.success('Đã lưu mẫu chữ của bạn thành công!');
  };

  const deleteCustomPreset = (id: string) => {
    const updated = customPresets.filter(p => p.id !== id);
    setCustomPresets(updated);
    localStorage.setItem('custom_typography_presets', JSON.stringify(updated));
    toast.success('Đã xóa mẫu chữ!');
  };

  const [isRemovingBg, setIsRemovingBg] = useState(false);

  const handleRemoveBackground = async () => {
    if (!canvas || !activeCanvasObject) return;
    const imageUrl = activeObjectProps?.src || (activeCanvasObject as any)._originalElement?.src || (activeCanvasObject as any).src || '';
    if (!imageUrl) {
      toast.error('Không tìm thấy nguồn hình ảnh để xóa nền.');
      return;
    }
    setIsRemovingBg(true);
    const loadingToast = toast.loading('Đang tách nền ảnh bằng AI...');
    try {
      const resultUrl = await removeImageBackground(imageUrl);
      
      const imgElement = new Image();
      imgElement.crossOrigin = 'anonymous';
      imgElement.src = resultUrl;
      await new Promise((resolve, reject) => {
        imgElement.onload = resolve;
        imgElement.onerror = reject;
      });
      
      (activeCanvasObject as any).setElement(imgElement);
      
      (activeCanvasObject as any).set({
        src: resultUrl,
        defaultImageUrl: resultUrl,
      });
      
      (activeCanvasObject as any).applyFilters();
      canvas.renderAll();
      
      updateActiveObject({
        src: resultUrl,
        defaultImageUrl: resultUrl,
      });
      
      pushState();
      toast.dismiss(loadingToast);
      toast.success('Xóa nền ảnh thành công!');
    } catch (err) {
      console.error('Failed to remove background:', err);
      toast.dismiss(loadingToast);
      toast.error('Không thể xóa nền ảnh. Vui lòng kiểm tra lại hình ảnh.');
    } finally {
      setIsRemovingBg(false);
    }
  };

  const handleClipShapeChange = async (shape: 'circle' | 'rounded-rect' | 'heart' | 'star' | 'hexagon' | 'none') => {
    if (!canvas || !activeCanvasObject) return;
    
    const { Circle, Rect, Path } = await import('fabric');
    const width = activeCanvasObject.width || 200;
    const height = activeCanvasObject.height || 200;
    
    let clipPathObj: any = null;
    
    if (shape === 'circle') {
      const radius = Math.min(width, height) / 2;
      clipPathObj = new Circle({
        radius,
        originX: 'center',
        originY: 'center',
        left: 0,
        top: 0,
      });
    } else if (shape === 'rounded-rect') {
      clipPathObj = new Rect({
        width,
        height,
        rx: Math.min(width, height) * 0.15,
        ry: Math.min(width, height) * 0.15,
        originX: 'center',
        originY: 'center',
        left: 0,
        top: 0,
      });
    } else if (shape === 'heart') {
      const w = width;
      const h = height;
      const pathData = `M 0 ${-h*0.35} C ${-w*0.25} ${-h*0.65} ${-w*0.55} ${-h*0.4} ${-w*0.5} ${-h*0.1} C ${-w*0.45} ${h*0.2} ${-w*0.2} ${h*0.35} 0 ${h*0.5} C ${w*0.2} ${h*0.35} ${w*0.45} ${h*0.2} ${w*0.5} ${-h*0.1} C ${w*0.55} ${-h*0.4} ${w*0.25} ${-h*0.65} 0 ${-h*0.35} Z`;
      clipPathObj = new Path(pathData, {
        originX: 'center',
        originY: 'center',
        left: 0,
        top: 0,
      });
    } else if (shape === 'star') {
      const w = width;
      const h = height;
      const rOuter = Math.min(w, h) / 2;
      const rInner = rOuter * 0.4;
      let pathData = '';
      for (let i = 0; i < 10; i++) {
        const angle = (i * Math.PI) / 5 - Math.PI / 2;
        const r = i % 2 === 0 ? rOuter : rInner;
        const x = r * Math.cos(angle);
        const y = r * Math.sin(angle);
        if (i === 0) {
          pathData += `M ${x} ${y} `;
        } else {
          pathData += `L ${x} ${y} `;
        }
      }
      pathData += 'Z';
      clipPathObj = new Path(pathData, {
        originX: 'center',
        originY: 'center',
        left: 0,
        top: 0,
      });
    } else if (shape === 'hexagon') {
      const w = width;
      const h = height;
      const pathData = `M 0 ${-h/2} L ${w/2} ${-h/4} L ${w/2} ${h/4} L 0 ${h/2} L ${-w/2} ${h/4} L ${-w/2} ${-h/4} Z`;
      clipPathObj = new Path(pathData, {
        originX: 'center',
        originY: 'center',
        left: 0,
        top: 0,
      });
    }
    
    if (clipPathObj) {
      clipPathObj.absolutePositioning = false;
    }
    
    activeCanvasObject.set({ clipPath: clipPathObj || undefined });
    updateActiveObject({ clipShape: shape });
    canvas.renderAll();
    pushState();
    toast.success(shape === 'none' ? 'Đã gỡ bỏ mặt nạ cắt' : 'Đã cắt hình ảnh thành công!');
  };

  const handleReplaceImageFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !canvas || !activeCanvasObject) return;
    
    const reader = new FileReader();
    reader.onload = async (event) => {
      const url = event.target?.result as string;
      if (!url) return;
      
      try {
        const loadingToast = toast.loading('Đang thay thế hình ảnh...');
        const { FabricImage } = await import('fabric');
        const newImg = await FabricImage.fromURL(url, { crossOrigin: 'anonymous' });
        
        (activeCanvasObject as any).setElement(newImg._element || newImg.getElement());
        (activeCanvasObject as any).set({
          src: url,
          defaultImageUrl: url,
        });
        (activeCanvasObject as any).applyFilters();
        canvas.renderAll();
        
        updateActiveObject({
          src: url,
          defaultImageUrl: url,
        });
        
        pushState();
        toast.dismiss(loadingToast);
        toast.success('Thay thế hình ảnh thành công!');
      } catch (err) {
        console.error(err);
        toast.error('Lỗi khi thay thế hình ảnh.');
      }
    };
    reader.readAsDataURL(file);
  };

  const applyFilterPreset = async (preset: 'none' | 'grayscale' | 'sepia' | 'vintage' | 'cool' | 'warm') => {
    if (!canvas) return;
    const activeObject = canvas.getActiveObject() as any;
    if (!activeObject || (activeObject.type !== 'image' && activeObject.layerType !== 'IMAGE')) return;
    
    const { filters } = await import('fabric');
    const newFilterList: any[] = [];
    let nextFilters: any = {
      brightness: 0,
      contrast: 0,
      saturation: 0,
      blur: 0,
      grayscale: false,
      sepia: false,
      toneColor: null,
      toneGrayscale: false,
      toneAlpha: 1,
    };
    
    if (preset === 'grayscale') {
      nextFilters.grayscale = true;
      newFilterList.push(new filters.Grayscale());
    } else if (preset === 'sepia') {
      nextFilters.sepia = true;
      newFilterList.push(new filters.Sepia());
    } else if (preset === 'vintage') {
      nextFilters.sepia = true;
      nextFilters.brightness = 0.05;
      nextFilters.contrast = -0.1;
      nextFilters.saturation = -0.15;
      newFilterList.push(new filters.Sepia());
      newFilterList.push(new filters.Brightness({ brightness: 0.05 }));
      newFilterList.push(new filters.Contrast({ contrast: -0.1 }));
      newFilterList.push(new filters.Saturation({ saturation: -0.15 }));
    } else if (preset === 'cool') {
      nextFilters.toneColor = '#0055ff';
      nextFilters.toneAlpha = 0.2;
      nextFilters.toneGrayscale = false;
      newFilterList.push(new filters.BlendColor({
        color: '#0055ff',
        mode: 'multiply',
        alpha: 0.2
      }));
    } else if (preset === 'warm') {
      nextFilters.toneColor = '#ffaa00';
      nextFilters.toneAlpha = 0.25;
      nextFilters.toneGrayscale = false;
      newFilterList.push(new filters.BlendColor({
        color: '#ffaa00',
        mode: 'multiply',
        alpha: 0.25
      }));
    }
    
    activeObject.filters = newFilterList;
    activeObject.applyFilters();
    canvas.renderAll();
    updateActiveObject({ imageFilters: nextFilters });
    pushState();
    toast.success(`Đã áp dụng bộ lọc ${preset === 'none' ? 'Mặc định' : preset}`);
  };

  const [isFontDropdownOpen, setIsFontDropdownOpen] = useState(false);
  const [fontSearchQuery, setFontSearchQuery] = useState('');
  const activeCanvasObject = canvas?.getActiveObject();
  const isImageObject = activeObjectProps?.layerType === 'IMAGE' || activeCanvasObject?.type === 'image';

  // Fetch dynamic fonts
  React.useEffect(() => {
    const fetchFonts = async () => {
      try {
        const res = await fetch('/api/v1/fonts');
        const data = await res.json();
        if (res.ok && data.success) {
          setDynamicFonts(data.fonts || []);
          data.fonts.forEach((font: any) => {
            const fontId = `dynamic-font-${font.family_slug}`;
            if (!document.getElementById(fontId)) {
              const style = document.createElement('style');
              style.id = fontId;
              style.appendChild(document.createTextNode(`
                @font-face {
                  font-family: '${font.family_slug}';
                  src: url('${font.font_url}') format('truetype');
                  font-display: swap;
                }
              `));
              document.head.appendChild(style);
            }
          });
        }
      } catch (err) { console.error('Failed to load dynamic fonts:', err); }
    };
    fetchFonts();
  }, []);

  // Click outside font dropdown handler
  React.useEffect(() => {
    if (!isFontDropdownOpen) return;
    const handleOutsideClick = (e: MouseEvent) => {
      const container = document.getElementById('font-family-selector-container');
      if (container && !container.contains(e.target as Node)) {
        setIsFontDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleOutsideClick);
    return () => document.removeEventListener('mousedown', handleOutsideClick);
  }, [isFontDropdownOpen]);

  const handlePropChange = (name: string, value: any) => {
    if (name === 'fill' && typeof value === 'string') addRecentColor(value);
    if (name === 'stroke' && typeof value === 'string') addRecentColor(value);
    if (name === 'fontFamily' && typeof value === 'string') {
      ensureFontLoaded(value).then(() => {
        updateActiveObject({ fontFamily: value });
      });
      return;
    }
    if (activeObjectProps.layerType === 'TEXT' && (name === 'stroke' || name === 'strokeWidth')) {
      updateActiveObject({ [name]: value, paintFirst: 'stroke' });
    } else {
      updateActiveObject({ [name]: value });
    }
  };

  const getShadowOpacity = (colorStr: string): number => {
    if (!colorStr) return 40;
    if (colorStr.startsWith('rgba')) {
      const match = colorStr.match(/rgba?\((?:\d+,\s*){3}([\d.]+)\)/);
      if (match) return Math.round(parseFloat(match[1]) * 100);
    }
    if (colorStr.startsWith('rgb')) return 100;
    if (colorStr.startsWith('#') && colorStr.length === 9) {
      return Math.round((parseInt(colorStr.slice(7, 9), 16) / 255) * 100);
    }
    return 100;
  };

  const setShadowOpacity = (colorStr: string, opacityPercent: number): string => {
    const opacity = opacityPercent / 100;
    let r = 0, g = 0, b = 0;
    if (colorStr.startsWith('rgba') || colorStr.startsWith('rgb')) {
      const match = colorStr.match(/\d+/g);
      if (match && match.length >= 3) {
        r = parseInt(match[0]);
        g = parseInt(match[1]);
        b = parseInt(match[2]);
      }
    } else if (colorStr.startsWith('#')) {
      const hex = colorStr.replace('#', '');
      if (hex.length === 3) {
        r = parseInt(hex[0] + hex[0], 16);
        g = parseInt(hex[1] + hex[1], 16);
        b = parseInt(hex[2] + hex[2], 16);
      } else if (hex.length === 6 || hex.length === 8) {
        r = parseInt(hex.slice(0, 2), 16);
        g = parseInt(hex.slice(2, 4), 16);
        b = parseInt(hex.slice(4, 6), 16);
      }
    }
    return `rgba(${r}, ${g}, ${b}, ${opacity.toFixed(2)})`;
  };

  const handleShadowChange = (shadowProp: string, value: any) => {
    const currentShadow = activeObjectProps?.shadow || {
      color: 'rgba(0, 0, 0, 0.4)', blur: 15, offsetX: 10, offsetY: 10
    };
    let updatedShadow = { ...currentShadow, [shadowProp]: value };
    if (shadowProp === 'distance' || shadowProp === 'angle') {
      const distance = shadowProp === 'distance' ? Number(value) : (currentShadow.distance || 0);
      const angle = shadowProp === 'angle' ? Number(value) : (currentShadow.angle || 45);
      const angleRad = (angle * Math.PI) / 180;
      updatedShadow.offsetX = Math.round(distance * Math.cos(angleRad));
      updatedShadow.offsetY = Math.round(distance * Math.sin(angleRad));
      updatedShadow.distance = distance;
      updatedShadow.angle = angle;
    }
    updateActiveObject({ shadow: updatedShadow });
  };

  const applyShadowPreset = (type: 'none' | 'soft' | 'medium' | 'hard' | 'glow') => {
    if (type === 'none') {
      updateActiveObject({ shadow: null });
      return;
    }
    let color = 'rgba(0, 0, 0, 0.15)';
    let blur = 12;
    let distance = 6;
    let angle = 45;
    
    if (type === 'medium') {
      color = 'rgba(0, 0, 0, 0.30)';
      blur = 20;
      distance = 12;
      angle = 45;
    } else if (type === 'hard') {
      color = 'rgba(0, 0, 0, 0.45)';
      blur = 10;
      distance = 15;
      angle = 45;
    } else if (type === 'glow') {
      const baseColor = typeof activeObjectProps?.fill === 'string' ? activeObjectProps.fill : '#6366f1';
      color = hexToRgba(rgbaToHex(baseColor), 'rgba(99, 102, 241, 0.50)');
      blur = 25;
      distance = 0;
      angle = 0;
    }
    
    const angleRad = (angle * Math.PI) / 180;
    const offsetX = Math.round(distance * Math.cos(angleRad));
    const offsetY = Math.round(distance * Math.sin(angleRad));
    
    updateActiveObject({
      shadow: {
        color,
        blur,
        offsetX,
        offsetY,
        distance,
        angle
      }
    });
  };

  const createShadowRegion = async () => {
    if (!canvas) return;
    const activeObject = canvas.getActiveObject() as any;
    if (!activeObject) {
      toast.warning('Hãy chọn một đối tượng trước khi tạo vùng bóng.');
      return;
    }

    const { Ellipse, Gradient } = await import('fabric');
    const rect = activeObject.getBoundingRect(true, true);
    const width = Math.max(120, rect.width * 1.25);
    const height = Math.max(24, rect.height * 0.22);
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height + height * 0.08;

    const shadowRegion = new Ellipse({
      left: centerX,
      top: centerY,
      originX: 'center',
      originY: 'center',
      rx: width / 2,
      ry: height / 2,
      fill: new Gradient({
        type: 'radial',
        gradientUnits: 'percentage',
        coords: { x1: 0.5, y1: 0.5, r1: 0, x2: 0.5, y2: 0.5, r2: 0.5 },
        colorStops: [
          { offset: 0, color: 'rgba(0,0,0,0.28)' },
          { offset: 0.55, color: 'rgba(0,0,0,0.16)' },
          { offset: 1, color: 'rgba(0,0,0,0)' },
        ],
      }),
      opacity: 0.95,
      selectable: true,
      evented: true,
      hasControls: true,
      hasBorders: true,
      layerId: `shadow_region_${Date.now()}`,
      layerName: `${activeObject.layerName || 'Layer'} Shadow`,
      layerType: 'DECORATION',
      isShadowRegion: true,
      sourceKind: 'shadow-region',
    } as any);

    canvas.add(shadowRegion);
    const activeIndex = canvas.getObjects().indexOf(activeObject);
    const shadowIndex = Math.max(0, activeIndex);
    if (typeof canvas.moveTo === 'function') {
      canvas.moveTo(shadowRegion, shadowIndex);
    } else if (typeof canvas.sendObjectToBack === 'function') {
      canvas.sendObjectToBack(shadowRegion);
    }
    canvas.setActiveObject(shadowRegion);
    canvas.renderAll();
    pushState();
    toast.success('Đã tạo vùng bóng');
  };

  const handleAlign = (type: 'left' | 'right' | 'center-h' | 'top' | 'bottom' | 'center-v') => {
    if (!canvas) return;
    const activeObject = canvas.getActiveObject();
    if (!activeObject) return;
    if (activeObject.type === 'activeselection') {
      const selection = activeObject;
      const objects = [...selection.getObjects()];
      const rects = objects.map(o => o.getBoundingRect());
      const minLeft = Math.min(...rects.map(r => r.left));
      const maxRight = Math.max(...rects.map(r => r.left + r.width));
      const minTop = Math.min(...rects.map(r => r.top));
      const maxBottom = Math.max(...rects.map(r => r.top + r.height));
      const centerH = minLeft + (maxRight - minLeft) / 2;
      const centerV = minTop + (maxBottom - minTop) / 2;
      canvas.discardActiveObject();
      objects.forEach((obj, idx) => {
        const rect = rects[idx];
        if (type === 'left') obj.left = obj.left - rect.left + minLeft;
        else if (type === 'right') obj.left = obj.left - rect.left + (maxRight - rect.width);
        else if (type === 'center-h') obj.left = obj.left - rect.left + (centerH - rect.width / 2);
        else if (type === 'top') obj.top = obj.top - rect.top + minTop;
        else if (type === 'bottom') obj.top = obj.top - rect.top + (maxBottom - rect.height);
        else if (type === 'center-v') obj.top = obj.top - rect.top + (centerV - rect.height / 2);
        obj.setCoords();
      });
      const newSelection = new ActiveSelection(objects, { canvas });
      canvas.setActiveObject(newSelection);
      canvas.renderAll();
      pushState();
    } else {
      const rect = activeObject.getBoundingRect();
      if (type === 'center-h') canvas.centerObjectH(activeObject);
      else if (type === 'center-v') canvas.centerObjectV(activeObject);
      else {
        if (type === 'left') activeObject.left = activeObject.left - rect.left;
        else if (type === 'right') activeObject.left = activeObject.left - rect.left + (canvas.width - rect.width);
        else if (type === 'top') activeObject.top = activeObject.top - rect.top;
        else if (type === 'bottom') activeObject.top = activeObject.top - rect.top + (canvas.height - rect.height);
      }
      activeObject.setCoords();
      canvas.renderAll();
      pushState();
      handlePropChange('left', Math.round(activeObject.left));
      handlePropChange('top', Math.round(activeObject.top));
    }
  };

  const handleDistribute = (direction: 'h' | 'v') => {
    if (!canvas) return;
    const activeObject = canvas.getActiveObject();
    if (!activeObject || activeObject.type !== 'activeselection') return;
    const selection = activeObject;
    const objects = [...selection.getObjects()];
    if (objects.length < 3) { toast.warning('Cần ít nhất 3 đối tượng để căn đều'); return; }
    const rects = objects.map(o => o.getBoundingRect());
    if (direction === 'h') {
      const items = objects.map((obj, i) => ({ obj, rect: rects[i] })).sort((a, b) => a.rect.left - b.rect.left);
      const minLeft = items[0].rect.left;
      const maxRight = items[items.length - 1].rect.left + items[items.length - 1].rect.width;
      const totalWidth = items.reduce((sum, item) => sum + item.rect.width, 0);
      const gap = ((maxRight - minLeft) - totalWidth) / (items.length - 1);
      canvas.discardActiveObject();
      let currentLeft = minLeft;
      items.forEach((item) => { item.obj.left = item.obj.left - item.rect.left + currentLeft; item.obj.setCoords(); currentLeft += item.rect.width + gap; });
    } else {
      const items = objects.map((obj, i) => ({ obj, rect: rects[i] })).sort((a, b) => a.rect.top - b.rect.top);
      const minTop = items[0].rect.top;
      const maxBottom = items[items.length - 1].rect.top + items[items.length - 1].rect.height;
      const totalHeight = items.reduce((sum, item) => sum + item.rect.height, 0);
      const gap = ((maxBottom - minTop) - totalHeight) / (items.length - 1);
      canvas.discardActiveObject();
      let currentTop = minTop;
      items.forEach((item) => { item.obj.top = item.obj.top - item.rect.top + currentTop; item.obj.setCoords(); currentTop += item.rect.height + gap; });
    }
    const newSelection = new ActiveSelection(objects, { canvas });
    canvas.setActiveObject(newSelection);
    canvas.renderAll();
    pushState();
    toast.success('Đã căn đều khoảng cách');
  };

  const handleFlip = (direction: 'h' | 'v') => {
    if (direction === 'h') handlePropChange('flipX', !activeObjectProps.flipX);
    else handlePropChange('flipY', !activeObjectProps.flipY);
    pushState();
  };

  // Background Manager
  const getBackgroundUrl = (): string => {
    if (!canvas) return '';
    const bg = canvas.backgroundImage;
    return bg ? (bg.src || bg._originalElement?.src || bg.defaultImageUrl || '') : '';
  };
  const getBackgroundColor = (): string => {
    if (!canvas) return '#ffffff';
    return typeof canvas.backgroundColor === 'string' ? canvas.backgroundColor : '#ffffff';
  };
  const getBackgroundGradient = () => {
    if (!canvas) return null;
    const bg = canvas.backgroundColor;
    if (bg && typeof bg === 'object' && 'type' in bg) return bg;
    return null;
  };

  const handleBackgroundUrlChange = async (url: string) => {
    if (!canvas) return;
    try {
      if (!url.trim()) {
        await canvas.setBackgroundImage(null, canvas.renderAll.bind(canvas));
        pushState(); canvas.renderAll(); setBgUrlInput('');
        toast.success('Đã xóa ảnh nền!'); return;
      }
      const { FabricImage } = await import('fabric');
      const bgImg = await FabricImage.fromURL(url, { crossOrigin: 'anonymous' });
      const baseWidth = canvas.getWidth(), baseHeight = canvas.getHeight();
      const scaleX = baseWidth / (bgImg.width || baseWidth), scaleY = baseHeight / (bgImg.height || baseHeight);
      bgImg.set({ originX: 'left', originY: 'top', left: 0, top: 0, scaleX: Math.max(scaleX, scaleY), scaleY: Math.max(scaleX, scaleY), selectable: false, evented: false, hasControls: false, hasBorders: false });
      (bgImg as any)._isBackground = true; (bgImg as any).layerId = 'background_layer'; (bgImg as any).layerName = 'Background';
      (bgImg as any).src = url; (bgImg as any).defaultImageUrl = url;
      await canvas.setBackgroundImage(bgImg, canvas.renderAll.bind(canvas));
      console.log(`[TPL_BG_DEBUG] properties background applied url=${url} canvasBackgroundSrc=${(canvas.backgroundImage as any)?.src || 'null'}`);
      pushState(); canvas.renderAll(); setBgUrlInput('');
      toast.success('Đã đổi ảnh nền!');
    } catch { toast.error('Không thể tải ảnh từ URL này.'); }
  };

  const handleBackgroundColorChange = (color: string) => {
    if (!canvas) return;
    addRecentColor(color);
    canvas.set('backgroundColor', color);
    pushState(); canvas.renderAll();
  };

  const handleBackgroundGradientChange = async (type: 'linear' | 'radial', color1: string, color2: string, angle: number) => {
    if (!canvas) return;
    addRecentColor(color1); addRecentColor(color2);
    const { Gradient } = await import('fabric');
    const baseWidth = canvas.getWidth(), baseHeight = canvas.getHeight();
    let coords: any;
    if (type === 'linear') {
      const angleRad = (angle * Math.PI) / 180;
      coords = { x1: baseWidth * (0.5 - 0.5 * Math.cos(angleRad)), y1: baseHeight * (0.5 - 0.5 * Math.sin(angleRad)), x2: baseWidth * (0.5 + 0.5 * Math.cos(angleRad)), y2: baseHeight * (0.5 + 0.5 * Math.sin(angleRad)) };
    } else {
      coords = { x1: baseWidth/2, y1: baseHeight/2, r1: 0, x2: baseWidth/2, y2: baseHeight/2, r2: Math.max(baseWidth, baseHeight)/2 };
    }
    canvas.set('backgroundColor', new Gradient({ type, gradientUnits: 'pixels', coords, colorStops: [{ offset: 0, color: color1 }, { offset: 1, color: color2 }] }));
    canvas.renderAll(); pushState();
  };

  const handleGradientChange = async (type: 'linear' | 'radial', color1: string, color2: string, angle: number) => {
    if (!canvas) return;
    const activeObject = canvas.getActiveObject();
    if (!activeObject) return;
    addRecentColor(color1); addRecentColor(color2);
    const { Gradient } = await import('fabric');
    let coords: any;
    if (type === 'linear') {
      const angleRad = (angle * Math.PI) / 180;
      coords = { x1: 0.5 - 0.5 * Math.cos(angleRad), y1: 0.5 - 0.5 * Math.sin(angleRad), x2: 0.5 + 0.5 * Math.cos(angleRad), y2: 0.5 + 0.5 * Math.sin(angleRad) };
    } else {
      coords = { x1: 0.5, y1: 0.5, r1: 0, x2: 0.5, y2: 0.5, r2: 0.5 };
    }
    updateActiveObject({ fill: new Gradient({ type, gradientUnits: 'percentage', coords, colorStops: [{ offset: 0, color: color1 }, { offset: 1, color: color2 }] }) });
    pushState();
  };

  const handleTextTransformToggle = () => {
    if (!canvas) return;
    const activeObject = canvas.getActiveObject() as any;
    if (!activeObject || (activeObject.type !== 'i-text' && activeObject.layerType !== 'TEXT')) return;
    const currentTransform = activeObject.textTransform || 'none';
    const newTransform = currentTransform === 'uppercase' ? 'none' : 'uppercase';
    const originalText = activeObject._originalText || activeObject.text;
    if (newTransform === 'uppercase') {
    updateActiveObject({ text: originalText.toUpperCase(), textTransform: 'uppercase', _originalText: originalText });
    } else {
    updateActiveObject({ text: originalText, textTransform: 'none' });
    }
    pushState();
  };

  const handleImageFilterChange = async (filterType: string, value: any) => {
    if (!canvas) return;
    const activeObject = canvas.getActiveObject() as any;
    if (!activeObject || (activeObject.type !== 'image' && activeObject.layerType !== 'IMAGE')) return;
    const { filters } = await import('fabric');
    const currentFilters = activeObjectProps.imageFilters || {};
    const nextFilters = { ...currentFilters, [filterType]: value };
    const newFilterList: any[] = [];

    if (nextFilters.brightness !== undefined && nextFilters.brightness !== 0) {
      newFilterList.push(new filters.Brightness({ brightness: nextFilters.brightness }));
    }
    if (nextFilters.contrast !== undefined && nextFilters.contrast !== 0) {
      newFilterList.push(new filters.Contrast({ contrast: nextFilters.contrast }));
    }
    if (nextFilters.saturation !== undefined && nextFilters.saturation !== 0) {
      newFilterList.push(new filters.Saturation({ saturation: nextFilters.saturation }));
    }
    if (nextFilters.blur !== undefined && nextFilters.blur !== 0) {
      newFilterList.push(new filters.Blur({ blur: nextFilters.blur }));
    }
    if (nextFilters.grayscale) {
      newFilterList.push(new filters.Grayscale());
    }
    if (nextFilters.sepia) {
      newFilterList.push(new filters.Sepia());
    }
    if (nextFilters.toneColor) {
      if (nextFilters.toneGrayscale) {
        newFilterList.push(new filters.Grayscale());
      }
      newFilterList.push(new filters.BlendColor({
        color: nextFilters.toneColor,
        mode: 'multiply',
        alpha: typeof nextFilters.toneAlpha === 'number' ? nextFilters.toneAlpha : 1
      }));
    }

    activeObject.filters = newFilterList;
    activeObject.applyFilters();
    canvas.renderAll();
    updateActiveObject({ imageFilters: nextFilters });
    pushState();
  };

  const applyTonePreset = async (toneColor: string | null, grayscale = true, toneAlpha = 1) => {
    if (!canvas) return;
    const activeObject = canvas.getActiveObject() as any;
    if (!activeObject || (activeObject.type !== 'image' && activeObject.layerType !== 'IMAGE')) return;
    const currentFilters = activeObjectProps.imageFilters || {};
    const nextFilters = {
      ...currentFilters,
      toneColor,
      toneGrayscale: grayscale,
      toneAlpha
    };
    const { filters } = await import('fabric');
    const newFilterList: any[] = [];
    if (nextFilters.brightness !== undefined && nextFilters.brightness !== 0) newFilterList.push(new filters.Brightness({ brightness: nextFilters.brightness }));
    if (nextFilters.contrast !== undefined && nextFilters.contrast !== 0) newFilterList.push(new filters.Contrast({ contrast: nextFilters.contrast }));
    if (nextFilters.saturation !== undefined && nextFilters.saturation !== 0) newFilterList.push(new filters.Saturation({ saturation: nextFilters.saturation }));
    if (nextFilters.blur !== undefined && nextFilters.blur !== 0) newFilterList.push(new filters.Blur({ blur: nextFilters.blur }));
    if (nextFilters.grayscale) newFilterList.push(new filters.Grayscale());
    if (nextFilters.sepia) newFilterList.push(new filters.Sepia());
    if (toneColor) {
      if (grayscale) newFilterList.push(new filters.Grayscale());
      newFilterList.push(new filters.BlendColor({
        color: toneColor,
        mode: 'multiply',
        alpha: typeof toneAlpha === 'number' ? toneAlpha : 1
      }));
    }

    activeObject.filters = newFilterList;
    activeObject.applyFilters();
    canvas.renderAll();
    updateActiveObject({ imageFilters: nextFilters });
    pushState();
  };

  // Eyedropper tool
  const handleEyedropper = () => {
    if (!canvas) return;
    toast.info('Click vào bất kỳ điểm nào trên canvas để lấy màu (tính năng đang phát triển)');
    // Future: canvas eyedropper implementation
  };

  // Color palette render helper
  const ColorPalette = ({ onSelect, selectedColor }: { onSelect: (c: string) => void; selectedColor?: string }) => (
    <div className="space-y-2 pt-1">
      {recentColors.length > 0 && (
        <div className="space-y-1">
          <span className="text-[8px] font-semibold text-slate-400">Gần đây</span>
          <div className="flex gap-1 flex-wrap">
            {recentColors.map(c => (
              <button key={c} onClick={() => onSelect(c)}
                className="w-5 h-5 rounded-md border transition-transform hover:scale-110 active:scale-95 cursor-pointer"
                style={{ backgroundColor: c, borderColor: selectedColor === c ? '#6366f1' : '#334155' }}
              />
            ))}
          </div>
        </div>
      )}
      <div className="space-y-1">
        <span className="text-[8px] font-semibold text-slate-400">Presets</span>
        <div className="flex gap-1 flex-wrap">
          {PRESET_COLORS.map(c => (
            <button key={c} onClick={() => onSelect(c)}
              className="w-5 h-5 rounded-md border border-slate-300 transition-transform hover:scale-110 active:scale-95 cursor-pointer"
              style={{ backgroundColor: c }}
            />
          ))}
        </div>
      </div>
    </div>
  );

  // ==================== BACKGROUND MANAGER (no object selected) ====================
  if (!activeObjectId || !activeObjectProps) {
    const currentBgUrl = getBackgroundUrl();
    const currentBgColor = getBackgroundColor();
    const currentBgGradient = getBackgroundGradient();
    const bgFillType: 'solid' | 'linear' | 'radial' = currentBgGradient
      ? (currentBgGradient.type === 'radial' ? 'radial' : 'linear') : 'solid';
    const bgColor1 = currentBgGradient?.colorStops?.[0]?.color || (typeof canvas?.backgroundColor === 'string' ? canvas.backgroundColor : '#ffffff');
    const bgColor2 = currentBgGradient?.colorStops?.[1]?.color || '#ffffff';
    const bgAngle = currentBgGradient?.type === 'linear' && currentBgGradient.coords ? getGradientAngle(currentBgGradient.coords) : 0;

    return (
      <div className="flex flex-col h-full space-y-4">
        <div className="flex items-center gap-1.5 px-1 border-b border-slate-200 pb-3">
          <Palette className="w-4 h-4 text-indigo-600" />
          <h3 className="font-bold text-sm text-slate-400">Quản lý Ảnh Nền</h3>
        </div>

        <div className="flex-1 overflow-y-auto pr-1 space-y-4 pb-8 scrollbar-thin scrollbar-thumb-slate-300">

          {/* Background Image */}
          <CollapsibleSection id="bg-image" icon={<ImageIcon className="w-3.5 h-3.5" />} title="Ảnh nền Template" defaultOpen={true}>
            <div className="aspect-video w-full rounded-xl bg-white border border-slate-200 flex items-center justify-center overflow-hidden relative group">
              {currentBgUrl ? (
                <img src={currentBgUrl} alt="Background" className="w-full h-full object-cover" />
              ) : (
                <div className="text-[10px] text-slate-400 font-mono">Trống (Màu nền đơn)</div>
              )}
              {currentBgUrl && (
                <div className="absolute inset-0 bg-white/80 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                  <Button size="sm" variant="destructive" onClick={() => handleBackgroundUrlChange('')} className="h-7 text-[10px] rounded-lg px-2 cursor-pointer">
                    <Trash2 className="w-3 h-3" /> Xóa ảnh nền
                  </Button>
                </div>
              )}
            </div>
            {currentBgUrl && (
              <div className="pt-2">
                <Button
                  size="sm"
                  onClick={() => {
                    if (!currentBgUrl) {
                      toast.error('Chưa có ảnh nền để cắt.');
                      return;
                    }
                    openCrop({
                      imageUrl: currentBgUrl,
                      sourceName: 'Nền',
                      initialRatio: 'FREE',
                    });
                  }}
                  className="w-full bg-indigo-600 hover:bg-indigo-500 rounded-xl text-xs font-bold h-8 text-white flex items-center justify-center gap-1.5 cursor-pointer"
                >
                  <CropIcon className="w-3.5 h-3.5" />
                  Trích xuất vùng cắt làm layer mới
                </Button>
              </div>
            )}
            <div className="space-y-1.5">
              <span className="text-[9px] font-semibold text-slate-400">Đổi ảnh nền bằng URL</span>
              <div className="flex gap-2">
                <Input placeholder="Dán liên kết ảnh..." value={bgUrlInput} onChange={(e) => setBgUrlInput(e.target.value)}
                  className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-8 flex-1" />
                <Button size="sm" onClick={() => handleBackgroundUrlChange(bgUrlInput)} className="bg-indigo-600 hover:bg-indigo-500 rounded-xl text-xs font-bold h-8 px-3 cursor-pointer">
                  Áp dụng
                </Button>
              </div>
            </div>
          </CollapsibleSection>

          {/* Background Color & Gradient */}
          <CollapsibleSection id="bg-color" icon={<Palette className="w-3.5 h-3.5" />} title="Màu nền & Chuyển sắc" defaultOpen={true}>
            <div className="flex p-0.5 bg-white border border-slate-200 rounded-xl">
              {(['solid', 'linear', 'radial'] as const).map(t => (
                <button key={t} type="button" onClick={() => {
                  if (t !== bgFillType) {
                    if (t === 'solid') handleBackgroundColorChange(bgColor1);
                    else handleBackgroundGradientChange(t, bgColor1, bgColor2, bgAngle);
                  }
                }} className={`flex-1 py-1 rounded-lg text-[10px] font-bold transition-all cursor-pointer ${bgFillType === t ? 'bg-indigo-50 text-indigo-600 border border-indigo-200' : 'text-slate-400 hover:text-slate-400'}`}>
                  {t === 'solid' ? 'Đơn' : t === 'linear' ? 'Linear' : 'Radial'}
                </button>
              ))}
            </div>
            {bgFillType === 'solid' ? (
              <div className="space-y-1">
                <span className="text-[9px] font-semibold text-slate-400">Chọn màu nền</span>
                <div className="flex gap-2">
                  <input type="color" value={rgbaToHex(bgColor1)} onChange={(e) => handleBackgroundColorChange(e.target.value)}
                    className="w-8 h-8 rounded-lg bg-transparent border border-slate-200 cursor-pointer overflow-hidden" />
                  <Input value={bgColor1} onChange={(e) => handleBackgroundColorChange(e.target.value)}
                    className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-8 font-mono flex-1" />
                  <Button size="icon" variant="ghost" onClick={handleEyedropper} className="w-8 h-8 rounded-lg text-slate-500 hover:text-slate-800 cursor-pointer" title="Công cụ lấy màu">
                    <Pipette className="w-3.5 h-3.5" />
                  </Button>
                </div>
                <ColorPalette onSelect={handleBackgroundColorChange} selectedColor={bgColor1} />
              </div>
            ) : (
              <div className="space-y-3 pt-1">
                {(['color1', 'color2'] as const).map((stop, i) => (
                  <div key={stop} className="space-y-1">
                    <span className="text-[9px] font-semibold text-slate-400">{i === 0 ? 'Màu bắt đầu' : 'Màu kết thúc'}</span>
                    <div className="flex gap-2">
                      <input type="color" value={rgbaToHex(i === 0 ? bgColor1 : bgColor2)}
                        onChange={(e) => handleBackgroundGradientChange(bgFillType, i === 0 ? e.target.value : bgColor1, i === 0 ? bgColor2 : e.target.value, bgAngle)}
                        className="w-8 h-8 rounded-lg bg-transparent border border-slate-200 cursor-pointer overflow-hidden" />
                      <Input value={i === 0 ? bgColor1 : bgColor2}
                        onChange={(e) => handleBackgroundGradientChange(bgFillType, i === 0 ? e.target.value : bgColor1, i === 0 ? bgColor2 : e.target.value, bgAngle)}
                        className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-8 font-mono flex-1" />
                    </div>
                  </div>
                ))}
                {bgFillType === 'linear' && (
                  <div className="space-y-1.5">
                    <div className="flex justify-between text-[9px] font-semibold text-slate-400"><span>Góc</span><span>{bgAngle}°</span></div>
                    <div className="flex gap-3 items-center">
                      <input type="range" min="0" max="360" step="5" value={bgAngle}
                        onChange={(e) => handleBackgroundGradientChange('linear', bgColor1, bgColor2, parseInt(e.target.value))}
                        className="flex-1 h-1 bg-white rounded-lg appearance-none cursor-pointer accent-indigo-600" />
                      <Input type="number" min="0" max="360" value={bgAngle}
                        onChange={(e) => handleBackgroundGradientChange('linear', bgColor1, bgColor2, Math.max(0, Math.min(360, parseInt(e.target.value) || 0)))}
                        className="w-14 bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-7 text-center px-1" />
                    </div>
                  </div>
                )}
              </div>
            )}
          </CollapsibleSection>

          <div className="flex flex-col items-center justify-center py-4 text-slate-400 space-y-1 text-center px-4">
            <Sliders className="w-5 h-5 text-slate-800" />
            <p className="text-[10px]">Chọn layer trên canvas để chỉnh sửa thuộc tính</p>
          </div>
        </div>
      </div>
    );
  }

  // ==================== OBJECT SELECTED: Properties ====================
  const offsetX = activeObjectProps.shadow?.offsetX || 0;
  const offsetY = activeObjectProps.shadow?.offsetY || 0;
  const currentDistance = Math.round(Math.sqrt(offsetX * offsetX + offsetY * offsetY));
  let currentAngle = Math.round(Math.atan2(offsetY, offsetX) * (180 / Math.PI));
  if (currentAngle < 0) currentAngle += 360;

  const fillValue = activeObjectProps?.fill;
  const isGradient = typeof fillValue === 'object' && fillValue !== null && 'type' in fillValue;
  const fillType: 'solid' | 'linear' | 'radial' = isGradient ? (fillValue.type === 'radial' ? 'radial' : 'linear') : 'solid';
  const color1 = isGradient && fillValue.colorStops?.[0]?.color || (typeof fillValue === 'string' ? fillValue : '#6366f1');
  const color2 = isGradient && fillValue.colorStops?.[1]?.color || '#ffffff';
  const gradientAngle = isGradient && fillValue.type === 'linear' && fillValue.coords ? getGradientAngle(fillValue.coords) : 0;

  const activeObject = activeCanvasObject;
  const selectedCount = activeObject?.type === 'activeselection' ? (activeObject as any)._objects?.length || 0 : (activeObject ? 1 : 0);

  // Prepare system fonts
  const systemFonts = [
    { name: 'Outfit', family_slug: 'Outfit', style: 'Hệ thống' },
    { name: 'Sans-Serif', family_slug: 'sans-serif', style: 'Hệ thống' },
    { name: 'Serif', family_slug: 'serif', style: 'Hệ thống' },
    { name: 'Monospace', family_slug: 'monospace', style: 'Hệ thống' },
    { name: 'Cursive', family_slug: 'cursive', style: 'Hệ thống' },
  ];

  // Combine all fonts
  const allFonts = [
    ...systemFonts,
    ...dynamicFonts.map(f => ({
      name: f.name,
      family_slug: f.family_slug,
      style: f.style || 'Chưa phân loại',
    })),
  ];

  // Assign global sequence number to all fonts
  const numberedFonts = allFonts.map((f, i) => ({
    ...f,
    seq: i + 1,
  }));

  // Filter based on search query
  const filteredFonts = numberedFonts.filter(f =>
    f.name.toLowerCase().includes(fontSearchQuery.toLowerCase()) ||
    f.family_slug.toLowerCase().includes(fontSearchQuery.toLowerCase()) ||
    (f.style || '').toLowerCase().includes(fontSearchQuery.toLowerCase())
  );

  // Group by category
  const groupedFonts: Record<string, typeof filteredFonts> = {};
  filteredFonts.forEach(f => {
    const cat = f.style || 'Chưa phân loại';
    if (!groupedFonts[cat]) {
      groupedFonts[cat] = [];
    }
    groupedFonts[cat].push(f);
  });

  return (
    <div className="flex flex-col h-full space-y-4 animate-in fade-in duration-200">
      <div className="flex items-center gap-1.5 px-1 border-b border-slate-200 pb-3 shrink-0">
        <Settings className="w-4 h-4 text-indigo-600" />
        <h3 className="font-bold text-sm text-slate-400">Properties</h3>
        {selectedCount > 1 && (
          <span className="ml-auto text-[10px] bg-indigo-50 text-indigo-600 px-1.5 py-0.5 rounded-md font-bold">
            {selectedCount} layers
          </span>
        )}
      </div>

      <div className="flex items-center gap-2 px-1 flex-wrap">
        <Button
          size="sm"
          variant="outline"
          onClick={() => copyToClipboard()}
          className="h-8 px-2.5 rounded-xl border-slate-200 bg-white text-slate-400 hover:text-slate-800 hover:bg-white text-xs font-semibold cursor-pointer flex-1"
        >
          <Copy className="w-3.5 h-3.5 mr-1.5" /> Nhân bản
        </Button>
        <Button
          size="sm"
          variant="outline"
          onClick={() => copyStyle()}
          className="h-8 px-2.5 rounded-xl border-slate-200 bg-white text-slate-400 hover:text-slate-800 hover:bg-white text-xs font-semibold cursor-pointer flex-1 flex items-center justify-center gap-1"
          title="Sao chép định dạng (Ctrl + Shift + C)"
        >
          <span>🎨</span> Sao chép kiểu
        </Button>
        <Button
          size="sm"
          variant="outline"
          onClick={() => pasteStyle()}
          disabled={!copiedStyle}
          className="h-8 px-2.5 rounded-xl border-slate-200 bg-white text-slate-400 hover:text-slate-800 hover:bg-white text-xs font-semibold cursor-pointer flex-1 flex items-center justify-center gap-1 disabled:opacity-50 disabled:cursor-not-allowed"
          title="Dán định dạng (Ctrl + Shift + V)"
        >
          <span>📋</span> Dán kiểu
        </Button>
      </div>

      <div className="flex-1 overflow-y-auto pr-1 scrollbar-thin scrollbar-thumb-slate-300 space-y-4 pb-8">

        {/* Section 1: Layer Info */}
        <CollapsibleSection id="layer-info" icon={<Settings className="w-3 h-3" />} title="Layer Info" defaultOpen={true}>
          <div className="space-y-1">
            <label className="text-[9px] font-semibold text-slate-400">Tên Layer</label>
            <Input value={activeObjectProps.layerName || ''} onChange={(e) => handlePropChange('layerName', e.target.value)}
              className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl focus-visible:ring-indigo-600" />
          </div>
          <div className="space-y-1">
            <label className="text-[9px] font-semibold text-slate-400">Loại Layer</label>
            <select value={activeObjectProps.layerType || 'DECORATION'} onChange={(e) => handlePropChange('layerType', e.target.value)}
              className="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-400 focus:outline-none focus:border-indigo-600">
              <option value="DECORATION">DECORATION (Cố định)</option>
              <option value="IMAGE">IMAGE (Placeholder)</option>
              <option value="TEXT">TEXT (Chữ động)</option>
            </select>
          </div>
          <div className="space-y-1">
            <label className="text-[9px] font-semibold text-slate-400">Chế độ hòa trộn (Blend Mode)</label>
            <select
              value={activeObjectProps.blendMode || 'normal'}
              onChange={(e) => {
                const val = e.target.value;
                updateActiveObject({
                  blendMode: val,
                  globalCompositeOperation: getCompositeOperation(val) as any
                });
                pushState();
              }}
              className="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-400 focus:outline-none focus:border-indigo-600"
            >
              <option value="normal">Bình thường (Normal)</option>
              <option value="multiply">Nhân màu (Multiply)</option>
              <option value="screen">Lọc màu (Screen)</option>
              <option value="overlay">Chồng màu (Overlay)</option>
              <option value="darken">Làm tối (Darken)</option>
              <option value="lighten">Làm sáng (Lighten)</option>
              <option value="color-dodge">Color Dodge</option>
              <option value="color-burn">Color Burn</option>
              <option value="hard-light">Ánh sáng mạnh (Hard Light)</option>
              <option value="soft-light">Ánh sáng dịu (Soft Light)</option>
              <option value="difference">Khác biệt (Difference)</option>
              <option value="exclusion">Loại trừ (Exclusion)</option>
              <option value="linear-dodge">Linear Dodge (Add)</option>
            </select>
          </div>
        </CollapsibleSection>

        {/* Section 2: Transform */}
        <CollapsibleSection id="transform" icon={<Maximize2 className="w-3 h-3" />} title="Transform" defaultOpen={true}>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <span className="text-[9px] font-semibold text-slate-400">X</span>
              <Input type="number" value={activeObjectProps.left !== undefined ? Math.round(activeObjectProps.left) : 0}
                onChange={(e) => handlePropChange('left', parseInt(e.target.value) || 0)}
                className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-8" />
            </div>
            <div className="space-y-1">
              <span className="text-[9px] font-semibold text-slate-400">Y</span>
              <Input type="number" value={activeObjectProps.top !== undefined ? Math.round(activeObjectProps.top) : 0}
                onChange={(e) => handlePropChange('top', parseInt(e.target.value) || 0)}
                className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-8" />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <span className="text-[9px] font-semibold text-slate-400">Scale</span>
              <Input type="number" step="0.05" value={activeObjectProps.scaleX !== undefined ? parseFloat(activeObjectProps.scaleX.toFixed(2)) : 1.0}
                onChange={(e) => { const v = parseFloat(e.target.value) || 1.0; updateActiveObject({ scaleX: v, scaleY: v }); }}
                className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-8" />
            </div>
            <div className="space-y-1">
              <span className="text-[9px] font-semibold text-slate-400">Xoay °</span>
              <Input type="number" value={activeObjectProps.angle !== undefined ? Math.round(activeObjectProps.angle) : 0}
                onChange={(e) => handlePropChange('angle', parseInt(e.target.value) || 0)}
                className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-8" />
            </div>
          </div>
          <div className="space-y-1.5">
            <div className="flex justify-between text-[9px] font-semibold text-slate-400">
              <span>Opacity</span>
              <span>{Math.round((activeObjectProps.opacity !== undefined ? activeObjectProps.opacity : 1.0) * 100)}%</span>
            </div>
            <input type="range" min="0" max="1" step="0.05" value={activeObjectProps.opacity !== undefined ? activeObjectProps.opacity : 1.0}
              onChange={(e) => handlePropChange('opacity', parseFloat(e.target.value))}
              className="w-full h-1 bg-white rounded-lg appearance-none cursor-pointer accent-indigo-600" />
          </div>

          {/* Flip & Align */}
          <div className="pt-2 border-t border-slate-200/50 space-y-3">
            <div className="space-y-1">
              <span className="text-[8px] font-semibold text-slate-400 block">Lật</span>
              <div className="grid grid-cols-2 gap-1.5">
                <Button size="sm" variant="outline" onClick={() => handleFlip('h')}
                  className={`rounded-xl border-slate-200 text-[10px] p-0 h-8 cursor-pointer ${activeObjectProps.flipX ? 'bg-indigo-50 text-indigo-600 border-indigo-500/30' : 'bg-white text-slate-500 hover:text-slate-800'}`}>
                  <FlipHorizontal className="w-3.5 h-3.5 mr-1" /> Ngang
                </Button>
                <Button size="sm" variant="outline" onClick={() => handleFlip('v')}
                  className={`rounded-xl border-slate-200 text-[10px] p-0 h-8 cursor-pointer ${activeObjectProps.flipY ? 'bg-indigo-50 text-indigo-600 border-indigo-500/30' : 'bg-white text-slate-500 hover:text-slate-800'}`}>
                  <FlipVertical className="w-3.5 h-3.5 mr-1" /> Dọc
                </Button>
              </div>
            </div>
            <div className="space-y-1">
              <span className="text-[8px] font-semibold text-slate-400 block">Căn ngang</span>
              <div className="grid grid-cols-3 gap-1.5">
                {(['left', 'center-h', 'right'] as const).map(a => (
                  <Button key={a} size="sm" variant="outline" onClick={() => handleAlign(a)}
                    className="rounded-xl border-slate-200 bg-white text-[10px] p-0 h-8 text-slate-500 hover:text-slate-800 cursor-pointer">
                    {a === 'left' ? 'Trái' : a === 'center-h' ? 'Giữa' : 'Phải'}
                  </Button>
                ))}
              </div>
            </div>
            <div className="space-y-1">
              <span className="text-[8px] font-semibold text-slate-400 block">Căn dọc</span>
              <div className="grid grid-cols-3 gap-1.5">
                {(['top', 'center-v', 'bottom'] as const).map(a => (
                  <Button key={a} size="sm" variant="outline" onClick={() => handleAlign(a)}
                    className="rounded-xl border-slate-200 bg-white text-[10px] p-0 h-8 text-slate-500 hover:text-slate-800 cursor-pointer">
                    {a === 'top' ? 'Trên' : a === 'center-v' ? 'Giữa' : 'Dưới'}
                  </Button>
                ))}
              </div>
            </div>
            {selectedCount >= 3 && (
              <div className="space-y-1 animate-in fade-in duration-200">
                <span className="text-[8px] font-semibold text-slate-400 block">Phân bổ đều</span>
                <div className="grid grid-cols-2 gap-1.5">
                  <Button size="sm" variant="outline" onClick={() => handleDistribute('h')}
                    className="rounded-xl border-slate-200 bg-white text-[10px] p-0 h-8 text-slate-500 hover:text-slate-800 cursor-pointer">Dàn ngang</Button>
                  <Button size="sm" variant="outline" onClick={() => handleDistribute('v')}
                    className="rounded-xl border-slate-200 bg-white text-[10px] p-0 h-8 text-slate-500 hover:text-slate-800 cursor-pointer">Dàn dọc</Button>
                </div>
              </div>
            )}
          </div>
        </CollapsibleSection>

        {/* Section 3: Fill & Stroke */}
        {(activeObjectProps.fill !== undefined && activeObjectProps.fill !== null) && (
          <CollapsibleSection id="fill-stroke" icon={<Palette className="w-3 h-3" />} title="Màu sắc & Tô" defaultOpen={true}>
            <div className="flex p-0.5 bg-white border border-slate-200 rounded-xl">
              {(['solid', 'linear', 'radial'] as const).map(t => (
                <button key={t} type="button" onClick={() => {
                  if (t !== fillType) {
                    if (t === 'solid') handlePropChange('fill', color1);
                    else handleGradientChange(t, color1, color2, gradientAngle);
                  }
                }} className={`flex-1 py-1 rounded-lg text-[10px] font-bold transition-all cursor-pointer ${fillType === t ? 'bg-indigo-50 text-indigo-600 border border-indigo-200' : 'text-slate-400 hover:text-slate-400'}`}>
                  {t === 'solid' ? 'Đơn' : t === 'linear' ? 'Linear' : 'Radial'}
                </button>
              ))}
            </div>
            {fillType === 'solid' ? (
              <div className="space-y-1">
                <span className="text-[9px] font-semibold text-slate-400">Màu tô</span>
                <div className="flex gap-2">
                  <input type="color" value={rgbaToHex(color1)} onChange={(e) => handlePropChange('fill', e.target.value)}
                    className="w-8 h-8 rounded-lg bg-transparent border border-slate-200 cursor-pointer overflow-hidden" />
                  <Input value={color1} onChange={(e) => handlePropChange('fill', e.target.value)}
                    className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-8 font-mono flex-1" />
                  <Button size="icon" variant="ghost" onClick={handleEyedropper} className="w-8 h-8 rounded-lg text-slate-500 hover:text-slate-800 cursor-pointer" title="Lấy màu">
                    <Pipette className="w-3.5 h-3.5" />
                  </Button>
                </div>
                <ColorPalette onSelect={(c) => handlePropChange('fill', c)} selectedColor={color1} />
              </div>
            ) : (
              <div className="space-y-3">
                {([color1, color2] as string[]).map((c, i) => (
                  <div key={i} className="space-y-1">
                    <span className="text-[9px] font-semibold text-slate-400">{i === 0 ? 'Màu bắt đầu' : 'Màu kết thúc'}</span>
                    <div className="flex gap-2">
                      <input type="color" value={rgbaToHex(c)}
                        onChange={(e) => handleGradientChange(fillType, i === 0 ? e.target.value : color1, i === 0 ? color2 : e.target.value, gradientAngle)}
                        className="w-8 h-8 rounded-lg bg-transparent border border-slate-200 cursor-pointer overflow-hidden" />
                      <Input value={c}
                        onChange={(e) => handleGradientChange(fillType, i === 0 ? e.target.value : color1, i === 0 ? color2 : e.target.value, gradientAngle)}
                        className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-8 font-mono flex-1" />
                    </div>
                  </div>
                ))}
                {fillType === 'linear' && (
                  <div className="space-y-1.5">
                    <div className="flex justify-between text-[9px] font-semibold text-slate-400"><span>Góc</span><span>{gradientAngle}°</span></div>
                    <div className="flex gap-3 items-center">
                      <input type="range" min="0" max="360" step="5" value={gradientAngle}
                        onChange={(e) => handleGradientChange('linear', color1, color2, parseInt(e.target.value))}
                        className="flex-1 h-1 bg-white rounded-lg appearance-none cursor-pointer accent-indigo-600" />
                      <Input type="number" min="0" max="360" value={gradientAngle}
                        onChange={(e) => handleGradientChange('linear', color1, color2, Math.max(0, Math.min(360, parseInt(e.target.value) || 0)))}
                        className="w-14 bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-7 text-center px-1" />
                    </div>
                  </div>
                )}
              </div>
            )}
          </CollapsibleSection>
        )}

        {/* Section 4: Text Style */}
        {activeObjectProps.layerType === 'TEXT' && (
          <CollapsibleSection id="text-style" icon={<span className="text-[10px]">🔤</span>} title="Kiểu chữ" defaultOpen={true}>
            <div className="space-y-1.5">
              <span className="text-[9px] font-semibold text-slate-400">Định dạng</span>
              <div className="grid grid-cols-5 gap-1.5">
                <Button size="sm" variant="outline" onClick={() => handlePropChange('fontWeight', activeObjectProps.fontWeight === 'bold' ? 'normal' : 'bold')}
                  className={`rounded-xl h-8 border-slate-200 cursor-pointer p-0 ${activeObjectProps.fontWeight === 'bold' ? 'bg-indigo-600/10 text-indigo-600 border-indigo-500/30' : 'bg-white text-slate-500'}`}>
                  <Bold className="w-3.5 h-3.5" />
                </Button>
                <Button size="sm" variant="outline" onClick={() => handlePropChange('fontStyle', activeObjectProps.fontStyle === 'italic' ? 'normal' : 'italic')}
                  className={`rounded-xl h-8 border-slate-200 cursor-pointer p-0 ${activeObjectProps.fontStyle === 'italic' ? 'bg-indigo-600/10 text-indigo-600 border-indigo-500/30' : 'bg-white text-slate-500'}`}>
                  <Italic className="w-3.5 h-3.5" />
                </Button>
                <Button size="sm" variant="outline" onClick={() => handlePropChange('underline', !activeObjectProps.underline)}
                  className={`rounded-xl h-8 border-slate-200 cursor-pointer p-0 ${activeObjectProps.underline ? 'bg-indigo-600/10 text-indigo-600 border-indigo-500/30' : 'bg-white text-slate-500'}`}>
                  <Underline className="w-3.5 h-3.5" />
                </Button>
                <Button size="sm" variant="outline" onClick={() => handlePropChange('linethrough', !activeObjectProps.linethrough)}
                  className={`rounded-xl h-8 border-slate-200 cursor-pointer p-0 ${activeObjectProps.linethrough ? 'bg-indigo-600/10 text-indigo-600 border-indigo-500/30' : 'bg-white text-slate-500'}`}>
                  <Strikethrough className="w-3.5 h-3.5" />
                </Button>
                <Button size="sm" variant="outline" onClick={handleTextTransformToggle}
                  className={`rounded-xl h-8 border-slate-200 cursor-pointer p-0 ${activeObjectProps.textTransform === 'uppercase' ? 'bg-indigo-600/10 text-indigo-600 border-indigo-500/30' : 'bg-white text-slate-500'}`}>
                  <span className="text-[10px] tracking-tighter">Aa</span>
                </Button>
              </div>
            </div>
            <div className="space-y-1.5">
              <span className="text-[9px] font-semibold text-slate-400">Căn lề</span>
              <div className="flex gap-1.5">
                {(['left', 'center', 'right'] as const).map(a => (
                  <Button key={a} size="sm" variant="outline" onClick={() => handlePropChange('textAlign', a)}
                    className={`flex-1 rounded-xl h-8 border-slate-200 cursor-pointer ${activeObjectProps.textAlign === a ? 'bg-indigo-600/10 text-indigo-600 border-indigo-500/30' : 'bg-white text-slate-500'}`}>
                    {a === 'left' ? <AlignLeft className="w-3.5 h-3.5" /> : a === 'center' ? <AlignCenter className="w-3.5 h-3.5" /> : <AlignRight className="w-3.5 h-3.5" />}
                  </Button>
                ))}
              </div>
            </div>
            <div className="space-y-1">
              <span className="text-[9px] font-semibold text-slate-400">Font Size</span>
              <Input type="number" value={activeObjectProps.fontSize || 40}
                onChange={(e) => handlePropChange('fontSize', parseInt(e.target.value) || 12)}
                className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-8" />
            </div>
            <div className="space-y-1">
              <div className="flex justify-between text-[9px] font-semibold text-slate-400"><span>Line Height</span><span>{activeObjectProps.lineHeight !== undefined ? activeObjectProps.lineHeight.toFixed(2) : '1.16'}</span></div>
              <input type="range" min="0.8" max="3.0" step="0.05" value={activeObjectProps.lineHeight !== undefined ? activeObjectProps.lineHeight : 1.16}
                onChange={(e) => handlePropChange('lineHeight', parseFloat(e.target.value))}
                className="w-full h-1 bg-white rounded-lg appearance-none cursor-pointer accent-indigo-600" />
            </div>
            <div className="space-y-1">
              <div className="flex justify-between text-[9px] font-semibold text-slate-400"><span>Letter Spacing</span><span>{activeObjectProps.charSpacing !== undefined ? activeObjectProps.charSpacing : '0'}</span></div>
              <input type="range" min="-50" max="200" step="5" value={activeObjectProps.charSpacing !== undefined ? activeObjectProps.charSpacing : 0}
                onChange={(e) => handlePropChange('charSpacing', parseInt(e.target.value))}
                className="w-full h-1 bg-white rounded-lg appearance-none cursor-pointer accent-indigo-600" />
            </div>
            <div className="space-y-1 relative" id="font-family-selector-container">
              <span className="text-[9px] font-semibold text-slate-400">Font Family</span>
              
              {/* Trigger Button */}
              <button
                type="button"
                onClick={() => setIsFontDropdownOpen(!isFontDropdownOpen)}
                className="w-full bg-white border border-slate-200 rounded-xl px-3 py-2.5 text-xs text-slate-400 focus:outline-none focus:border-indigo-600 flex items-center justify-between hover:bg-white cursor-pointer"
                style={{ fontFamily: activeObjectProps.fontFamily || 'sans-serif' }}
              >
                <span className="truncate text-sm">
                  {numberedFonts.find(f => f.family_slug === activeObjectProps.fontFamily)?.seq ? 
                    `#${numberedFonts.find(f => f.family_slug === activeObjectProps.fontFamily)?.seq}. ` : ''}
                  {numberedFonts.find(f => f.family_slug === activeObjectProps.fontFamily)?.name || activeObjectProps.fontFamily || 'Outfit'}
                </span>
                <ChevronDown className={`w-3.5 h-3.5 text-slate-500 transition-transform duration-200 ${isFontDropdownOpen ? 'rotate-180' : ''}`} />
              </button>

              {/* Dropdown Popover */}
              {isFontDropdownOpen && (
                <div className="absolute z-[999] left-0 right-0 mt-1 bg-white border border-slate-200 rounded-xl shadow-xl flex flex-col max-h-[360px] overflow-hidden animate-in fade-in slide-in-from-top-1 duration-150">
                  {/* Search Bar */}
                  <div className="p-2 border-b border-slate-200/80 sticky top-0 bg-white z-10">
                    <input
                      type="text"
                      placeholder="Tìm kiếm font..."
                      value={fontSearchQuery}
                      onChange={(e) => setFontSearchQuery(e.target.value)}
                      className="w-full bg-white border border-slate-200 rounded-lg px-2.5 py-1.5 text-xs text-slate-400 focus:outline-none focus:border-indigo-600 font-sans"
                      autoFocus
                    />
                  </div>

                  {/* List Container */}
                  <div className="flex-1 overflow-y-auto divide-y divide-slate-900 scrollbar-thin scrollbar-thumb-slate-300">
                    {Object.keys(groupedFonts).length === 0 ? (
                      <div className="p-4 text-center text-xs text-slate-400 font-sans">
                        Không tìm thấy font nào
                      </div>
                    ) : (
                      ['Hệ thống', 'Quảng cáo', 'In ấn & Sách', 'Thư pháp & Nghệ thuật', 'Hiện đại', 'Cổ điển', 'Trang trí', 'Chưa phân loại']
                        .filter(cat => groupedFonts[cat] && groupedFonts[cat].length > 0)
                        .concat(Object.keys(groupedFonts).filter(cat => !['Hệ thống', 'Quảng cáo', 'In ấn & Sách', 'Thư pháp & Nghệ thuật', 'Hiện đại', 'Cổ điển', 'Trang trí', 'Chưa phân loại'].includes(cat) && groupedFonts[cat].length > 0))
                        .map(category => (
                          <div key={category} className="bg-white">
                            {/* Category Header */}
                            <div className="bg-white/60 text-slate-500 font-bold uppercase text-[9px] tracking-wider py-1 px-3 border-y border-slate-200/40 sticky top-0 backdrop-blur-md font-sans">
                              {category}
                            </div>
                            
                            {/* Font List Items */}
                            <div className="py-1">
                              {groupedFonts[category].map(f => (
                                <button
                                  key={f.family_slug}
                                  type="button"
                                  onClick={() => {
                                    handlePropChange('fontFamily', f.family_slug);
                                    setIsFontDropdownOpen(false);
                                    setFontSearchQuery('');
                                  }}
                                  className={`w-full text-left px-3 py-2 hover:bg-indigo-600/10 transition-colors flex items-center justify-between cursor-pointer group ${
                                    activeObjectProps.fontFamily === f.family_slug ? 'bg-indigo-600/5 border-l-2 border-indigo-500' : ''
                                  }`}
                                >
                                  {/* Font Name with sequential number and formatted preview */}
                                  <div className="flex flex-col min-w-0">
                                    <div className="flex items-center gap-1.5 text-[10px] text-slate-400 group-hover:text-indigo-600 font-sans">
                                      <span className="font-mono font-bold">
                                        #{String(f.seq).padStart(2, '0')}
                                      </span>
                                      <span>•</span>
                                      <span className="truncate">{f.name}</span>
                                    </div>
                                    <span 
                                      className="text-base text-slate-400 group-hover:text-slate-800 truncate pt-0.5"
                                      style={{ fontFamily: f.family_slug }}
                                    >
                                      {f.name}
                                    </span>
                                  </div>

                                  {activeObjectProps.fontFamily === f.family_slug && (
                                    <span className="text-xs text-indigo-600 font-bold font-sans">✓</span>
                                  )}
                                </button>
                              ))}
                            </div>
                          </div>
                        ))
                    )}
                  </div>
                </div>
              )}
            </div>
            <div className="space-y-1 pt-2 border-t border-slate-200/30">
              <span className="text-[9px] font-semibold text-slate-400">Màu nền chữ</span>
              <div className="flex gap-2">
                <input type="color" value={rgbaToHex(activeObjectProps.textBackgroundColor || 'rgba(0,0,0,0)')}
                  onChange={(e) => handlePropChange('textBackgroundColor', e.target.value)}
                  className="w-8 h-8 rounded-lg bg-transparent border border-slate-200 cursor-pointer overflow-hidden" />
                <Input value={activeObjectProps.textBackgroundColor || ''} onChange={(e) => handlePropChange('textBackgroundColor', e.target.value)}
                  className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-8 font-mono flex-1" placeholder="Không màu nền" />
                {activeObjectProps.textBackgroundColor && (
                  <Button size="sm" variant="destructive" onClick={() => handlePropChange('textBackgroundColor', null)} className="h-8 rounded-xl px-2.5 cursor-pointer text-xs">Xóa</Button>
                )}
              </div>
            </div>

            {/* Presets and Custom Presets */}
            <div className="space-y-2 pt-2 border-t border-slate-200/30">
              <span className="text-[9px] font-semibold text-slate-400 block">Mẫu chữ nhanh (Presets)</span>
              <div className="grid grid-cols-2 gap-1.5">
                {[
                  { name: 'Tiêu đề chính', font: 'Outfit', size: 90, weight: 'bold' },
                  { name: 'Tiêu đề phụ', font: 'Outfit', size: 45, weight: '600' },
                  { name: 'Văn bản thân', font: 'Outfit', size: 24, weight: 'normal' },
                  { name: 'Chú thích', font: 'Outfit', size: 16, weight: 'normal' },
                ].map((p, idx) => (
                  <Button
                    key={idx}
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() => {
                      handlePropChange('fontFamily', p.font);
                      handlePropChange('fontSize', p.size);
                      handlePropChange('fontWeight', p.weight);
                    }}
                    className="bg-white border-slate-200 text-[10px] py-1 h-7 rounded-xl text-slate-500 hover:text-slate-800 cursor-pointer hover:bg-white"
                  >
                    {p.name}
                  </Button>
                ))}
              </div>

              {customPresets.length > 0 && (
                <div className="space-y-1.5 pt-1.5">
                  <span className="text-[8px] font-semibold text-slate-400 block">Mẫu đã lưu</span>
                  <div className="space-y-1">
                    {customPresets.map((p) => (
                      <div key={p.id} className="flex items-center gap-1.5">
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          onClick={() => {
                            handlePropChange('fontFamily', p.fontFamily);
                            handlePropChange('fontSize', p.fontSize);
                            handlePropChange('fontWeight', p.fontWeight);
                            handlePropChange('fontStyle', p.fontStyle);
                            handlePropChange('fill', p.fill);
                          }}
                          className="flex-1 bg-white border-slate-200 text-[10px] text-left justify-start px-2.5 h-7 rounded-xl text-slate-400 hover:text-slate-800 cursor-pointer truncate"
                        >
                          {p.name}
                        </Button>
                        <Button
                          type="button"
                          size="icon"
                          variant="ghost"
                          onClick={() => deleteCustomPreset(p.id)}
                          className="w-7 h-7 text-rose-500 hover:bg-rose-500/10 rounded-xl"
                          title="Xóa mẫu chữ này"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </Button>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              <Button
                type="button"
                size="sm"
                onClick={saveCurrentAsPreset}
                className="w-full bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-[10px] font-bold h-7.5 mt-1 cursor-pointer"
              >
                + Lưu định dạng hiện tại làm mẫu
              </Button>
            </div>

            {/* Auto Fit toggle */}
            <div className="flex items-center justify-between pt-2 border-t border-slate-200/30">
              <div className="flex flex-col">
                <span className="text-[10px] font-semibold text-slate-400">Co chữ vừa khung</span>
                <span className="text-[8px] text-slate-400">Giữ chữ nằm gọn trong khung viền</span>
              </div>
              <input
                type="checkbox"
                checked={!!activeObjectProps.autoFit}
                onChange={(e) => {
                  const checked = e.target.checked;
                  if (checked) {
                    const canvasInstance = canvas;
                    const activeObj = canvasInstance?.getActiveObject();
                    if (activeObj) {
                      const h = activeObj.height * activeObj.scaleY;
                      handlePropChange('autoFit', true);
                      handlePropChange('maxHeight', h);
                    }
                  } else {
                    handlePropChange('autoFit', false);
                    handlePropChange('maxHeight', null);
                  }
                }}
                className="rounded text-indigo-600 focus:ring-indigo-600 bg-white border-slate-200 cursor-pointer"
              />
            </div>
          </CollapsibleSection>
        )}

        {/* Section 5: Corner Radius (Rect only) */}
        {activeObjectProps.rx !== undefined && (
          <CollapsibleSection id="corner-radius" icon={<Maximize2 className="w-3 h-3" />} title="Bo góc">
            <div className="space-y-1.5">
              <div className="flex justify-between text-[9px] font-semibold text-slate-400"><span>Corner Radius</span><span>{Math.round(activeObjectProps.rx)}px</span></div>
              <div className="flex gap-3 items-center">
                <input type="range" min="0" max="100" step="1" value={activeObjectProps.rx || 0}
                  onChange={(e) => { const v = parseInt(e.target.value) || 0; updateActiveObject({ rx: v, ry: v }); }}
                  className="flex-1 h-1 bg-white rounded-lg appearance-none cursor-pointer accent-indigo-600" />
                <Input type="number" min="0" max="200" value={Math.round(activeObjectProps.rx || 0)}
                  onChange={(e) => { const v = Math.max(0, parseInt(e.target.value) || 0); updateActiveObject({ rx: v, ry: v }); }}
                  className="w-14 bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-7 text-center px-1" />
              </div>
            </div>
          </CollapsibleSection>
        )}

        {/* Section 6: Stroke */}
        {(activeObjectProps.layerType === 'TEXT' || activeObjectProps.layerType === 'DECORATION') && (
          <CollapsibleSection id="stroke" icon={<Settings className="w-3 h-3" />} title="Viền (Stroke)">
            <div className="space-y-1">
              <span className="text-[9px] font-semibold text-slate-400">Màu viền</span>
              <div className="flex gap-2">
                <input type="color" value={rgbaToHex(activeObjectProps.stroke || 'rgba(0,0,0,0)')}
                  onChange={(e) => handlePropChange('stroke', e.target.value)}
                  className="w-8 h-8 rounded-lg bg-transparent border border-slate-200 cursor-pointer overflow-hidden" />
                <Input value={activeObjectProps.stroke || ''} onChange={(e) => handlePropChange('stroke', e.target.value)}
                  className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-8 font-mono flex-1" placeholder="Không viền" />
              </div>
            </div>
            <div className="space-y-1.5">
              <div className="flex justify-between text-[9px] font-semibold text-slate-400"><span>Độ dày</span><span>{activeObjectProps.strokeWidth || 0}px</span></div>
              <div className="flex gap-3 items-center">
                <input type="range" min="0" max="50" step="1" value={activeObjectProps.strokeWidth || 0}
                  onChange={(e) => handlePropChange('strokeWidth', parseInt(e.target.value) || 0)}
                  className="flex-1 h-1 bg-white rounded-lg appearance-none cursor-pointer accent-indigo-600" />
                <Input type="number" min="0" max="100" value={activeObjectProps.strokeWidth || 0}
                  onChange={(e) => handlePropChange('strokeWidth', parseInt(e.target.value) || 0)}
                  className="w-14 bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-7 text-center px-1" />
              </div>
            </div>
            <div className="space-y-1">
              <span className="text-[9px] font-semibold text-slate-400">Kiểu viền</span>
              <select value={activeObjectProps.strokeDashArray ? JSON.stringify(activeObjectProps.strokeDashArray) : ''}
                onChange={(e) => handlePropChange('strokeDashArray', e.target.value ? JSON.parse(e.target.value) : null)}
                className="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-400 focus:outline-none focus:border-indigo-600">
                <option value="">Liền nét</option>
                <option value="[10,5]">Đứt nét thưa</option>
                <option value="[5,5]">Đứt nét dày</option>
                <option value="[2,2]">Chấm bi</option>
              </select>
            </div>
          </CollapsibleSection>
        )}

        {/* Section 7: Shadow */}
        <CollapsibleSection id="shadow" icon={<AlignHorizontalSpaceAround className="w-3 h-3" />} title="Đổ bóng (Shadow)">
          <div className="flex items-center justify-between border-b border-slate-200/50 pb-2 mb-2">
            <span className="text-[9px] font-semibold text-slate-500">Bật/Tắt</span>
            <input type="checkbox" checked={activeObjectProps.shadow !== null}
              onChange={(e) => {
                if (e.target.checked) {
                  applyShadowPreset('medium');
                } else { updateActiveObject({ shadow: null }); }
              }}
              className="rounded text-indigo-600 focus:ring-indigo-600 bg-white border-slate-200 cursor-pointer" />
          </div>
          {activeObjectProps.shadow !== null && (
            <div className="space-y-3 animate-in fade-in duration-200">
              
              {/* Presets Grid */}
              <div className="space-y-1">
                <span className="text-[9px] font-semibold text-slate-400">Mẫu bóng nhanh</span>
                <div className="grid grid-cols-4 gap-1">
              {[
                    { key: 'soft', label: 'Nhẹ' },
                    { key: 'medium', label: 'Vừa' },
                    { key: 'hard', label: 'Đậm' },
                    { key: 'glow', label: 'Glow' }
                  ].map(preset => (
                    <Button
                      key={preset.key}
                      size="sm"
                      variant="outline"
                      onClick={() => applyShadowPreset(preset.key as any)}
                      className="rounded-lg border-slate-200 bg-white text-[10px] p-0 h-7 text-slate-500 hover:text-slate-800 cursor-pointer"
                    >
                      {preset.label}
                    </Button>
                  ))}
                </div>
              </div>

              <div className="space-y-1">
                <Button
                  size="sm"
                  variant="outline"
                  onClick={createShadowRegion}
                  className="w-full rounded-lg border-slate-200 bg-white text-[10px] p-0 h-8 text-slate-400 hover:text-slate-800 cursor-pointer gap-1"
                >
                  <MoonStar className="w-3.5 h-3.5" />
                  Tạo vùng bóng
                </Button>
                <p className="text-[9px] text-slate-400 leading-normal">
                  Tạo một dải bóng mềm riêng bên dưới đối tượng để chỉnh như một layer độc lập.
                </p>
              </div>

              {/* Color Picker */}
              <div className="space-y-1">
                <span className="text-[9px] font-semibold text-slate-400">Màu bóng</span>
                <div className="flex gap-2">
                  <input type="color" value={rgbaToHex(activeObjectProps.shadow?.color || 'rgba(0,0,0,0.4)')}
                    onChange={(e) => handleShadowChange('color', hexToRgba(e.target.value, activeObjectProps.shadow?.color || 'rgba(0,0,0,0.4)'))}
                    className="w-8 h-8 rounded-lg bg-transparent border border-slate-200 cursor-pointer overflow-hidden" />
                  <Input value={activeObjectProps.shadow?.color || 'rgba(0,0,0,0.4)'}
                    onChange={(e) => handleShadowChange('color', e.target.value)}
                    className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-8 font-mono flex-1" />
                </div>
              </div>

              {/* Opacity Slider */}
              <div className="space-y-1.5">
                <div className="flex justify-between text-[9px] font-semibold text-slate-400">
                  <span>Độ đậm (Opacity)</span>
                  <span>{getShadowOpacity(activeObjectProps.shadow?.color || 'rgba(0,0,0,0.4)')}%</span>
                </div>
                <div className="flex gap-3 items-center">
                  <input type="range" min="0" max="100" value={getShadowOpacity(activeObjectProps.shadow?.color || 'rgba(0,0,0,0.4)')}
                    onChange={(e) => {
                      const newOpacity = parseInt(e.target.value);
                      const currentGrad = activeObjectProps.shadow?.color || 'rgba(0,0,0,0.4)';
                      handleShadowChange('color', setShadowOpacity(currentGrad, newOpacity));
                    }}
                    className="flex-1 h-1 bg-white rounded-lg appearance-none cursor-pointer accent-indigo-600" />
                  <Input type="number" min="0" max="100" value={getShadowOpacity(activeObjectProps.shadow?.color || 'rgba(0,0,0,0.4)')}
                    onChange={(e) => {
                      const newOpacity = Math.max(0, Math.min(100, parseInt(e.target.value) || 0));
                      const currentGrad = activeObjectProps.shadow?.color || 'rgba(0,0,0,0.4)';
                      handleShadowChange('color', setShadowOpacity(currentGrad, newOpacity));
                    }}
                    className="w-12 bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-7 text-center px-1" />
                </div>
              </div>

              {/* Blur Slider */}
              <div className="space-y-1.5">
                <div className="flex justify-between text-[9px] font-semibold text-slate-400">
                  <span>Độ mờ (Blur)</span>
                  <span>{activeObjectProps.shadow?.blur || 0}px</span>
                </div>
                <div className="flex gap-3 items-center">
                  <input type="range" min="0" max="100" value={activeObjectProps.shadow?.blur || 0}
                    onChange={(e) => handleShadowChange('blur', parseInt(e.target.value))}
                    className="flex-1 h-1 bg-white rounded-lg appearance-none cursor-pointer accent-indigo-600" />
                  <Input type="number" min="0" max="100" value={activeObjectProps.shadow?.blur || 0}
                    onChange={(e) => handleShadowChange('blur', Math.max(0, Math.min(100, parseInt(e.target.value) || 0)))}
                    className="w-12 bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-7 text-center px-1" />
                </div>
              </div>

              {/* Distance Slider */}
              <div className="space-y-1.5">
                <div className="flex justify-between text-[9px] font-semibold text-slate-400">
                  <span>Khoảng cách (Distance)</span>
                  <span>{currentDistance}px</span>
                </div>
                <div className="flex gap-3 items-center">
                  <input type="range" min="0" max="150" value={currentDistance}
                    onChange={(e) => handleShadowChange('distance', parseInt(e.target.value))}
                    className="flex-1 h-1 bg-white rounded-lg appearance-none cursor-pointer accent-indigo-600" />
                  <Input type="number" min="0" max="150" value={currentDistance}
                    onChange={(e) => handleShadowChange('distance', Math.max(0, Math.min(150, parseInt(e.target.value) || 0)))}
                    className="w-12 bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-7 text-center px-1" />
                </div>
              </div>

              {/* Angle Slider */}
              <div className="space-y-1.5">
                <div className="flex justify-between text-[9px] font-semibold text-slate-400">
                  <span>Góc đổ bóng (Angle)</span>
                  <span>{currentAngle}°</span>
                </div>
                <div className="flex gap-3 items-center">
                  <input type="range" min="0" max="360" value={currentAngle}
                    onChange={(e) => handleShadowChange('angle', parseInt(e.target.value))}
                    className="flex-1 h-1 bg-white rounded-lg appearance-none cursor-pointer accent-indigo-600" />
                  <Input type="number" min="0" max="360" value={currentAngle}
                    onChange={(e) => handleShadowChange('angle', Math.max(0, Math.min(360, parseInt(e.target.value) || 0)))}
                    className="w-12 bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-7 text-center px-1" />
                </div>
              </div>

            </div>
          )}
        </CollapsibleSection>

        {/* Section 8: Image Filters */}
        {isImageObject && (
          <CollapsibleSection id="image-filters" icon={<ImageIcon className="w-3 h-3" />} title="Hiệu ứng Ảnh">
            {[
              { key: 'brightness', label: 'Độ sáng', min: -1, max: 1, step: 0.05 },
              { key: 'contrast', label: 'Tương phản', min: -1, max: 1, step: 0.05 },
              { key: 'saturation', label: 'Bão hòa', min: -1, max: 1, step: 0.05 },
              { key: 'blur', label: 'Làm mờ', min: 0, max: 1, step: 0.05 },
            ].map(({ key, label, min, max, step }) => (
              <div key={key} className="space-y-1">
                <div className="flex justify-between text-[9px] font-semibold text-slate-400"><span>{label}</span><span>{activeObjectProps.imageFilters?.[key] || 0}</span></div>
                <input type="range" min={min} max={max} step={step} value={activeObjectProps.imageFilters?.[key] || 0}
                  onChange={(e) => handleImageFilterChange(key, parseFloat(e.target.value))}
                  className="w-full h-1 bg-white rounded-lg appearance-none cursor-pointer accent-indigo-600" />
              </div>
            ))}
            <div className="flex gap-4 pt-2 border-t border-slate-200/50">
              <label className="flex items-center gap-2 cursor-pointer">
                <input type="checkbox" checked={activeObjectProps.imageFilters?.grayscale || false}
                  onChange={(e) => handleImageFilterChange('grayscale', e.target.checked)}
                  className="rounded text-indigo-600 focus:ring-indigo-600 bg-white border-slate-200" />
                <span className="text-[10px] font-semibold text-slate-500">Trắng đen</span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer">
                <input type="checkbox" checked={activeObjectProps.imageFilters?.sepia || false}
                  onChange={(e) => handleImageFilterChange('sepia', e.target.checked)}
                  className="rounded text-indigo-600 focus:ring-indigo-600 bg-white border-slate-200" />
                <span className="text-[10px] font-semibold text-slate-500">Sepia</span>
              </label>
            </div>

            <div className="pt-3 border-t border-slate-200/50 space-y-1.5">
              <span className="text-[9px] font-semibold text-slate-400 block">Bộ lọc nhanh (Presets)</span>
              <div className="grid grid-cols-3 gap-1.5">
                {[
                  { key: 'none', label: 'Mặc định' },
                  { key: 'vintage', label: 'Cổ điển' },
                  { key: 'cool', label: 'Mát lạnh' },
                  { key: 'warm', label: 'Ấm áp' },
                  { key: 'grayscale', label: 'Trắng đen' },
                  { key: 'sepia', label: 'Sepia' },
                ].map((p) => (
                  <button
                    key={p.key}
                    type="button"
                    onClick={() => applyFilterPreset(p.key as any)}
                    className="px-2 py-1 rounded-lg border border-slate-200 bg-white text-[9px] font-semibold text-slate-500 hover:text-slate-800 hover:border-slate-300 transition-colors"
                  >
                    {p.label}
                  </button>
                ))}
              </div>
            </div>

            <div className="pt-3 border-t border-slate-200/50 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-[9px] font-semibold text-slate-400">Tô bóng / đổi tone</span>
                <button
                  onClick={() => applyTonePreset(null, false, 1)}
                  className="text-[9px] font-semibold text-slate-400 hover:text-rose-400 cursor-pointer"
                >
                  Xóa tone
                </button>
              </div>
              <div className="flex flex-wrap gap-1.5">
                {[
                  { label: 'Xám', color: '#808080', gray: true },
                  { label: 'Đen', color: '#202020', gray: true },
                  { label: 'Xanh', color: '#4f83ff', gray: false },
                  { label: 'Nâu', color: '#9c6b3f', gray: false },
                ].map((preset) => (
                  <button
                    key={preset.label}
                    onClick={() => applyTonePreset(preset.color, preset.gray, 1)}
                    className={`px-2.5 py-1 rounded-lg border text-[10px] font-bold cursor-pointer transition-all ${
                      activeObjectProps.imageFilters?.toneColor === preset.color
                        ? 'bg-indigo-600/15 border-indigo-500/50 text-indigo-300'
                        : 'bg-white border-slate-200 text-slate-500 hover:text-slate-700 hover:border-slate-300'
                    }`}
                  >
                    {preset.label}
                  </button>
                ))}
              </div>
              <div className="grid grid-cols-[auto_1fr_auto] items-center gap-2">
                <input
                  type="color"
                  value={activeObjectProps.imageFilters?.toneColor || '#808080'}
                  onChange={(e) => applyTonePreset(e.target.value, true, activeObjectProps.imageFilters?.toneAlpha ?? 1)}
                  className="w-9 h-8 rounded-lg bg-white border border-slate-200 cursor-pointer"
                  title="Chọn màu tone"
                />
                <input
                  type="range"
                  min="0"
                  max="100"
                  value={Math.round((activeObjectProps.imageFilters?.toneAlpha ?? 1) * 100)}
                  onChange={(e) => applyTonePreset(activeObjectProps.imageFilters?.toneColor || '#808080', true, Math.max(0, Math.min(1, (parseInt(e.target.value) || 0) / 100)))}
                  className="w-full h-1 bg-white rounded-lg appearance-none cursor-pointer accent-indigo-600"
                  title="Mức tone"
                />
                <span className="text-[9px] font-semibold text-slate-400 w-9 text-right">
                  {Math.round((activeObjectProps.imageFilters?.toneAlpha ?? 1) * 100)}%
                </span>
              </div>
              <p className="text-[9px] text-slate-400 leading-normal">
                Dùng cho object PNG/ảnh cắt nền để giữ form nhưng đổi thành bóng màu hoặc xám.
              </p>
            </div>
          </CollapsibleSection>
        )}

        {/* Section 8.5: AI & Masking (IMAGE only) */}
        {isImageObject && (
          <CollapsibleSection id="image-ai-mask" icon={<Sparkles className="w-3.5 h-3.5" />} title="Cắt ảnh & Nền (AI)" defaultOpen={true}>
            <div className="space-y-3">
              {/* AI Background Removal */}
              <div className="space-y-1">
                <span className="text-[10px] font-semibold text-slate-400">Công cụ AI</span>
                <Button
                  size="sm"
                  onClick={handleRemoveBackground}
                  disabled={isRemovingBg}
                  className="w-full bg-gradient-to-r from-violet-600 to-indigo-600 hover:from-violet-500 hover:to-indigo-500 text-white rounded-xl text-xs font-bold h-8 flex items-center justify-center gap-1.5 shadow-lg disabled:opacity-50 cursor-pointer"
                >
                  <Sparkles className="w-3.5 h-3.5 animate-pulse" />
                  {isRemovingBg ? 'Đang xóa nền...' : 'Xóa nền ảnh (AI)'}
                </Button>
                <p className="text-[8px] text-slate-400">Tách nền chủ thể tự động bằng Google MediaPipe WebAssembly.</p>
              </div>

              {/* Replace Image */}
              <div className="space-y-1 pt-1.5 border-t border-slate-200/50">
                <span className="text-[10px] font-semibold text-slate-400">Thay thế ảnh</span>
                <div className="flex gap-2">
                  <input
                    type="file"
                    accept="image/*"
                    id="replace-image-file-input"
                    className="hidden"
                    onChange={handleReplaceImageFile}
                  />
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => document.getElementById('replace-image-file-input')?.click()}
                    className="w-full rounded-xl border-slate-200 bg-white text-slate-400 hover:text-slate-800 hover:bg-white text-xs font-semibold h-8 cursor-pointer flex items-center justify-center gap-1.5"
                  >
                    <RefreshCw className="w-3.5 h-3.5" />
                    Tải ảnh lên thay thế
                  </Button>
                </div>
              </div>

              {/* Shape Mask / Clip to Shape */}
              <div className="space-y-1.5 pt-1.5 border-t border-slate-200/50">
                <span className="text-[10px] font-semibold text-slate-400">Cắt theo hình dạng (Mask)</span>
                <div className="grid grid-cols-3 gap-1.5">
                  {[
                    { key: 'none', label: 'Mặc định' },
                    { key: 'circle', label: 'Hình tròn' },
                    { key: 'rounded-rect', label: 'Bo góc' },
                    { key: 'heart', label: 'Trái tim' },
                    { key: 'star', label: 'Ngôi sao' },
                    { key: 'hexagon', label: 'Lục giác' },
                  ].map((shape) => (
                    <Button
                      key={shape.key}
                      size="sm"
                      variant="outline"
                      onClick={() => handleClipShapeChange(shape.key as any)}
                      className={`rounded-xl border-slate-200 text-[10px] p-0 h-8 cursor-pointer ${
                        (activeObjectProps.clipShape || 'none') === shape.key
                          ? 'bg-indigo-50 text-indigo-600 border-indigo-500/30'
                          : 'bg-white text-slate-500 hover:text-slate-800'
                      }`}
                    >
                      {shape.label}
                    </Button>
                  ))}
                </div>
              </div>
            </div>
          </CollapsibleSection>
        )}

        {/* Section 9: Placeholder Constraints (IMAGE only) */}
        {isImageObject && (
          <CollapsibleSection id="placeholder" icon={<ImageIcon className="w-3 h-3" />} title="Placeholder">
            <div className="space-y-1">
              <span className="text-[9px] font-semibold text-slate-400">URL ảnh mặc định</span>
              <Input value={activeObjectProps.defaultImageUrl || ''} onChange={(e) => handlePropChange('defaultImageUrl', e.target.value)}
                placeholder="https://pub-yourbucket.r2.dev/..." className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl" />
            </div>
            <div className="space-y-1">
              <span className="text-[9px] font-semibold text-slate-400">Tỷ lệ cắt (Crop Ratio)</span>
              <select value={activeObjectProps.cropRatio || ''} onChange={(e) => handlePropChange('cropRatio', e.target.value || null)}
                className="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-400 focus:outline-none focus:border-indigo-600">
                <option value="">Tự do</option>
                <option value="ORIGINAL">Giữ tỷ lệ gốc</option>
                <option value="RATIO_1_1">1:1 (Vuông)</option>
                <option value="RATIO_3_4">3:4 (Chân dung)</option>
                <option value="RATIO_4_3">4:3 (Ngang)</option>
                <option value="RATIO_9_16">9:16 (Dọc dài)</option>
                <option value="RATIO_16_9">16:9 (Ngang rộng)</option>
              </select>
            </div>
            <div className="space-y-1 pt-2">
              <Button
                size="sm"
                onClick={() => {
                  const imageUrl = activeObjectProps?.src || activeObjectProps?.defaultImageUrl || '';
                  if (!imageUrl) {
                    toast.error('Chưa có ảnh nào để cắt.');
                    return;
                  }
                  openCrop({
                    imageUrl,
                    sourceName: activeObjectProps?.layerName || 'Ảnh',
                    initialRatio: activeObjectProps?.cropRatio || 'FREE',
                  });
                }}
                className="w-full bg-indigo-600 hover:bg-indigo-500 rounded-xl text-xs font-bold h-8 text-white flex items-center justify-center gap-1.5 cursor-pointer"
              >
                <CropIcon className="w-3.5 h-3.5" />
                Trích xuất vùng cắt làm layer mới
              </Button>
            </div>
            <p className="text-[9px] text-slate-400 leading-normal mt-1">URL ảnh ban đầu trên app Android. Người dùng có thể thay bằng ảnh của họ.</p>
          </CollapsibleSection>
        )}
      </div>
    </div>
  );
}
