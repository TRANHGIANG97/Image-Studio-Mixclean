import { create } from 'zustand';

interface UiState {
  // Panel widths (persisted to localStorage)
  leftPanelWidth: number;
  rightPanelWidth: number;
  // Properties accordion state (persisted to localStorage)
  accordionState: Record<string, boolean>;
  // MiniMap visibility
  miniMapVisible: boolean;
  // Asset drawer open state
  assetDrawerOpen: boolean;

  setLeftPanelWidth: (width: number) => void;
  setRightPanelWidth: (width: number) => void;
  setAccordionState: (state: Record<string, boolean>) => void;
  toggleMiniMap: () => void;
  setAssetDrawerOpen: (open: boolean) => void;
}

/**
 * Load persisted values from localStorage (browser only).
 */
function loadPersisted<T>(key: string, fallback: T): T {
  if (typeof window === 'undefined') return fallback;
  try {
    const stored = localStorage.getItem(key);
    return stored ? JSON.parse(stored) : fallback;
  } catch {
    return fallback;
  }
}

function persist(key: string, value: unknown) {
  if (typeof window === 'undefined') return;
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch { /* quota exceeded — ignore */ }
}

export const useUiStore = create<UiState>((set) => ({
  leftPanelWidth: loadPersisted<number>('editor_left_panel_width', 280),
  rightPanelWidth: loadPersisted<number>('editor_right_panel_width', 320),
  accordionState: loadPersisted<Record<string, boolean>>('editor_props_accordion', {}),
  miniMapVisible: true,
  assetDrawerOpen: false,

  setLeftPanelWidth: (width) => {
    persist('editor_left_panel_width', width);
    set({ leftPanelWidth: width });
  },

  setRightPanelWidth: (width) => {
    persist('editor_right_panel_width', width);
    set({ rightPanelWidth: width });
  },

  setAccordionState: (state) => {
    persist('editor_props_accordion', state);
    set({ accordionState: state });
  },

  toggleMiniMap: () => set((s) => ({ miniMapVisible: !s.miniMapVisible })),

  setAssetDrawerOpen: (open) => set({ assetDrawerOpen: open }),
}));
