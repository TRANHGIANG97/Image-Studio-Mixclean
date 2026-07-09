'use client';

import React, { useState, useCallback } from 'react';
import { ActiveSelection } from 'fabric';
import {
  Sliders, Trash2, FlipHorizontal, FlipVertical,
  AlignHorizontalSpaceAround, Settings, Maximize2,
  ChevronRight, EyeOff, Copy, MoonStar
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useEditorStore } from '@/store/editor.store';
import { useLayersStore } from '@/store/layers.store';
import { toast } from 'sonner';
import { CollapsibleSection } from './properties/CollapsibleSection';
import { BackgroundPropertiesPanel } from './properties/BackgroundPropertiesPanel';
import { ShapePropertiesSection } from './properties/ShapePropertiesSection';
import { TextPropertiesSection } from './properties/TextPropertiesSection';
import { ImagePropertiesSection } from './properties/ImagePropertiesSection';
import { useRecentColors } from './properties/useRecentColors';
import { rgbaToHex, hexToRgba } from './properties/color-utils';
import { ensureFontLoaded } from '@/lib/font-loader';
import { resolveLayerType } from '@/lib/canvas-object-props';
import { recordCanvasHistory, scheduleHistoryCommit, shouldDebounceProp } from '@/lib/canvas-commands';
import { getCompositeOperation } from '@/lib/blend-mode-utils';

interface PropertiesPanelProps {
  onDirty?: () => void;
}

// ==================== Main Component ====================
export default function PropertiesPanel({ onDirty }: PropertiesPanelProps) {
  const { canvas, copyToClipboard, copyStyle, pasteStyle, copiedStyle } = useEditorStore();
  const { activeObjectId, activeObjectProps, updateActiveObject } = useLayersStore();

  const recordChange = useCallback(() => {
    recordCanvasHistory(onDirty);
  }, [onDirty]);

  type PropertyTab = 'all' | 'basic' | 'content' | 'effects';
  const [propertyTab, setPropertyTab] = useState<PropertyTab>('all');

  const activeCanvasObject = canvas?.getActiveObject();

  React.useEffect(() => {
    if (!activeObjectId || !activeObjectProps) return;
    const type =
      activeObjectProps.layerType ||
      (activeCanvasObject ? resolveLayerType(activeCanvasObject) : null);
    if (type === 'TEXT' || type === 'IMAGE' || type === 'PLACEHOLDER_OBJECT') {
      setPropertyTab('content');
    } else {
      setPropertyTab('basic');
    }
  }, [activeObjectId, activeObjectProps?.layerType, activeCanvasObject]);

  const { addRecentColor } = useRecentColors();

  const isImageObject =
    activeObjectProps?.layerType === 'IMAGE' ||
    activeObjectProps?.layerType === 'PLACEHOLDER_OBJECT' ||
    activeCanvasObject?.type === 'image';

  const handlePropChange = (name: string, value: any) => {
    if (name === 'fill' && typeof value === 'string') addRecentColor(value);
    if (name === 'stroke' && typeof value === 'string') addRecentColor(value);

    const applyProps = (props: Record<string, any>) => {
      updateActiveObject(props);
    };

    if (name === 'fontFamily' && typeof value === 'string') {
      ensureFontLoaded(value).then(() => {
        applyProps({ fontFamily: value });
        recordChange();
      });
      return;
    }

    const isText =
      activeObjectProps?.layerType === 'TEXT' ||
      (activeCanvasObject ? resolveLayerType(activeCanvasObject) === 'TEXT' : false);
    const props =
      name === 'layerType'
        ? { layerType: value, isReplaceable: value === 'PLACEHOLDER_OBJECT' }
        : name === 'isReplaceable'
          ? {
              isReplaceable: value,
              // Keep Fabric layerType in sync so badge / drop-replace / export stay consistent.
              layerType: value ? 'PLACEHOLDER_OBJECT' : 'IMAGE',
            }
          : isText && (name === 'stroke' || name === 'strokeWidth')
            ? { [name]: value, paintFirst: 'stroke' }
            : { [name]: value };

    applyProps(props);

    if (shouldDebounceProp(name)) {
      scheduleHistoryCommit(`prop:${activeObjectId}:${name}`, recordChange, 300);
    } else {
      recordChange();
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
    scheduleHistoryCommit(`shadow:${activeObjectId}:${shadowProp}`, recordChange, 300);
  };

  const applyShadowPreset = (type: 'none' | 'soft' | 'medium' | 'hard' | 'glow') => {
    if (type === 'none') {
      updateActiveObject({ shadow: null });
      recordChange();
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
    recordChange();
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
      layerType: 'SHADOW_REGION',
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
    recordChange();
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
      recordChange();
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
      updateActiveObject({
        left: Math.round(activeObject.left),
        top: Math.round(activeObject.top),
      });
      recordChange();
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
    recordChange();
    toast.success('Đã căn đều khoảng cách');
  };

  const handleFlip = (direction: 'h' | 'v') => {
    if (direction === 'h') handlePropChange('flipX', !activeObjectProps.flipX);
    else handlePropChange('flipY', !activeObjectProps.flipY);
  };


  if (!activeObjectId || !activeObjectProps) {
    return <BackgroundPropertiesPanel canvas={canvas} onDirty={onDirty} />;
  }

  // ==================== OBJECT SELECTED: Properties ====================
  const offsetX = activeObjectProps.shadow?.offsetX || 0;
  const offsetY = activeObjectProps.shadow?.offsetY || 0;
  const currentDistance = Math.round(Math.sqrt(offsetX * offsetX + offsetY * offsetY));
  let currentAngle = Math.round(Math.atan2(offsetY, offsetX) * (180 / Math.PI));
  if (currentAngle < 0) currentAngle += 360;

  const selectedCount =
    activeCanvasObject?.type === 'activeselection'
      ? (activeCanvasObject as any)._objects?.length || 0
      : activeCanvasObject
        ? 1
        : 0;

  const showBasic = propertyTab === 'all' || propertyTab === 'basic';
  const showContent = propertyTab === 'all' || propertyTab === 'content';
  const showEffects = propertyTab === 'all' || propertyTab === 'effects';

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

      <div className="flex gap-1 px-1 flex-wrap shrink-0">
        {([
          ['all', 'Tất cả'],
          ['basic', 'Cơ bản'],
          ['content', 'Nội dung'],
          ['effects', 'Hiệu ứng'],
        ] as const).map(([key, label]) => (
          <button
            key={key}
            type="button"
            onClick={() => setPropertyTab(key)}
            className={`px-2 py-1 rounded-lg text-[10px] font-semibold transition-colors cursor-pointer ${
              propertyTab === key
                ? 'bg-indigo-50 text-indigo-600 border border-indigo-200'
                : 'text-slate-400 hover:text-slate-600 border border-transparent'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      <div className="flex-1 overflow-y-auto pr-1 scrollbar-thin scrollbar-thumb-slate-300 space-y-4 pb-8">

        {/* Section 1: Layer Info */}
        {showBasic && (
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
              <option value="SHADOW_REGION">SHADOW_REGION (Vùng bóng)</option>
              <option value="IMAGE">IMAGE (Ảnh thường)</option>
              <option value="PLACEHOLDER_OBJECT">PLACEHOLDER_OBJECT (Vật thể thay thế/Sản phẩm mẫu)</option>
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
                recordChange();
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
        )}

        {/* Section 2: Transform */}
        {showBasic && (
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
                onChange={(e) => {
                  const v = parseFloat(e.target.value) || 1.0;
                  updateActiveObject({ scaleX: v, scaleY: v });
                  scheduleHistoryCommit(`scale:${activeObjectId}`, recordChange, 300);
                }}
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
        )}

        <ShapePropertiesSection
          showBasic={showBasic}
          activeObjectProps={activeObjectProps}
          activeObjectId={activeObjectId}
          onPropChange={handlePropChange}
          onRecordChange={recordChange}
          addRecentColor={addRecentColor}
        />

        <TextPropertiesSection
          showContent={showContent}
          activeObjectProps={activeObjectProps}
          onPropChange={handlePropChange}
          onRecordChange={recordChange}
        />

        {/* Section 7: Shadow */}
        {showEffects && (
        <CollapsibleSection id="shadow" icon={<AlignHorizontalSpaceAround className="w-3 h-3" />} title="Đổ bóng (Shadow)">
          <div className="flex items-center justify-between border-b border-slate-200/50 pb-2 mb-2">
            <span className="text-[9px] font-semibold text-slate-500">Bật/Tắt</span>
            <input type="checkbox" checked={activeObjectProps.shadow !== null}
              onChange={(e) => {
                if (e.target.checked) {
                  applyShadowPreset('medium');
                } else {
                  updateActiveObject({ shadow: null });
                  recordChange();
                }
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
        )}

        <ImagePropertiesSection
          showContent={showContent}
          isImageObject={isImageObject}
          activeObjectProps={activeObjectProps}
          onPropChange={handlePropChange}
          onRecordChange={recordChange}
        />

      </div>
    </div>
  );
}
