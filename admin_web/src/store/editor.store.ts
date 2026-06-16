import { create } from 'zustand';
import { CANVAS_SERIALIZE_PROPS } from './canvas-serialize.constants';
import { useLayersStore } from './layers.store';
import { toast } from 'sonner';

type BackgroundSnapshot = {
  src?: string | null;
  defaultImageUrl?: string | null;
  left?: number;
  top?: number;
  scaleX?: number;
  scaleY?: number;
  angle?: number;
  opacity?: number;
  visible?: boolean;
  selectable?: boolean;
  evented?: boolean;
  hasControls?: boolean;
  hasBorders?: boolean;
  originX?: string;
  originY?: string;
  layerId?: string;
  layerName?: string;
};

type CanvasSnapshot = {
  json: any;
  backgroundImage: BackgroundSnapshot | null;
};

interface EditorState {
  /** Fabric Canvas instance reference. */
  canvas: any | null;
  /** Undo stack — JSON-serialized canvas snapshots, newest last. */
  undoStack: string[];
  /** Redo stack — JSON-serialized canvas snapshots, newest last. */
  redoStack: string[];
  /** Clipboard for copy-paste operations. */
  clipboard: any | null;

  setCanvas: (canvas: any) => void;
  setClipboard: (clipboard: any) => void;

  /** Push current canvas state onto undo stack (max 50). */
  pushState: () => void;
  /** Undo: restore previous canvas state. */
  undo: () => void;
  /** Redo: restore next canvas state. */
  redo: () => void;
  /** Clear both history stacks. */
  clearHistory: () => void;

  /** Copy selected object to clipboard. */
  copyToClipboard: () => void;
  /** Paste object from clipboard. */
  pasteFromClipboard: () => void;
  /** Group active selection. */
  groupSelection: () => void;
  /** Ungroup active group. */
  ungroupSelection: () => void;
}

function snapshotCanvas(canvas: any): string {
  const json = canvas.toJSON(CANVAS_SERIALIZE_PROPS);
  const bg = canvas.backgroundImage;
  const backgroundImage: BackgroundSnapshot | null = bg
    ? {
        src: bg.src || bg._element?.src || bg._originalElement?.src || null,
        defaultImageUrl: bg.defaultImageUrl || null,
        left: bg.left,
        top: bg.top,
        scaleX: bg.scaleX,
        scaleY: bg.scaleY,
        angle: bg.angle,
        opacity: bg.opacity,
        visible: bg.visible,
        selectable: bg.selectable,
        evented: bg.evented,
        hasControls: bg.hasControls,
        hasBorders: bg.hasBorders,
        originX: bg.originX,
        originY: bg.originY,
        layerId: bg.layerId,
        layerName: bg.layerName,
      }
    : null;

  return JSON.stringify({
    json: {
      ...json,
      backgroundColor: canvas.backgroundColor ?? null,
    },
    backgroundImage,
  } satisfies CanvasSnapshot);
}

async function restoreSnapshot(canvas: any, snapshotJson: string) {
  const snapshot = JSON.parse(snapshotJson) as CanvasSnapshot;
  const { FabricImage } = await import('fabric');

  canvas.backgroundImage = null;
  await canvas.loadFromJSON(snapshot.json);

  if (snapshot.backgroundImage?.src) {
    try {
      const bgImg = await FabricImage.fromURL(snapshot.backgroundImage.src, { crossOrigin: 'anonymous' });
      bgImg.set({
        left: snapshot.backgroundImage.left ?? 0,
        top: snapshot.backgroundImage.top ?? 0,
        scaleX: snapshot.backgroundImage.scaleX ?? 1,
        scaleY: snapshot.backgroundImage.scaleY ?? 1,
        angle: snapshot.backgroundImage.angle ?? 0,
        opacity: snapshot.backgroundImage.opacity ?? 1,
        visible: snapshot.backgroundImage.visible !== false,
        selectable: snapshot.backgroundImage.selectable === true ? true : false,
        evented: snapshot.backgroundImage.evented === true ? true : false,
        hasControls: snapshot.backgroundImage.hasControls === true ? true : false,
        hasBorders: snapshot.backgroundImage.hasBorders === true ? true : false,
        originX: snapshot.backgroundImage.originX || 'left',
        originY: snapshot.backgroundImage.originY || 'top',
      });
      (bgImg as any)._isBackground = true;
      (bgImg as any).layerId = snapshot.backgroundImage.layerId || 'background_layer';
      (bgImg as any).layerName = snapshot.backgroundImage.layerName || 'Background';
      (bgImg as any).defaultImageUrl = snapshot.backgroundImage.defaultImageUrl || snapshot.backgroundImage.src;
      canvas.backgroundImage = bgImg;
    } catch (error) {
      console.warn('Failed to restore background image from history:', error);
    }
  }

  if (snapshot.json?.backgroundColor !== undefined && snapshot.json?.backgroundColor !== null) {
    canvas.backgroundColor = snapshot.json.backgroundColor;
  } else if (!canvas.backgroundColor) {
    canvas.backgroundColor = '#ffffff';
  }

  canvas.renderAll();
  useLayersStore.getState().syncLayersFromCanvas(canvas);
  useLayersStore.getState().setActiveObjectId(null);
  useLayersStore.getState().setActiveObjectProps(null);
}

export const useEditorStore = create<EditorState>((set, get) => ({
  canvas: null,
  undoStack: [],
  redoStack: [],
  clipboard: null,

  setCanvas: (canvas) => set({ canvas }),
  setClipboard: (clipboard) => set({ clipboard }),

  pushState: () => {
    const { canvas, undoStack } = get();
    if (!canvas) return;

    const stateJson = snapshotCanvas(canvas);
    if (undoStack[undoStack.length - 1] === stateJson) return;
    const newUndoStack = [...undoStack, stateJson];
    if (newUndoStack.length > 50) newUndoStack.shift();

    set({ undoStack: newUndoStack, redoStack: [] });
  },

  undo: () => {
    const { canvas, undoStack, redoStack } = get();
    if (!canvas || undoStack.length === 0) return;

    if (undoStack.length < 2) return;

    const currentState = undoStack[undoStack.length - 1];
    const previousState = undoStack[undoStack.length - 2];
    const newUndoStack = undoStack.slice(0, -1);

    restoreSnapshot(canvas, previousState).then(() => {
      set({
        undoStack: newUndoStack,
        redoStack: [...redoStack, currentState],
      });
    });
  },

  redo: () => {
    const { canvas, undoStack, redoStack } = get();
    if (!canvas || redoStack.length === 0) return;

    const nextState = redoStack[redoStack.length - 1];
    const newRedoStack = redoStack.slice(0, -1);
    const currentState = snapshotCanvas(canvas);

    restoreSnapshot(canvas, nextState).then(() => {
      set({
        undoStack: [...undoStack, currentState],
        redoStack: newRedoStack,
      });
    });
  },

  clearHistory: () => {
    set({ undoStack: [], redoStack: [] });
  },

  copyToClipboard: () => {
    const { canvas } = get();
    if (!canvas) return;
    const activeObj = canvas.getActiveObject();
    if (activeObj && !(activeObj as any)._isBackground) {
      activeObj.clone().then((cloned: any) => {
        set({ clipboard: cloned });
        toast.success('Đã sao chép đối tượng vào bộ nhớ tạm!');
      });
    } else {
      toast.error('Không tìm thấy đối tượng hợp lệ để sao chép.');
    }
  },

  pasteFromClipboard: () => {
    const { canvas, clipboard, pushState } = get();
    if (!canvas) return;
    if (!clipboard) {
      toast.warning('Bộ nhớ tạm đang trống!');
      return;
    }

    clipboard.clone().then((clonedObj: any) => {
      canvas.discardActiveObject();
      clonedObj.set({
        left: clonedObj.left + 24,
        top: clonedObj.top + 24,
        layerId: `layer_${Date.now()}`,
        layerName: `${clonedObj.layerName || 'Layer'} Copy`,
        evented: true,
        selectable: true,
      });

      if (clonedObj.type === 'activeselection') {
        clonedObj.canvas = canvas;
        clonedObj.forEachObject((obj: any) => {
          obj.layerId = `layer_${Date.now()}_${Math.random().toString(36).substring(2, 5)}`;
          canvas.add(obj);
        });
        clonedObj.setCoords();
      } else {
        canvas.add(clonedObj);
      }
      canvas.setActiveObject(clonedObj);
      canvas.renderAll();
      pushState();
      useLayersStore.getState().syncLayersFromCanvas(canvas);
      toast.success('Đã dán đối tượng vào canvas!');
    });
  },

  groupSelection: () => {
    const { canvas, pushState } = get();
    if (!canvas) return;
    const activeObj = canvas.getActiveObject();
    if (activeObj && activeObj.type === 'activeselection') {
      const group = (activeObj as any).toGroup();
      group.set({
        layerId: `layer_${Date.now()}`,
        layerName: 'Nhóm Layer',
        layerType: 'DECORATION',
      });
      canvas.setActiveObject(group);
      canvas.renderAll();
      pushState();
      useLayersStore.getState().syncLayersFromCanvas(canvas);
      toast.success('Đã nhóm các đối tượng thành công!');
    }
  },

  ungroupSelection: () => {
    const { canvas, pushState } = get();
    if (!canvas) return;
    const activeObj = canvas.getActiveObject();
    if (activeObj && activeObj.type === 'group') {
      const activeSelection = (activeObj as any).toActiveSelection();
      canvas.setActiveObject(activeSelection);
      canvas.renderAll();
      pushState();
      useLayersStore.getState().syncLayersFromCanvas(canvas);
      toast.success('Đã rã nhóm đối tượng!');
    }
  },
}));
