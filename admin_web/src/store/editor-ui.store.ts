import { create } from 'zustand';
import { EDITOR_CONTROLS_EXPANDED_KEY } from '@/lib/editor-tokens';
import type { LabelEditTabId } from '@/components/editor/label/LabelIconTabBar';
import type { ShapeEditTabId } from '@/components/editor/shape/ShapeIconTabBar';
import type { ShapeSubtype } from '@/lib/canvas-factory';

export type EditorToolId =
  | 'replace'
  | 'sticker'
  | 'label'
  | 'shape'
  | 'rotate'
  | 'shadow'
  | 'transparency'
  | 'crop'
  | 'duplicate'
  | 'delete'
  | null;

export interface EditorCanvasActions {
  addText?: () => void;
  addShape?: (type: ShapeSubtype) => void;
  startTextEdit?: () => void;
  openCrop?: () => void;
  duplicateSelection?: () => void;
  deleteSelection?: () => void;
  openReplace?: () => void;
  openAssets?: () => void;
}

interface EditorUiState {
  selectedTool: EditorToolId;
  /** Parity B7: tab memory when switching label layers */
  labelActiveTab: LabelEditTabId;
  /** Parity B7: tab memory when switching shape layers */
  shapeActiveTab: ShapeEditTabId;
  controlsExpanded: boolean;
  layerRailOpen: boolean;
  layerRailExpanded: boolean;
  assetDrawerOpen: boolean;
  canvasActions: EditorCanvasActions;
  setSelectedTool: (tool: EditorToolId) => void;
  setLabelActiveTab: (tab: LabelEditTabId) => void;
  setShapeActiveTab: (tab: ShapeEditTabId) => void;
  /** Re-tap same tool closes panel without deselecting canvas (parity B8). */
  toggleTool: (tool: EditorToolId) => void;
  setControlsExpanded: (expanded: boolean) => void;
  toggleControlsExpanded: () => void;
  setLayerRailOpen: (open: boolean) => void;
  toggleLayerRail: () => void;
  setLayerRailExpanded: (expanded: boolean) => void;
  setAssetDrawerOpen: (open: boolean) => void;
  registerCanvasActions: (actions: EditorCanvasActions) => void;
}

function readControlsExpanded(): boolean {
  if (typeof window === 'undefined') return false;
  try {
    return localStorage.getItem(EDITOR_CONTROLS_EXPANDED_KEY) === '1';
  } catch {
    return false;
  }
}

export const useEditorUiStore = create<EditorUiState>((set, get) => ({
  selectedTool: null,
  labelActiveTab: 'EDIT',
  shapeActiveTab: 'FILL',
  controlsExpanded: readControlsExpanded(),
  layerRailOpen: false,
  layerRailExpanded: false,
  assetDrawerOpen: false,
  canvasActions: {},

  setSelectedTool: (tool) => set({ selectedTool: tool }),

  setLabelActiveTab: (tab) => set({ labelActiveTab: tab }),
  setShapeActiveTab: (tab) => set({ shapeActiveTab: tab }),

  toggleTool: (tool) => {
    const { selectedTool, controlsExpanded } = get();
    if (selectedTool === tool && controlsExpanded) {
      set({ controlsExpanded: false });
      try {
        localStorage.setItem(EDITOR_CONTROLS_EXPANDED_KEY, '0');
      } catch {
        /* ignore */
      }
      return;
    }
    set({ selectedTool: tool, controlsExpanded: true });
    try {
      localStorage.setItem(EDITOR_CONTROLS_EXPANDED_KEY, '1');
    } catch {
      /* ignore */
    }
  },

  setControlsExpanded: (expanded) => {
    set({ controlsExpanded: expanded });
    try {
      localStorage.setItem(EDITOR_CONTROLS_EXPANDED_KEY, expanded ? '1' : '0');
    } catch {
      /* ignore */
    }
  },

  toggleControlsExpanded: () => {
    const next = !get().controlsExpanded;
    get().setControlsExpanded(next);
  },

  setLayerRailOpen: (open) => set({ layerRailOpen: open }),
  toggleLayerRail: () => set((s) => ({ layerRailOpen: !s.layerRailOpen })),
  setLayerRailExpanded: (expanded) => set({ layerRailExpanded: expanded }),
  setAssetDrawerOpen: (open) => set({ assetDrawerOpen: open }),

  registerCanvasActions: (actions) =>
    set((s) => ({ canvasActions: { ...s.canvasActions, ...actions } })),
}));
