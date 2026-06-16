import { create } from 'zustand';

interface DashboardState {
  /** ID of the currently open dropdown menu (TemplateCard / CategoryTable row) */
  activeDropdownId: string | null;
  /** IDs of templates selected for bulk operations */
  selectedTemplateIds: string[];
  /** Current view mode for the template listing */
  templateViewMode: 'grid' | 'list';

  setActiveDropdownId: (id: string | null) => void;
  toggleTemplateSelection: (id: string) => void;
  selectAll: (ids: string[]) => void;
  clearSelection: () => void;
  setTemplateViewMode: (mode: 'grid' | 'list') => void;
}

export const useDashboardStore = create<DashboardState>((set, get) => ({
  activeDropdownId: null,
  selectedTemplateIds: [],
  templateViewMode: 'grid',

  setActiveDropdownId: (id) => set({ activeDropdownId: id }),

  toggleTemplateSelection: (id) =>
    set((s) => ({
      selectedTemplateIds: s.selectedTemplateIds.includes(id)
        ? s.selectedTemplateIds.filter((t) => t !== id)
        : [...s.selectedTemplateIds, id],
    })),

  selectAll: (ids) => set({ selectedTemplateIds: ids }),

  clearSelection: () => set({ selectedTemplateIds: [] }),

  setTemplateViewMode: (mode) => set({ templateViewMode: mode }),
}));
