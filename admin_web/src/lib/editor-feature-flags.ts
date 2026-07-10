import { EDITOR_V2_LAYOUT_KEY } from '@/lib/editor-tokens';

/** Feature flag: canvas-first V2 layout (localStorage or ?editorV2=1). */
export function isEditorV2LayoutEnabled(): boolean {
  if (typeof window === 'undefined') return false;
  try {
    const params = new URLSearchParams(window.location.search);
    if (params.get('editorV2') === '1') return true;
    return localStorage.getItem(EDITOR_V2_LAYOUT_KEY) === '1';
  } catch {
    return false;
  }
}

export function setEditorV2LayoutEnabled(enabled: boolean): void {
  try {
    localStorage.setItem(EDITOR_V2_LAYOUT_KEY, enabled ? '1' : '0');
  } catch {
    /* ignore */
  }
}
