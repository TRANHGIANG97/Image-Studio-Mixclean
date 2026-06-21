'use client';

import React from 'react';
import {
  Eye, EyeOff, Lock, Unlock, Trash2,
  ChevronUp, ChevronDown, Copy,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { LayerState } from '@/store/layers.store';
import { LayerPreview } from './LayerPreview';
import { LayerTypeBadge } from './LayerTypeBadge';
import { LAYER_PREVIEW_SIZE_PX } from './layer-preview-utils';

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
      className={`rounded-xl border transition-all overflow-hidden ${
        isEditing
          ? 'bg-indigo-50 border-indigo-500/50'
          : isActive
            ? 'bg-indigo-50 border-indigo-600/50 shadow-md shadow-indigo-600/5 cursor-grab active:cursor-grabbing'
            : isDragOver
              ? 'bg-indigo-50 border-indigo-500/60 shadow-md shadow-indigo-500/5 cursor-grab active:cursor-grabbing'
              : 'bg-white border-slate-200/80 hover:border-slate-300/80 cursor-grab active:cursor-grabbing'
      }`}
    >
      <div className="flex gap-3 p-3 items-start">
        <div className="shrink-0">
          <LayerPreview layer={layer} thumbnail={thumbnail} size={LAYER_PREVIEW_SIZE_PX} />
        </div>

        <div className="flex-1 min-w-0 pt-0.5">
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
              className={`text-sm font-semibold leading-snug line-clamp-2 transition-colors ${isActive ? 'text-indigo-600' : 'text-slate-800'}`}
              title={layer.type === 'TEXT' && layer.textPreview ? layer.textPreview : 'Double click để đổi tên'}
            >
              {layer.name}
            </p>
          )}
          <div className="flex items-center gap-1.5 mt-1.5 flex-wrap">
            <LayerTypeBadge type={layer.type} />
            {layer.type === 'TEXT' && layer.textPreview && layer.name !== layer.textPreview && (
              <span className="text-[10px] font-medium text-slate-500 truncate">
                {layer.textPreview.length > 28 ? `${layer.textPreview.slice(0, 28)}…` : layer.textPreview}
              </span>
            )}
          </div>
        </div>
      </div>

      <div
        className="flex items-center gap-0 pl-2 pr-1.5 py-1 bg-slate-50/80 border-t border-slate-100"
        onClick={(e) => e.stopPropagation()}
      >
        <Button size="icon" variant="ghost" onClick={onMoveUp} className="w-6 h-6 text-slate-400 hover:text-slate-700 rounded-md cursor-pointer shrink-0" title="Đưa lên">
          <ChevronUp className="w-3.5 h-3.5" />
        </Button>
        <Button size="icon" variant="ghost" onClick={onMoveDown} className="w-6 h-6 text-slate-400 hover:text-slate-700 rounded-md cursor-pointer shrink-0" title="Đưa xuống">
          <ChevronDown className="w-3.5 h-3.5" />
        </Button>
        <Button size="icon" variant="ghost" onClick={onLockToggle}
          className={`w-6 h-6 rounded-md cursor-pointer shrink-0 ${layer.locked ? 'text-amber-500' : 'text-slate-400 hover:text-slate-700'}`}
          title={layer.locked ? 'Mở khóa' : 'Khóa'}>
          {layer.locked ? <Lock className="w-3.5 h-3.5" /> : <Unlock className="w-3.5 h-3.5" />}
        </Button>
        <Button size="icon" variant="ghost" onClick={onVisibilityToggle}
          className={`w-6 h-6 rounded-md cursor-pointer shrink-0 ${!layer.visible ? 'text-slate-300' : 'text-slate-400 hover:text-slate-700'}`}
          title={layer.visible ? 'Ẩn' : 'Hiện'}>
          {layer.visible ? <Eye className="w-3.5 h-3.5" /> : <EyeOff className="w-3.5 h-3.5" />}
        </Button>
        <Button size="icon" variant="ghost" onClick={onDuplicate} className="w-6 h-6 text-slate-400 hover:text-indigo-600 rounded-md cursor-pointer shrink-0" title="Nhân đôi">
          <Copy className="w-3.5 h-3.5" />
        </Button>
        <Button size="icon" variant="ghost" onClick={onDelete} className="w-6 h-6 text-slate-400 hover:text-rose-500 rounded-md cursor-pointer shrink-0" title="Xóa">
          <Trash2 className="w-3.5 h-3.5" />
        </Button>
      </div>
    </div>
  );
}
