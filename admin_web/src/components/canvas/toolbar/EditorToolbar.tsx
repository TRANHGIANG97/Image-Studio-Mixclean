'use client';

import React, { useState } from 'react';
import {
  Type, Shapes, ImageUp, Crop as CropIcon, Sparkles, ZoomIn, ZoomOut,
  Undo, Redo, Grid3X3, MousePointer2, Magnet,
} from 'lucide-react';
import { Button } from '@/components/ui/button';

interface EditorToolbarProps {
  zoom: number;
  onZoomIn: () => void;
  onZoomOut: () => void;
  onUndo: () => void;
  onRedo: () => void;
  undoStack: string[];
  redoStack: string[];
  onAddText: () => void;
  onAddImage: () => void;
  onCrop: () => void;
  hasImageTarget?: boolean;
  showGrid: boolean;
  onToggleGrid: () => void;
  snappingEnabled: boolean;
  onToggleSnapping: () => void;
  onShapeSelect: (type: 'rect' | 'circle' | 'triangle' | 'line' | 'star' | 'arrow' | 'diamond' | 'hexagon') => void;
}

const SHAPE_TYPES: { key: string; label: string }[] = [
  { key: 'rect', label: '▬' },
  { key: 'circle', label: '●' },
  { key: 'triangle', label: '▲' },
  { key: 'star', label: '★' },
  { key: 'diamond', label: '◆' },
  { key: 'hexagon', label: '⬡' },
  { key: 'arrow', label: '→' },
  { key: 'line', label: '╱' },
];

export default function EditorToolbar({
  zoom, onZoomIn, onZoomOut, onUndo, onRedo, undoStack, redoStack,
  onAddText, onAddImage, onCrop, hasImageTarget = false, showGrid, onToggleGrid, snappingEnabled, onToggleSnapping, onShapeSelect,
}: EditorToolbarProps) {
  const [shapeOpen, setShapeOpen] = useState(false);

  return (
    <div className="flex items-center gap-1 px-4 py-2 bg-white/90 border-b border-slate-200/80 backdrop-blur-sm">
      {/* Undo / Redo */}
      <Button variant="ghost" size="icon" onClick={onUndo} disabled={undoStack.length < 2}
        className="w-8 h-8 rounded-xl text-slate-500 hover:text-slate-800 hover:bg-slate-100 disabled:opacity-30 cursor-pointer">
        <Undo className="w-4 h-4" />
      </Button>
      <Button variant="ghost" size="icon" onClick={onRedo} disabled={redoStack.length === 0}
        className="w-8 h-8 rounded-xl text-slate-500 hover:text-slate-800 hover:bg-slate-100 disabled:opacity-30 cursor-pointer">
        <Redo className="w-4 h-4" />
      </Button>

      <div className="w-px h-6 bg-slate-200 mx-1" />

      {/* Cursor / Select */}
      <Button variant="ghost" size="icon"
        className="w-8 h-8 rounded-xl text-indigo-600 bg-indigo-50 cursor-pointer">
        <MousePointer2 className="w-4 h-4" />
      </Button>

      {/* Add Text */}
      <Button variant="ghost" size="icon" onClick={onAddText}
        className="w-8 h-8 rounded-xl text-slate-500 hover:text-slate-800 hover:bg-slate-100 cursor-pointer"
        title="Thêm chữ (T)">
        <Type className="w-4 h-4" />
      </Button>

      {/* Add Shape */}
      <div className="relative">
        <Button variant="ghost" size="icon" onClick={() => setShapeOpen(!shapeOpen)}
          className="w-8 h-8 rounded-xl text-slate-500 hover:text-slate-800 hover:bg-slate-100 cursor-pointer"
          title="Thêm hình dạng">
          <Shapes className="w-4 h-4" />
        </Button>
        {shapeOpen && (
          <>
            <div className="fixed inset-0 z-10" onClick={() => setShapeOpen(false)} />
            <div className="absolute top-full left-0 mt-1 z-20 bg-white border border-slate-200 rounded-2xl p-2 shadow-xl grid grid-cols-4 gap-1 min-w-[180px]">
              {SHAPE_TYPES.map((s) => (
                <button key={s.key} onClick={() => { onShapeSelect(s.key as any); setShapeOpen(false); }}
                  className="w-10 h-10 flex items-center justify-center text-lg text-slate-600 hover:text-slate-800 hover:bg-slate-100 rounded-xl transition-colors cursor-pointer">
                  {s.label}
                </button>
              ))}
            </div>
          </>
        )}
      </div>

      {/* Add Image */}
      <Button variant="ghost" size="icon" onClick={onAddImage}
        className="w-8 h-8 rounded-xl text-slate-500 hover:text-slate-800 hover:bg-slate-100 cursor-pointer"
        title="Thêm ảnh từ URL">
        <ImageUp className="w-4 h-4" />
      </Button>

      <Button variant="ghost" size="icon" onClick={hasImageTarget ? onCrop : undefined}
        disabled={!hasImageTarget}
        className={`w-8 h-8 rounded-xl cursor-pointer transition-all ${
          hasImageTarget
            ? 'text-slate-500 hover:text-slate-800 hover:bg-slate-100'
            : 'text-slate-300 opacity-40 cursor-not-allowed'
        }`}
        title={hasImageTarget ? 'Trích xuất vùng cắt làm layer mới' : 'Chọn layer ảnh hoặc có ảnh nền để cắt'}>
        <CropIcon className="w-4 h-4" />
      </Button>

      <div className="w-px h-6 bg-slate-200 mx-1" />

      {/* Grid Toggle */}
      <Button variant="ghost" size="icon" onClick={onToggleGrid}
        className={`w-8 h-8 rounded-xl cursor-pointer ${showGrid ? 'text-indigo-600 bg-indigo-50' : 'text-slate-500 hover:text-slate-800 hover:bg-slate-100'}`}
        title="Lưới">
        <Grid3X3 className="w-4 h-4" />
      </Button>

      {/* Snapping */}
      <Button variant="ghost" size="icon" onClick={onToggleSnapping}
        className={`w-8 h-8 rounded-xl cursor-pointer ${snappingEnabled ? 'text-indigo-600 bg-indigo-50' : 'text-slate-500 hover:text-slate-800 hover:bg-slate-100'}`}
        title="Snap">
        <Magnet className="w-4 h-4" />
      </Button>

      <div className="flex-1" />

      {/* Zoom Controls */}
      <div className="flex items-center gap-1 bg-slate-100 rounded-xl px-2 py-1 border border-slate-200">
        <button onClick={onZoomOut} className="w-6 h-6 flex items-center justify-center text-slate-500 hover:text-slate-800 rounded-lg hover:bg-slate-200 cursor-pointer">
          <ZoomOut className="w-3.5 h-3.5" />
        </button>
        <span className="text-[11px] font-mono text-slate-600 min-w-[48px] text-center select-none">
          {Math.round(zoom * 100)}%
        </span>
        <button onClick={onZoomIn} className="w-6 h-6 flex items-center justify-center text-slate-500 hover:text-slate-800 rounded-lg hover:bg-slate-200 cursor-pointer">
          <ZoomIn className="w-3.5 h-3.5" />
        </button>
      </div>

      {/* After effects icon */}
      <Sparkles className="w-4 h-4 text-slate-300 ml-1" />
    </div>
  );
}
