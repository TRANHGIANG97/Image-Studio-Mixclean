'use client';

import { useEditorStore } from '@/store/editor.store';
import { useLayersStore } from '@/store/layers.store';

export type CommitOptions = {
  pushUndo?: boolean;
  markDirty?: boolean;
  syncLayers?: boolean;
  onDirty?: () => void;
};

const DEBOUNCED_PROPS = new Set([
  'opacity',
  'fontSize',
  'charSpacing',
  'lineHeight',
  'angle',
  'left',
  'top',
  'strokeWidth',
  'layerName',
  'scaleX',
  'scaleY',
  'rx',
  'ry',
]);

const historyTimers = new Map<string, ReturnType<typeof setTimeout>>();
const pendingCommits = new Map<string, () => void>();

export function shouldDebounceProp(name: string): boolean {
  return DEBOUNCED_PROPS.has(name);
}

/** Push undo snapshot and mark template dirty. */
export function recordCanvasHistory(onDirty?: () => void): void {
  useEditorStore.getState().pushState();
  onDirty?.();
}

/** Debounce undo/dirty commits (e.g. slider drags). Mutation should run immediately before calling this. */
export function scheduleHistoryCommit(
  key: string,
  commit: () => void,
  debounceMs = 300
): void {
  pendingCommits.set(key, commit);
  const existing = historyTimers.get(key);
  if (existing) clearTimeout(existing);

  historyTimers.set(
    key,
    setTimeout(() => {
      historyTimers.delete(key);
      const fn = pendingCommits.get(key);
      pendingCommits.delete(key);
      fn?.();
    }, debounceMs)
  );
}

/** Apply a canvas mutation then sync layers, undo, and dirty state. */
export function commitCanvasChange(
  canvas: any,
  mutate: () => void,
  opts: CommitOptions = {}
): void {
  const {
    pushUndo = true,
    markDirty = true,
    syncLayers = true,
    onDirty,
  } = opts;

  mutate();

  if (syncLayers && canvas) {
    useLayersStore.getState().syncLayersFromCanvas(canvas);
  }

  if (pushUndo) {
    useEditorStore.getState().pushState();
  }

  if (markDirty) {
    onDirty?.();
  }
}
