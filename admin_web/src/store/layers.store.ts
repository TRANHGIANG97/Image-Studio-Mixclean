import { create } from 'zustand';
import { ensureFontLoaded } from '@/lib/font-loader';
import { resolveLayerType, isFabricShadowRegion } from '@/lib/canvas-object-props';
import { getEditorCanvasRef } from './canvas-ref';


/**
 * Layer state derived from Fabric canvas objects.
 */
export interface LayerState {
  id: string;
  name: string;
  type: string;
  visible: boolean;
  locked: boolean;
  textPreview?: string;
  fontFamily?: string;
  fill?: string;
  isShadowRegion?: boolean;
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

function resolveTextPreview(obj: any, type: string): string | undefined {
  if (type !== 'TEXT') return undefined;
  const raw = String(obj.text ?? obj._originalText ?? '')
    .replace(/\s+/g, ' ')
    .trim();
  return raw || undefined;
}

function resolveLayerName(obj: any, type: string, textPreview?: string): string {
  const customName = obj.layerName as string | undefined;
  const isGeneric =
    !customName ||
    customName === 'Layer' ||
    customName === 'Nhập chữ...' ||
    customName === 'Text Layer';
  if (type === 'TEXT' && textPreview && isGeneric) {
    return textPreview.length > 32 ? `${textPreview.slice(0, 32)}…` : textPreview;
  }
  return customName || 'Layer';
}

function buildLayersFromCanvas(canvas: any): LayerState[] {
  if (!canvas) return [];
  return canvas
    .getObjects()
    .filter((obj: any) => obj._isBackground !== true)
    .map((obj: any) => {
      if (!obj.layerId) {
        obj.layerId = `layer_${Math.random().toString(36).substring(2, 11)}`;
      }
      const type = resolveLayerType(obj);
      const textPreview = resolveTextPreview(obj, type);
      const fill =
        typeof obj.fill === 'string'
          ? obj.fill
          : obj.fill?.colorStops?.[0]?.color;
      const isShadowRegion = isFabricShadowRegion(obj);
      return {
        id: obj.layerId,
        name: resolveLayerName(obj, type, textPreview),
        type: isShadowRegion ? 'SHADOW_REGION' : type,
        visible: obj.visible !== false,
        locked: obj.lockMovementX === true,
        textPreview,
        fontFamily: type === 'TEXT' ? obj.fontFamily : undefined,
        fill: type === 'TEXT' ? fill : undefined,
        isShadowRegion,
      };
    });
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
    const canvas = getEditorCanvasRef();
    if (!canvas) return;

    const activeObject = canvas.getActiveObject();
    const { activeObjectId, activeObjectProps } = get();

    if (!activeObject || (activeObject as any).layerId !== activeObjectId) return;

    // Handle shadow specially (Fabric serializes shadow as object)
    const shadowUpdate = props.shadow;
    if (shadowUpdate && typeof shadowUpdate === 'object') {
      const currentShadow = activeObject.shadow || {};
      const mergedShadow = { ...currentShadow, ...shadowUpdate };
      import('fabric').then(({ Shadow }) => {
        activeObject.set('shadow', new Shadow(mergedShadow));
        canvas.renderAll();
      });
      delete props.shadow;
    }

    const isText = activeObject.type === 'i-text' || activeObject.type === 'textbox' || activeObject.type === 'text';
    if (isText && activeObject.isEditing && activeObject.selectionStart !== activeObject.selectionEnd) {
      activeObject.setSelectionStyles(props);
    } else {
      activeObject.set(props);
    }
    canvas.renderAll();

    // If fontFamily is updated, wait for font to load and trigger renderAll again
    if (props.fontFamily && typeof document !== 'undefined') {
      const fontName = props.fontFamily;
      ensureFontLoaded(fontName).then(() => {
        document.fonts.load(`12px "${fontName}"`).then(() => {
          canvas.renderAll();
        }).catch((err) => {
          console.warn(`Failed to load font "${fontName}":`, err);
        });
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
