'use client';

import React, { useCallback, useState } from 'react';
import { Sliders, Trash2, Image as ImageIcon, Palette, Pipette, Crop as CropIcon } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { toast } from 'sonner';
import { CollapsibleSection } from './CollapsibleSection';
import { useRecentColors, PRESET_COLORS } from './useRecentColors';
import { getGradientAngle, rgbaToHex } from './color-utils';
import { useCropStore } from '@/store/crop.store';
import { recordCanvasHistory } from '@/lib/canvas-commands';

interface BackgroundPropertiesPanelProps {
  canvas: any;
  onDirty?: () => void;
}

export function BackgroundPropertiesPanel({ canvas, onDirty }: BackgroundPropertiesPanelProps) {
  const { openCrop } = useCropStore();
  const [bgUrlInput, setBgUrlInput] = useState('');
  const { recentColors, addRecentColor } = useRecentColors();

  const recordChange = useCallback(() => {
    recordCanvasHistory(onDirty);
  }, [onDirty]);

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
        recordChange();
        canvas.renderAll();
        setBgUrlInput('');
        toast.success('Đã xóa ảnh nền!');
        return;
      }
      const { FabricImage } = await import('fabric');
      const bgImg = await FabricImage.fromURL(url, { crossOrigin: 'anonymous' });
      const baseWidth = canvas.getWidth();
      const baseHeight = canvas.getHeight();
      const scaleX = baseWidth / (bgImg.width || baseWidth);
      const scaleY = baseHeight / (bgImg.height || baseHeight);
      bgImg.set({
        originX: 'left',
        originY: 'top',
        left: 0,
        top: 0,
        scaleX: Math.max(scaleX, scaleY),
        scaleY: Math.max(scaleX, scaleY),
        selectable: false,
        evented: false,
        hasControls: false,
        hasBorders: false,
      });
      (bgImg as any)._isBackground = true;
      (bgImg as any).layerId = 'background_layer';
      (bgImg as any).layerName = 'Background';
      (bgImg as any).src = url;
      (bgImg as any).defaultImageUrl = url;
      await canvas.setBackgroundImage(bgImg, canvas.renderAll.bind(canvas));
      recordChange();
      canvas.renderAll();
      setBgUrlInput('');
      toast.success('Đã đổi ảnh nền!');
    } catch {
      toast.error('Không thể tải ảnh từ URL này.');
    }
  };

  const handleBackgroundColorChange = (color: string) => {
    if (!canvas) return;
    addRecentColor(color);
    canvas.set('backgroundColor', color);
    recordChange();
    canvas.renderAll();
  };

  const handleBackgroundGradientChange = async (
    type: 'linear' | 'radial',
    color1: string,
    color2: string,
    angle: number
  ) => {
    if (!canvas) return;
    addRecentColor(color1);
    addRecentColor(color2);
    const { Gradient } = await import('fabric');
    const baseWidth = canvas.getWidth();
    const baseHeight = canvas.getHeight();
    let coords: any;
    if (type === 'linear') {
      const angleRad = (angle * Math.PI) / 180;
      coords = {
        x1: baseWidth * (0.5 - 0.5 * Math.cos(angleRad)),
        y1: baseHeight * (0.5 - 0.5 * Math.sin(angleRad)),
        x2: baseWidth * (0.5 + 0.5 * Math.cos(angleRad)),
        y2: baseHeight * (0.5 + 0.5 * Math.sin(angleRad)),
      };
    } else {
      coords = {
        x1: baseWidth / 2,
        y1: baseHeight / 2,
        r1: 0,
        x2: baseWidth / 2,
        y2: baseHeight / 2,
        r2: Math.max(baseWidth, baseHeight) / 2,
      };
    }
    canvas.set(
      'backgroundColor',
      new Gradient({
        type,
        gradientUnits: 'pixels',
        coords,
        colorStops: [
          { offset: 0, color: color1 },
          { offset: 1, color: color2 },
        ],
      })
    );
    canvas.renderAll();
    recordChange();
  };

  const handleEyedropper = () => {
    if (!canvas) return;
    toast.info('Click vào bất kỳ điểm nào trên canvas để lấy màu (tính năng đang phát triển)');
  };

  const ColorPalette = ({
    onSelect,
    selectedColor,
  }: {
    onSelect: (c: string) => void;
    selectedColor?: string;
  }) => (
    <div className="space-y-2 pt-1">
      {recentColors.length > 0 && (
        <div className="space-y-1">
          <span className="text-[8px] font-semibold text-slate-400">Gần đây</span>
          <div className="flex gap-1 flex-wrap">
            {recentColors.map((c) => (
              <button
                key={c}
                onClick={() => onSelect(c)}
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
          {PRESET_COLORS.map((c) => (
            <button
              key={c}
              onClick={() => onSelect(c)}
              className="w-5 h-5 rounded-md border border-slate-300 transition-transform hover:scale-110 active:scale-95 cursor-pointer"
              style={{ backgroundColor: c }}
            />
          ))}
        </div>
      </div>
    </div>
  );

  const currentBgUrl = getBackgroundUrl();
  const currentBgGradient = getBackgroundGradient();
  const bgFillType: 'solid' | 'linear' | 'radial' = currentBgGradient
    ? currentBgGradient.type === 'radial'
      ? 'radial'
      : 'linear'
    : 'solid';
  const bgColor1 =
    currentBgGradient?.colorStops?.[0]?.color ||
    (typeof canvas?.backgroundColor === 'string' ? canvas.backgroundColor : '#ffffff');
  const bgColor2 = currentBgGradient?.colorStops?.[1]?.color || '#ffffff';
  const bgAngle =
    currentBgGradient?.type === 'linear' && currentBgGradient.coords
      ? getGradientAngle(currentBgGradient.coords)
      : 0;

  return (
    <div className="flex flex-col h-full space-y-4">
      <div className="flex items-center gap-1.5 px-1 border-b border-slate-200 pb-3">
        <Palette className="w-4 h-4 text-indigo-600" />
        <h3 className="font-bold text-sm text-slate-400">Quản lý Ảnh Nền</h3>
      </div>

      <div className="flex-1 overflow-y-auto pr-1 space-y-4 pb-8 scrollbar-thin scrollbar-thumb-slate-300">
        <CollapsibleSection
          id="bg-image"
          icon={<ImageIcon className="w-3.5 h-3.5" />}
          title="Ảnh nền Template"
          defaultOpen={true}
        >
          <div className="aspect-video w-full rounded-xl bg-white border border-slate-200 flex items-center justify-center overflow-hidden relative group">
            {currentBgUrl ? (
              <img src={currentBgUrl} alt="Background" className="w-full h-full object-cover" />
            ) : (
              <div className="text-[10px] text-slate-400 font-mono">Trống (Màu nền đơn)</div>
            )}
            {currentBgUrl && (
              <div className="absolute inset-0 bg-white/80 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                <Button
                  size="sm"
                  variant="destructive"
                  onClick={() => handleBackgroundUrlChange('')}
                  className="h-7 text-[10px] rounded-lg px-2 cursor-pointer"
                >
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
              <Input
                placeholder="Dán liên kết ảnh..."
                value={bgUrlInput}
                onChange={(e) => setBgUrlInput(e.target.value)}
                className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-8 flex-1"
              />
              <Button
                size="sm"
                onClick={() => handleBackgroundUrlChange(bgUrlInput)}
                className="bg-indigo-600 hover:bg-indigo-500 rounded-xl text-xs font-bold h-8 px-3 cursor-pointer"
              >
                Áp dụng
              </Button>
            </div>
          </div>
        </CollapsibleSection>

        <CollapsibleSection
          id="bg-color"
          icon={<Palette className="w-3.5 h-3.5" />}
          title="Màu nền & Chuyển sắc"
          defaultOpen={true}
        >
          <div className="flex p-0.5 bg-white border border-slate-200 rounded-xl">
            {(['solid', 'linear', 'radial'] as const).map((t) => (
              <button
                key={t}
                type="button"
                onClick={() => {
                  if (t !== bgFillType) {
                    if (t === 'solid') handleBackgroundColorChange(bgColor1);
                    else handleBackgroundGradientChange(t, bgColor1, bgColor2, bgAngle);
                  }
                }}
                className={`flex-1 py-1 rounded-lg text-[10px] font-bold transition-all cursor-pointer ${
                  bgFillType === t
                    ? 'bg-indigo-50 text-indigo-600 border border-indigo-200'
                    : 'text-slate-400 hover:text-slate-400'
                }`}
              >
                {t === 'solid' ? 'Đơn' : t === 'linear' ? 'Linear' : 'Radial'}
              </button>
            ))}
          </div>
          {bgFillType === 'solid' ? (
            <div className="space-y-1">
              <span className="text-[9px] font-semibold text-slate-400">Chọn màu nền</span>
              <div className="flex gap-2">
                <input
                  type="color"
                  value={rgbaToHex(bgColor1)}
                  onChange={(e) => handleBackgroundColorChange(e.target.value)}
                  className="w-8 h-8 rounded-lg bg-transparent border border-slate-200 cursor-pointer overflow-hidden"
                />
                <Input
                  value={bgColor1}
                  onChange={(e) => handleBackgroundColorChange(e.target.value)}
                  className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-8 font-mono flex-1"
                />
                <Button
                  size="icon"
                  variant="ghost"
                  onClick={handleEyedropper}
                  className="w-8 h-8 rounded-lg text-slate-500 hover:text-slate-800 cursor-pointer"
                  title="Công cụ lấy màu"
                >
                  <Pipette className="w-3.5 h-3.5" />
                </Button>
              </div>
              <ColorPalette onSelect={handleBackgroundColorChange} selectedColor={bgColor1} />
            </div>
          ) : (
            <div className="space-y-3 pt-1">
              {(['color1', 'color2'] as const).map((stop, i) => (
                <div key={stop} className="space-y-1">
                  <span className="text-[9px] font-semibold text-slate-400">
                    {i === 0 ? 'Màu bắt đầu' : 'Màu kết thúc'}
                  </span>
                  <div className="flex gap-2">
                    <input
                      type="color"
                      value={rgbaToHex(i === 0 ? bgColor1 : bgColor2)}
                      onChange={(e) =>
                        handleBackgroundGradientChange(
                          bgFillType,
                          i === 0 ? e.target.value : bgColor1,
                          i === 0 ? bgColor2 : e.target.value,
                          bgAngle
                        )
                      }
                      className="w-8 h-8 rounded-lg bg-transparent border border-slate-200 cursor-pointer overflow-hidden"
                    />
                    <Input
                      value={i === 0 ? bgColor1 : bgColor2}
                      onChange={(e) =>
                        handleBackgroundGradientChange(
                          bgFillType,
                          i === 0 ? e.target.value : bgColor1,
                          i === 0 ? bgColor2 : e.target.value,
                          bgAngle
                        )
                      }
                      className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-8 font-mono flex-1"
                    />
                  </div>
                </div>
              ))}
              {bgFillType === 'linear' && (
                <div className="space-y-1.5">
                  <div className="flex justify-between text-[9px] font-semibold text-slate-400">
                    <span>Góc</span>
                    <span>{bgAngle}°</span>
                  </div>
                  <div className="flex gap-3 items-center">
                    <input
                      type="range"
                      min="0"
                      max="360"
                      step="5"
                      value={bgAngle}
                      onChange={(e) =>
                        handleBackgroundGradientChange('linear', bgColor1, bgColor2, parseInt(e.target.value))
                      }
                      className="flex-1 h-1 bg-white rounded-lg appearance-none cursor-pointer accent-indigo-600"
                    />
                    <Input
                      type="number"
                      min="0"
                      max="360"
                      value={bgAngle}
                      onChange={(e) =>
                        handleBackgroundGradientChange(
                          'linear',
                          bgColor1,
                          bgColor2,
                          Math.max(0, Math.min(360, parseInt(e.target.value) || 0))
                        )
                      }
                      className="w-14 bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-7 text-center px-1"
                    />
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
