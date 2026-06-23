'use client';

import React, { useState, useEffect } from 'react';
import {
  Layers,
  Eye,
  EyeOff,
  Lock,
  Unlock,
  Trash2,
  ChevronUp,
  ChevronDown,
  ChevronsUp,
  ChevronsDown,
  Copy,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useEditorStore } from '@/store/editor.store';
import { useLayersStore, LayerState } from '@/store/layers.store';
import { toast } from 'sonner';
import { LayerItem } from './layers/LayerItem';
import { LayerPreview } from './layers/LayerPreview';
import { LayerTypeBadge } from './layers/LayerTypeBadge';
import { recordCanvasHistory } from '@/lib/canvas-commands';
import { extractActiveObjectProps } from '@/lib/canvas-object-props';

const LAYER_PANEL_EXPANDED_KEY = 'editor_layer_panel_expanded';

interface LayerPanelProps {
  compact?: boolean;
  onDirty?: () => void;
}

export default function LayerPanel({ compact = false, onDirty }: LayerPanelProps) {
  const { canvas } = useEditorStore();
  const { layers, activeObjectId, setActiveObjectId, setActiveObjectProps, syncLayersFromCanvas } = useLayersStore();
  const [draggedLayerId, setDraggedLayerId] = useState<string | null>(null);
  const [dropTargetLayerId, setDropTargetLayerId] = useState<string | null>(null);
  const [editingLayerId, setEditingLayerId] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState('');
  const [expanded, setExpanded] = useState(true);

  useEffect(() => {
    try {
      const saved = localStorage.getItem(LAYER_PANEL_EXPANDED_KEY);
      if (saved !== null) setExpanded(saved === 'true');
    } catch {}
  }, []);

  const toggleExpanded = () => {
    setExpanded((prev) => {
      const next = !prev;
      try { localStorage.setItem(LAYER_PANEL_EXPANDED_KEY, String(next)); } catch {}
      return next;
    });
  };

  const syncStoreLayers = () => {
    if (!canvas) return;
    syncLayersFromCanvas(canvas);
  };

  const getFabricObject = (layerId: string) => {
    if (!canvas) return null;
    return canvas.getObjects().find((obj: any) => obj.layerId === layerId);
  };

  const handleSelectLayer = (layerId: string) => {
    if (!canvas) return;
    const obj = getFabricObject(layerId);
    if (obj) {
      canvas.setActiveObject(obj);
      canvas.renderAll();
      setActiveObjectId(layerId);
      setActiveObjectProps(extractActiveObjectProps(obj));
    }
  };

  const toggleVisibility = (layerId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!canvas) return;
    const obj = getFabricObject(layerId);
    if (obj) {
      obj.set('visible', !obj.visible);
      if (!obj.visible && canvas.getActiveObject() === obj) canvas.discardActiveObject();
      canvas.renderAll();
      recordCanvasHistory(onDirty);
      syncStoreLayers();
      toast.info(`Đã ${obj.visible ? 'hiện' : 'ẩn'} layer`);
    }
  };

  const toggleLock = (layerId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!canvas) return;
    const obj = getFabricObject(layerId);
    if (obj) {
      const isLocked = !obj.lockMovementX;
      obj.set({
        lockMovementX: isLocked, lockMovementY: isLocked,
        lockScalingX: isLocked, lockScalingY: isLocked,
        lockRotation: isLocked, hasControls: !isLocked, selectable: !isLocked,
        evented: !isLocked
      });
      if (isLocked && canvas.getActiveObject() === obj) canvas.discardActiveObject();
      canvas.renderAll();
      recordCanvasHistory(onDirty);
      syncStoreLayers();
      toast.info(isLocked ? 'Đã khóa layer' : 'Đã mở khóa layer');
    }
  };

  const handleDeleteLayer = (layerId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!canvas) return;
    const obj = getFabricObject(layerId);
    if (obj) {
      canvas.remove(obj);
      canvas.discardActiveObject();
      canvas.renderAll();
      recordCanvasHistory(onDirty);
      syncStoreLayers();
      if (activeObjectId === layerId) {
        setActiveObjectId(null);
        setActiveObjectProps(null);
      }
      toast.success('Đã xóa layer');
    }
  };

  const handleDuplicateLayer = (layerId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!canvas) return;
    const obj = getFabricObject(layerId);
    if (obj) {
      obj.clone().then((cloned: any) => {
        canvas.discardActiveObject();
        cloned.set({
          left: obj.left + 30,
          top: obj.top + 30,
          layerId: `layer_${Date.now()}`,
          layerName: `${obj.layerName} Copy`,
          layerType: obj.layerType,
          selectable: true
        });
        canvas.add(cloned);
        canvas.setActiveObject(cloned);
        canvas.renderAll();
        recordCanvasHistory(onDirty);
        syncStoreLayers();
        toast.success('Đã nhân đôi layer');
      });
    }
  };

  const handleMoveUp = (layerId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!canvas) return;
    const obj = getFabricObject(layerId);
    if (obj) {
      const objects = canvas.getObjects();
      const bgIndex = objects.findIndex((o: any) => o._isBackground === true);
      const minIndex = bgIndex !== -1 ? bgIndex + 1 : 0;
      const currentIndex = objects.indexOf(obj);
      if (currentIndex < objects.length - 1) {
        canvas.moveObjectTo(obj, Math.min(currentIndex + 1, objects.length - 1));
        if (bgIndex !== -1 && objects.indexOf(obj) <= bgIndex) {
          canvas.moveObjectTo(obj, minIndex);
        }
      }
      canvas.renderAll();
      recordCanvasHistory(onDirty);
      syncStoreLayers();
    }
  };

  const handleMoveDown = (layerId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!canvas) return;
    const obj = getFabricObject(layerId);
    if (obj) {
      const objects = canvas.getObjects();
      const bgIndex = objects.findIndex((o: any) => o._isBackground === true);
      const minIndex = bgIndex !== -1 ? bgIndex + 1 : 0;
      const layerIndex = objects.indexOf(obj);
      if (layerIndex > minIndex) {
        canvas.moveObjectTo(obj, Math.max(layerIndex - 1, minIndex));
      }
      canvas.renderAll();
      recordCanvasHistory(onDirty);
      syncStoreLayers();
    }
  };

  const handleMoveToTop = (layerId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!canvas) return;
    const obj = getFabricObject(layerId);
    if (obj) {
      const objects = canvas.getObjects();
      canvas.moveObjectTo(obj, objects.length - 1);
      canvas.renderAll();
      recordCanvasHistory(onDirty);
      syncStoreLayers();
    }
  };

  const handleMoveToBottom = (layerId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!canvas) return;
    const obj = getFabricObject(layerId);
    if (obj) {
      const objects = canvas.getObjects();
      const bgIndex = objects.findIndex((o: any) => o._isBackground === true);
      const targetIndex = bgIndex !== -1 ? bgIndex + 1 : 0;
      canvas.moveObjectTo(obj, targetIndex);
      canvas.renderAll();
      recordCanvasHistory(onDirty);
      syncStoreLayers();
    }
  };

  const handleRename = (layerId: string, newName: string) => {
    if (!canvas || !newName.trim()) return;
    const obj = getFabricObject(layerId);
    if (obj) {
      obj.set('layerName', newName);
      canvas.renderAll();
      recordCanvasHistory(onDirty);
      syncStoreLayers();
    }
  };

  const getLayerThumbnail = (layerId: string): string | null => {
    if (!canvas) return null;
    const obj = getFabricObject(layerId);
    if (!obj) return null;
    return (obj as any).src || (obj as any).defaultImageUrl || null;
  };

  const handleDragStart = (layerId: string) => (e: React.DragEvent<HTMLDivElement>) => {
    setDraggedLayerId(layerId);
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', layerId);
  };

  const handleDragOver = (layerId: string) => (e: React.DragEvent<HTMLDivElement>) => {
    if (!draggedLayerId || draggedLayerId === layerId) return;
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
    setDropTargetLayerId(layerId);
  };

  const handleDrop = (layerId: string) => (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    if (!canvas || !draggedLayerId || draggedLayerId === layerId) {
      setDraggedLayerId(null); setDropTargetLayerId(null); return;
    }
    const objects = canvas.getObjects();
    const sourceObj = objects.find((obj: any) => obj.layerId === draggedLayerId);
    const targetObj = objects.find((obj: any) => obj.layerId === layerId);
    if (!sourceObj || !targetObj) { setDraggedLayerId(null); setDropTargetLayerId(null); return; }
    canvas.moveObjectTo(sourceObj, objects.indexOf(targetObj));
    canvas.renderAll();
    recordCanvasHistory(onDirty);
    syncStoreLayers();
    setDraggedLayerId(null); setDropTargetLayerId(null);
  };

  const reversedLayers = [...layers].reverse();

  // ==================== COMPACT MODE: Icon Strip ====================
  if (compact) {
    return (
      <div className="flex flex-col items-center gap-0.5 w-full h-full min-h-0">
        <div className="mb-2 text-[10px] font-bold text-slate-400 flex flex-col items-center gap-0.5">
          <Layers className="w-4 h-4 text-indigo-400" />
          <span className="text-[8px]">{layers.length}</span>
        </div>

        {activeObjectId && (
          <div className="mb-2 flex flex-col items-center gap-1 w-full px-1">
            <button
              onClick={(e) => handleMoveToTop(activeObjectId, e)}
              className="w-8 h-8 rounded-lg bg-slate-100/70 border border-slate-300/60 text-slate-400 hover:text-slate-800 hover:bg-slate-200 flex items-center justify-center transition-colors cursor-pointer"
              title="Lên đầu"
            >
              <ChevronsUp className="w-3.5 h-3.5" />
            </button>
            <div className="flex items-center gap-1">
              <button
                onClick={(e) => handleMoveUp(activeObjectId, e)}
                className="w-8 h-8 rounded-lg bg-slate-100/70 border border-slate-300/60 text-slate-400 hover:text-slate-800 hover:bg-slate-200 flex items-center justify-center transition-colors cursor-pointer"
                title="Lên trên"
              >
                <ChevronUp className="w-3.5 h-3.5" />
              </button>
              <button
                onClick={(e) => handleMoveDown(activeObjectId, e)}
                className="w-8 h-8 rounded-lg bg-slate-100/70 border border-slate-300/60 text-slate-400 hover:text-slate-800 hover:bg-slate-200 flex items-center justify-center transition-colors cursor-pointer"
                title="Xuống"
              >
                <ChevronDown className="w-3.5 h-3.5" />
              </button>
            </div>
            <button
              onClick={(e) => handleMoveToBottom(activeObjectId, e)}
              className="w-8 h-8 rounded-lg bg-slate-100/70 border border-slate-300/60 text-slate-400 hover:text-slate-800 hover:bg-slate-200 flex items-center justify-center transition-colors cursor-pointer"
              title="Xuống đáy"
            >
              <ChevronsDown className="w-3.5 h-3.5" />
            </button>
          </div>
        )}

        <div className="flex-1 min-h-0 flex flex-col items-center gap-1 w-full overflow-y-auto scrollbar-thin scrollbar-thumb-slate-300 py-1">
          {reversedLayers.length === 0 ? (
            <p className="text-[8px] text-slate-400 text-center mt-8 leading-tight">Chưa có layer</p>
          ) : (
            reversedLayers.map((layer) => {
              const isActive = activeObjectId === layer.id;
              const thumbnail = getLayerThumbnail(layer.id);

              return (
                <button
                  key={layer.id}
                  onClick={() => handleSelectLayer(layer.id)}
                  className={`relative w-10 h-10 rounded-xl flex items-center justify-center transition-all group border ${
                    isActive
                      ? 'bg-indigo-600/20 border-indigo-500/60 ring-1 ring-indigo-500/30'
                      : 'bg-slate-100/50 border-transparent hover:border-slate-300 hover:bg-slate-100'
                  }`}
                  title={`${layer.name} (${layer.type})`}
                >
                  <LayerPreview layer={layer} thumbnail={thumbnail} size={32} />

                  {/* Visibility/Lock indicators */}
                  <div className="absolute -top-1 -right-1 flex gap-0">
                    {!layer.visible && (
                      <span className="w-3.5 h-3.5 rounded-full bg-slate-100 border border-slate-300 text-[6px] flex items-center justify-center text-slate-400">👁</span>
                    )}
                    {layer.locked && (
                      <span className="w-3.5 h-3.5 rounded-full bg-slate-100 border border-slate-300 text-[6px] flex items-center justify-center text-amber-500">🔒</span>
                    )}
                  </div>
                </button>
              );
            })
          )}
        </div>

        {/* Quick actions at bottom */}
        <button
          onClick={toggleExpanded}
          className="mt-2 w-8 h-8 rounded-lg bg-slate-100/50 border border-slate-300/50 text-slate-500 hover:text-slate-800 hover:bg-slate-200 flex items-center justify-center transition-colors cursor-pointer"
          title="Mở rộng Layers"
        >
          <ChevronUp className={`w-3.5 h-3.5 transition-transform ${expanded ? 'rotate-0' : 'rotate-180'}`} />
        </button>

        {/* Expanded popover */}
        {expanded && (
          <>
            <div className="fixed inset-0 z-30" onClick={() => setExpanded(false)} />
            <div className="absolute left-14 bottom-0 z-40 w-64 bg-white border border-slate-300 rounded-2xl shadow-xl p-3 animate-in slide-in-from-left-2 duration-200 max-h-80 overflow-y-auto">
              <h4 className="text-xs font-bold text-slate-400 mb-2">Layers ({layers.length})</h4>
              <div className="space-y-1">
                {reversedLayers.map((layer) => (
                  <div key={layer.id}
                    onClick={() => { handleSelectLayer(layer.id); setExpanded(false); }}
                    className={`flex items-center gap-2 px-2 py-1.5 rounded-lg cursor-pointer text-xs transition-colors ${
                      activeObjectId === layer.id ? 'bg-indigo-600/20 text-indigo-300' : 'hover:bg-slate-100 text-slate-500'
                    }`}
                  >
                    <LayerPreview layer={layer} thumbnail={getLayerThumbnail(layer.id)} size={40} />
                    <span className="truncate flex-1">{layer.name}</span>
                    <LayerTypeBadge type={layer.type} />
                  </div>
                ))}
              </div>
            </div>
          </>
        )}
      </div>
    );
  }

  // ==================== FULL MODE: Expanded Layer Tree ====================
  return (
    <div className="flex flex-col h-full space-y-4">
      <div className="flex items-center gap-1.5 px-1">
        <Layers className="w-4 h-4 text-indigo-400" />
        <h3 className="font-bold text-sm text-slate-400">Layers Tree</h3>
        <span className="ml-auto text-[10px] text-slate-400">{layers.length}</span>
      </div>

      {activeObjectId && (
        <div className="grid grid-cols-2 gap-1.5 px-1">
          <Button size="sm" variant="outline" onClick={(e) => handleMoveToTop(activeObjectId, e)} className="h-7 px-2 text-[10px] border-slate-300 text-slate-500 hover:text-slate-800 hover:bg-slate-100 rounded-lg justify-center">
            <ChevronsUp className="w-3 h-3 mr-1 shrink-0" /> Lên đầu
          </Button>
          <Button size="sm" variant="outline" onClick={(e) => handleMoveToBottom(activeObjectId, e)} className="h-7 px-2 text-[10px] border-slate-300 text-slate-500 hover:text-slate-800 hover:bg-slate-100 rounded-lg justify-center">
            <ChevronsDown className="w-3 h-3 mr-1 shrink-0" /> Xuống đáy
          </Button>
          <Button size="sm" variant="outline" onClick={(e) => handleMoveUp(activeObjectId, e)} className="h-7 px-2 text-[10px] border-slate-300 text-slate-500 hover:text-slate-800 hover:bg-slate-100 rounded-lg justify-center">
            <ChevronUp className="w-3 h-3 mr-1 shrink-0" /> Lên trên
          </Button>
          <Button size="sm" variant="outline" onClick={(e) => handleMoveDown(activeObjectId, e)} className="h-7 px-2 text-[10px] border-slate-300 text-slate-500 hover:text-slate-800 hover:bg-slate-100 rounded-lg justify-center">
            <ChevronDown className="w-3 h-3 mr-1 shrink-0" /> Xuống
          </Button>
        </div>
      )}

      <div className="flex-1 overflow-y-auto pr-0.5 scrollbar-thin scrollbar-thumb-slate-300 space-y-2.5 select-none">
        {reversedLayers.length === 0 ? (
          <div className="text-center py-20 text-slate-655">
            <Layers className="w-8 h-8 mx-auto text-slate-400 mb-2" />
            <p className="text-[11px] font-medium">Chưa có Layer nào</p>
            <p className="text-[10px] text-slate-400 mt-0.5">Mở Assets để thêm ảnh</p>
          </div>
        ) : (
          reversedLayers.map((layer) => {
            const isActive = activeObjectId === layer.id;
            const isEditing = editingLayerId === layer.id;

            return (
              <LayerItem
                key={layer.id}
                layer={layer}
                isActive={isActive}
                isEditing={isEditing}
                isDragOver={dropTargetLayerId === layer.id}
                renameValue={renameValue}
                thumbnail={getLayerThumbnail(layer.id)}
                onSelect={() => handleSelectLayer(layer.id)}
                onVisibilityToggle={(e) => toggleVisibility(layer.id, e)}
                onLockToggle={(e) => toggleLock(layer.id, e)}
                onDelete={(e) => handleDeleteLayer(layer.id, e)}
                onDuplicate={(e) => handleDuplicateLayer(layer.id, e)}
                onMoveUp={(e) => handleMoveUp(layer.id, e)}
                onMoveDown={(e) => handleMoveDown(layer.id, e)}
                onDragStart={handleDragStart(layer.id)}
                onDragOver={handleDragOver(layer.id)}
                onDrop={handleDrop(layer.id)}
                onDragEnd={() => { setDraggedLayerId(null); setDropTargetLayerId(null); }}
                onStartRename={() => { setEditingLayerId(layer.id); setRenameValue(layer.name); }}
                onRenameChange={setRenameValue}
                onRenameSubmit={() => { handleRename(layer.id, renameValue); setEditingLayerId(null); }}
                onRenameCancel={() => setEditingLayerId(null)}
              />
            );
          })
        )}
      </div>
    </div>
  );
}
