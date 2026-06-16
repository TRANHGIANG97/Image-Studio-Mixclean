'use client';

import React, { useState, useCallback } from 'react';
import { ActiveSelection } from 'fabric';
import {
  Sliders, Trash2, FlipHorizontal, FlipVertical,
  Maximize2, AlignHorizontalSpaceAround, Settings,
  ChevronRight, EyeOff, Image as ImageIcon, Palette,
  Bold, Italic, Underline, AlignLeft, AlignCenter, AlignRight,
  Strikethrough, ChevronDown, Pipette, Copy, Crop as CropIcon, MoonStar,
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

// ==================== Quick Color Palette ====================

// ==================== Main Component ====================
export default function PropertiesPanel() {
  const { canvas, pushState } = useEditorStore();
  const { copyToClipboard } = useEditorStore();
  const { activeObjectId, activeObjectProps, updateActiveObject } = useLayersStore();
  const { openCrop } = useCropStore();
  const [dynamicFonts, setDynamicFonts] = useState<{ name: string; family_slug: string; font_url: string }[]>([]);
  const [bgUrlInput, setBgUrlInput] = useState('');
  const { recentColors, addRecentColor } = useRecentColors();
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

  const handlePropChange = (name: string, value: any) => {
    if (name === 'fill' && typeof value === 'string') addRecentColor(value);
    if (name === 'stroke' && typeof value === 'string') addRecentColor(value);
    updateActiveObject({ [name]: value });
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
          <span className="text-[8px] font-semibold text-slate-500">Gần đây</span>
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
        <span className="text-[8px] font-semibold text-slate-500">Presets</span>
        <div className="flex gap-1 flex-wrap">
          {PRESET_COLORS.map(c => (
            <button key={c} onClick={() => onSelect(c)}
              className="w-5 h-5 rounded-md border border-slate-700 transition-transform hover:scale-110 active:scale-95 cursor-pointer"
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
        <div className="flex items-center gap-1.5 px-1 border-b border-slate-800 pb-3">
          <Palette className="w-4 h-4 text-indigo-400" />
          <h3 className="font-bold text-sm text-slate-200">Quản lý Ảnh Nền</h3>
        </div>

        <div className="flex-1 overflow-y-auto pr-1 space-y-4 pb-8 scrollbar-thin scrollbar-thumb-slate-800">

          {/* Background Image */}
          <CollapsibleSection id="bg-image" icon={<ImageIcon className="w-3.5 h-3.5" />} title="Ảnh nền Template" defaultOpen={true}>
            <div className="aspect-video w-full rounded-xl bg-slate-950 border border-slate-800 flex items-center justify-center overflow-hidden relative group">
              {currentBgUrl ? (
                <img src={currentBgUrl} alt="Background" className="w-full h-full object-cover" />
              ) : (
                <div className="text-[10px] text-slate-600 font-mono">Trống (Màu nền đơn)</div>
              )}
              {currentBgUrl && (
                <div className="absolute inset-0 bg-slate-950/80 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
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
              <span className="text-[9px] font-semibold text-slate-500">Đổi ảnh nền bằng URL</span>
              <div className="flex gap-2">
                <Input placeholder="Dán liên kết ảnh..." value={bgUrlInput} onChange={(e) => setBgUrlInput(e.target.value)}
                  className="bg-slate-950 border-slate-800 text-xs text-slate-300 rounded-xl h-8 flex-1" />
                <Button size="sm" onClick={() => handleBackgroundUrlChange(bgUrlInput)} className="bg-indigo-600 hover:bg-indigo-500 rounded-xl text-xs font-bold h-8 px-3 cursor-pointer">
                  Áp dụng
                </Button>
              </div>
            </div>
          </CollapsibleSection>

          {/* Background Color & Gradient */}
          <CollapsibleSection id="bg-color" icon={<Palette className="w-3.5 h-3.5" />} title="Màu nền & Chuyển sắc" defaultOpen={true}>
            <div className="flex p-0.5 bg-slate-950 border border-slate-800 rounded-xl">
              {(['solid', 'linear', 'radial'] as const).map(t => (
                <button key={t} type="button" onClick={() => {
                  if (t !== bgFillType) {
                    if (t === 'solid') handleBackgroundColorChange(bgColor1);
                    else handleBackgroundGradientChange(t, bgColor1, bgColor2, bgAngle);
                  }
                }} className={`flex-1 py-1 rounded-lg text-[10px] font-bold transition-all cursor-pointer ${bgFillType === t ? 'bg-indigo-600/20 text-indigo-400 border border-indigo-500/20' : 'text-slate-500 hover:text-slate-300'}`}>
                  {t === 'solid' ? 'Đơn' : t === 'linear' ? 'Linear' : 'Radial'}
                </button>
              ))}
            </div>
            {bgFillType === 'solid' ? (
              <div className="space-y-1">
                <span className="text-[9px] font-semibold text-slate-500">Chọn màu nền</span>
                <div className="flex gap-2">
                  <input type="color" value={rgbaToHex(bgColor1)} onChange={(e) => handleBackgroundColorChange(e.target.value)}
                    className="w-8 h-8 rounded-lg bg-transparent border border-slate-800 cursor-pointer overflow-hidden" />
                  <Input value={bgColor1} onChange={(e) => handleBackgroundColorChange(e.target.value)}
                    className="bg-slate-950 border-slate-800 text-xs text-slate-300 rounded-xl h-8 font-mono flex-1" />
                  <Button size="icon" variant="ghost" onClick={handleEyedropper} className="w-8 h-8 rounded-lg text-slate-400 hover:text-white cursor-pointer" title="Công cụ lấy màu">
                    <Pipette className="w-3.5 h-3.5" />
                  </Button>
                </div>
                <ColorPalette onSelect={handleBackgroundColorChange} selectedColor={bgColor1} />
              </div>
            ) : (
              <div className="space-y-3 pt-1">
                {(['color1', 'color2'] as const).map((stop, i) => (
                  <div key={stop} className="space-y-1">
                    <span className="text-[9px] font-semibold text-slate-500">{i === 0 ? 'Màu bắt đầu' : 'Màu kết thúc'}</span>
                    <div className="flex gap-2">
                      <input type="color" value={rgbaToHex(i === 0 ? bgColor1 : bgColor2)}
                        onChange={(e) => handleBackgroundGradientChange(bgFillType, i === 0 ? e.target.value : bgColor1, i === 0 ? bgColor2 : e.target.value, bgAngle)}
                        className="w-8 h-8 rounded-lg bg-transparent border border-slate-800 cursor-pointer overflow-hidden" />
                      <Input value={i === 0 ? bgColor1 : bgColor2}
                        onChange={(e) => handleBackgroundGradientChange(bgFillType, i === 0 ? e.target.value : bgColor1, i === 0 ? bgColor2 : e.target.value, bgAngle)}
                        className="bg-slate-950 border-slate-800 text-xs text-slate-300 rounded-xl h-8 font-mono flex-1" />
                    </div>
                  </div>
                ))}
                {bgFillType === 'linear' && (
                  <div className="space-y-1.5">
                    <div className="flex justify-between text-[9px] font-semibold text-slate-500"><span>Góc</span><span>{bgAngle}°</span></div>
                    <div className="flex gap-3 items-center">
                      <input type="range" min="0" max="360" step="5" value={bgAngle}
                        onChange={(e) => handleBackgroundGradientChange('linear', bgColor1, bgColor2, parseInt(e.target.value))}
                        className="flex-1 h-1 bg-slate-950 rounded-lg appearance-none cursor-pointer accent-indigo-600" />
                      <Input type="number" min="0" max="360" value={bgAngle}
                        onChange={(e) => handleBackgroundGradientChange('linear', bgColor1, bgColor2, Math.max(0, Math.min(360, parseInt(e.target.value) || 0)))}
                        className="w-14 bg-slate-950 border-slate-800 text-xs text-slate-300 rounded-xl h-7 text-center px-1" />
                    </div>
                  </div>
                )}
              </div>
            )}
          </CollapsibleSection>

          <div className="flex flex-col items-center justify-center py-4 text-slate-600 space-y-1 text-center px-4">
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

  return (
    <div className="flex flex-col h-full space-y-4 animate-in fade-in duration-200">
      <div className="flex items-center gap-1.5 px-1 border-b border-slate-800 pb-3 shrink-0">
        <Settings className="w-4 h-4 text-indigo-400" />
        <h3 className="font-bold text-sm text-slate-200">Properties</h3>
        {selectedCount > 1 && (
          <span className="ml-auto text-[10px] bg-indigo-600/20 text-indigo-400 px-1.5 py-0.5 rounded-md font-bold">
            {selectedCount} layers
          </span>
        )}
      </div>

      <div className="flex items-center gap-2 px-1">
        <Button
          size="sm"
          variant="outline"
          onClick={() => copyToClipboard()}
          className="h-8 px-3 rounded-xl border-slate-800 bg-slate-950 text-slate-300 hover:text-white hover:bg-slate-900 text-xs font-semibold cursor-pointer"
        >
          <Copy className="w-3.5 h-3.5 mr-1.5" /> Sao chép đối tượng
        </Button>
      </div>

      <div className="flex-1 overflow-y-auto pr-1 scrollbar-thin scrollbar-thumb-slate-800 space-y-4 pb-8">

        {/* Section 1: Layer Info */}
        <CollapsibleSection id="layer-info" icon={<Settings className="w-3 h-3" />} title="Layer Info" defaultOpen={true}>
          <div className="space-y-1">
            <label className="text-[9px] font-semibold text-slate-500">Tên Layer</label>
            <Input value={activeObjectProps.layerName || ''} onChange={(e) => handlePropChange('layerName', e.target.value)}
              className="bg-slate-950 border-slate-800 text-xs text-slate-200 rounded-xl focus-visible:ring-indigo-600" />
          </div>
          <div className="space-y-1">
            <label className="text-[9px] font-semibold text-slate-500">Loại Layer</label>
            <select value={activeObjectProps.layerType || 'DECORATION'} onChange={(e) => handlePropChange('layerType', e.target.value)}
              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-indigo-600">
              <option value="DECORATION">DECORATION (Cố định)</option>
              <option value="IMAGE">IMAGE (Placeholder)</option>
              <option value="TEXT">TEXT (Chữ động)</option>
            </select>
          </div>
        </CollapsibleSection>

        {/* Section 2: Transform */}
        <CollapsibleSection id="transform" icon={<Maximize2 className="w-3 h-3" />} title="Transform" defaultOpen={true}>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <span className="text-[9px] font-semibold text-slate-500">X</span>
              <Input type="number" value={activeObjectProps.left !== undefined ? Math.round(activeObjectProps.left) : 0}
                onChange={(e) => handlePropChange('left', parseInt(e.target.value) || 0)}
                className="bg-slate-950 border-slate-800 text-xs text-slate-300 rounded-xl h-8" />
            </div>
            <div className="space-y-1">
              <span className="text-[9px] font-semibold text-slate-500">Y</span>
              <Input type="number" value={activeObjectProps.top !== undefined ? Math.round(activeObjectProps.top) : 0}
                onChange={(e) => handlePropChange('top', parseInt(e.target.value) || 0)}
                className="bg-slate-950 border-slate-800 text-xs text-slate-300 rounded-xl h-8" />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <span className="text-[9px] font-semibold text-slate-500">Scale</span>
              <Input type="number" step="0.05" value={activeObjectProps.scaleX !== undefined ? parseFloat(activeObjectProps.scaleX.toFixed(2)) : 1.0}
                onChange={(e) => { const v = parseFloat(e.target.value) || 1.0; updateActiveObject({ scaleX: v, scaleY: v }); }}
                className="bg-slate-950 border-slate-800 text-xs text-slate-300 rounded-xl h-8" />
            </div>
            <div className="space-y-1">
              <span className="text-[9px] font-semibold text-slate-500">Xoay °</span>
              <Input type="number" value={activeObjectProps.angle !== undefined ? Math.round(activeObjectProps.angle) : 0}
                onChange={(e) => handlePropChange('angle', parseInt(e.target.value) || 0)}
                className="bg-slate-950 border-slate-800 text-xs text-slate-300 rounded-xl h-8" />
            </div>
          </div>
          <div className="space-y-1.5">
            <div className="flex justify-between text-[9px] font-semibold text-slate-500">
              <span>Opacity</span>
              <span>{Math.round((activeObjectProps.opacity !== undefined ? activeObjectProps.opacity : 1.0) * 100)}%</span>
            </div>
            <input type="range" min="0" max="1" step="0.05" value={activeObjectProps.opacity !== undefined ? activeObjectProps.opacity : 1.0}
              onChange={(e) => handlePropChange('opacity', parseFloat(e.target.value))}
              className="w-full h-1 bg-slate-950 rounded-lg appearance-none cursor-pointer accent-indigo-600" />
          </div>

          {/* Flip & Align */}
          <div className="pt-2 border-t border-slate-800/50 space-y-3">
            <div className="space-y-1">
              <span className="text-[8px] font-semibold text-slate-500 block">Lật</span>
              <div className="grid grid-cols-2 gap-1.5">
                <Button size="sm" variant="outline" onClick={() => handleFlip('h')}
                  className={`rounded-xl border-slate-800 text-[10px] p-0 h-8 cursor-pointer ${activeObjectProps.flipX ? 'bg-indigo-600/20 text-indigo-400 border-indigo-500/30' : 'bg-slate-950 text-slate-400 hover:text-white'}`}>
                  <FlipHorizontal className="w-3.5 h-3.5 mr-1" /> Ngang
                </Button>
                <Button size="sm" variant="outline" onClick={() => handleFlip('v')}
                  className={`rounded-xl border-slate-800 text-[10px] p-0 h-8 cursor-pointer ${activeObjectProps.flipY ? 'bg-indigo-600/20 text-indigo-400 border-indigo-500/30' : 'bg-slate-950 text-slate-400 hover:text-white'}`}>
                  <FlipVertical className="w-3.5 h-3.5 mr-1" /> Dọc
                </Button>
              </div>
            </div>
            <div className="space-y-1">
              <span className="text-[8px] font-semibold text-slate-500 block">Căn ngang</span>
              <div className="grid grid-cols-3 gap-1.5">
                {(['left', 'center-h', 'right'] as const).map(a => (
                  <Button key={a} size="sm" variant="outline" onClick={() => handleAlign(a)}
                    className="rounded-xl border-slate-800 bg-slate-950 text-[10px] p-0 h-8 text-slate-400 hover:text-white cursor-pointer">
                    {a === 'left' ? 'Trái' : a === 'center-h' ? 'Giữa' : 'Phải'}
                  </Button>
                ))}
              </div>
            </div>
            <div className="space-y-1">
              <span className="text-[8px] font-semibold text-slate-500 block">Căn dọc</span>
              <div className="grid grid-cols-3 gap-1.5">
                {(['top', 'center-v', 'bottom'] as const).map(a => (
                  <Button key={a} size="sm" variant="outline" onClick={() => handleAlign(a)}
                    className="rounded-xl border-slate-800 bg-slate-950 text-[10px] p-0 h-8 text-slate-400 hover:text-white cursor-pointer">
                    {a === 'top' ? 'Trên' : a === 'center-v' ? 'Giữa' : 'Dưới'}
                  </Button>
                ))}
              </div>
            </div>
            {selectedCount >= 3 && (
              <div className="space-y-1 animate-in fade-in duration-200">
                <span className="text-[8px] font-semibold text-slate-500 block">Phân bổ đều</span>
                <div className="grid grid-cols-2 gap-1.5">
                  <Button size="sm" variant="outline" onClick={() => handleDistribute('h')}
                    className="rounded-xl border-slate-800 bg-slate-950 text-[10px] p-0 h-8 text-slate-400 hover:text-white cursor-pointer">Dàn ngang</Button>
                  <Button size="sm" variant="outline" onClick={() => handleDistribute('v')}
                    className="rounded-xl border-slate-800 bg-slate-950 text-[10px] p-0 h-8 text-slate-400 hover:text-white cursor-pointer">Dàn dọc</Button>
                </div>
              </div>
            )}
          </div>
        </CollapsibleSection>

        {/* Section 3: Fill & Stroke */}
        {(activeObjectProps.fill !== undefined && activeObjectProps.fill !== null) && (
          <CollapsibleSection id="fill-stroke" icon={<Palette className="w-3 h-3" />} title="Màu sắc & Tô" defaultOpen={true}>
            <div className="flex p-0.5 bg-slate-950 border border-slate-800 rounded-xl">
              {(['solid', 'linear', 'radial'] as const).map(t => (
                <button key={t} type="button" onClick={() => {
                  if (t !== fillType) {
                    if (t === 'solid') handlePropChange('fill', color1);
                    else handleGradientChange(t, color1, color2, gradientAngle);
                  }
                }} className={`flex-1 py-1 rounded-lg text-[10px] font-bold transition-all cursor-pointer ${fillType === t ? 'bg-indigo-600/20 text-indigo-400 border border-indigo-500/20' : 'text-slate-500 hover:text-slate-300'}`}>
                  {t === 'solid' ? 'Đơn' : t === 'linear' ? 'Linear' : 'Radial'}
                </button>
              ))}
            </div>
            {fillType === 'solid' ? (
              <div className="space-y-1">
                <span className="text-[9px] font-semibold text-slate-500">Màu tô</span>
                <div className="flex gap-2">
                  <input type="color" value={rgbaToHex(color1)} onChange={(e) => handlePropChange('fill', e.target.value)}
                    className="w-8 h-8 rounded-lg bg-transparent border border-slate-800 cursor-pointer overflow-hidden" />
                  <Input value={color1} onChange={(e) => handlePropChange('fill', e.target.value)}
                    className="bg-slate-950 border-slate-800 text-xs text-slate-300 rounded-xl h-8 font-mono flex-1" />
                  <Button size="icon" variant="ghost" onClick={handleEyedropper} className="w-8 h-8 rounded-lg text-slate-400 hover:text-white cursor-pointer" title="Lấy màu">
                    <Pipette className="w-3.5 h-3.5" />
                  </Button>
                </div>
                <ColorPalette onSelect={(c) => handlePropChange('fill', c)} selectedColor={color1} />
              </div>
            ) : (
              <div className="space-y-3">
                {([color1, color2] as string[]).map((c, i) => (
                  <div key={i} className="space-y-1">
                    <span className="text-[9px] font-semibold text-slate-500">{i === 0 ? 'Màu bắt đầu' : 'Màu kết thúc'}</span>
                    <div className="flex gap-2">
                      <input type="color" value={rgbaToHex(c)}
                        onChange={(e) => handleGradientChange(fillType, i === 0 ? e.target.value : color1, i === 0 ? color2 : e.target.value, gradientAngle)}
                        className="w-8 h-8 rounded-lg bg-transparent border border-slate-800 cursor-pointer overflow-hidden" />
                      <Input value={c}
                        onChange={(e) => handleGradientChange(fillType, i === 0 ? e.target.value : color1, i === 0 ? color2 : e.target.value, gradientAngle)}
                        className="bg-slate-950 border-slate-800 text-xs text-slate-300 rounded-xl h-8 font-mono flex-1" />
                    </div>
                  </div>
                ))}
                {fillType === 'linear' && (
                  <div className="space-y-1.5">
                    <div className="flex justify-between text-[9px] font-semibold text-slate-500"><span>Góc</span><span>{gradientAngle}°</span></div>
                    <div className="flex gap-3 items-center">
                      <input type="range" min="0" max="360" step="5" value={gradientAngle}
                        onChange={(e) => handleGradientChange('linear', color1, color2, parseInt(e.target.value))}
                        className="flex-1 h-1 bg-slate-950 rounded-lg appearance-none cursor-pointer accent-indigo-600" />
                      <Input type="number" min="0" max="360" value={gradientAngle}
                        onChange={(e) => handleGradientChange('linear', color1, color2, Math.max(0, Math.min(360, parseInt(e.target.value) || 0)))}
                        className="w-14 bg-slate-950 border-slate-800 text-xs text-slate-300 rounded-xl h-7 text-center px-1" />
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
              <span className="text-[9px] font-semibold text-slate-500">Định dạng</span>
              <div className="grid grid-cols-5 gap-1.5">
                <Button size="sm" variant="outline" onClick={() => handlePropChange('fontWeight', activeObjectProps.fontWeight === 'bold' ? 'normal' : 'bold')}
                  className={`rounded-xl h-8 border-slate-800 cursor-pointer p-0 ${activeObjectProps.fontWeight === 'bold' ? 'bg-indigo-600/10 text-indigo-400 border-indigo-500/30' : 'bg-slate-950 text-slate-400'}`}>
                  <Bold className="w-3.5 h-3.5" />
                </Button>
                <Button size="sm" variant="outline" onClick={() => handlePropChange('fontStyle', activeObjectProps.fontStyle === 'italic' ? 'normal' : 'italic')}
                  className={`rounded-xl h-8 border-slate-800 cursor-pointer p-0 ${activeObjectProps.fontStyle === 'italic' ? 'bg-indigo-600/10 text-indigo-400 border-indigo-500/30' : 'bg-slate-950 text-slate-400'}`}>
                  <Italic className="w-3.5 h-3.5" />
                </Button>
                <Button size="sm" variant="outline" onClick={() => handlePropChange('underline', !activeObjectProps.underline)}
                  className={`rounded-xl h-8 border-slate-800 cursor-pointer p-0 ${activeObjectProps.underline ? 'bg-indigo-600/10 text-indigo-400 border-indigo-500/30' : 'bg-slate-950 text-slate-400'}`}>
                  <Underline className="w-3.5 h-3.5" />
                </Button>
                <Button size="sm" variant="outline" onClick={() => handlePropChange('linethrough', !activeObjectProps.linethrough)}
                  className={`rounded-xl h-8 border-slate-800 cursor-pointer p-0 ${activeObjectProps.linethrough ? 'bg-indigo-600/10 text-indigo-400 border-indigo-500/30' : 'bg-slate-950 text-slate-400'}`}>
                  <Strikethrough className="w-3.5 h-3.5" />
                </Button>
                <Button size="sm" variant="outline" onClick={handleTextTransformToggle}
                  className={`rounded-xl h-8 border-slate-800 cursor-pointer p-0 ${activeObjectProps.textTransform === 'uppercase' ? 'bg-indigo-600/10 text-indigo-400 border-indigo-500/30' : 'bg-slate-950 text-slate-400'}`}>
                  <span className="text-[10px] tracking-tighter">Aa</span>
                </Button>
              </div>
            </div>
            <div className="space-y-1.5">
              <span className="text-[9px] font-semibold text-slate-500">Căn lề</span>
              <div className="flex gap-1.5">
                {(['left', 'center', 'right'] as const).map(a => (
                  <Button key={a} size="sm" variant="outline" onClick={() => handlePropChange('textAlign', a)}
                    className={`flex-1 rounded-xl h-8 border-slate-800 cursor-pointer ${activeObjectProps.textAlign === a ? 'bg-indigo-600/10 text-indigo-400 border-indigo-500/30' : 'bg-slate-950 text-slate-400'}`}>
                    {a === 'left' ? <AlignLeft className="w-3.5 h-3.5" /> : a === 'center' ? <AlignCenter className="w-3.5 h-3.5" /> : <AlignRight className="w-3.5 h-3.5" />}
                  </Button>
                ))}
              </div>
            </div>
            <div className="space-y-1">
              <span className="text-[9px] font-semibold text-slate-500">Font Size</span>
              <Input type="number" value={activeObjectProps.fontSize || 40}
                onChange={(e) => handlePropChange('fontSize', parseInt(e.target.value) || 12)}
                className="bg-slate-950 border-slate-800 text-xs text-slate-300 rounded-xl h-8" />
            </div>
            <div className="space-y-1">
              <div className="flex justify-between text-[9px] font-semibold text-slate-500"><span>Line Height</span><span>{activeObjectProps.lineHeight !== undefined ? activeObjectProps.lineHeight.toFixed(2) : '1.16'}</span></div>
              <input type="range" min="0.8" max="3.0" step="0.05" value={activeObjectProps.lineHeight !== undefined ? activeObjectProps.lineHeight : 1.16}
                onChange={(e) => handlePropChange('lineHeight', parseFloat(e.target.value))}
                className="w-full h-1 bg-slate-950 rounded-lg appearance-none cursor-pointer accent-indigo-600" />
            </div>
            <div className="space-y-1">
              <div className="flex justify-between text-[9px] font-semibold text-slate-500"><span>Letter Spacing</span><span>{activeObjectProps.charSpacing !== undefined ? activeObjectProps.charSpacing : '0'}</span></div>
              <input type="range" min="-50" max="200" step="5" value={activeObjectProps.charSpacing !== undefined ? activeObjectProps.charSpacing : 0}
                onChange={(e) => handlePropChange('charSpacing', parseInt(e.target.value))}
                className="w-full h-1 bg-slate-950 rounded-lg appearance-none cursor-pointer accent-indigo-600" />
            </div>
            <div className="space-y-1">
              <span className="text-[9px] font-semibold text-slate-500">Font Family</span>
              <select 
                value={activeObjectProps.fontFamily || 'sans-serif'} 
                onChange={(e) => handlePropChange('fontFamily', e.target.value)}
                style={{ fontFamily: activeObjectProps.fontFamily || 'sans-serif' }}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-indigo-600"
              >
                <option value="Outfit" style={{ fontFamily: 'Outfit' }}>Outfit</option>
                <option value="sans-serif" style={{ fontFamily: 'sans-serif' }}>Sans-Serif</option>
                <option value="serif" style={{ fontFamily: 'serif' }}>Serif</option>
                <option value="monospace" style={{ fontFamily: 'monospace' }}>Monospace</option>
                <option value="cursive" style={{ fontFamily: 'cursive' }}>Cursive</option>
                {dynamicFonts.map(f => (
                  <option key={f.family_slug} value={f.family_slug} style={{ fontFamily: f.family_slug }}>
                    {f.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1 pt-2 border-t border-slate-800/30">
              <span className="text-[9px] font-semibold text-slate-500">Màu nền chữ</span>
              <div className="flex gap-2">
                <input type="color" value={rgbaToHex(activeObjectProps.textBackgroundColor || 'rgba(0,0,0,0)')}
                  onChange={(e) => handlePropChange('textBackgroundColor', e.target.value)}
                  className="w-8 h-8 rounded-lg bg-transparent border border-slate-800 cursor-pointer overflow-hidden" />
                <Input value={activeObjectProps.textBackgroundColor || ''} onChange={(e) => handlePropChange('textBackgroundColor', e.target.value)}
                  className="bg-slate-950 border-slate-800 text-xs text-slate-300 rounded-xl h-8 font-mono flex-1" placeholder="Không màu nền" />
                {activeObjectProps.textBackgroundColor && (
                  <Button size="sm" variant="destructive" onClick={() => handlePropChange('textBackgroundColor', null)} className="h-8 rounded-xl px-2.5 cursor-pointer text-xs">Xóa</Button>
                )}
              </div>
            </div>
          </CollapsibleSection>
        )}

        {/* Section 5: Corner Radius (Rect only) */}
        {activeObjectProps.rx !== undefined && (
          <CollapsibleSection id="corner-radius" icon={<Maximize2 className="w-3 h-3" />} title="Bo góc">
            <div className="space-y-1.5">
              <div className="flex justify-between text-[9px] font-semibold text-slate-500"><span>Corner Radius</span><span>{Math.round(activeObjectProps.rx)}px</span></div>
              <div className="flex gap-3 items-center">
                <input type="range" min="0" max="100" step="1" value={activeObjectProps.rx || 0}
                  onChange={(e) => { const v = parseInt(e.target.value) || 0; updateActiveObject({ rx: v, ry: v }); }}
                  className="flex-1 h-1 bg-slate-950 rounded-lg appearance-none cursor-pointer accent-indigo-600" />
                <Input type="number" min="0" max="200" value={Math.round(activeObjectProps.rx || 0)}
                  onChange={(e) => { const v = Math.max(0, parseInt(e.target.value) || 0); updateActiveObject({ rx: v, ry: v }); }}
                  className="w-14 bg-slate-950 border-slate-800 text-xs text-slate-300 rounded-xl h-7 text-center px-1" />
              </div>
            </div>
          </CollapsibleSection>
        )}

        {/* Section 6: Stroke */}
        {(activeObjectProps.layerType === 'TEXT' || activeObjectProps.layerType === 'DECORATION') && (
          <CollapsibleSection id="stroke" icon={<Settings className="w-3 h-3" />} title="Viền (Stroke)">
            <div className="space-y-1">
              <span className="text-[9px] font-semibold text-slate-500">Màu viền</span>
              <div className="flex gap-2">
                <input type="color" value={rgbaToHex(activeObjectProps.stroke || 'rgba(0,0,0,0)')}
                  onChange={(e) => handlePropChange('stroke', e.target.value)}
                  className="w-8 h-8 rounded-lg bg-transparent border border-slate-800 cursor-pointer overflow-hidden" />
                <Input value={activeObjectProps.stroke || ''} onChange={(e) => handlePropChange('stroke', e.target.value)}
                  className="bg-slate-950 border-slate-800 text-xs text-slate-300 rounded-xl h-8 font-mono flex-1" placeholder="Không viền" />
              </div>
            </div>
            <div className="space-y-1.5">
              <div className="flex justify-between text-[9px] font-semibold text-slate-500"><span>Độ dày</span><span>{activeObjectProps.strokeWidth || 0}px</span></div>
              <div className="flex gap-3 items-center">
                <input type="range" min="0" max="50" step="1" value={activeObjectProps.strokeWidth || 0}
                  onChange={(e) => handlePropChange('strokeWidth', parseInt(e.target.value) || 0)}
                  className="flex-1 h-1 bg-slate-950 rounded-lg appearance-none cursor-pointer accent-indigo-600" />
                <Input type="number" min="0" max="100" value={activeObjectProps.strokeWidth || 0}
                  onChange={(e) => handlePropChange('strokeWidth', parseInt(e.target.value) || 0)}
                  className="w-14 bg-slate-950 border-slate-800 text-xs text-slate-300 rounded-xl h-7 text-center px-1" />
              </div>
            </div>
            <div className="space-y-1">
              <span className="text-[9px] font-semibold text-slate-500">Kiểu viền</span>
              <select value={activeObjectProps.strokeDashArray ? JSON.stringify(activeObjectProps.strokeDashArray) : ''}
                onChange={(e) => handlePropChange('strokeDashArray', e.target.value ? JSON.parse(e.target.value) : null)}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-indigo-600">
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
          <div className="flex items-center justify-between border-b border-slate-800/50 pb-2 mb-2">
            <span className="text-[9px] font-semibold text-slate-400">Bật/Tắt</span>
            <input type="checkbox" checked={activeObjectProps.shadow !== null}
              onChange={(e) => {
                if (e.target.checked) {
                  applyShadowPreset('medium');
                } else { updateActiveObject({ shadow: null }); }
              }}
              className="rounded text-indigo-600 focus:ring-indigo-600 bg-slate-950 border-slate-800 cursor-pointer" />
          </div>
          {activeObjectProps.shadow !== null && (
            <div className="space-y-3 animate-in fade-in duration-200">
              
              {/* Presets Grid */}
              <div className="space-y-1">
                <span className="text-[9px] font-semibold text-slate-500">Mẫu bóng nhanh</span>
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
                      className="rounded-lg border-slate-800 bg-slate-950 text-[10px] p-0 h-7 text-slate-400 hover:text-white cursor-pointer"
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
                  className="w-full rounded-lg border-slate-800 bg-slate-950 text-[10px] p-0 h-8 text-slate-300 hover:text-white cursor-pointer gap-1"
                >
                  <MoonStar className="w-3.5 h-3.5" />
                  Tạo vùng bóng
                </Button>
                <p className="text-[9px] text-slate-500 leading-normal">
                  Tạo một dải bóng mềm riêng bên dưới đối tượng để chỉnh như một layer độc lập.
                </p>
              </div>

              {/* Color Picker */}
              <div className="space-y-1">
                <span className="text-[9px] font-semibold text-slate-500">Màu bóng</span>
                <div className="flex gap-2">
                  <input type="color" value={rgbaToHex(activeObjectProps.shadow?.color || 'rgba(0,0,0,0.4)')}
                    onChange={(e) => handleShadowChange('color', hexToRgba(e.target.value, activeObjectProps.shadow?.color || 'rgba(0,0,0,0.4)'))}
                    className="w-8 h-8 rounded-lg bg-transparent border border-slate-800 cursor-pointer overflow-hidden" />
                  <Input value={activeObjectProps.shadow?.color || 'rgba(0,0,0,0.4)'}
                    onChange={(e) => handleShadowChange('color', e.target.value)}
                    className="bg-slate-950 border-slate-800 text-xs text-slate-300 rounded-xl h-8 font-mono flex-1" />
                </div>
              </div>

              {/* Opacity Slider */}
              <div className="space-y-1.5">
                <div className="flex justify-between text-[9px] font-semibold text-slate-500">
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
                    className="flex-1 h-1 bg-slate-950 rounded-lg appearance-none cursor-pointer accent-indigo-600" />
                  <Input type="number" min="0" max="100" value={getShadowOpacity(activeObjectProps.shadow?.color || 'rgba(0,0,0,0.4)')}
                    onChange={(e) => {
                      const newOpacity = Math.max(0, Math.min(100, parseInt(e.target.value) || 0));
                      const currentGrad = activeObjectProps.shadow?.color || 'rgba(0,0,0,0.4)';
                      handleShadowChange('color', setShadowOpacity(currentGrad, newOpacity));
                    }}
                    className="w-12 bg-slate-950 border-slate-800 text-xs text-slate-300 rounded-xl h-7 text-center px-1" />
                </div>
              </div>

              {/* Blur Slider */}
              <div className="space-y-1.5">
                <div className="flex justify-between text-[9px] font-semibold text-slate-500">
                  <span>Độ mờ (Blur)</span>
                  <span>{activeObjectProps.shadow?.blur || 0}px</span>
                </div>
                <div className="flex gap-3 items-center">
                  <input type="range" min="0" max="100" value={activeObjectProps.shadow?.blur || 0}
                    onChange={(e) => handleShadowChange('blur', parseInt(e.target.value))}
                    className="flex-1 h-1 bg-slate-950 rounded-lg appearance-none cursor-pointer accent-indigo-600" />
                  <Input type="number" min="0" max="100" value={activeObjectProps.shadow?.blur || 0}
                    onChange={(e) => handleShadowChange('blur', Math.max(0, Math.min(100, parseInt(e.target.value) || 0)))}
                    className="w-12 bg-slate-950 border-slate-800 text-xs text-slate-300 rounded-xl h-7 text-center px-1" />
                </div>
              </div>

              {/* Distance Slider */}
              <div className="space-y-1.5">
                <div className="flex justify-between text-[9px] font-semibold text-slate-500">
                  <span>Khoảng cách (Distance)</span>
                  <span>{currentDistance}px</span>
                </div>
                <div className="flex gap-3 items-center">
                  <input type="range" min="0" max="150" value={currentDistance}
                    onChange={(e) => handleShadowChange('distance', parseInt(e.target.value))}
                    className="flex-1 h-1 bg-slate-950 rounded-lg appearance-none cursor-pointer accent-indigo-600" />
                  <Input type="number" min="0" max="150" value={currentDistance}
                    onChange={(e) => handleShadowChange('distance', Math.max(0, Math.min(150, parseInt(e.target.value) || 0)))}
                    className="w-12 bg-slate-950 border-slate-800 text-xs text-slate-300 rounded-xl h-7 text-center px-1" />
                </div>
              </div>

              {/* Angle Slider */}
              <div className="space-y-1.5">
                <div className="flex justify-between text-[9px] font-semibold text-slate-500">
                  <span>Góc đổ bóng (Angle)</span>
                  <span>{currentAngle}°</span>
                </div>
                <div className="flex gap-3 items-center">
                  <input type="range" min="0" max="360" value={currentAngle}
                    onChange={(e) => handleShadowChange('angle', parseInt(e.target.value))}
                    className="flex-1 h-1 bg-slate-950 rounded-lg appearance-none cursor-pointer accent-indigo-600" />
                  <Input type="number" min="0" max="360" value={currentAngle}
                    onChange={(e) => handleShadowChange('angle', Math.max(0, Math.min(360, parseInt(e.target.value) || 0)))}
                    className="w-12 bg-slate-950 border-slate-800 text-xs text-slate-300 rounded-xl h-7 text-center px-1" />
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
                <div className="flex justify-between text-[9px] font-semibold text-slate-500"><span>{label}</span><span>{activeObjectProps.imageFilters?.[key] || 0}</span></div>
                <input type="range" min={min} max={max} step={step} value={activeObjectProps.imageFilters?.[key] || 0}
                  onChange={(e) => handleImageFilterChange(key, parseFloat(e.target.value))}
                  className="w-full h-1 bg-slate-950 rounded-lg appearance-none cursor-pointer accent-indigo-600" />
              </div>
            ))}
            <div className="flex gap-4 pt-2 border-t border-slate-800/50">
              <label className="flex items-center gap-2 cursor-pointer">
                <input type="checkbox" checked={activeObjectProps.imageFilters?.grayscale || false}
                  onChange={(e) => handleImageFilterChange('grayscale', e.target.checked)}
                  className="rounded text-indigo-600 focus:ring-indigo-600 bg-slate-950 border-slate-800" />
                <span className="text-[10px] font-semibold text-slate-400">Trắng đen</span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer">
                <input type="checkbox" checked={activeObjectProps.imageFilters?.sepia || false}
                  onChange={(e) => handleImageFilterChange('sepia', e.target.checked)}
                  className="rounded text-indigo-600 focus:ring-indigo-600 bg-slate-950 border-slate-800" />
                <span className="text-[10px] font-semibold text-slate-400">Sepia</span>
              </label>
            </div>

            <div className="pt-3 border-t border-slate-800/50 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-[9px] font-semibold text-slate-500">Tô bóng / đổi tone</span>
                <button
                  onClick={() => applyTonePreset(null, false, 1)}
                  className="text-[9px] font-semibold text-slate-500 hover:text-rose-400 cursor-pointer"
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
                        : 'bg-slate-950 border-slate-800 text-slate-400 hover:text-slate-200 hover:border-slate-700'
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
                  className="w-9 h-8 rounded-lg bg-slate-950 border border-slate-800 cursor-pointer"
                  title="Chọn màu tone"
                />
                <input
                  type="range"
                  min="0"
                  max="100"
                  value={Math.round((activeObjectProps.imageFilters?.toneAlpha ?? 1) * 100)}
                  onChange={(e) => applyTonePreset(activeObjectProps.imageFilters?.toneColor || '#808080', true, Math.max(0, Math.min(1, (parseInt(e.target.value) || 0) / 100)))}
                  className="w-full h-1 bg-slate-950 rounded-lg appearance-none cursor-pointer accent-indigo-600"
                  title="Mức tone"
                />
                <span className="text-[9px] font-semibold text-slate-500 w-9 text-right">
                  {Math.round((activeObjectProps.imageFilters?.toneAlpha ?? 1) * 100)}%
                </span>
              </div>
              <p className="text-[9px] text-slate-500 leading-normal">
                Dùng cho object PNG/ảnh cắt nền để giữ form nhưng đổi thành bóng màu hoặc xám.
              </p>
            </div>
          </CollapsibleSection>
        )}

        {/* Section 9: Placeholder Constraints (IMAGE only) */}
        {isImageObject && (
          <CollapsibleSection id="placeholder" icon={<ImageIcon className="w-3 h-3" />} title="Placeholder">
            <div className="space-y-1">
              <span className="text-[9px] font-semibold text-slate-500">URL ảnh mặc định</span>
              <Input value={activeObjectProps.defaultImageUrl || ''} onChange={(e) => handlePropChange('defaultImageUrl', e.target.value)}
                placeholder="https://pub-yourbucket.r2.dev/..." className="bg-slate-950 border-slate-800 text-xs text-slate-300 rounded-xl" />
            </div>
            <div className="space-y-1">
              <span className="text-[9px] font-semibold text-slate-500">Tỷ lệ cắt (Crop Ratio)</span>
              <select value={activeObjectProps.cropRatio || ''} onChange={(e) => handlePropChange('cropRatio', e.target.value || null)}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-indigo-600">
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
            <p className="text-[9px] text-slate-500 leading-normal mt-1">URL ảnh ban đầu trên app Android. Người dùng có thể thay bằng ảnh của họ.</p>
          </CollapsibleSection>
        )}
      </div>
    </div>
  );
}
