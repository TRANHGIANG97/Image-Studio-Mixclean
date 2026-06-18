'use client';

import React from 'react';
import {
  Copy,
  Clipboard,
  Trash2,
  ChevronUp,
  ChevronDown,
  Lock,
  Unlock,
  FlipHorizontal,
  FlipVertical,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { toast } from 'sonner';
import { extractActiveObjectProps } from '@/lib/canvas-object-props';

export interface ToolbarPosition {
  left: number;
  top: number;
  visible: boolean;
}

interface FloatingObjectToolbarProps {
  position: ToolbarPosition | null;
  fabricCanvasRef: React.RefObject<any>;
  onCopy: () => void;
  onDirty: () => void;
  onPushState: () => void;
  onSyncLayers: (canvas: any) => void;
  onSyncProps: (props: Record<string, unknown> | null) => void;
  onReposition: (canvas: any) => void;
}

export function FloatingObjectToolbar({
  position,
  fabricCanvasRef,
  onCopy,
  onDirty,
  onPushState,
  onSyncLayers,
  onSyncProps,
  onReposition,
}: FloatingObjectToolbarProps) {
  if (!position?.visible) return null;

  const canvas = fabricCanvasRef.current;
  const activeObj = canvas?.getActiveObject();
  const isLocked = activeObj?.lockMovementX === true;

  const commit = (mutate: () => void, message?: string) => {
    if (!canvas) return;
    mutate();
    onPushState();
    onDirty();
    onReposition(canvas);
    if (message) toast.success(message);
  };

  return (
    <div
      style={{
        position: 'absolute',
        left: `${position.left}px`,
        top: `${position.top}px`,
        transform: 'translateX(-50%)',
        zIndex: 40,
      }}
      className="flex items-center gap-1 p-1 bg-white/95 border border-slate-200/90 rounded-2xl shadow-xl backdrop-blur-md animate-in fade-in slide-in-from-top-1 duration-150 pointer-events-auto"
    >
      <Button
        size="icon"
        variant="ghost"
        onClick={() => {
          if (!activeObj) return;
          activeObj.clone().then((cloned: any) => {
            commit(() => {
              canvas.discardActiveObject();
              cloned.set({
                left: activeObj.left + 30,
                top: activeObj.top + 30,
                layerId: `layer_${Date.now()}`,
                layerName: `${activeObj.layerName || 'Layer'} Copy`,
                selectable: true,
              });
              canvas.add(cloned);
              canvas.setActiveObject(cloned);
              canvas.renderAll();
              onSyncLayers(canvas);
            }, 'Đã nhân đôi đối tượng!');
          });
        }}
        className="w-7 h-7 hover:bg-slate-100 hover:text-indigo-400 text-slate-400 rounded-xl transition-all"
        title="Nhân đôi (Ctrl+D)"
      >
        <Copy className="w-3.5 h-3.5" />
      </Button>

      <Button
        size="icon"
        variant="ghost"
        onClick={onCopy}
        className="w-7 h-7 hover:bg-slate-100 hover:text-indigo-400 text-slate-400 rounded-xl transition-all"
        title="Sao chép (Ctrl+C)"
      >
        <Clipboard className="w-3.5 h-3.5" />
      </Button>

      <Button
        size="icon"
        variant="ghost"
        onClick={() => {
          if (!activeObj) return;
          commit(() => {
            canvas.bringForward(activeObj);
            canvas.renderAll();
            onSyncLayers(canvas);
          }, 'Đã đưa lên 1 lớp!');
        }}
        className="w-7 h-7 hover:bg-slate-100 hover:text-indigo-400 text-slate-400 rounded-xl transition-all"
        title="Đưa lên 1 lớp"
      >
        <ChevronUp className="w-3.5 h-3.5" />
      </Button>

      <Button
        size="icon"
        variant="ghost"
        onClick={() => {
          if (!activeObj) return;
          const objects = canvas.getObjects();
          const bgObj = objects.find((o: any) => o._isBackground);
          const activeIndex = objects.indexOf(activeObj);
          if (bgObj && activeIndex <= objects.indexOf(bgObj) + 1) {
            toast.warning('Đối tượng đã ở dưới cùng sát ảnh nền.');
            return;
          }
          commit(() => {
            canvas.sendBackwards(activeObj);
            canvas.renderAll();
            onSyncLayers(canvas);
          }, 'Đã giảm xuống 1 lớp!');
        }}
        className="w-7 h-7 hover:bg-slate-100 hover:text-indigo-400 text-slate-400 rounded-xl transition-all"
        title="Đưa xuống 1 lớp"
      >
        <ChevronDown className="w-3.5 h-3.5" />
      </Button>

      <Button
        size="icon"
        variant="ghost"
        onClick={() => {
          if (!activeObj) return;
          commit(() => {
            activeObj.set({ flipX: !activeObj.flipX });
            canvas.renderAll();
            onSyncProps(extractActiveObjectProps(activeObj));
          }, 'Đã lật ngang đối tượng!');
        }}
        className="w-7 h-7 hover:bg-slate-100 hover:text-indigo-400 text-slate-400 rounded-xl transition-all"
        title="Lật ngang"
      >
        <FlipHorizontal className="w-3.5 h-3.5" />
      </Button>

      <Button
        size="icon"
        variant="ghost"
        onClick={() => {
          if (!activeObj) return;
          commit(() => {
            activeObj.set({ flipY: !activeObj.flipY });
            canvas.renderAll();
            onSyncProps(extractActiveObjectProps(activeObj));
          }, 'Đã lật dọc đối tượng!');
        }}
        className="w-7 h-7 hover:bg-slate-100 hover:text-indigo-400 text-slate-400 rounded-xl transition-all"
        title="Lật dọc"
      >
        <FlipVertical className="w-3.5 h-3.5" />
      </Button>

      <Button
        size="icon"
        variant="ghost"
        onClick={() => {
          if (!activeObj) return;
          const locked = activeObj.lockMovementX === true;
          commit(() => {
            activeObj.set({
              lockMovementX: !locked,
              lockMovementY: !locked,
              lockScalingX: !locked,
              lockScalingY: !locked,
              lockRotation: !locked,
              hasControls: locked,
            });
            canvas.renderAll();
            onSyncProps(extractActiveObjectProps(activeObj));
            onSyncLayers(canvas);
          }, locked ? 'Đã mở khóa đối tượng!' : 'Đã khóa đối tượng!');
        }}
        className="w-7 h-7 hover:bg-slate-100 hover:text-indigo-400 text-slate-400 rounded-xl transition-all"
        title={isLocked ? 'Mở khóa' : 'Khóa'}
      >
        {isLocked ? (
          <Lock className="w-3.5 h-3.5 text-amber-500" />
        ) : (
          <Unlock className="w-3.5 h-3.5" />
        )}
      </Button>

      <div className="w-px h-4 bg-slate-100 mx-0.5" />

      <Button
        size="icon"
        variant="ghost"
        onClick={() => {
          if (!activeObj) return;
          commit(() => {
            canvas.remove(activeObj);
            canvas.discardActiveObject();
            canvas.renderAll();
            onSyncLayers(canvas);
          }, 'Đã xóa đối tượng!');
        }}
        className="w-7 h-7 hover:bg-rose-500/20 text-rose-400 rounded-xl transition-all"
        title="Xóa đối tượng (Delete)"
      >
        <Trash2 className="w-3.5 h-3.5" />
      </Button>
    </div>
  );
}

/** Compute toolbar screen position from active Fabric object. */
export function computeFloatingToolbarPosition(canvasInstance: any): ToolbarPosition | null {
  if (!canvasInstance) return null;
  const activeObj = canvasInstance.getActiveObject();
  if (!activeObj || activeObj._isBackground) return null;
  const rect = activeObj.getBoundingRect();
  return {
    left: rect.left + rect.width / 2,
    top: Math.max(10, rect.top - 52),
    visible: true,
  };
}
