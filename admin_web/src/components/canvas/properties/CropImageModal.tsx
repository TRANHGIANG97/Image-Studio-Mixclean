'use client';

import React, { useState, useRef, useEffect, useCallback } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Loader2, Crop as CropIcon, Plus, Replace } from 'lucide-react';
import { toast } from 'sonner';

interface CropImageModalProps {
  isOpen: boolean;
  onClose: () => void;
  imageUrl: string;
  initialRatio?: string;
  onCropComplete: (newImageUrl: string, ratio: string) => void;
  onCropReplace?: (newImageUrl: string) => void;
}

const CROP_RATIOS = [
  { value: 'FREE', label: 'Tự do', widthRatio: 0, heightRatio: 0 },
  { value: 'ORIGINAL', label: 'Giữ tỷ lệ gốc', widthRatio: -1, heightRatio: -1 },
  { value: 'RATIO_1_1', label: '1:1 (Vuông)', widthRatio: 1, heightRatio: 1 },
  { value: 'RATIO_3_4', label: '3:4 (Dọc)', widthRatio: 3, heightRatio: 4 },
  { value: 'RATIO_4_3', label: '4:3 (Ngang)', widthRatio: 4, heightRatio: 3 },
  { value: 'RATIO_9_16', label: '9:16 (Dọc dài)', widthRatio: 9, heightRatio: 16 },
  { value: 'RATIO_16_9', label: '16:9 (Ngang rộng)', widthRatio: 16, heightRatio: 9 },
  { value: 'CUSTOM', label: 'Tùy chỉnh W:H...', widthRatio: 0, heightRatio: 0 },
];

export default function CropImageModal({
  isOpen,
  onClose,
  imageUrl,
  initialRatio = 'FREE',
  onCropComplete,
  onCropReplace,
}: CropImageModalProps) {
  const [cropMode, setCropMode] = useState<'RECT' | 'POLYGON'>('RECT');
  const [selectedRatio, setSelectedRatio] = useState<string>('FREE');
  const [customRatioInput, setCustomRatioInput] = useState('4:3');
  const [loadingPhase, setLoadingPhase] = useState<'rendering' | 'uploading' | null>(null);
  const [imgLoaded, setImgLoaded] = useState(false);
  const [imgDimensions, setImgDimensions] = useState({ width: 0, height: 0, naturalWidth: 0, naturalHeight: 0 });
  const [crop, setCrop] = useState({ x: 0, y: 0, width: 0, height: 0 });
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

  // Manual numeric inputs for precise crop (RECT mode)
  const [manualX, setManualX] = useState('');
  const [manualY, setManualY] = useState('');
  const [manualW, setManualW] = useState('');
  const [manualH, setManualH] = useState('');

  // Polygon mode states
  const [points, setPoints] = useState<{ x: number; y: number }[]>([]);
  const [isPolygonClosed, setIsPolygonClosed] = useState(false);

  const containerRef = useRef<HTMLDivElement>(null);
  const imgRef = useRef<HTMLImageElement>(null);
  const previewCanvasRef = useRef<HTMLCanvasElement>(null);

  // Sync initial ratio and reset when modal opens
  useEffect(() => {
    if (isOpen) {
      const match = CROP_RATIOS.find(r => r.value === initialRatio);
      setSelectedRatio(match ? match.value : 'FREE');
      setImgLoaded(false);
      setCropMode('RECT');
      setPoints([]);
      setIsPolygonClosed(false);
      setPreviewUrl(null);
      setLoadingPhase(null);
      setManualX('');
      setManualY('');
      setManualW('');
      setManualH('');
    }
  }, [isOpen, initialRatio]);

  const getRatioVal = (ratioKey: string, naturalRatio: number): number | undefined => {
    if (ratioKey === 'FREE') return undefined;
    if (ratioKey === 'ORIGINAL') return naturalRatio;
    if (ratioKey === 'CUSTOM') {
      const parts = customRatioInput.split(':').map(Number);
      if (parts.length === 2 && parts[0] > 0 && parts[1] > 0) return parts[0] / parts[1];
      return undefined;
    }
    const match = CROP_RATIOS.find(r => r.value === ratioKey);
    if (match && match.widthRatio > 0) return match.widthRatio / match.heightRatio;
    return undefined;
  };

  const currentRatioVal = getRatioVal(
    selectedRatio,
    imgDimensions.naturalWidth / (imgDimensions.naturalHeight || 1)
  );

  const initCropBox = useCallback((
    containerWidth: number,
    containerHeight: number,
    naturalRatio: number,
    ratioValue?: number
  ) => {
    const finalRatio = ratioValue !== undefined
      ? ratioValue
      : getRatioVal(selectedRatio, naturalRatio);

    let w = containerWidth * 0.8;
    let h = containerHeight * 0.8;

    if (finalRatio) {
      if (w / h > finalRatio) {
        w = h * finalRatio;
      } else {
        h = w / finalRatio;
      }
    }

    const x = (containerWidth - w) / 2;
    const y = (containerHeight - h) / 2;

    const newCrop = { x, y, width: w, height: h };
    setCrop(newCrop);
    syncManualInputs(newCrop, containerWidth, containerHeight, naturalRatio);
  }, [selectedRatio, customRatioInput]);

  const syncManualInputs = (c: typeof crop, imgW: number, imgH: number, natW: number) => {
    if (!imgW || !imgH || !natW) return;
    const scaleX = natW / imgW;
    const scaleY = (imgDimensions.naturalHeight || natW) / (imgH || 1);
    setManualX(Math.round(c.x * scaleX).toString());
    setManualY(Math.round(c.y * scaleY).toString());
    setManualW(Math.round(c.width * scaleX).toString());
    setManualH(Math.round(c.height * scaleY).toString());
  };

  const handleImageLoad = (e: React.SyntheticEvent<HTMLImageElement>) => {
    const img = e.currentTarget;
    const width = img.width;
    const height = img.height;
    const naturalWidth = img.naturalWidth;
    const naturalHeight = img.naturalHeight;

    setImgDimensions({ width, height, naturalWidth, naturalHeight });
    setImgLoaded(true);

    const naturalRatio = naturalWidth / (naturalHeight || 1);
    const initialRatioVal = getRatioVal(selectedRatio, naturalRatio);
    initCropBox(width, height, naturalRatio, initialRatioVal);

    setPoints([]);
    setIsPolygonClosed(false);
  };

  // ResizeObserver: recompute crop box when container resizes
  useEffect(() => {
    if (!containerRef.current || !imgLoaded) return;
    const observer = new ResizeObserver(() => {
      const img = imgRef.current;
      if (!img) return;
      const newW = img.width;
      const newH = img.height;
      if (newW !== imgDimensions.width || newH !== imgDimensions.height) {
        const naturalRatio = img.naturalWidth / (img.naturalHeight || 1);
        setImgDimensions({ width: newW, height: newH, naturalWidth: img.naturalWidth, naturalHeight: img.naturalHeight });
        const rVal = getRatioVal(selectedRatio, naturalRatio);
        initCropBox(newW, newH, naturalRatio, rVal);
      }
    });
    observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, [imgLoaded, selectedRatio, customRatioInput]);

  // Re-adjust crop box when ratio changes
  const handleRatioChange = (ratioValue: string) => {
    setSelectedRatio(ratioValue);
    if (!imgLoaded) return;
    const naturalRatio = imgDimensions.naturalWidth / (imgDimensions.naturalHeight || 1);
    const rVal = getRatioVal(ratioValue, naturalRatio);
    initCropBox(imgDimensions.width, imgDimensions.height, naturalRatio, rVal);
  };

  // Apply manual numeric inputs to crop box (in natural pixels → display coords)
  const applyManualInputs = () => {
    if (!imgLoaded || !imgDimensions.width) return;
    const scaleX = imgDimensions.width / imgDimensions.naturalWidth;
    const scaleY = imgDimensions.height / imgDimensions.naturalHeight;
    const x = parseFloat(manualX) * scaleX;
    const y = parseFloat(manualY) * scaleY;
    const w = parseFloat(manualW) * scaleX;
    const h = parseFloat(manualH) * scaleY;
    if (!isNaN(x) && !isNaN(y) && !isNaN(w) && !isNaN(h) && w > 0 && h > 0) {
      const clampedX = Math.max(0, Math.min(imgDimensions.width - 1, x));
      const clampedY = Math.max(0, Math.min(imgDimensions.height - 1, y));
      const clampedW = Math.min(imgDimensions.width - clampedX, w);
      const clampedH = Math.min(imgDimensions.height - clampedY, h);
      setCrop({ x: clampedX, y: clampedY, width: clampedW, height: clampedH });
    }
  };

  // Live preview thumbnail update
  useEffect(() => {
    if (!imgRef.current || !imgLoaded || cropMode !== 'RECT' || crop.width < 2 || crop.height < 2) return;
    const timer = setTimeout(() => {
      try {
        const img = imgRef.current!;
        const scaleX = imgDimensions.naturalWidth / imgDimensions.width;
        const scaleY = imgDimensions.naturalHeight / imgDimensions.height;
        const PREVIEW_MAX = 120;
        const aspect = crop.width / crop.height;
        const pw = aspect > 1 ? PREVIEW_MAX : Math.round(PREVIEW_MAX * aspect);
        const ph = aspect > 1 ? Math.round(PREVIEW_MAX / aspect) : PREVIEW_MAX;
        const offscreen = document.createElement('canvas');
        offscreen.width = pw;
        offscreen.height = ph;
        const ctx = offscreen.getContext('2d')!;
        ctx.drawImage(img, crop.x * scaleX, crop.y * scaleY, crop.width * scaleX, crop.height * scaleY, 0, 0, pw, ph);
        setPreviewUrl(offscreen.toDataURL('image/jpeg', 0.7));
      } catch {
        setPreviewUrl(null);
      }
    }, 80);
    return () => clearTimeout(timer);
  }, [crop, imgLoaded, cropMode, imgDimensions]);

  const handleMouseDown = (e: React.MouseEvent, action: 'drag' | 'tl' | 'tr' | 'bl' | 'br') => {
    e.preventDefault();
    e.stopPropagation();

    const startX = e.clientX;
    const startY = e.clientY;
    const initialCrop = { ...crop };
    const ratio = currentRatioVal;

    const handleMouseMove = (moveEvent: MouseEvent) => {
      const dx = moveEvent.clientX - startX;
      const dy = moveEvent.clientY - startY;
      let newCrop = { ...initialCrop };

      if (action === 'drag') {
        newCrop.x = Math.max(0, Math.min(imgDimensions.width - newCrop.width, initialCrop.x + dx));
        newCrop.y = Math.max(0, Math.min(imgDimensions.height - newCrop.height, initialCrop.y + dy));
      } else {
        if (action === 'tl') {
          let proposedWidth = initialCrop.width - dx;
          let proposedHeight = initialCrop.height - dy;
          let proposedX = initialCrop.x + dx;
          let proposedY = initialCrop.y + dy;

          if (proposedX < 0) { proposedWidth += proposedX; proposedX = 0; }
          if (proposedY < 0) { proposedHeight += proposedY; proposedY = 0; }
          if (proposedWidth < 20) proposedWidth = 20;
          if (proposedHeight < 20) proposedHeight = 20;

          if (ratio) {
            if (Math.abs(dx) > Math.abs(dy)) {
              proposedHeight = proposedWidth / ratio;
              proposedY = initialCrop.y + (initialCrop.height - proposedHeight);
              if (proposedY < 0) {
                proposedY = 0;
                proposedHeight = initialCrop.y + initialCrop.height;
                proposedWidth = proposedHeight * ratio;
                proposedX = initialCrop.x + (initialCrop.width - proposedWidth);
              }
            } else {
              proposedWidth = proposedHeight * ratio;
              proposedX = initialCrop.x + (initialCrop.width - proposedWidth);
              if (proposedX < 0) {
                proposedX = 0;
                proposedWidth = initialCrop.x + initialCrop.width;
                proposedHeight = proposedWidth / ratio;
                proposedY = initialCrop.y + (initialCrop.height - proposedHeight);
              }
            }
          }
          newCrop = { x: proposedX, y: proposedY, width: proposedWidth, height: proposedHeight };
        } else if (action === 'tr') {
          let proposedWidth = initialCrop.width + dx;
          let proposedHeight = initialCrop.height - dy;
          let proposedY = initialCrop.y + dy;

          if (initialCrop.x + proposedWidth > imgDimensions.width) proposedWidth = imgDimensions.width - initialCrop.x;
          if (proposedY < 0) { proposedHeight += proposedY; proposedY = 0; }
          if (proposedWidth < 20) proposedWidth = 20;
          if (proposedHeight < 20) proposedHeight = 20;

          if (ratio) {
            if (Math.abs(dx) > Math.abs(dy)) {
              proposedHeight = proposedWidth / ratio;
              proposedY = initialCrop.y + (initialCrop.height - proposedHeight);
              if (proposedY < 0) {
                proposedY = 0;
                proposedHeight = initialCrop.y + initialCrop.height;
                proposedWidth = proposedHeight * ratio;
              }
            } else {
              proposedWidth = proposedHeight * ratio;
              if (initialCrop.x + proposedWidth > imgDimensions.width) {
                proposedWidth = imgDimensions.width - initialCrop.x;
                proposedHeight = proposedWidth / ratio;
                proposedY = initialCrop.y + (initialCrop.height - proposedHeight);
              }
            }
          }
          newCrop = { x: initialCrop.x, y: proposedY, width: proposedWidth, height: proposedHeight };
        } else if (action === 'bl') {
          let proposedWidth = initialCrop.width - dx;
          let proposedHeight = initialCrop.height + dy;
          let proposedX = initialCrop.x + dx;

          if (proposedX < 0) { proposedWidth += proposedX; proposedX = 0; }
          if (initialCrop.y + proposedHeight > imgDimensions.height) proposedHeight = imgDimensions.height - initialCrop.y;
          if (proposedWidth < 20) proposedWidth = 20;
          if (proposedHeight < 20) proposedHeight = 20;

          if (ratio) {
            if (Math.abs(dx) > Math.abs(dy)) {
              proposedHeight = proposedWidth / ratio;
              if (initialCrop.y + proposedHeight > imgDimensions.height) {
                proposedHeight = imgDimensions.height - initialCrop.y;
                proposedWidth = proposedHeight * ratio;
                proposedX = initialCrop.x + (initialCrop.width - proposedWidth);
              }
            } else {
              proposedWidth = proposedHeight * ratio;
              proposedX = initialCrop.x + (initialCrop.width - proposedWidth);
              if (proposedX < 0) {
                proposedX = 0;
                proposedWidth = initialCrop.x + initialCrop.width;
                proposedHeight = proposedWidth / ratio;
              }
            }
          }
          newCrop = { x: proposedX, y: initialCrop.y, width: proposedWidth, height: proposedHeight };
        } else if (action === 'br') {
          let proposedWidth = initialCrop.width + dx;
          let proposedHeight = initialCrop.height + dy;

          if (initialCrop.x + proposedWidth > imgDimensions.width) proposedWidth = imgDimensions.width - initialCrop.x;
          if (initialCrop.y + proposedHeight > imgDimensions.height) proposedHeight = imgDimensions.height - initialCrop.y;
          if (proposedWidth < 20) proposedWidth = 20;
          if (proposedHeight < 20) proposedHeight = 20;

          if (ratio) {
            if (Math.abs(dx) > Math.abs(dy)) {
              proposedHeight = proposedWidth / ratio;
              if (initialCrop.y + proposedHeight > imgDimensions.height) {
                proposedHeight = imgDimensions.height - initialCrop.y;
                proposedWidth = proposedHeight * ratio;
              }
            } else {
              proposedWidth = proposedHeight * ratio;
              if (initialCrop.x + proposedWidth > imgDimensions.width) {
                proposedWidth = imgDimensions.width - initialCrop.x;
                proposedHeight = proposedWidth / ratio;
              }
            }
          }
          newCrop = { x: initialCrop.x, y: initialCrop.y, width: proposedWidth, height: proposedHeight };
        }
      }

      setCrop(newCrop);
      syncManualInputs(newCrop, imgDimensions.width, imgDimensions.height, imgDimensions.naturalWidth);
    };

    const handleMouseUp = () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);
  };

  // Polygon point handlers
  const projectPointToSegment = (px: number, py: number, ax: number, ay: number, bx: number, by: number) => {
    const dx = bx - ax, dy = by - ay;
    const lenSq = dx * dx + dy * dy;
    if (lenSq === 0) return { x: ax, y: ay, distance: Math.hypot(px - ax, py - ay) };
    const t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lenSq));
    const x = ax + t * dx, y = ay + t * dy;
    return { x, y, distance: Math.hypot(px - x, py - y) };
  };

  const findNearestSegment = (x: number, y: number) => {
    if (points.length < 2) return null;
    let bestIndex = -1;
    let bestPoint = { x: 0, y: 0, distance: Number.POSITIVE_INFINITY };
    for (let i = 0; i < points.length - 1; i++) {
      const projected = projectPointToSegment(x, y, points[i].x, points[i].y, points[i + 1].x, points[i + 1].y);
      if (projected.distance < bestPoint.distance) { bestIndex = i; bestPoint = projected; }
    }
    return bestIndex >= 0 ? { index: bestIndex, point: { x: bestPoint.x, y: bestPoint.y }, distance: bestPoint.distance } : null;
  };

  const handleContainerClick = (e: React.MouseEvent<HTMLDivElement>) => {
    if (cropMode !== 'POLYGON') return;
    if (!containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    const cleanX = Math.max(0, Math.min(imgDimensions.width, x));
    const cleanY = Math.max(0, Math.min(imgDimensions.height, y));

    const insertion = findNearestSegment(cleanX, cleanY);
    if (insertion && insertion.distance < 12) {
      setPoints(prev => { const updated = [...prev]; updated.splice(insertion.index + 1, 0, insertion.point); return updated; });
      return;
    }

    if (points.length >= 3 && !isPolygonClosed) {
      const firstPt = points[0];
      const dist = Math.sqrt((cleanX - firstPt.x) ** 2 + (cleanY - firstPt.y) ** 2);
      if (dist < 12) { setIsPolygonClosed(true); return; }
    }

    setPoints([...points, { x: cleanX, y: cleanY }]);
  };

  const handlePointMouseDown = (e: React.MouseEvent, index: number) => {
    e.stopPropagation();
    e.preventDefault();
    const startX = e.clientX, startY = e.clientY;
    const initialPt = points[index];

    const handleMouseMove = (moveEvent: MouseEvent) => {
      if (!containerRef.current) return;
      const dx = moveEvent.clientX - startX;
      const dy = moveEvent.clientY - startY;
      const proposedX = Math.max(0, Math.min(imgDimensions.width, initialPt.x + dx));
      const proposedY = Math.max(0, Math.min(imgDimensions.height, initialPt.y + dy));
      setPoints(prev => { const updated = [...prev]; updated[index] = { x: proposedX, y: proposedY }; return updated; });
    };

    const handleMouseUp = () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);
  };

  const renderAndUpload = async (mode: 'new' | 'replace') => {
    if (!imgRef.current || !imgLoaded) return;
    setLoadingPhase('rendering');

    try {
      const img = imgRef.current;
      const scaleX = imgDimensions.naturalWidth / imgDimensions.width;
      const scaleY = imgDimensions.naturalHeight / imgDimensions.height;

      const offCanvas = document.createElement('canvas');
      const ctx = offCanvas.getContext('2d')!;

      let mimeType = 'image/webp';
      let quality = 0.9;

      if (cropMode === 'RECT') {
        const cropX = crop.x * scaleX;
        const cropY = crop.y * scaleY;
        const cropW = crop.width * scaleX;
        const cropH = crop.height * scaleY;

        offCanvas.width = cropW;
        offCanvas.height = cropH;
        ctx.drawImage(img, cropX, cropY, cropW, cropH, 0, 0, cropW, cropH);
      } else {
        // Polygon Mode — use PNG to preserve alpha transparency
        if (points.length < 3) {
          toast.error('Vui lòng chọn ít nhất 3 điểm để cắt đa giác');
          setLoadingPhase(null);
          return;
        }

        mimeType = 'image/png'; // PNG preserves alpha channel
        quality = 1;

        const naturalPoints = points.map(pt => ({ x: pt.x * scaleX, y: pt.y * scaleY }));
        const xs = naturalPoints.map(pt => pt.x);
        const ys = naturalPoints.map(pt => pt.y);
        const minX = Math.min(...xs), maxX = Math.max(...xs);
        const minY = Math.min(...ys), maxY = Math.max(...ys);
        const cropW = maxX - minX;
        const cropH = maxY - minY;

        if (cropW < 2 || cropH < 2) {
          toast.error('Kích thước vùng cắt quá nhỏ');
          setLoadingPhase(null);
          return;
        }

        offCanvas.width = cropW;
        offCanvas.height = cropH;

        // Clear to transparent before clipping — CRITICAL for alpha channel
        ctx.clearRect(0, 0, cropW, cropH);

        ctx.beginPath();
        ctx.moveTo(naturalPoints[0].x - minX, naturalPoints[0].y - minY);
        for (let i = 1; i < naturalPoints.length; i++) {
          ctx.lineTo(naturalPoints[i].x - minX, naturalPoints[i].y - minY);
        }
        ctx.closePath();
        ctx.clip();

        ctx.drawImage(img, minX, minY, cropW, cropH, 0, 0, cropW, cropH);
      }

      setLoadingPhase('uploading');

      offCanvas.toBlob(async (blob) => {
        if (!blob) {
          toast.error('Lỗi khi trích xuất dữ liệu ảnh cắt');
          setLoadingPhase(null);
          return;
        }

        try {
          const ext = mimeType === 'image/png' ? 'png' : 'webp';
          const formData = new FormData();
          formData.append('file', blob, `cropped.${ext}`);
          formData.append('folder', 'cropped');
          formData.append('registerAsset', 'false');

          const response = await fetch('/api/upload', { method: 'POST', body: formData });

          if (!response.ok) throw new Error(`Upload API returned status ${response.status}`);

          const data = await response.json();
          if (data.success && data.fileUrl) {
            if (mode === 'replace' && onCropReplace) {
              onCropReplace(data.fileUrl);
            } else {
              onCropComplete(data.fileUrl, cropMode === 'RECT' && selectedRatio !== 'FREE' ? selectedRatio : '');
            }
            onClose();
          } else {
            throw new Error(data.error || 'Unknown upload response error');
          }
        } catch (uploadErr: any) {
          console.error('[CROP_ERROR] Uploading cropped blob failed:', uploadErr);
          toast.error(`Lỗi khi lưu ảnh cắt lên máy chủ: ${uploadErr.message}`);
        } finally {
          setLoadingPhase(null);
        }
      }, mimeType, quality);

    } catch (err: any) {
      console.error('[CROP_ERROR] Canvas rendering failed:', err);
      toast.error(`Lỗi khi xử lý cắt ảnh: ${err.message}`);
      setLoadingPhase(null);
    }
  };

  const isBusy = loadingPhase !== null;
  const loadingLabel = loadingPhase === 'rendering' ? 'Đang cắt ảnh...' : loadingPhase === 'uploading' ? 'Đang lưu lên server...' : '';

  // Relative positions for rect mask overlays
  const maskTop = crop.y;
  const maskBottom = imgDimensions.height - (crop.y + crop.height);
  const maskLeft = crop.x;

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent
        fullScreen
        className="fixed inset-0 top-0 left-0 translate-x-0 translate-y-0 w-screen h-screen max-w-none max-h-none rounded-none border-none p-6 flex flex-col gap-4 bg-slate-950/98 backdrop-blur-md focus:outline-none"
        style={{ top: 0, left: 0, transform: 'none' }}
      >
        <DialogHeader className="shrink-0 pb-3 border-b border-slate-800 flex flex-row items-center justify-between">
          <div className="flex items-center gap-2">
            <CropIcon className="w-5 h-5 text-indigo-400" />
            <DialogTitle className="text-lg font-bold text-slate-100">Cắt trích xuất Layer mới</DialogTitle>
          </div>
          {isBusy && (
            <div className="flex items-center gap-2 text-xs text-indigo-300 animate-pulse">
              <Loader2 className="w-4 h-4 animate-spin" />
              {loadingLabel}
            </div>
          )}
        </DialogHeader>

        <div className="flex-1 grid grid-cols-1 md:grid-cols-[1fr_300px] gap-6 min-h-0 py-2">
          {/* Main Cropper viewport */}
          <div className="relative overflow-hidden bg-slate-900 rounded-2xl flex items-center justify-center select-none h-full border border-slate-800 p-6">
            {!imgLoaded && (
              <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 text-slate-500">
                <Loader2 className="w-8 h-8 animate-spin text-indigo-400" />
                <span className="text-xs">Đang tải ảnh...</span>
              </div>
            )}

            <div
              ref={containerRef}
              className="relative inline-block max-h-full max-w-full"
              style={{ visibility: imgLoaded ? 'visible' : 'hidden' }}
              onClick={handleContainerClick}
            >
              <img
                ref={imgRef}
                src={imageUrl}
                alt="Crop preview"
                onLoad={handleImageLoad}
                className="max-h-[calc(100vh-200px)] max-w-full object-contain pointer-events-none"
                crossOrigin="anonymous"
              />

              {imgLoaded && cropMode === 'RECT' && (
                <>
                  {/* Dark mask overlays */}
                  <div className="absolute bg-black/60 pointer-events-none" style={{ top: 0, left: 0, right: 0, height: maskTop }} />
                  <div className="absolute bg-black/60 pointer-events-none" style={{ top: crop.y + crop.height, left: 0, right: 0, bottom: 0 }} />
                  <div className="absolute bg-black/60 pointer-events-none" style={{ top: crop.y, bottom: maskBottom, left: 0, width: maskLeft }} />
                  <div className="absolute bg-black/60 pointer-events-none" style={{ top: crop.y, bottom: maskBottom, left: crop.x + crop.width, right: 0 }} />

                  {/* Crop Box */}
                  <div
                    className="absolute cursor-move border-2 border-white shadow-[0_0_0_1px_rgba(0,0,0,0.5)] flex items-center justify-center"
                    style={{ left: crop.x, top: crop.y, width: crop.width, height: crop.height }}
                    onMouseDown={(e) => handleMouseDown(e, 'drag')}
                  >
                    {/* Rule of Thirds */}
                    <div className="absolute inset-0 grid grid-cols-3 grid-rows-3 pointer-events-none opacity-30">
                      <div className="border-r border-dashed border-white/50" />
                      <div className="border-r border-dashed border-white/50" />
                      <div className="border-b border-dashed border-white/50 col-span-3" style={{ gridRowStart: 1 }} />
                      <div className="border-b border-dashed border-white/50 col-span-3" style={{ gridRowStart: 2 }} />
                    </div>
                    {/* Size indicator */}
                    <span className="text-[10px] font-mono text-white/60 pointer-events-none select-none bg-black/30 px-1 rounded">
                      {Math.round(crop.width * (imgDimensions.naturalWidth / (imgDimensions.width || 1)))} × {Math.round(crop.height * (imgDimensions.naturalHeight / (imgDimensions.height || 1)))}
                    </span>
                    {/* Corner Handles */}
                    <div className="absolute w-4 h-4 -top-1 -left-1 border-t-4 border-l-4 border-white cursor-nwse-resize z-10" onMouseDown={(e) => handleMouseDown(e, 'tl')} />
                    <div className="absolute w-4 h-4 -top-1 -right-1 border-t-4 border-r-4 border-white cursor-nesw-resize z-10" onMouseDown={(e) => handleMouseDown(e, 'tr')} />
                    <div className="absolute w-4 h-4 -bottom-1 -left-1 border-b-4 border-l-4 border-white cursor-nesw-resize z-10" onMouseDown={(e) => handleMouseDown(e, 'bl')} />
                    <div className="absolute w-4 h-4 -bottom-1 -right-1 border-b-4 border-r-4 border-white cursor-nwse-resize z-10" onMouseDown={(e) => handleMouseDown(e, 'br')} />
                  </div>
                </>
              )}

              {/* Polygon Mode overlay */}
              {imgLoaded && cropMode === 'POLYGON' && (
                <svg
                  className="absolute inset-0 pointer-events-auto cursor-crosshair"
                  style={{ width: imgDimensions.width, height: imgDimensions.height }}
                >
                  <defs>
                    <mask id="polygon-mask">
                      <rect width="100%" height="100%" fill="white" />
                      {points.length >= 3 && (
                        <polygon points={points.map(p => `${p.x},${p.y}`).join(' ')} fill="black" />
                      )}
                    </mask>
                  </defs>
                  {points.length >= 3 && (
                    <rect width="100%" height="100%" fill="rgba(0,0,0,0.6)" mask="url(#polygon-mask)" />
                  )}
                  {points.length > 0 && (
                    <polyline points={points.map(p => `${p.x},${p.y}`).join(' ')} fill="none" stroke="#ffffff" strokeWidth="2.5" strokeDasharray="4 4" />
                  )}
                  {(isPolygonClosed || points.length >= 3) && points.length > 0 && (
                    <line x1={points[points.length - 1].x} y1={points[points.length - 1].y} x2={points[0].x} y2={points[0].y} stroke="#ffffff" strokeWidth="2.5" strokeDasharray="4 4" />
                  )}
                  {points.map((pt, idx) => (
                    <circle key={idx} cx={pt.x} cy={pt.y} r={idx === 0 ? 7 : 5}
                      fill={idx === 0 ? '#6366f1' : '#ffffff'} stroke="#4f46e5" strokeWidth="2"
                      className="transition-transform hover:scale-125 cursor-move"
                      onMouseDown={(e) => handlePointMouseDown(e, idx)} />
                  ))}
                </svg>
              )}
            </div>
          </div>

          {/* Right sidebar */}
          <div className="flex flex-col space-y-4 shrink-0 overflow-y-auto pr-1 pb-4 scrollbar-thin scrollbar-thumb-slate-800">
            {/* Crop Mode Toggle */}
            <div>
              <label className="text-xs font-semibold text-slate-400 block mb-2">Chế độ cắt</label>
              <div className="flex p-0.5 bg-slate-950 border border-slate-800 rounded-xl mb-4">
                {(['RECT', 'POLYGON'] as const).map((m) => (
                  <button key={m} type="button" onClick={() => { setCropMode(m); setPoints([]); setIsPolygonClosed(false); }}
                    className={`flex-1 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${cropMode === m ? 'bg-indigo-600 text-white shadow' : 'text-slate-500 hover:text-slate-300'}`}>
                    {m === 'RECT' ? 'Khung tỷ lệ' : 'Tự vẽ vùng cắt'}
                  </button>
                ))}
              </div>
            </div>

            {/* Live Preview (RECT only) */}
            {cropMode === 'RECT' && previewUrl && (
              <div>
                <label className="text-xs font-semibold text-slate-400 block mb-2">Xem trước vùng cắt</label>
                <div className="flex items-center justify-center bg-slate-950 rounded-2xl border border-slate-800 p-2" style={{ minHeight: 80 }}>
                  <img src={previewUrl} alt="Live Preview" className="max-w-full max-h-[120px] object-contain rounded-lg" />
                </div>
              </div>
            )}

            {/* Ratio / Polygon Instructions */}
            {cropMode === 'RECT' ? (
              <div>
                <label className="text-xs font-semibold text-slate-400 block mb-2">Tỷ lệ cắt ảnh</label>
                <div className="space-y-1.5 max-h-[200px] overflow-y-auto pr-1 scrollbar-thin scrollbar-thumb-slate-800 mb-3">
                  {CROP_RATIOS.map((ratio) => (
                    <button key={ratio.value} onClick={() => handleRatioChange(ratio.value)}
                      className={`w-full text-left px-3 py-2 rounded-xl text-xs font-medium transition-all cursor-pointer ${selectedRatio === ratio.value ? 'bg-indigo-600 text-white shadow-lg' : 'bg-slate-950 border border-slate-800 text-slate-300 hover:bg-slate-900 hover:text-white'}`}>
                      {ratio.label}
                    </button>
                  ))}
                </div>

                {/* Custom ratio input */}
                {selectedRatio === 'CUSTOM' && (
                  <div className="mb-3 flex gap-2 items-center">
                    <input
                      type="text"
                      value={customRatioInput}
                      onChange={(e) => {
                        setCustomRatioInput(e.target.value);
                        if (!imgLoaded) return;
                        const naturalRatio = imgDimensions.naturalWidth / (imgDimensions.naturalHeight || 1);
                        initCropBox(imgDimensions.width, imgDimensions.height, naturalRatio, undefined);
                      }}
                      placeholder="Vd: 4:3 hoặc 16:9"
                      className="flex-1 rounded-xl border border-slate-700 bg-slate-950 px-3 py-2 text-xs text-slate-200 outline-none focus:border-indigo-500"
                    />
                    <button
                      onClick={() => handleRatioChange('CUSTOM')}
                      className="text-xs text-indigo-400 hover:text-white px-2 py-2 rounded-xl bg-indigo-600/10 hover:bg-indigo-600/20 transition-all"
                    >
                      Áp dụng
                    </button>
                  </div>
                )}

                {/* Manual X/Y/W/H inputs */}
                <div>
                  <label className="text-xs font-semibold text-slate-400 block mb-2">Nhập tọa độ chính xác (px)</label>
                  <div className="grid grid-cols-2 gap-2 mb-2">
                    {[
                      { label: 'X', value: manualX, setter: setManualX },
                      { label: 'Y', value: manualY, setter: setManualY },
                      { label: 'W', value: manualW, setter: setManualW },
                      { label: 'H', value: manualH, setter: setManualH },
                    ].map(({ label, value, setter }) => (
                      <div key={label} className="flex items-center gap-1.5 bg-slate-950 border border-slate-800 rounded-xl px-2 py-1">
                        <span className="text-[10px] font-mono text-slate-500 w-3">{label}</span>
                        <input
                          type="number"
                          value={value}
                          onChange={(e) => setter(e.target.value)}
                          onBlur={applyManualInputs}
                          onKeyDown={(e) => e.key === 'Enter' && applyManualInputs()}
                          className="flex-1 bg-transparent text-xs text-slate-200 outline-none min-w-0"
                        />
                      </div>
                    ))}
                  </div>
                  <button onClick={applyManualInputs} className="w-full text-xs text-slate-400 hover:text-white py-1.5 rounded-xl border border-slate-800 hover:border-slate-600 transition-all">
                    Áp dụng tọa độ
                  </button>
                </div>
              </div>
            ) : (
              <div className="space-y-3">
                <div className="rounded-xl bg-slate-950 border border-slate-800 p-3">
                  <h4 className="text-xs font-bold text-slate-200 mb-1">Hướng dẫn vẽ:</h4>
                  <p className="text-[10px] text-slate-400 leading-relaxed">
                    1. Click trực tiếp lên ảnh để thêm các điểm góc.<br />
                    2. Nối ít nhất 3 điểm.<br />
                    3. Click vào điểm đầu tiên màu xanh để đóng kín vùng chọn.<br />
                    4. Kéo các điểm để chỉnh sửa tùy ý.
                  </p>
                </div>
                <div className="rounded-xl bg-indigo-950/30 border border-indigo-800/30 p-2">
                  <p className="text-[9px] text-indigo-300">✓ Vùng cắt đa giác xuất ra PNG trong suốt (không có nền trắng)</p>
                </div>

                {points.length > 0 && (
                  <Button type="button" variant="destructive" onClick={() => { setPoints([]); setIsPolygonClosed(false); }}
                    className="w-full text-xs font-bold rounded-xl h-8 cursor-pointer">
                    Xóa các điểm vẽ
                  </Button>
                )}

                <div className="text-[10px] text-slate-400 leading-normal">
                  {points.length === 0 ? 'Chưa vẽ điểm nào. Hãy click vào ảnh.'
                    : isPolygonClosed ? 'Đã khép kín vùng chọn. Bạn có thể bấm Áp dụng cắt.'
                      : `Đang vẽ: ${points.length} điểm. ${points.length >= 3 ? 'Click điểm đầu tiên để khép kín.' : 'Cần ít nhất 3 điểm.'}`}
                </div>
              </div>
            )}
          </div>
        </div>

        <DialogFooter className="shrink-0 mt-auto border-t border-slate-800 pt-4 flex flex-wrap gap-2 justify-end">
          <Button type="button" variant="ghost" onClick={onClose} disabled={isBusy}
            className="text-slate-400 hover:text-white rounded-xl hover:bg-slate-800">
            Hủy
          </Button>

          {/* Replace original image (only if callback provided) */}
          {onCropReplace && (
            <Button type="button" onClick={() => renderAndUpload('replace')}
              disabled={!imgLoaded || isBusy || (cropMode === 'POLYGON' && points.length < 3)}
              variant="outline"
              className="border-amber-600/30 text-amber-400 hover:text-white hover:bg-amber-600/20 rounded-xl px-5">
              {isBusy ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : <Replace className="w-4 h-4 mr-2" />}
              Thay thế ảnh gốc
            </Button>
          )}

          <Button type="button" onClick={() => renderAndUpload('new')}
            disabled={!imgLoaded || isBusy || (cropMode === 'POLYGON' && points.length < 3)}
            className="bg-indigo-600 hover:bg-indigo-500 rounded-xl px-6 text-white">
            {isBusy ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : <Plus className="w-4 h-4 mr-2" />}
            {isBusy ? loadingLabel : 'Thêm layer mới'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
