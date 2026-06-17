'use client';

import React from 'react';
import {
  Eye, EyeOff, Lock, Unlock, Trash2,
  ChevronUp, ChevronDown, Copy, Text,
  Image as ImageIcon, Sparkles,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { LayerState } from '@/store/layers.store';

/** Get icon for layer type. */
export function getLayerIcon(type: string) {
  switch (type) {
    case 'TEXT': return <Text className="w-3.5 h-3.5 text-pink-400" />;
    case 'IMAGE': return <ImageIcon className="w-3.5 h-3.5 text-cyan-400" />;
    default: return <Sparkles className="w-3.5 h-3.5 text-amber-400" />;
  }
}

/** Render a small preview for the layer. */
export function renderLayerPreview(layer: LayerState, thumbnail?: string | null) {
  if (layer.type === 'TEXT') return <Text className="w-3.5 h-3.5 text-pink-400" />;
  if (thumbnail) {
    return (
      <div className="w-6 h-6 rounded-md overflow-hidden bg-slate-50 border border-slate-200">
        <img src={thumbnail} className="w-full h-full object-cover" alt="" />
      </div>
    );
  }
  return getLayerIcon(layer.type);
}

interface LayerItemProps {
  layer: LayerState;
  isActive: boolean;
  isEditing: boolean;
  isDragOver: boolean;
  renameValue: string;
  thumbnail?: string | null;
  onSelect: () => void;
  onVisibilityToggle: (e: React.MouseEvent) => void;
  onLockToggle: (e: React.MouseEvent) => void;
  onDelete: (e: React.MouseEvent) => void;
  onDuplicate: (e: React.MouseEvent) => void;
  onMoveUp: (e: React.MouseEvent) => void;
  onMoveDown: (e: React.MouseEvent) => void;
  onDragStart: (e: React.DragEvent<HTMLDivElement>) => void;
  onDragOver: (e: React.DragEvent<HTMLDivElement>) => void;
  onDrop: (e: React.DragEvent<HTMLDivElement>) => void;
  onDragEnd: () => void;
  onStartRename: () => void;
  onRenameChange: (value: string) => void;
  onRenameSubmit: () => void;
  onRenameCancel: () => void;
}

export function LayerItem({
  layer, isActive, isEditing, isDragOver, renameValue, thumbnail,
  onSelect, onVisibilityToggle, onLockToggle, onDelete, onDuplicate,
  onMoveUp, onMoveDown, onDragStart, onDragOver, onDrop, onDragEnd,
  onStartRename, onRenameChange, onRenameSubmit, onRenameCancel,
}: LayerItemProps) {
  return (
    <div
      onClick={onSelect}
      draggable={!isEditing}
      onDragStart={onDragStart}
      onDragOver={onDragOver}
      onDrop={onDrop}
      onDragEnd={onDragEnd}
      className={`p-3 rounded-xl border transition-all flex items-center justify-between group ${
        isEditing
          ? 'bg-indigo-50 border-indigo-500/50'
          : isActive
            ? 'bg-indigo-50 border-indigo-600/50 shadow-md shadow-indigo-600/5 cursor-grab active:cursor-grabbing'
            : isDragOver
              ? 'bg-indigo-50 border-indigo-500/60 shadow-md shadow-indigo-500/5 cursor-grab active:cursor-grabbing'
              : 'bg-white border-slate-200/80 hover:border-slate-300/80 cursor-grab active:cursor-grabbing'
      }`}
    >
      <div className="flex items-center gap-2.5 truncate flex-1 pr-2">
        <div className="shrink-0 p-1 rounded-lg bg-slate-50 border border-slate-200/50 flex items-center justify-center">
          {renderLayerPreview(layer, thumbnail)}
        </div>
        <div className="truncate text-left flex-1">
          {isEditing ? (
            <input
              value={renameValue}
              onChange={(e) => onRenameChange(e.target.value)}
              onBlur={onRenameSubmit}
              onKeyDown={(e) => {
                if (e.key === 'Enter') onRenameSubmit();
                else if (e.key === 'Escape') onRenameCancel();
              }}
              className="bg-white border border-indigo-500/60 text-slate-800 text-xs rounded px-1.5 py-0.5 focus:outline-none w-full font-semibold"
              autoFocus
              onClick={(e) => e.stopPropagation()}
            />
          ) : (
            <p
              onDoubleClick={(e) => { e.stopPropagation(); onStartRename(); }}
              className={`text-xs font-semibold truncate transition-colors ${isActive ? 'text-indigo-600' : 'text-slate-700'}`}
              title="Double click để đổi tên"
            >
              {layer.name}
            </p>
          )}
          <p className="text-[9px] uppercase tracking-wider font-bold text-slate-400 mt-0.5">{layer.type}</p>
        </div>
      </div>

      <div className="flex items-center gap-1 shrink-0">
        <div className="flex items-center">
          <Button size="icon" variant="ghost" onClick={onMoveUp} className="w-6 h-6 text-slate-400 hover:text-slate-700 rounded-md cursor-pointer" title="Đưa lên">
            <ChevronUp className="w-3.5 h-3.5" />
          </Button>
          <Button size="icon" variant="ghost" onClick={onMoveDown} className="w-6 h-6 text-slate-400 hover:text-slate-700 rounded-md cursor-pointer" title="Đưa xuống">
            <ChevronDown className="w-3.5 h-3.5" />
          </Button>
        </div>
        <Button size="icon" variant="ghost" onClick={onLockToggle}
          className={`w-6 h-6 rounded-md cursor-pointer ${layer.locked ? 'text-amber-500' : 'text-slate-400 hover:text-slate-700'}`}
          title={layer.locked ? 'Mở khóa' : 'Khóa'}>
          {layer.locked ? <Lock className="w-3 h-3" /> : <Unlock className="w-3 h-3" />}
        </Button>
        <Button size="icon" variant="ghost" onClick={onVisibilityToggle}
          className={`w-6 h-6 rounded-md cursor-pointer ${!layer.visible ? 'text-slate-300' : 'text-slate-400 hover:text-slate-700'}`}
          title={layer.visible ? 'Ẩn' : 'Hiện'}>
          {layer.visible ? <Eye className="w-3.5 h-3.5" /> : <EyeOff className="w-3.5 h-3.5" />}
        </Button>
        <div className="flex items-center">
          <Button size="icon" variant="ghost" onClick={onDuplicate} className="w-6 h-6 text-slate-400 hover:text-indigo-600 rounded-md cursor-pointer" title="Nhân đôi">
            <Copy className="w-3 h-3" />
          </Button>
          <Button size="icon" variant="ghost" onClick={onDelete} className="w-6 h-6 text-slate-400 hover:text-rose-500 rounded-md cursor-pointer" title="Xóa">
            <Trash2 className="w-3 h-3" />
          </Button>
        </div>
      </div>
    </div>
  );
}
