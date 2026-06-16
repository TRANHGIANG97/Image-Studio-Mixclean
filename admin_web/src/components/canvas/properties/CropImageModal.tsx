'use client';

import React, { useState, useRef, useEffect } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Loader2, Crop as CropIcon } from 'lucide-react';
import { toast } from 'sonner';

interface CropImageModalProps {
  isOpen: boolean;
  onClose: () => void;
  imageUrl: string;
  initialRatio?: string;
  onCropComplete: (newImageUrl: string, ratio: string) => void;
}

const CROP_RATIOS = [
  { value: 'FREE', label: 'Tự do', widthRatio: 0, heightRatio: 0 },
  { value: 'ORIGINAL', label: 'Giữ tỷ lệ gốc', widthRatio: -1, heightRatio: -1 },
  { value: 'RATIO_1_1', label: '1:1 (Vuông)', widthRatio: 1, heightRatio: 1 },
  { value: 'RATIO_3_4', label: '3:4 (Dọc)', widthRatio: 3, heightRatio: 4 },
  { value: 'RATIO_4_3', label: '4:3 (Ngang)', widthRatio: 4, heightRatio: 3 },
  { value: 'RATIO_9_16', label: '9:16 (Dọc dài)', widthRatio: 9, heightRatio: 16 },
  { value: 'RATIO_16_9', label: '16:9 (Ngang rộng)', widthRatio: 16, heightRatio: 9 }
];

export default function CropImageModal({
  isOpen,
  onClose,
  imageUrl,
  initialRatio = 'FREE',
  onCropComplete
}: CropImageModalProps) {
  const [cropMode, setCropMode] = useState<'RECT' | 'POLYGON'>('RECT');
  const [selectedRatio, setSelectedRatio] = useState<string>('FREE');
  const [isCropping, setIsCropping] = useState(false);
  const [imgLoaded, setImgLoaded] = useState(false);
  const [imgDimensions, setImgDimensions] = useState({ width: 0, height: 0, naturalWidth: 0, naturalHeight: 0 });
  const [crop, setCrop] = useState({ x: 0, y: 0, width: 0, height: 0 });
  
  // Polygon mode states
  const [points, setPoints] = useState<{ x: number; y: number }[]>([]);
  const [isPolygonClosed, setIsPolygonClosed] = useState(false);
  
  const containerRef = useRef<HTMLDivElement>(null);
  const imgRef = useRef<HTMLImageElement>(null);

  // Sync initial ratio and reset when modal opens
  useEffect(() => {
    if (isOpen) {
      const match = CROP_RATIOS.find(r => r.value === initialRatio);
      setSelectedRatio(match ? match.value : 'FREE');
      setImgLoaded(false);
      setCropMode('RECT');
      setPoints([]);
      setIsPolygonClosed(false);
    }
  }, [isOpen, initialRatio]);

  const getRatioVal = (ratioKey: string, naturalRatio: number): number | undefined => {
    if (ratioKey === 'FREE') return undefined;
    if (ratioKey === 'ORIGINAL') return naturalRatio;
    const match = CROP_RATIOS.find(r => r.value === ratioKey);
    if (match && match.widthRatio > 0) {
      return match.widthRatio / match.heightRatio;
    }
    return undefined;
  };

  const currentRatioVal = getRatioVal(
    selectedRatio,
    imgDimensions.naturalWidth / (imgDimensions.naturalHeight || 1)
  );

  const initCropBox = (
    containerWidth: number,
    containerHeight: number,
    naturalRatio: number,
    ratioValue?: number
  ) => {
    const finalRatio = ratioValue !== undefined
      ? ratioValue
      : (selectedRatio === 'ORIGINAL' ? naturalRatio : getRatioVal(selectedRatio, naturalRatio));
    
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
    
    setCrop({ x, y, width: w, height: h });
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

  // Re-adjust crop box when ratio changes
  const handleRatioChange = (ratioValue: string) => {
    setSelectedRatio(ratioValue);
    if (!imgLoaded) return;
    const naturalRatio = imgDimensions.naturalWidth / (imgDimensions.naturalHeight || 1);
    const rVal = getRatioVal(ratioValue, naturalRatio);
    initCropBox(imgDimensions.width, imgDimensions.height, naturalRatio, rVal);
  };

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
          
          if (proposedX < 0) {
            proposedWidth += proposedX;
            proposedX = 0;
          }
          if (proposedY < 0) {
            proposedHeight += proposedY;
            proposedY = 0;
          }
          
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
          
          if (initialCrop.x + proposedWidth > imgDimensions.width) {
            proposedWidth = imgDimensions.width - initialCrop.x;
          }
          if (proposedY < 0) {
            proposedHeight += proposedY;
            proposedY = 0;
          }
          
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
          
          if (proposedX < 0) {
            proposedWidth += proposedX;
            proposedX = 0;
          }
          if (initialCrop.y + proposedHeight > imgDimensions.height) {
            proposedHeight = imgDimensions.height - initialCrop.y;
          }
          
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
          
          if (initialCrop.x + proposedWidth > imgDimensions.width) {
            proposedWidth = imgDimensions.width - initialCrop.x;
          }
          if (initialCrop.y + proposedHeight > imgDimensions.height) {
            proposedHeight = imgDimensions.height - initialCrop.y;
          }
          
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
    };
    
    const handleMouseUp = () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
    
    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);
  };

  // Polygon point handlers
  const projectPointToSegment = (
    px: number,
    py: number,
    ax: number,
    ay: number,
    bx: number,
    by: number
  ) => {
    const dx = bx - ax;
    const dy = by - ay;
    const lenSq = dx * dx + dy * dy;
    if (lenSq === 0) {
      return { x: ax, y: ay, distance: Math.hypot(px - ax, py - ay) };
    }

    const t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lenSq));
    const x = ax + t * dx;
    const y = ay + t * dy;
    return { x, y, distance: Math.hypot(px - x, py - y) };
  };

  const findNearestSegment = (x: number, y: number) => {
    if (points.length < 2) return null;
    const segmentCount = points.length - 1;
    let bestIndex = -1;
    let bestPoint = { x: 0, y: 0, distance: Number.POSITIVE_INFINITY };

    for (let i = 0; i < segmentCount; i++) {
      const a = points[i];
      const b = points[i + 1];
      const projected = projectPointToSegment(x, y, a.x, a.y, b.x, b.y);
      if (projected.distance < bestPoint.distance) {
        bestIndex = i;
        bestPoint = projected;
      }
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
      setPoints(prev => {
        const updated = [...prev];
        updated.splice(insertion.index + 1, 0, insertion.point);
        return updated;
      });
      return;
    }

    if (points.length >= 3 && !isPolygonClosed) {
      const firstPt = points[0];
      const dist = Math.sqrt((cleanX - firstPt.x) ** 2 + (cleanY - firstPt.y) ** 2);
      if (dist < 12) {
        setIsPolygonClosed(true);
        return;
      }
    }
    
    setPoints([...points, { x: cleanX, y: cleanY }]);
  };

  const handlePointMouseDown = (e: React.MouseEvent, index: number) => {
    e.stopPropagation();
    e.preventDefault();
    
    const startX = e.clientX;
    const startY = e.clientY;
    const initialPt = points[index];
    
    const handleMouseMove = (moveEvent: MouseEvent) => {
      if (!containerRef.current) return;
      const rect = containerRef.current.getBoundingClientRect();
      
      const dx = moveEvent.clientX - startX;
      const dy = moveEvent.clientY - startY;
      
      const proposedX = Math.max(0, Math.min(imgDimensions.width, initialPt.x + dx));
      const proposedY = Math.max(0, Math.min(imgDimensions.height, initialPt.y + dy));
      
      setPoints(prev => {
        const updated = [...prev];
        updated[index] = { x: proposedX, y: proposedY };
        return updated;
      });
    };
    
    const handleMouseUp = () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
    
    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);
  };

  const handleApply = async () => {
    if (!imgRef.current || !imgLoaded) return;
    
    setIsCropping(true);
    try {
      const img = imgRef.current;
      const scaleX = imgDimensions.naturalWidth / imgDimensions.width;
      const scaleY = imgDimensions.naturalHeight / imgDimensions.height;
      
      const canvas = document.createElement('canvas');
      const ctx = canvas.getContext('2d');
      if (!ctx) {
        throw new Error('Could not get 2D canvas context');
      }

      if (cropMode === 'RECT') {
        const cropX = crop.x * scaleX;
        const cropY = crop.y * scaleY;
        const cropW = crop.width * scaleX;
        const cropH = crop.height * scaleY;
        
        canvas.width = cropW;
        canvas.height = cropH;
        
        ctx.drawImage(
          img,
          cropX,
          cropY,
          cropW,
          cropH,
          0,
          0,
          cropW,
          cropH
        );
      } else {
        // Polygon Mode
        if (points.length < 3) {
          toast.error('Vui lòng chọn ít nhất 3 điểm để cắt đa giác');
          setIsCropping(false);
          return;
        }

        const naturalPoints = points.map(pt => ({
          x: pt.x * scaleX,
          y: pt.y * scaleY
        }));
        
        const xs = naturalPoints.map(pt => pt.x);
        const ys = naturalPoints.map(pt => pt.y);
        const minX = Math.min(...xs);
        const maxX = Math.max(...xs);
        const minY = Math.min(...ys);
        const maxY = Math.max(...ys);
        
        const cropW = maxX - minX;
        const cropH = maxY - minY;

        if (cropW < 2 || cropH < 2) {
          toast.error('Kích thước vùng cắt quá nhỏ');
          setIsCropping(false);
          return;
        }

        canvas.width = cropW;
        canvas.height = cropH;
        
        // Define path
        ctx.beginPath();
        ctx.moveTo(naturalPoints[0].x - minX, naturalPoints[0].y - minY);
        for (let i = 1; i < naturalPoints.length; i++) {
          ctx.lineTo(naturalPoints[i].x - minX, naturalPoints[i].y - minY);
        }
        ctx.closePath();
        ctx.clip();
        
        // Draw the image, offset by minX and minY
        ctx.drawImage(
          img,
          minX,
          minY,
          cropW,
          cropH,
          0,
          0,
          cropW,
          cropH
        );
      }
      
      canvas.toBlob(async (blob) => {
        if (!blob) {
          toast.error('Lỗi khi trích xuất dữ liệu ảnh cắt');
          setIsCropping(false);
          return;
        }
        
        try {
          const formData = new FormData();
          formData.append('file', blob, 'cropped.webp');
          formData.append('folder', 'cropped');
          formData.append('registerAsset', 'false');
          
          const response = await fetch('/api/upload', {
            method: 'POST',
            body: formData
          });
          
          if (!response.ok) {
            throw new Error(`Upload API returned status ${response.status}`);
          }
          
          const data = await response.json();
          if (data.success && data.fileUrl) {
            onCropComplete(data.fileUrl, cropMode === 'RECT' && selectedRatio !== 'FREE' ? selectedRatio : '');
            onClose();
          } else {
            throw new Error(data.error || 'Unknown upload response error');
          }
        } catch (uploadErr: any) {
          console.error('[CROP_ERROR] Uploading cropped blob failed:', uploadErr);
          toast.error(`Lỗi khi lưu ảnh cắt lên máy chủ: ${uploadErr.message}`);
        } finally {
          setIsCropping(false);
        }
      }, 'image/webp', 0.9);
      
    } catch (err: any) {
      console.error('[CROP_ERROR] Canvas rendering failed:', err);
      toast.error(`Lỗi khi xử lý cắt ảnh: ${err.message}`);
      setIsCropping(false);
    }
  };

  // Relative positions for rect mask overlays
  const maskTop = crop.y;
  const maskBottom = imgDimensions.height - (crop.y + crop.height);
  const maskLeft = crop.x;
  const maskRight = imgDimensions.width - (crop.x + crop.width);

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
        </DialogHeader>

        <div className="flex-1 grid grid-cols-1 md:grid-cols-[1fr_300px] gap-6 min-h-0 py-2">
          {/* Main Cropper viewport */}
          <div className="relative overflow-hidden bg-slate-900 rounded-2xl flex items-center justify-center select-none h-full border border-slate-850 p-6">
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
                  {/* Semi-transparent dark mask overlay */}
                  <div className="absolute bg-black/60 pointer-events-none" style={{ top: 0, left: 0, right: 0, height: maskTop }} />
                  <div className="absolute bg-black/60 pointer-events-none" style={{ top: crop.y + crop.height, left: 0, right: 0, bottom: 0 }} />
                  <div className="absolute bg-black/60 pointer-events-none" style={{ top: crop.y, bottom: maskBottom, left: 0, width: maskLeft }} />
                  <div className="absolute bg-black/60 pointer-events-none" style={{ top: crop.y, bottom: maskBottom, left: crop.x + crop.width, right: 0 }} />

                  {/* Crop Box Container */}
                  <div
                    className="absolute cursor-move border-2 border-white shadow-[0_0_0_1px_rgba(0,0,0,0.5)] flex items-center justify-center"
                    style={{ left: crop.x, top: crop.y, width: crop.width, height: crop.height }}
                    onMouseDown={(e) => handleMouseDown(e, 'drag')}
                  >
                    {/* Rule of Thirds Gridlines */}
                    <div className="absolute inset-0 grid grid-cols-3 grid-rows-3 pointer-events-none opacity-40">
                      <div className="border-r border-dashed border-white/50" />
                      <div className="border-r border-dashed border-white/50" />
                      <div className="border-b border-dashed border-white/50 col-span-3" style={{ gridRowStart: 1 }} />
                      <div className="border-b border-dashed border-white/50 col-span-3" style={{ gridRowStart: 2 }} />
                    </div>

                    {/* Corner Handles */}
                    {/* Top-Left */}
                    <div
                      className="absolute w-4 h-4 -top-1 -left-1 border-t-4 border-l-4 border-white cursor-nwse-resize z-10"
                      onMouseDown={(e) => handleMouseDown(e, 'tl')}
                    />
                    {/* Top-Right */}
                    <div
                      className="absolute w-4 h-4 -top-1 -right-1 border-t-4 border-r-4 border-white cursor-nesw-resize z-10"
                      onMouseDown={(e) => handleMouseDown(e, 'tr')}
                    />
                    {/* Bottom-Left */}
                    <div
                      className="absolute w-4 h-4 -bottom-1 -left-1 border-b-4 border-l-4 border-white cursor-nesw-resize z-10"
                      onMouseDown={(e) => handleMouseDown(e, 'bl')}
                    />
                    {/* Bottom-Right */}
                    <div
                      className="absolute w-4 h-4 -bottom-1 -right-1 border-b-4 border-r-4 border-white cursor-nwse-resize z-10"
                      onMouseDown={(e) => handleMouseDown(e, 'br')}
                    />
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
                        <polygon
                          points={points.map(p => `${p.x},${p.y}`).join(' ')}
                          fill="black"
                        />
                      )}
                    </mask>
                  </defs>
                  
                  {/* Translucent overlay outside crop region */}
                  {points.length >= 3 && (
                    <rect
                      width="100%"
                      height="100%"
                      fill="rgba(0,0,0,0.6)"
                      mask="url(#polygon-mask)"
                    />
                  )}
                  
                  {/* Polyline connecting points */}
                  {points.length > 0 && (
                    <polyline
                      points={points.map(p => `${p.x},${p.y}`).join(' ')}
                      fill="none"
                      stroke="#ffffff"
                      strokeWidth="2.5"
                      strokeDasharray="4 4"
                    />
                  )}

                  {/* Closing line if polygon is closed or has >= 3 points */}
                  {(isPolygonClosed || points.length >= 3) && points.length > 0 && (
                    <line
                      x1={points[points.length - 1].x}
                      y1={points[points.length - 1].y}
                      x2={points[0].x}
                      y2={points[0].y}
                      stroke="#ffffff"
                      strokeWidth="2.5"
                      strokeDasharray="4 4"
                    />
                  )}

                  {/* Vertices handles */}
                  {points.map((pt, idx) => (
                    <circle
                      key={idx}
                      cx={pt.x}
                      cy={pt.y}
                      r={idx === 0 ? 7 : 5}
                      fill={idx === 0 ? '#6366f1' : '#ffffff'}
                      stroke="#4f46e5"
                      strokeWidth="2"
                      className="transition-transform hover:scale-125 cursor-move"
                      onMouseDown={(e) => handlePointMouseDown(e, idx)}
                    />
                  ))}
                </svg>
              )}
            </div>
          </div>

          {/* Right sidebar options */}
          <div className="flex flex-col space-y-4 shrink-0 overflow-y-auto pr-1 pb-4 scrollbar-thin scrollbar-thumb-slate-800">
            <div>
              <label className="text-xs font-semibold text-slate-400 block mb-2">Chế độ cắt</label>
              <div className="flex p-0.5 bg-slate-950 border border-slate-800 rounded-xl mb-4">
                <button
                  type="button"
                  onClick={() => setCropMode('RECT')}
                  className={`flex-1 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                    cropMode === 'RECT'
                      ? 'bg-indigo-600 text-white shadow'
                      : 'text-slate-500 hover:text-slate-300'
                  }`}
                >
                  Khung tỷ lệ
                </button>
                <button
                  type="button"
                  onClick={() => setCropMode('POLYGON')}
                  className={`flex-1 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                    cropMode === 'POLYGON'
                      ? 'bg-indigo-600 text-white shadow'
                      : 'text-slate-500 hover:text-slate-300'
                  }`}
                >
                  Tự vẽ vùng cắt
                </button>
              </div>
            </div>

            {cropMode === 'RECT' ? (
              <div>
                <label className="text-xs font-semibold text-slate-400 block mb-2">Tỷ lệ cắt ảnh</label>
                <div className="space-y-1.5 max-h-[300px] overflow-y-auto pr-1 scrollbar-thin scrollbar-thumb-slate-800">
                  {CROP_RATIOS.map((ratio) => (
                    <button
                      key={ratio.value}
                      onClick={() => handleRatioChange(ratio.value)}
                      className={`w-full text-left px-3 py-2 rounded-xl text-xs font-medium transition-all cursor-pointer ${
                        selectedRatio === ratio.value
                          ? 'bg-indigo-600 text-white shadow-lg'
                          : 'bg-slate-950 border border-slate-850 text-slate-300 hover:bg-slate-900 hover:text-white'
                      }`}
                    >
                      {ratio.label}
                    </button>
                  ))}
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

                {points.length > 0 && (
                  <Button
                    type="button"
                    variant="destructive"
                    onClick={() => {
                      setPoints([]);
                      setIsPolygonClosed(false);
                    }}
                    className="w-full text-xs font-bold rounded-xl h-8 cursor-pointer"
                  >
                    Xóa các điểm vẽ
                  </Button>
                )}

                <div className="text-[10px] text-slate-450 leading-normal">
                  {points.length === 0 ? (
                    "Chưa vẽ điểm nào. Hãy click vào ảnh."
                  ) : isPolygonClosed ? (
                    "Đã khép kín vùng chọn. Bạn có thể bấm Áp dụng cắt."
                  ) : (
                    `Đang vẽ: đã nối ${points.length} điểm. ${
                      points.length >= 3 ? "Click điểm đầu tiên để khép kín." : "Cần ít nhất 3 điểm."
                    }`
                  )}
                </div>
              </div>
            )}
          </div>
        </div>

        <DialogFooter className="shrink-0 mt-auto border-t border-slate-800 pt-4 flex gap-2 justify-end">
          <Button
            type="button"
            variant="ghost"
            onClick={onClose}
            disabled={isCropping}
            className="text-slate-400 hover:text-white rounded-xl hover:bg-slate-800"
          >
            Hủy
          </Button>
          <Button
            type="button"
            onClick={handleApply}
            disabled={!imgLoaded || isCropping || (cropMode === 'POLYGON' && points.length < 3)}
            className="bg-indigo-600 hover:bg-indigo-500 rounded-xl px-6 text-white"
          >
            {isCropping && <Loader2 className="w-4 h-4 animate-spin mr-2" />}
            Áp dụng cắt
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
