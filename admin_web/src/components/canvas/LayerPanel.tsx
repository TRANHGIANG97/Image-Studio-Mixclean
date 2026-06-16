'use client';

import React, { useState } from 'react';
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
  Text,
  Image as ImageIcon,
  Sparkles
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useEditorStore } from '@/store/editor.store';
import { useLayersStore, LayerState } from '@/store/layers.store';
import { toast } from 'sonner';
import { LayerItem, renderLayerPreview, getLayerIcon } from './layers/LayerItem';

interface LayerPanelProps {
  compact?: boolean;
}

export default function LayerPanel({ compact = false }: LayerPanelProps) {
  const { canvas, pushState } = useEditorStore();
  const { layers, activeObjectId, setLayers, setActiveObjectId, setActiveObjectProps } = useLayersStore();
  const [draggedLayerId, setDraggedLayerId] = useState<string | null>(null);
  const [dropTargetLayerId, setDropTargetLayerId] = useState<string | null>(null);
  const [editingLayerId, setEditingLayerId] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState('');
  const [expanded, setExpanded] = useState(false);

  const syncStoreLayers = () => {
    if (!canvas) return;
    const objects = canvas.getObjects();
    const updatedLayers = objects
      .filter((obj: any) => obj._isBackground !== true)
      .map((obj: any) => {
        if (!obj.layerId) {
          obj.layerId = `layer_${Math.random().toString(36).substring(2, 11)}`;
        }
        return {
          id: obj.layerId,
          name: obj.layerName || 'Layer',
          type: obj.layerType || (obj.type === 'image' ? 'IMAGE' : obj.type === 'i-text' ? 'TEXT' : 'DECORATION'),
          visible: obj.visible !== false,
          locked: obj.lockMovementX === true
        };
      });
    setLayers(updatedLayers);
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
      const props = {
        left: Math.round(obj.left),
        top: Math.round(obj.top),
        scaleX: obj.scaleX,
        scaleY: obj.scaleY,
        angle: obj.angle,
        opacity: obj.opacity,
        flipX: obj.flipX,
        flipY: obj.flipY,
        layerId: obj.layerId,
        layerType: obj.layerType || (obj.type === 'image' ? 'IMAGE' : obj.type === 'i-text' ? 'TEXT' : 'DECORATION'),
        layerName: obj.layerName,
        text: (obj as any).text,
        fontFamily: (obj as any).fontFamily,
        src: (obj as any).src,
        defaultImageUrl: (obj as any).defaultImageUrl,
        fontWeight: (obj as any).fontWeight || 'normal',
        fontStyle: (obj as any).fontStyle || 'normal',
        underline: (obj as any).underline || false,
        textAlign: (obj as any).textAlign || 'left',
        lineHeight: (obj as any).lineHeight || 1.16,
        charSpacing: (obj as any).charSpacing || 0,
        fill: obj.fill || null,
        fontSize: (obj as any).fontSize || null,
        rx: (obj as any).rx || 0,
        ry: (obj as any).ry || 0,
        stroke: obj.stroke || null,
        strokeWidth: obj.strokeWidth || 0,
        strokeDashArray: obj.strokeDashArray || null,
        imageFilters: (obj as any).imageFilters || {},
        textBackgroundColor: (obj as any).textBackgroundColor || null,
        linethrough: (obj as any).linethrough || false,
        textTransform: (obj as any).textTransform || 'none',
        _originalText: (obj as any)._originalText || (obj as any).text || '',
        shadow: obj.shadow ? {
          color: obj.shadow.color,
          blur: obj.shadow.blur,
          offsetX: obj.shadow.offsetX,
          offsetY: obj.shadow.offsetY
        } : null
      };
      setActiveObjectProps(props);
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
      pushState();
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
        lockRotation: isLocked, hasControls: !isLocked, selectable: !isLocked
      });
      if (isLocked && canvas.getActiveObject() === obj) canvas.discardActiveObject();
      canvas.renderAll();
      pushState();
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
      pushState();
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
        pushState();
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
      pushState();
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
      pushState();
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
      pushState();
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
      pushState();
      syncStoreLayers();
    }
  };

  const handleRename = (layerId: string, newName: string) => {
    if (!canvas || !newName.trim()) return;
    const obj = getFabricObject(layerId);
    if (obj) {
      obj.set('layerName', newName);
      canvas.renderAll();
      pushState();
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
    pushState();
    syncStoreLayers();
    setDraggedLayerId(null); setDropTargetLayerId(null);
  };

  const getLayerIcon = (type: string) => {
    switch (type) {
      case 'TEXT': return <Text className="w-3.5 h-3.5 text-pink-400" />;
      case 'IMAGE': return <ImageIcon className="w-3.5 h-3.5 text-cyan-400" />;
      default: return <Sparkles className="w-3.5 h-3.5 text-amber-400" />;
    }
  };

  const renderLayerPreview = (layer: LayerState) => {
    const thumbnail = getLayerThumbnail(layer.id);
    if (layer.type === 'TEXT') return <Text className="w-3.5 h-3.5 text-pink-400" />;
    if (thumbnail) {
      return (
        <div className="w-6 h-6 rounded-md overflow-hidden bg-slate-950 border border-slate-800">
          <img src={thumbnail} className="w-full h-full object-cover" alt="" />
        </div>
      );
    }
    return getLayerIcon(layer.type);
  };

  const reversedLayers = [...layers].reverse();

  // ==================== COMPACT MODE: Icon Strip ====================
  if (compact) {
    return (
      <div className="flex flex-col items-center gap-0.5 w-full h-full min-h-0">
        <div className="mb-2 text-[10px] font-bold text-slate-500 flex flex-col items-center gap-0.5">
          <Layers className="w-4 h-4 text-indigo-400" />
          <span className="text-[8px]">{layers.length}</span>
        </div>

        {activeObjectId && (
          <div className="mb-2 flex flex-col items-center gap-1 w-full px-1">
            <button
              onClick={(e) => handleMoveToTop(activeObjectId, e)}
              className="w-8 h-8 rounded-lg bg-slate-800/70 border border-slate-700/60 text-slate-300 hover:text-white hover:bg-slate-700 flex items-center justify-center transition-colors cursor-pointer"
              title="Lên đầu"
            >
              <ChevronsUp className="w-3.5 h-3.5" />
            </button>
            <div className="flex items-center gap-1">
              <button
                onClick={(e) => handleMoveUp(activeObjectId, e)}
                className="w-8 h-8 rounded-lg bg-slate-800/70 border border-slate-700/60 text-slate-300 hover:text-white hover:bg-slate-700 flex items-center justify-center transition-colors cursor-pointer"
                title="Lên trên"
              >
                <ChevronUp className="w-3.5 h-3.5" />
              </button>
              <button
                onClick={(e) => handleMoveDown(activeObjectId, e)}
                className="w-8 h-8 rounded-lg bg-slate-800/70 border border-slate-700/60 text-slate-300 hover:text-white hover:bg-slate-700 flex items-center justify-center transition-colors cursor-pointer"
                title="Xuống"
              >
                <ChevronDown className="w-3.5 h-3.5" />
              </button>
            </div>
            <button
              onClick={(e) => handleMoveToBottom(activeObjectId, e)}
              className="w-8 h-8 rounded-lg bg-slate-800/70 border border-slate-700/60 text-slate-300 hover:text-white hover:bg-slate-700 flex items-center justify-center transition-colors cursor-pointer"
              title="Xuống đáy"
            >
              <ChevronsDown className="w-3.5 h-3.5" />
            </button>
          </div>
        )}

        <div className="flex-1 min-h-0 flex flex-col items-center gap-1 w-full overflow-y-auto scrollbar-thin scrollbar-thumb-slate-800 py-1">
          {reversedLayers.length === 0 ? (
            <p className="text-[8px] text-slate-600 text-center mt-8 leading-tight">Chưa có layer</p>
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
                      : 'bg-slate-800/50 border-transparent hover:border-slate-700 hover:bg-slate-800'
                  }`}
                  title={`${layer.name} (${layer.type})`}
                >
                  {thumbnail ? (
                    <div className="w-8 h-8 rounded-lg overflow-hidden bg-slate-950 border border-slate-800/50">
                      <img src={thumbnail} className="w-full h-full object-cover" alt="" />
                    </div>
                  ) : (
                    getLayerIcon(layer.type)
                  )}

                  {/* Visibility/Lock indicators */}
                  <div className="absolute -top-1 -right-1 flex gap-0">
                    {!layer.visible && (
                      <span className="w-3.5 h-3.5 rounded-full bg-slate-800 border border-slate-700 text-[6px] flex items-center justify-center text-slate-500">👁</span>
                    )}
                    {layer.locked && (
                      <span className="w-3.5 h-3.5 rounded-full bg-slate-800 border border-slate-700 text-[6px] flex items-center justify-center text-amber-500">🔒</span>
                    )}
                  </div>
                </button>
              );
            })
          )}
        </div>

        {/* Quick actions at bottom */}
        <button
          onClick={() => setExpanded(!expanded)}
          className="mt-2 w-8 h-8 rounded-lg bg-slate-800/50 border border-slate-700/50 text-slate-400 hover:text-white hover:bg-slate-700 flex items-center justify-center transition-colors cursor-pointer"
          title="Mở rộng Layers"
        >
          <ChevronUp className={`w-3.5 h-3.5 transition-transform ${expanded ? 'rotate-0' : 'rotate-180'}`} />
        </button>

        {/* Expanded popover */}
        {expanded && (
          <>
            <div className="fixed inset-0 z-30" onClick={() => setExpanded(false)} />
            <div className="absolute left-14 bottom-0 z-40 w-64 bg-slate-900 border border-slate-700 rounded-2xl shadow-2xl p-3 animate-in slide-in-from-left-2 duration-200 max-h-80 overflow-y-auto">
              <h4 className="text-xs font-bold text-slate-200 mb-2">Layers ({layers.length})</h4>
              <div className="space-y-1">
                {reversedLayers.map((layer) => (
                  <div key={layer.id}
                    onClick={() => { handleSelectLayer(layer.id); setExpanded(false); }}
                    className={`flex items-center gap-2 px-2 py-1.5 rounded-lg cursor-pointer text-xs transition-colors ${
                      activeObjectId === layer.id ? 'bg-indigo-600/20 text-indigo-300' : 'hover:bg-slate-800 text-slate-400'
                    }`}
                  >
                    {renderLayerPreview(layer)}
                    <span className="truncate flex-1">{layer.name}</span>
                    <span className="text-[9px] text-slate-600">{layer.type}</span>
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
        <h3 className="font-bold text-sm text-slate-200">Layers Tree</h3>
        <span className="ml-auto text-[10px] text-slate-500">{layers.length}</span>
      </div>

      {activeObjectId && (
        <div className="flex flex-wrap items-center gap-2 px-1">
          <Button size="sm" variant="outline" onClick={(e) => handleMoveToTop(activeObjectId, e)} className="h-8 px-2.5 text-[11px] border-slate-700 text-slate-300 hover:text-white hover:bg-slate-800 rounded-lg">
            <ChevronsUp className="w-3.5 h-3.5 mr-1" /> Lên đầu
          </Button>
          <Button size="sm" variant="outline" onClick={(e) => handleMoveUp(activeObjectId, e)} className="h-8 px-2.5 text-[11px] border-slate-700 text-slate-300 hover:text-white hover:bg-slate-800 rounded-lg">
            <ChevronUp className="w-3.5 h-3.5 mr-1" /> Lên trên
          </Button>
          <Button size="sm" variant="outline" onClick={(e) => handleMoveDown(activeObjectId, e)} className="h-8 px-2.5 text-[11px] border-slate-700 text-slate-300 hover:text-white hover:bg-slate-800 rounded-lg">
            <ChevronDown className="w-3.5 h-3.5 mr-1" /> Xuống
          </Button>
          <Button size="sm" variant="outline" onClick={(e) => handleMoveToBottom(activeObjectId, e)} className="h-8 px-2.5 text-[11px] border-slate-700 text-slate-300 hover:text-white hover:bg-slate-800 rounded-lg">
            <ChevronsDown className="w-3.5 h-3.5 mr-1" /> Xuống đáy
          </Button>
        </div>
      )}

      <div className="flex-1 overflow-y-auto pr-1 scrollbar-thin scrollbar-thumb-slate-800 space-y-2 select-none">
        {reversedLayers.length === 0 ? (
          <div className="text-center py-20 text-slate-655">
            <Layers className="w-8 h-8 mx-auto text-slate-700 mb-2" />
            <p className="text-[11px] font-medium">Chưa có Layer nào</p>
            <p className="text-[10px] text-slate-500 mt-0.5">Mở Assets để thêm ảnh</p>
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
