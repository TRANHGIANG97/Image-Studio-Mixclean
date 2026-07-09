'use client';

import React, { useState } from 'react';
import { Image as ImageIcon, Sparkles, RefreshCw, Crop as CropIcon } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { toast } from 'sonner';
import { CollapsibleSection } from './CollapsibleSection';
import { useEditorStore } from '@/store/editor.store';
import { useLayersStore } from '@/store/layers.store';
import { useCropStore } from '@/store/crop.store';
import { removeImageBackground } from '@/lib/background-remover';
import { uploadInlineImageUrl } from '@/lib/canvas-upload';

interface ImagePropertiesSectionProps {
  showContent: boolean;
  isImageObject: boolean;
  activeObjectProps: Record<string, any>;
  onPropChange: (name: string, value: any) => void;
  onRecordChange: () => void;
}

export function ImagePropertiesSection({
  showContent,
  isImageObject,
  activeObjectProps,
  onPropChange,
  onRecordChange,
}: ImagePropertiesSectionProps) {
  const { canvas } = useEditorStore();
  const { updateActiveObject } = useLayersStore();
  const { openCrop } = useCropStore();
  const [isRemovingBg, setIsRemovingBg] = useState(false);

  const activeCanvasObject = canvas?.getActiveObject();

  const handleRemoveBackground = async () => {
    if (!canvas || !activeCanvasObject) return;
    const imageUrl =
      activeObjectProps?.src ||
      (activeCanvasObject as any)._originalElement?.src ||
      (activeCanvasObject as any).src ||
      '';
    if (!imageUrl) {
      toast.error('Không tìm thấy nguồn hình ảnh để xóa nền.');
      return;
    }
    setIsRemovingBg(true);
    const loadingToast = toast.loading('Đang tách nền ảnh bằng AI...');
    try {
      const resultUrl = await removeImageBackground(imageUrl);
      const layerId = (activeCanvasObject as any).layerId || `layer_${Date.now()}`;
      const uploadedUrl = await uploadInlineImageUrl(
        resultUrl,
        `${layerId}_nobg.png`,
        'template-layers',
      );
      if (!uploadedUrl) {
        toast.dismiss(loadingToast);
        toast.error('Tách nền thành công nhưng không thể tải ảnh lên server.');
        return;
      }
      const imgElement = new Image();
      imgElement.crossOrigin = 'anonymous';
      imgElement.src = uploadedUrl;
      await new Promise((resolve, reject) => {
        imgElement.onload = resolve;
        imgElement.onerror = reject;
      });
      (activeCanvasObject as any).setElement(imgElement);
      (activeCanvasObject as any).set({ src: uploadedUrl, defaultImageUrl: uploadedUrl });
      (activeCanvasObject as any).applyFilters();
      canvas.renderAll();
      updateActiveObject({ src: uploadedUrl, defaultImageUrl: uploadedUrl });
      onRecordChange();
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

  const handleClipShapeChange = async (
    shape: 'circle' | 'rounded-rect' | 'heart' | 'star' | 'hexagon' | 'none'
  ) => {
    if (!canvas || !activeCanvasObject) return;
    const { Circle, Rect, Path } = await import('fabric');
    const width = activeCanvasObject.width || 200;
    const height = activeCanvasObject.height || 200;
    let clipPathObj: any = null;

    if (shape === 'circle') {
      clipPathObj = new Circle({
        radius: Math.min(width, height) / 2,
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
      const pathData = `M 0 ${-height * 0.35} C ${-width * 0.25} ${-height * 0.65} ${-width * 0.55} ${-height * 0.4} ${-width * 0.5} ${-height * 0.1} C ${-width * 0.45} ${height * 0.2} ${-width * 0.2} ${height * 0.35} 0 ${height * 0.5} C ${width * 0.2} ${height * 0.35} ${width * 0.45} ${height * 0.2} ${width * 0.5} ${-height * 0.1} C ${width * 0.55} ${-height * 0.4} ${width * 0.25} ${-height * 0.65} 0 ${-height * 0.35} Z`;
      clipPathObj = new Path(pathData, { originX: 'center', originY: 'center', left: 0, top: 0 });
    } else if (shape === 'star') {
      const rOuter = Math.min(width, height) / 2;
      const rInner = rOuter * 0.4;
      let pathData = '';
      for (let i = 0; i < 10; i++) {
        const angle = (i * Math.PI) / 5 - Math.PI / 2;
        const r = i % 2 === 0 ? rOuter : rInner;
        const x = r * Math.cos(angle);
        const y = r * Math.sin(angle);
        pathData += i === 0 ? `M ${x} ${y} ` : `L ${x} ${y} `;
      }
      pathData += 'Z';
      clipPathObj = new Path(pathData, { originX: 'center', originY: 'center', left: 0, top: 0 });
    } else if (shape === 'hexagon') {
      const pathData = `M 0 ${-height / 2} L ${width / 2} ${-height / 4} L ${width / 2} ${height / 4} L 0 ${height / 2} L ${-width / 2} ${height / 4} L ${-width / 2} ${-height / 4} Z`;
      clipPathObj = new Path(pathData, { originX: 'center', originY: 'center', left: 0, top: 0 });
    }

    if (clipPathObj) clipPathObj.absolutePositioning = false;
    activeCanvasObject.set({ clipPath: clipPathObj || undefined });
    updateActiveObject({ clipShape: shape });
    canvas.renderAll();
    onRecordChange();
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
        const isPlaceholder =
          (activeCanvasObject as any).layerType === 'PLACEHOLDER_OBJECT' ||
          (activeCanvasObject as any).isReplaceable === true;
        const nextDefault = isPlaceholder
          ? (activeCanvasObject as any).defaultImageUrl || url
          : url;
        (activeCanvasObject as any).set({ src: url, defaultImageUrl: nextDefault });
        (activeCanvasObject as any).applyFilters();
        canvas.renderAll();
        updateActiveObject({ src: url, defaultImageUrl: nextDefault });
        onRecordChange();
        toast.dismiss(loadingToast);
        toast.success('Thay thế hình ảnh thành công!');
      } catch (err) {
        console.error(err);
        toast.error('Lỗi khi thay thế hình ảnh.');
      }
    };
    reader.readAsDataURL(file);
  };

  const applyFilterPreset = async (
    preset: 'none' | 'grayscale' | 'sepia' | 'vintage' | 'cool' | 'warm'
  ) => {
    if (!canvas) return;
    const activeObject = canvas.getActiveObject() as any;
    if (!activeObject || (activeObject.type !== 'image' && activeObject.layerType !== 'IMAGE' && activeObject.layerType !== 'PLACEHOLDER_OBJECT')) return;
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
      nextFilters = { ...nextFilters, sepia: true, brightness: 0.05, contrast: -0.1, saturation: -0.15 };
      newFilterList.push(new filters.Sepia());
      newFilterList.push(new filters.Brightness({ brightness: 0.05 }));
      newFilterList.push(new filters.Contrast({ contrast: -0.1 }));
      newFilterList.push(new filters.Saturation({ saturation: -0.15 }));
    } else if (preset === 'cool') {
      nextFilters.toneColor = '#0055ff';
      nextFilters.toneAlpha = 0.2;
      newFilterList.push(new filters.BlendColor({ color: '#0055ff', mode: 'multiply', alpha: 0.2 }));
    } else if (preset === 'warm') {
      nextFilters.toneColor = '#ffaa00';
      nextFilters.toneAlpha = 0.25;
      newFilterList.push(new filters.BlendColor({ color: '#ffaa00', mode: 'multiply', alpha: 0.25 }));
    }

    activeObject.filters = newFilterList;
    activeObject.applyFilters();
    canvas.renderAll();
    updateActiveObject({ imageFilters: nextFilters });
    onRecordChange();
    toast.success(`Đã áp dụng bộ lọc ${preset === 'none' ? 'Mặc định' : preset}`);
  };

  const handleImageFilterChange = async (filterType: string, value: any) => {
    if (!canvas) return;
    const activeObject = canvas.getActiveObject() as any;
    if (!activeObject || (activeObject.type !== 'image' && activeObject.layerType !== 'IMAGE' && activeObject.layerType !== 'PLACEHOLDER_OBJECT')) return;
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
    if (nextFilters.grayscale) newFilterList.push(new filters.Grayscale());
    if (nextFilters.sepia) newFilterList.push(new filters.Sepia());
    if (nextFilters.toneColor) {
      if (nextFilters.toneGrayscale) newFilterList.push(new filters.Grayscale());
      newFilterList.push(
        new filters.BlendColor({
          color: nextFilters.toneColor,
          mode: 'multiply',
          alpha: typeof nextFilters.toneAlpha === 'number' ? nextFilters.toneAlpha : 1,
        })
      );
    }

    activeObject.filters = newFilterList;
    activeObject.applyFilters();
    canvas.renderAll();
    updateActiveObject({ imageFilters: nextFilters });
    onRecordChange();
  };

  const applyTonePreset = async (toneColor: string | null, grayscale = true, toneAlpha = 1) => {
    if (!canvas) return;
    const activeObject = canvas.getActiveObject() as any;
    if (!activeObject || (activeObject.type !== 'image' && activeObject.layerType !== 'IMAGE' && activeObject.layerType !== 'PLACEHOLDER_OBJECT')) return;
    const currentFilters = activeObjectProps.imageFilters || {};
    const nextFilters = { ...currentFilters, toneColor, toneGrayscale: grayscale, toneAlpha };
    const { filters } = await import('fabric');
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
    if (nextFilters.grayscale) newFilterList.push(new filters.Grayscale());
    if (nextFilters.sepia) newFilterList.push(new filters.Sepia());
    if (toneColor) {
      if (grayscale) newFilterList.push(new filters.Grayscale());
      newFilterList.push(
        new filters.BlendColor({
          color: toneColor,
          mode: 'multiply',
          alpha: typeof toneAlpha === 'number' ? toneAlpha : 1,
        })
      );
    }
    activeObject.filters = newFilterList;
    activeObject.applyFilters();
    canvas.renderAll();
    updateActiveObject({ imageFilters: nextFilters });
    onRecordChange();
  };

  if (!showContent || !isImageObject) return null;

  return (
    <>
      <CollapsibleSection id="image-filters" icon={<ImageIcon className="w-3 h-3" />} title="Hiệu ứng Ảnh">
        {[
          { key: 'brightness', label: 'Độ sáng', min: -1, max: 1, step: 0.05 },
          { key: 'contrast', label: 'Tương phản', min: -1, max: 1, step: 0.05 },
          { key: 'saturation', label: 'Bão hòa', min: -1, max: 1, step: 0.05 },
          { key: 'blur', label: 'Làm mờ', min: 0, max: 1, step: 0.05 },
        ].map(({ key, label, min, max, step }) => (
          <div key={key} className="space-y-1">
            <div className="flex justify-between text-[9px] font-semibold text-slate-400">
              <span>{label}</span>
              <span>{activeObjectProps.imageFilters?.[key] || 0}</span>
            </div>
            <input
              type="range"
              min={min}
              max={max}
              step={step}
              value={activeObjectProps.imageFilters?.[key] || 0}
              onChange={(e) => handleImageFilterChange(key, parseFloat(e.target.value))}
              className="w-full h-1 bg-white rounded-lg appearance-none cursor-pointer accent-indigo-600"
            />
          </div>
        ))}
        <div className="flex gap-4 pt-2 border-t border-slate-200/50">
          <label className="flex items-center gap-2 cursor-pointer">
            <input
              type="checkbox"
              checked={activeObjectProps.imageFilters?.grayscale || false}
              onChange={(e) => handleImageFilterChange('grayscale', e.target.checked)}
              className="rounded text-indigo-600 focus:ring-indigo-600 bg-white border-slate-200"
            />
            <span className="text-[10px] font-semibold text-slate-500">Trắng đen</span>
          </label>
          <label className="flex items-center gap-2 cursor-pointer">
            <input
              type="checkbox"
              checked={activeObjectProps.imageFilters?.sepia || false}
              onChange={(e) => handleImageFilterChange('sepia', e.target.checked)}
              className="rounded text-indigo-600 focus:ring-indigo-600 bg-white border-slate-200"
            />
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
              onChange={(e) =>
                applyTonePreset(e.target.value, true, activeObjectProps.imageFilters?.toneAlpha ?? 1)
              }
              className="w-9 h-8 rounded-lg bg-white border border-slate-200 cursor-pointer"
              title="Chọn màu tone"
            />
            <input
              type="range"
              min="0"
              max="100"
              value={Math.round((activeObjectProps.imageFilters?.toneAlpha ?? 1) * 100)}
              onChange={(e) =>
                applyTonePreset(
                  activeObjectProps.imageFilters?.toneColor || '#808080',
                  true,
                  Math.max(0, Math.min(1, (parseInt(e.target.value) || 0) / 100))
                )
              }
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

      <CollapsibleSection
        id="image-ai-mask"
        icon={<Sparkles className="w-3.5 h-3.5" />}
        title="Cắt ảnh & Nền (AI)"
        defaultOpen={true}
      >
        <div className="space-y-3">
          <div className="space-y-1">
            <span className="text-[10px] font-semibold text-slate-400">Công cụ AI</span>
            <Button
              size="sm"
              onClick={handleRemoveBackground}
              disabled={isRemovingBg}
              className="w-full bg-gradient-to-r from-violet-600 to-indigo-600 hover:from-violet-500 hover:to-indigo-500 text-white rounded-xl text-xs font-bold h-8 flex items-center justify-center gap-1.5 shadow-lg disabled:opacity-50 cursor-pointer"
            >
              <Sparkles className="w-3.5 h-3.5 animate-pulse" />
              {isRemovingBg ? 'Đang xóa nền...' : 'Tách nền chân dung (AI)'}
            </Button>
            <p className="text-[8px] text-slate-400">
              MediaPipe Selfie Segmentation — phù hợp ảnh chân dung, không tối ưu cho sản phẩm/logo.
            </p>
          </div>
          <div className="space-y-1 pt-1.5 border-t border-slate-200/50">
            <span className="text-[10px] font-semibold text-slate-400">Thay thế ảnh</span>
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

      <CollapsibleSection id="placeholder" icon={<ImageIcon className="w-3 h-3" />} title="Placeholder">
        <div className="flex items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white px-3 py-2.5">
          <div className="min-w-0">
            <p className="text-[10px] font-semibold text-slate-600">Đối tượng có thể thay thế</p>
            <p className="text-[8px] text-slate-400 leading-snug mt-0.5">
              Hiển thị nút &quot;Thay thế&quot; trên app Android studio_edit.
            </p>
          </div>
          <label className="relative inline-flex shrink-0 cursor-pointer items-center">
            <input
              type="checkbox"
              checked={activeObjectProps.isReplaceable === true}
              onChange={(e) => onPropChange('isReplaceable', e.target.checked)}
              className="peer sr-only"
            />
            <span className="h-5 w-9 rounded-full bg-slate-200 transition-colors peer-checked:bg-pink-500" />
            <span className="absolute left-0.5 top-0.5 h-4 w-4 rounded-full bg-white shadow transition-transform peer-checked:translate-x-4" />
          </label>
        </div>
        <div className="space-y-1">
          <span className="text-[9px] font-semibold text-slate-400">URL ảnh mặc định</span>
          <Input
            value={activeObjectProps.defaultImageUrl || ''}
            onChange={(e) => onPropChange('defaultImageUrl', e.target.value)}
            placeholder="https://pub-yourbucket.r2.dev/..."
            className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl"
          />
        </div>
        <div className="space-y-1">
          <span className="text-[9px] font-semibold text-slate-400">Tỷ lệ cắt (Crop Ratio)</span>
          <select
            value={activeObjectProps.cropRatio || ''}
            onChange={(e) => onPropChange('cropRatio', e.target.value || null)}
            className="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-400 focus:outline-none focus:border-indigo-600"
          >
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
        <p className="text-[9px] text-slate-400 leading-normal mt-1">
          URL ảnh ban đầu trên app Android. Người dùng có thể thay bằng ảnh của họ.
        </p>
      </CollapsibleSection>
    </>
  );
}
