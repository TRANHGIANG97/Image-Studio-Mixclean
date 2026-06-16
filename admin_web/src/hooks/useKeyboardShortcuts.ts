'use client';

import { useEffect, useRef } from 'react';
import { useEditorStore } from '@/store/editor.store';
import { useLayersStore } from '@/store/layers.store';

interface KeyboardShortcutOptions {
  onSave?: () => void;
  setIsDirty?: (dirty: boolean) => void;
}

/**
 * Global keyboard shortcuts for the canvas editor:
 * - Ctrl+S: Save
 * - Ctrl+Z: Undo
 * - Ctrl+Y / Ctrl+Shift+Z: Redo
 * - Delete/Backspace: Delete selected layer
 * - Ctrl+D: Duplicate selected layer
 * - Escape: Deselect
 * - Arrow keys: Nudge selected object
 */
export function useKeyboardShortcuts({ onSave, setIsDirty }: KeyboardShortcutOptions = {}) {
  const optsRef = useRef({ onSave, setIsDirty });
  optsRef.current = { onSave, setIsDirty };

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Get fresh canvas ref from store
      const { canvas, undo, redo, pushState } = useEditorStore.getState();
      const { syncLayersFromCanvas, setActiveObjectId, setActiveObjectProps } = useLayersStore.getState();
      if (!canvas) return;

      const activeEl = document.activeElement;
      const isTyping =
        activeEl?.tagName === 'INPUT' ||
        activeEl?.tagName === 'TEXTAREA' ||
        (activeEl as any)?.isContentEditable ||
        canvas.getActiveObject()?.isEditing;

      if (isTyping) return;

      const activeObj = canvas.getActiveObject();
      const { onSave: save, setIsDirty: dirty } = optsRef.current;

      // Ctrl+S: Save
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
        e.preventDefault();
        save?.();
        return;
      }

      // Ctrl+Z: Undo
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z' && !e.shiftKey) {
        e.preventDefault();
        undo();
        return;
      }

      // Ctrl+Y / Ctrl+Shift+Z: Redo
      if (((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'y') ||
          ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key.toLowerCase() === 'z')) {
        e.preventDefault();
        redo();
        return;
      }

      // Escape: Deselect
      if (e.key === 'Escape') {
        canvas.discardActiveObject();
        canvas.renderAll();
        return;
      }

      // Ctrl+C: Copy
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'c') {
        if (activeObj && !(activeObj as any)._isBackground) {
          e.preventDefault();
          const { copyToClipboard } = useEditorStore.getState();
          copyToClipboard();
        }
        return;
      }

      // Ctrl+V: Paste
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'v') {
        e.preventDefault();
        const { pasteFromClipboard } = useEditorStore.getState();
        pasteFromClipboard();
        dirty?.(true);
        return;
      }

      // Ctrl+G: Group / Ctrl+Shift+G: Ungroup
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'g') {
        e.preventDefault();
        const { groupSelection, ungroupSelection } = useEditorStore.getState();
        if (e.shiftKey) {
          ungroupSelection();
        } else {
          groupSelection();
        }
        dirty?.(true);
        return;
      }

      if (activeObj && !(activeObj as any)._isBackground) {
        // Delete / Backspace: Remove layer
        if (e.key === 'Delete' || e.key === 'Backspace') {
          e.preventDefault();
          canvas.remove(activeObj);
          canvas.discardActiveObject();
          canvas.renderAll();
          pushState();
          dirty?.(true);
          syncLayersFromCanvas(canvas);
          return;
        }

        // Ctrl+D: Duplicate
        if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'd') {
          e.preventDefault();
          activeObj.clone().then((cloned: any) => {
            canvas.discardActiveObject();
            cloned.set({
              left: activeObj.left + 30,
              top: activeObj.top + 30,
              layerId: `layer_${Date.now()}`,
              layerName: `${activeObj.layerName || 'Layer'} Copy`,
              selectable: true,
            });
            canvas.add(cloned);
            canvas.setActiveObject(cloned);
            canvas.renderAll();
            pushState();
            dirty?.(true);
            syncLayersFromCanvas(canvas);
          });
          return;
        }

        // Arrow keys: Nudge by 1px (or 10px with Shift)
        const step = e.shiftKey ? 10 : 1;
        switch (e.key) {
          case 'ArrowUp':
            e.preventDefault();
            activeObj.top = (activeObj.top || 0) - step;
            activeObj.setCoords();
            canvas.renderAll();
            break;
          case 'ArrowDown':
            e.preventDefault();
            activeObj.top = (activeObj.top || 0) + step;
            activeObj.setCoords();
            canvas.renderAll();
            break;
          case 'ArrowLeft':
            e.preventDefault();
            activeObj.left = (activeObj.left || 0) - step;
            activeObj.setCoords();
            canvas.renderAll();
            break;
          case 'ArrowRight':
            e.preventDefault();
            activeObj.left = (activeObj.left || 0) + step;
            activeObj.setCoords();
            canvas.renderAll();
            break;
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);
}
