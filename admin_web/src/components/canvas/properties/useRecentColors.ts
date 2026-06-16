'use client';

import { useState, useCallback } from 'react';

const RECENT_COLORS_KEY = 'editor_recent_colors';
const MAX_RECENT = 10;

export function useRecentColors() {
  const [recentColors, setRecentColors] = useState<string[]>(() => {
    try { const saved = localStorage.getItem(RECENT_COLORS_KEY); return saved ? JSON.parse(saved) : []; }
    catch { return []; }
  });

  const addRecentColor = useCallback((color: string) => {
    setRecentColors((prev: string[]) => {
      const next = [color, ...prev.filter(c => c !== color)].slice(0, MAX_RECENT);
      try { localStorage.setItem(RECENT_COLORS_KEY, JSON.stringify(next)); } catch {}
      return next;
    });
  }, []);

  return { recentColors, addRecentColor };
}

export const PRESET_COLORS = [
  '#6366f1', '#10b981', '#ef4444', '#f59e0b', '#3b82f6', '#ec4899',
  '#ffffff', '#000000', '#f8fafc', '#e2e8f0', '#8b5cf6', '#06b6d4'
];
