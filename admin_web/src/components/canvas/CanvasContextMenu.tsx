'use client';

import React from 'react';
import {
  Copy,
  Clipboard,
  Trash2,
  Layers,
  ChevronUp,
  ChevronDown,
  Type,
  Link,
} from 'lucide-react';
import { toast } from 'sonner';
import { recordCanvasHistory } from '@/lib/canvas-commands';

export interface ContextMenuState {
  x: number;
  y: number;
  visible: boolean;
}

interface CanvasContextMenuProps {
  menu: ContextMenuState | null;
  fabricCanvasRef: React.RefObject<any>;
  clipboard: any;
  onClose: () => void;
  onDirty: () => void;
  onCopy: () => void;
  onPaste: () => void;
  onGroup: () => void;
  onUngroup: () => void;
  syncLayersFromCanvas: (canvas: any) => void;
  onAddText: () => void;
  onAddShape: (type: 'rect' | 'circle') => void;
  onOpenImageUrlDialog: () => void;
}

export function CanvasContextMenu({
  menu,
  fabricCanvasRef,
  clipboard,
  onClose,
  onDirty,
  onCopy,
  onPaste,
  onGroup,
  onUngroup,
  syncLayersFromCanvas,
  onAddText,
  onAddShape,
  onOpenImageUrlDialog,
}: CanvasContextMenuProps) {
  if (!menu?.visible) return null;

  const commitChange = (canvasInstance: any, mutate: () => void) => {
    mutate();
    recordCanvasHistory(onDirty);
    syncLayersFromCanvas(canvasInstance);
  };

  const activeObj = fabricCanvasRef.current?.getActiveObject();
  const hasObjectSelection = activeObj && !activeObj._isBackground;

  return (
    <div
      style={{
        position: 'absolute',
        left: `${menu.x}px`,
        top: `${menu.y}px`,
        zIndex: 100,
      }}
      className="w-56 p-1.5 bg-white/95 backdrop-blur-md border border-slate-200/80 rounded-2xl shadow-xl animate-in fade-in zoom-in-95 duration-100 flex flex-col select-none text-slate-400"
      onClick={(e) => e.stopPropagation()}
    >
      {hasObjectSelection ? (
        <>
          <button
            onClick={() => {
              onCopy();
              onClose();
            }}
            className="flex items-center justify-between px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
          >
            <span className="flex items-center gap-2">
              <Copy className="w-3.5 h-3.5" /> Sao chép
            </span>
            <span className="text-[10px] text-slate-500 font-mono">Ctrl+C</span>
          </button>

          <button
            onClick={() => {
              const canvasInstance = fabricCanvasRef.current;
              const obj = canvasInstance?.getActiveObject();
              if (obj) {
                obj.clone().then((cloned: any) => {
                  commitChange(canvasInstance, () => {
                    canvasInstance.discardActiveObject();
                    cloned.set({
                      left: obj.left + 30,
                      top: obj.top + 30,
                      layerId: `layer_${Date.now()}`,
                      layerName: `${obj.layerName || 'Layer'} Copy`,
                      selectable: true,
                    });
                    canvasInstance.add(cloned);
                    canvasInstance.setActiveObject(cloned);
                    canvasInstance.renderAll();
                  });
                  toast.success('Đã nhân đôi layer!');
                });
              }
              onClose();
            }}
            className="flex items-center justify-between px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
          >
            <span className="flex items-center gap-2">
              <Copy className="w-3.5 h-3.5" /> Nhân đôi
            </span>
            <span className="text-[10px] text-slate-500 font-mono">Ctrl+D</span>
          </button>

          <button
            onClick={() => {
              const canvasInstance = fabricCanvasRef.current;
              const obj = canvasInstance?.getActiveObject();
              if (obj) {
                commitChange(canvasInstance, () => {
                  canvasInstance.remove(obj);
                  canvasInstance.discardActiveObject();
                  canvasInstance.renderAll();
                });
                toast.success('Đã xóa layer!');
              }
              onClose();
            }}
            className="flex items-center justify-between px-3 py-1.5 hover:bg-rose-500/10 hover:text-rose-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
          >
            <span className="flex items-center gap-2">
              <Trash2 className="w-3.5 h-3.5" /> Xóa
            </span>
            <span className="text-[10px] text-rose-500/50 font-mono">Del</span>
          </button>

          {activeObj?.type === 'activeselection' && (
            <button
              onClick={() => {
                onGroup();
                onDirty();
                onClose();
              }}
              className="flex items-center justify-between px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
            >
              <span className="flex items-center gap-2">
                <Layers className="w-3.5 h-3.5" /> Nhóm lại (Group)
              </span>
              <span className="text-[10px] text-slate-500 font-mono">Ctrl+G</span>
            </button>
          )}

          {activeObj?.type === 'group' && (
            <button
              onClick={() => {
                onUngroup();
                onDirty();
                onClose();
              }}
              className="flex items-center justify-between px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
            >
              <span className="flex items-center gap-2">
                <Layers className="w-3.5 h-3.5" /> Rã nhóm (Ungroup)
              </span>
              <span className="text-[10px] text-slate-500 font-mono">Ctrl+Shift+G</span>
            </button>
          )}

          <div className="my-1 border-t border-slate-200/60" />

          <button
            onClick={() => {
              const canvasInstance = fabricCanvasRef.current;
              const obj = canvasInstance?.getActiveObject();
              if (obj) {
                commitChange(canvasInstance, () => {
                  canvasInstance.bringToFront(obj);
                  canvasInstance.renderAll();
                });
                toast.success('Đã chuyển lên trên cùng!');
              }
              onClose();
            }}
            className="flex items-center gap-2 px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
          >
            <ChevronUp className="w-3.5 h-3.5" /> Đưa lên trên cùng
          </button>

          <button
            onClick={() => {
              const canvasInstance = fabricCanvasRef.current;
              const obj = canvasInstance?.getActiveObject();
              if (obj) {
                commitChange(canvasInstance, () => {
                  canvasInstance.bringForward(obj);
                  canvasInstance.renderAll();
                });
              }
              onClose();
            }}
            className="flex items-center gap-2 px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
          >
            <ChevronUp className="w-3.5 h-3.5" /> Tăng 1 lớp
          </button>

          <button
            onClick={() => {
              const canvasInstance = fabricCanvasRef.current;
              const obj = canvasInstance?.getActiveObject();
              if (obj) {
                commitChange(canvasInstance, () => {
                  const objects = canvasInstance.getObjects();
                  const bgObj = objects.find((o: any) => o._isBackground);
                  const activeIndex = objects.indexOf(obj);
                  if (!(bgObj && activeIndex <= objects.indexOf(bgObj) + 1)) {
                    canvasInstance.sendBackwards(obj);
                  }
                  canvasInstance.renderAll();
                });
              }
              onClose();
            }}
            className="flex items-center gap-2 px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
          >
            <ChevronDown className="w-3.5 h-3.5" /> Giảm 1 lớp
          </button>

          <button
            onClick={() => {
              const canvasInstance = fabricCanvasRef.current;
              const obj = canvasInstance?.getActiveObject();
              if (obj) {
                commitChange(canvasInstance, () => {
                  const objects = canvasInstance.getObjects();
                  const bgObj = objects.find((o: any) => o._isBackground);
                  if (bgObj) {
                    obj.moveTo(objects.indexOf(bgObj) + 1);
                  } else {
                    canvasInstance.sendToBack(obj);
                  }
                  canvasInstance.renderAll();
                });
                toast.success('Đã chuyển xuống dưới cùng!');
              }
              onClose();
            }}
            className="flex items-center gap-2 px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
          >
            <ChevronDown className="w-3.5 h-3.5" /> Đưa xuống dưới cùng
          </button>
        </>
      ) : (
        <>
          <button
            disabled={!clipboard}
            onClick={() => {
              onPaste();
              onDirty();
              onClose();
            }}
            className="flex items-center justify-between px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent"
          >
            <span className="flex items-center gap-2">
              <Clipboard className="w-3.5 h-3.5" /> Dán đối tượng
            </span>
            <span className="text-[10px] text-slate-500 font-mono">Ctrl+V</span>
          </button>

          <div className="my-1 border-t border-slate-200/60" />

          <button
            onClick={() => {
              onAddText();
              onClose();
            }}
            className="flex items-center gap-2 px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
          >
            <Type className="w-3.5 h-3.5" /> Thêm văn bản
          </button>

          <button
            onClick={() => {
              onAddShape('rect');
              onClose();
            }}
            className="flex items-center gap-2 px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
          >
            <Layers className="w-3.5 h-3.5" /> Thêm hình vuông
          </button>

          <button
            onClick={() => {
              onAddShape('circle');
              onClose();
            }}
            className="flex items-center gap-2 px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
          >
            <Layers className="w-3.5 h-3.5" /> Thêm hình tròn
          </button>

          <button
            onClick={() => {
              onOpenImageUrlDialog();
              onClose();
            }}
            className="flex items-center gap-2 px-3 py-1.5 hover:bg-indigo-600/15 hover:text-indigo-400 rounded-xl text-left text-xs font-semibold cursor-pointer transition-all"
          >
            <Link className="w-3.5 h-3.5" /> Chèn ảnh từ URL
          </button>
        </>
      )}
    </div>
  );
}
