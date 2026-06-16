import { create } from 'zustand';

/**
 * Layer state derived from Fabric canvas objects.
 */
export interface LayerState {
  id: string;
  name: string;
  type: string;
  visible: boolean;
  locked: boolean;
}

interface LayersState {
  /** Ordered list of layers (excluding background). */
  layers: LayerState[];
  /** Currently selected Fabric object's layerId. */
  activeObjectId: string | null;
  /** All properties of the currently selected object. */
  activeObjectProps: any | null;

  setLayers: (layers: LayerState[]) => void;
  setActiveObjectId: (id: string | null) => void;
  setActiveObjectProps: (props: any | null) => void;

  /**
   * Sync the layers list from the current Fabric canvas state.
   */
  syncLayersFromCanvas: (canvas: any) => void;

  /**
   * Build a layers array from canvas objects (reusable helper).
   */
  buildLayersFromCanvas: (canvas: any) => LayerState[];

  /**
   * Apply property changes to the active Fabric object and sync layers.
   */
  updateActiveObject: (props: Record<string, any>) => void;
}

function buildLayersFromCanvas(canvas: any): LayerState[] {
  if (!canvas) return [];
  return canvas.getObjects()
    .filter((obj: any) => obj.type !== 'image' || obj._isBackground !== true)
    .map((obj: any) => ({
      id: obj.layerId || `layer_${Math.random().toString(36).substring(2, 11)}`,
      name: obj.layerName || 'Layer',
      type: obj.layerType || 'DECORATION',
      visible: obj.visible !== false,
      locked: obj.lockMovementX === true,
    }));
}

export const useLayersStore = create<LayersState>((set, get) => ({
  layers: [],
  activeObjectId: null,
  activeObjectProps: null,

  setLayers: (layers) => set({ layers }),

  setActiveObjectId: (id) => set({ activeObjectId: id }),

  setActiveObjectProps: (props) => set({ activeObjectProps: props }),

  buildLayersFromCanvas,

  syncLayersFromCanvas: (canvas) => {
    set({ layers: buildLayersFromCanvas(canvas) });
  },

  updateActiveObject: (props) => {
    // Lazy access to canvas to avoid circular dependency at module level
    let canvas: any = null;
    try {
      canvas = require('./editor.store').useEditorStore.getState().canvas;
    } catch { return; }
    if (!canvas) return;

    const activeObject = canvas.getActiveObject();
    const { activeObjectId, activeObjectProps } = get();

    if (!activeObject || (activeObject as any).layerId !== activeObjectId) return;

    // Handle shadow specially (Fabric serializes shadow as object)
    const shadowUpdate = props.shadow;
    if (shadowUpdate && typeof shadowUpdate === 'object') {
      const currentShadow = activeObject.shadow || {};
      activeObject.set('shadow', { ...currentShadow, ...shadowUpdate });
      delete props.shadow;
    }

    activeObject.set(props);
    canvas.renderAll();

    // If fontFamily is updated, wait for font to load and trigger renderAll again
    if (props.fontFamily && typeof document !== 'undefined') {
      const fontName = props.fontFamily;
      document.fonts.load(`12px "${fontName}"`).then(() => {
        canvas.renderAll();
      }).catch((err) => {
        console.warn(`Failed to load font "${fontName}":`, err);
      });
    }

    const updatedProps = {
      ...activeObjectProps,
      ...props,
      ...(shadowUpdate ? { shadow: { ...activeObjectProps?.shadow, ...shadowUpdate } } : {}),
    };

    set({
      activeObjectProps: updatedProps,
      layers: buildLayersFromCanvas(canvas),
    });
  },
}));

export { buildLayersFromCanvas };
