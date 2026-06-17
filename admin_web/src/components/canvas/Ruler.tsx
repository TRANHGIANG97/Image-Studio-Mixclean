'use client';

import React, { useEffect, useRef } from 'react';

interface RulerProps {
  type: 'horizontal' | 'vertical';
  zoom: number;
  baseSize: number;
  onAddGuide: (position: number) => void;
}

export default function Ruler({ type, zoom, baseSize, onAddGuide }: RulerProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const isH = type === 'horizontal';
    const scale = typeof window !== 'undefined' ? window.devicePixelRatio || 1 : 1;
    
    const width = isH ? baseSize * zoom : 20;
    const height = isH ? 20 : baseSize * zoom;

    canvas.width = width * scale;
    canvas.height = height * scale;
    canvas.style.width = `${width}px`;
    canvas.style.height = `${height}px`;
    
    ctx.scale(scale, scale);
    ctx.fillStyle = '#f8fafc'; // slate-50 background
    ctx.fillRect(0, 0, width, height);

    ctx.strokeStyle = '#cbd5e1'; // slate-300 lines
    ctx.lineWidth = 1;
    ctx.fillStyle = '#64748b'; // slate-500 text
    ctx.font = '9px sans-serif';

    const step = 100; // Major tick step in original pixels
    const minorStep = 10; // Minor tick step in original pixels

    // Draw background border
    ctx.strokeStyle = '#e2e8f0'; // slate-200 border
    ctx.beginPath();
    if (isH) {
      ctx.moveTo(0, height - 1);
      ctx.lineTo(width, height - 1);
    } else {
      ctx.moveTo(width - 1, 0);
      ctx.lineTo(width - 1, height);
    }
    ctx.stroke();

    ctx.strokeStyle = '#94a3b8'; // slate-400 ticks

    for (let i = 0; i <= baseSize; i += minorStep) {
      const pos = i * zoom;
      ctx.beginPath();
      
      if (isH) {
        if (i % step === 0) {
          ctx.moveTo(pos, height);
          ctx.lineTo(pos, height - 12);
          ctx.fillText(i.toString(), pos + 4, height - 10);
        } else if (i % (step / 2) === 0) {
          ctx.moveTo(pos, height);
          ctx.lineTo(pos, height - 8);
        } else {
          ctx.moveTo(pos, height);
          ctx.lineTo(pos, height - 4);
        }
      } else {
        if (i % step === 0) {
          ctx.moveTo(width, pos);
          ctx.lineTo(width - 12, pos);
          ctx.save();
          ctx.translate(width - 10, pos + 3);
          ctx.rotate(-Math.PI / 2);
          ctx.fillText(i.toString(), 4, 0);
          ctx.restore();
        } else if (i % (step / 2) === 0) {
          ctx.moveTo(width, pos);
          ctx.lineTo(width - 8, pos);
        } else {
          ctx.moveTo(width, pos);
          ctx.lineTo(width - 4, pos);
        }
      }
      ctx.stroke();
    }
  }, [type, zoom, baseSize]);

  const handleMouseDown = (e: React.MouseEvent) => {
    const rect = canvasRef.current?.getBoundingClientRect();
    if (!rect) return;
    const clickPos = type === 'horizontal' ? e.clientX - rect.left : e.clientY - rect.top;
    const originalPos = Math.round(clickPos / zoom);
    onAddGuide(originalPos);
  };

  return (
    <canvas
      ref={canvasRef}
      className="cursor-crosshair select-none opacity-80 hover:opacity-100 transition-opacity"
      onMouseDown={handleMouseDown}
      title="Nhấp chuột để tạo đường gióng"
    />
  );
}
