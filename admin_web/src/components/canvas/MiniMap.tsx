'use client';

import React, { useEffect, useRef, useState, useCallback } from 'react';
import { useEditorStore } from '@/store/editor.store';

/**
 * MiniMap v2 — Cải tiến toàn diện:
 * - Dùng Fabric canvas.toDataURL() để render ảnh thật thay vì box màu
 * - Tỷ lệ đúng với canvas (9:16 hoặc custom)
 * - Real-time update khi canvas thay đổi (event-driven, không interval)
 * - Click-to-pan: click vào minimap để scroll canvas tới vị trí đó
 * - Viewport indicator: hình chữ nhật indigo hiển thị vùng đang xem
 * - Nút thu/mở với animation mượt
 */
export default function MiniMap() {
  const { canvas } = useEditorStore();
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [isVisible, setIsVisible] = useState(true);
  const [isHovered, setIsHovered] = useState(false);
  const rafRef = useRef<number | null>(null);
  const lastRenderRef = useRef<number>(0);
  const containerRef = useRef<HTMLDivElement>(null);

  const THROTTLE_MS = 150; // min ms giữa 2 lần render

  // Kích thước minimap — tỷ lệ 9:16 để match canvas dọc
  const MINI_W = 90;
  const MINI_H = 160;

  // Capture canvas content dưới dạng DataURL
  const captureCanvas = useCallback(() => {
    if (!canvas || (canvas as any).disposed) return;

    const now = Date.now();
    if (now - lastRenderRef.current < THROTTLE_MS) {
      // Throttle: lên lịch lại sau THROTTLE_MS
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
      rafRef.current = requestAnimationFrame(() => captureCanvas());
      return;
    }
    lastRenderRef.current = now;

    // Strategy 1: Export directly from Fabric canvas
    try {
      const dataUrl = canvas.toDataURL({
        format: 'webp',
        quality: 0.6,
        multiplier: MINI_W / (canvas.width || 1080),
        backgroundColor: canvas.backgroundColor || 'rgba(0,0,0,0)'
      });
      setPreviewUrl(dataUrl);
      return;
    } catch (err) {
      // SecurityError: canvas is tainted by cross-origin images
      // Fall through to fallback strategies
    }

    // Strategy 2: Render fallback with background color + layer placeholders
    try {
      const cw = canvas.width || 1080;
      const ch = canvas.height || 1920;
      const scale = MINI_W / cw;

      const offscreen = document.createElement('canvas');
      offscreen.width = MINI_W;
      offscreen.height = MINI_H;
      const ctx = offscreen.getContext('2d');
      if (!ctx) throw new Error('No 2D context');

      // Background
      const bgColor = canvas.backgroundColor;
      if (bgColor) {
        ctx.fillStyle = bgColor;
        ctx.fillRect(0, 0, MINI_W, MINI_H);
      } else {
        ctx.clearRect(0, 0, MINI_W, MINI_H);
      }

      // Render simplified placeholders for each object
      const objects = canvas.getObjects();
      const colors = ['#6366f1', '#8b5cf6', '#ec4899', '#f59e0b', '#10b981', '#06b6d4'];
      let colorIdx = 0;

      for (const obj of objects) {
        if ((obj as any)._isBackground) continue;
        const bounds = obj.getBoundingRect();
        if (!bounds) continue;

        const x = bounds.left * scale;
        const y = bounds.top * scale;
        const w = bounds.width * scale;
        const h = bounds.height * scale;

        if (w < 0.5 || h < 0.5) continue;

        ctx.fillStyle = colors[colorIdx % colors.length] + '60';
        ctx.fillRect(x, y, w, h);
        ctx.strokeStyle = colors[colorIdx % colors.length] + '99';
        ctx.lineWidth = 0.5;
        ctx.strokeRect(x, y, w, h);
        colorIdx++;
      }

      const fallbackUrl = offscreen.toDataURL('image/png');
      setPreviewUrl(fallbackUrl);
      return;
    } catch (fallbackErr) {
      // Both strategies failed — show empty placeholder
      console.warn('MiniMap: both capture strategies failed', fallbackErr);
      setPreviewUrl(null);
    }
  }, [canvas]);

  // Subscribe vào Fabric events
  useEffect(() => {
    if (!canvas) {
      setPreviewUrl(null);
      return;
    }

    // Chụp lần đầu ngay sau khi canvas render xong
    const onAfterRender = () => {
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
      rafRef.current = requestAnimationFrame(() => captureCanvas());
    };

    canvas.on('after:render', onAfterRender);

    // Capture ngay lần đầu
    setTimeout(captureCanvas, 200);

    return () => {
      canvas.off('after:render', onAfterRender);
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, [canvas, captureCanvas]);

  // Click trên minimap → scroll canvas viewport tới vị trí tương ứng
  const handleClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const canvasEl = document.getElementById('fabric-canvas');
    const containerEl = canvasEl?.closest('[data-canvas-viewport]');
    if (!canvasEl || !containerEl || !canvas) return;

    const rect = e.currentTarget.getBoundingClientRect();
    const relX = (e.clientX - rect.left) / rect.width;
    const relY = (e.clientY - rect.top) / rect.height;

    const baseWidth = canvas.width || 1080;
    const baseHeight = canvas.height || 1920;

    const canvRect = canvasEl.getBoundingClientRect();
    const targetX = relX * canvRect.width - containerEl.clientWidth / 2;
    const targetY = relY * canvRect.height - containerEl.clientHeight / 2;

    containerEl.scrollTo({
      left: Math.max(0, targetX),
      top: Math.max(0, targetY),
      behavior: 'smooth',
    });
  };

  // Viewport indicator: tính vùng đang hiển thị
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
        className="absolute bottom-4 right-4 z-20 w-9 h-9 rounded-xl bg-slate-900/90 border border-slate-700 text-slate-400 hover:text-white hover:border-indigo-500/50 flex items-center justify-center backdrop-blur-sm cursor-pointer shadow-xl transition-all hover:scale-105"
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
      {/* Card container */}
      <div
        className="rounded-2xl overflow-hidden border border-slate-700/60 shadow-2xl bg-slate-950 cursor-pointer transition-all duration-200"
        style={{
          width: MINI_W + 2,  // +2 for border
          opacity: isHovered ? 1 : 0.75,
          transform: isHovered ? 'scale(1.04)' : 'scale(1)',
          boxShadow: isHovered
            ? '0 0 0 1px rgba(99,102,241,0.4), 0 20px 40px rgba(0,0,0,0.5)'
            : '0 8px 24px rgba(0,0,0,0.4)',
        }}
        onClick={handleClick}
        title="Click để cuộn canvas đến vị trí này"
      >
        {/* Preview image */}
        <div
          className="relative bg-slate-900"
          style={{ width: MINI_W, height: MINI_H }}
        >
          {previewUrl ? (
            <img
              src={previewUrl}
              alt="Canvas preview"
              className="w-full h-full object-cover"
              draggable={false}
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center">
              <div className="w-5 h-5 border-2 border-slate-700 border-t-indigo-500 rounded-full animate-spin" />
            </div>
          )}

          {/* Viewport rect overlay */}
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

          {/* "MAP" label */}
          <div className="absolute top-1.5 left-1.5 px-1.5 py-0.5 rounded-md bg-slate-900/80 backdrop-blur-sm border border-slate-700/60">
            <span className="text-[8px] font-black text-slate-400 tracking-widest uppercase">Map</span>
          </div>
        </div>

        {/* Bottom info bar */}
        <div className="px-2 py-1.5 bg-slate-900/80 border-t border-slate-800 flex items-center justify-between">
          <span className="text-[8px] text-slate-500 font-mono">
            {canvas ? `${canvas.width || 1080}×${canvas.height || 1920}` : '—'}
          </span>
          <span className="text-[8px] text-indigo-400 font-semibold">
            {canvas ? `${canvas.getObjects().filter((o: any) => !o._isBackground).length}L` : '0L'}
          </span>
        </div>
      </div>

      {/* Close button */}
      <button
        onClick={(e) => { e.stopPropagation(); setIsVisible(false); }}
        className="absolute -top-2 -right-2 w-5 h-5 rounded-full bg-slate-800 border border-slate-700 text-slate-400 hover:text-white hover:bg-slate-700 text-[9px] flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all cursor-pointer shadow-lg"
        title="Ẩn Minimap"
      >
        ×
      </button>
    </div>
  );
}
