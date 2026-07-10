/** Design tokens mirroring studio_edit EditorDesignTokens.kt */
export const EDITOR_TOKENS = {
  workspaceBg: '#EBEBEB',
  artboardBg: '#FFFFFF',
  artboardShadow: '0 8px 32px rgba(0, 0, 0, 0.12)',
  accent: '#6366f1',
  accentSoft: 'rgba(99, 102, 241, 0.12)',
  textPrimary: '#1e293b',
  textSecondary: '#64748b',
  border: '#e2e8f0',
  dockBg: '#FFFFFF',
  panelBg: 'rgba(255, 255, 255, 0.95)',
  selectionColor: '#6366f1',
  layerRailCollapsed: 72,
  layerRailExpanded: 280,
  bottomDockHeight: 64,
  topBarHeight: 48,
  contextPanelPeek: 44,
  contextPanelExpanded: 280,
} as const;

export const EDITOR_MOTION = {
  springPanel: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
  springEmphasized: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
  fadeQuick: '120ms ease-out',
  pressScale: 0.97,
  selectionScale: 1.08,
} as const;

export const EDITOR_V2_LAYOUT_KEY = 'editor_v2_layout';
export const EDITOR_CONTROLS_EXPANDED_KEY = 'editor_controls_expanded';
