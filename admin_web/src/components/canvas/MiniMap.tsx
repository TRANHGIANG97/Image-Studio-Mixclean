'use client';

import React, { useEffect, useRef, useState, useCallback } from 'react';
import { useEditorStore } from '@/store/editor.store';

const THROTTLE_MS = 400;
const MINI_W = 90;
const MINI_H = 160;

const CAPTURE_EVENTS = [
  'object:modified',
  'object:added',
  'object:removed',
  'selection:created',
  'selection:updated',
  'selection:cleared',
] as const;

/**
 * MiniMap — lightweight canvas overview with click-to-pan.
 * Captures on structural changes only (not every frame during drag).
 */
export default function MiniMap() {
  const { canvas } = useEditorStore();
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [isVisible, setIsVisible] = useState(true);
  const [isHovered, setIsHovered] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const scheduleRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const isMovingRef = useRef(false);

  const captureCanvas = useCallback(() => {
    if (!canvas || (canvas as any).disposed) return;

    try {
      const dataUrl = canvas.toDataURL({
        format: 'webp',
        quality: 0.55,
        multiplier: MINI_W / (canvas.width || 1080),
        backgroundColor: canvas.backgroundColor || 'rgba(0,0,0,0)',
      });
      setPreviewUrl(dataUrl);
      return;
    } catch {
      // tainted canvas — fallback placeholders
    }

    try {
      const cw = canvas.width || 1080;
      const ch = canvas.height || 1920;
      const scale = MINI_W / cw;

      const offscreen = document.createElement('canvas');
      offscreen.width = MINI_W;
      offscreen.height = MINI_H;
      const ctx = offscreen.getContext('2d');
      if (!ctx) throw new Error('No 2D context');

      const bgColor = canvas.backgroundColor;
      if (bgColor && typeof bgColor === 'string') {
        ctx.fillStyle = bgColor;
        ctx.fillRect(0, 0, MINI_W, MINI_H);
      }

      const colors = ['#6366f1', '#8b5cf6', '#ec4899', '#f59e0b', '#10b981', '#06b6d4'];
      let colorIdx = 0;

      for (const obj of canvas.getObjects()) {
        if ((obj as any)._isBackground) continue;
        const bounds = obj.getBoundingRect();
        if (!bounds || bounds.width < 0.5 || bounds.height < 0.5) continue;

        ctx.fillStyle = colors[colorIdx % colors.length] + '60';
        ctx.fillRect(bounds.left * scale, bounds.top * scale, bounds.width * scale, bounds.height * scale);
        colorIdx++;
      }

      setPreviewUrl(offscreen.toDataURL('image/png'));
    } catch {
      setPreviewUrl(null);
    }
  }, [canvas]);

  const scheduleCapture = useCallback(() => {
    if (isMovingRef.current) return;
    if (scheduleRef.current) clearTimeout(scheduleRef.current);
    scheduleRef.current = setTimeout(() => {
      scheduleRef.current = null;
      captureCanvas();
    }, THROTTLE_MS);
  }, [captureCanvas]);

  useEffect(() => {
    if (!canvas) {
      setPreviewUrl(null);
      return;
    }

    const onMoving = () => {
      isMovingRef.current = true;
    };
    const onMoveEnd = () => {
      isMovingRef.current = false;
      scheduleCapture();
    };

    CAPTURE_EVENTS.forEach((event) => canvas.on(event, scheduleCapture));
    canvas.on('object:moving', onMoving);
    canvas.on('object:scaling', onMoving);
    canvas.on('object:rotating', onMoving);
    canvas.on('object:modified', onMoveEnd);
    canvas.on('mouse:up', onMoveEnd);

    const initialTimer = setTimeout(scheduleCapture, 300);

    return () => {
      clearTimeout(initialTimer);
      if (scheduleRef.current) clearTimeout(scheduleRef.current);
      CAPTURE_EVENTS.forEach((event) => canvas.off(event, scheduleCapture));
      canvas.off('object:moving', onMoving);
      canvas.off('object:scaling', onMoving);
      canvas.off('object:rotating', onMoving);
      canvas.off('object:modified', onMoveEnd);
      canvas.off('mouse:up', onMoveEnd);
    };
  }, [canvas, scheduleCapture]);

  const handleClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const canvasEl = document.getElementById('fabric-canvas');
    const containerEl = canvasEl?.closest('[data-canvas-viewport]');
    if (!canvasEl || !containerEl || !canvas) return;

    const rect = e.currentTarget.getBoundingClientRect();
    const relX = (e.clientX - rect.left) / rect.width;
    const relY = (e.clientY - rect.top) / rect.height;

    const canvRect = canvasEl.getBoundingClientRect();
    const targetX = relX * canvRect.width - containerEl.clientWidth / 2;
    const targetY = relY * canvRect.height - containerEl.clientHeight / 2;

    containerEl.scrollTo({
      left: Math.max(0, targetX),
      top: Math.max(0, targetY),
      behavior: 'smooth',
    });
  };

  const getViewportRect = () => {
    const canvasEl = document.getElementById('fabric-canvas');
    const containerEl = canvasEl?.closest('[data-canvas-viewport]');
    if (!canvasEl || !containerEl) return null;

    const canvRect = canvasEl.getBoundingClientRect();
    const viewRect = containerEl.getBoundingClientRect();
    if (!canvRect.width || !canvRect.height) return null;

    const x = Math.max(0, (viewRect.left - canvRect.left) / canvRect.width) * 100;
    const y = Math.max(0, (viewRect.top - canvRect.top) / canvRect.height) * 100;
    const w = Math.min(100, (viewRect.width / canvRect.width) * 100);
    const h = Math.min(100, (viewRect.height / canvRect.height) * 100);

    return { x, y, w, h };
  };

  const vpRect = isHovered ? getViewportRect() : null;

  if (!isVisible) {
    return (
      <button
        onClick={() => setIsVisible(true)}
        className="absolute bottom-4 right-4 z-20 w-9 h-9 rounded-xl bg-white/90 border border-slate-200 text-slate-400 hover:text-slate-700 hover:border-indigo-500/50 flex items-center justify-center backdrop-blur-sm cursor-pointer shadow-lg transition-all hover:scale-105"
        title="Hiện Minimap"
      >
        <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <rect x="3" y="3" width="18" height="18" rx="2" />
          <rect x="7" y="7" width="4" height="4" />
          <rect x="13" y="7" width="4" height="4" />
          <rect x="7" y="13" width="4" height="4" />
          <rect x="13" y="13" width="4" height="4" />
        </svg>
      </button>
    );
  }

  return (
    <div
      className="absolute bottom-4 right-4 z-20 group"
      ref={containerRef}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
    >
      <div
        className="rounded-2xl overflow-hidden border border-slate-200/60 shadow-xl bg-white cursor-pointer transition-all duration-200"
        style={{
          width: MINI_W + 2,
          opacity: isHovered ? 1 : 0.75,
          transform: isHovered ? 'scale(1.04)' : 'scale(1)',
          boxShadow: isHovered
            ? '0 0 0 1px rgba(99,102,241,0.4), 0 12px 28px rgba(0,0,0,0.08)'
            : '0 4px 16px rgba(0,0,0,0.06)',
        }}
        onClick={handleClick}
        title="Click để cuộn canvas đến vị trí này"
      >
        <div className="relative bg-slate-100" style={{ width: MINI_W, height: MINI_H }}>
          {previewUrl ? (
            <img src={previewUrl} alt="Canvas preview" className="w-full h-full object-cover" draggable={false} />
          ) : (
            <div className="w-full h-full flex items-center justify-center">
              <div className="w-5 h-5 border-2 border-slate-200 border-t-indigo-500 rounded-full animate-spin" />
            </div>
          )}

          {vpRect && (
            <div
              className="absolute pointer-events-none border-2 border-indigo-400/80 rounded-sm"
              style={{
                left: `${vpRect.x}%`,
                top: `${vpRect.y}%`,
                width: `${vpRect.w}%`,
                height: `${vpRect.h}%`,
                backgroundColor: 'rgba(99,102,241,0.08)',
                boxShadow: 'inset 0 0 0 1px rgba(99,102,241,0.3)',
              }}
            />
          )}

          <div className="absolute top-1.5 left-1.5 px-1.5 py-0.5 rounded-md bg-slate-100/80 backdrop-blur-sm border border-slate-200/60">
            <span className="text-[8px] font-black text-slate-500 tracking-widest uppercase">Map</span>
          </div>
        </div>

        <div className="px-2 py-1.5 bg-slate-50/80 border-t border-slate-200 flex items-center justify-between">
          <span className="text-[8px] text-slate-400 font-mono">
            {canvas ? `${canvas.width || 1080}×${canvas.height || 1920}` : '—'}
          </span>
          <span className="text-[8px] text-indigo-400 font-semibold">
            {canvas ? `${canvas.getObjects().filter((o: any) => !o._isBackground).length}L` : '0L'}
          </span>
        </div>
      </div>

      <button
        onClick={(e) => {
          e.stopPropagation();
          setIsVisible(false);
        }}
        className="absolute -top-2 -right-2 w-5 h-5 rounded-full bg-white border border-slate-200 text-slate-400 hover:text-slate-700 hover:bg-slate-100 text-[9px] flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all cursor-pointer shadow-lg"
        title="Ẩn Minimap"
      >
        ×
      </button>
    </div>
  );
}
