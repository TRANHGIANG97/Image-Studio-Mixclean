'use client';

import React from 'react';
import { PRESET_COLORS, useRecentColors } from './useRecentColors';

export function ColorPalette({ onSelect, selectedColor }: { onSelect: (c: string) => void; selectedColor?: string }) {
  const { recentColors } = useRecentColors();

  return (
    <div className="space-y-2 pt-1">
      {recentColors.length > 0 && (
        <div className="space-y-1">
          <span className="text-[8px] font-semibold text-slate-400">Gần đây</span>
          <div className="flex gap-1 flex-wrap">
            {recentColors.map(c => (
              <button key={c} onClick={() => onSelect(c)}
                className="w-5 h-5 rounded-md border transition-transform hover:scale-110 active:scale-95 cursor-pointer"
                style={{ backgroundColor: c, borderColor: selectedColor === c ? '#6366f1' : '#cbd5e1' }}
              />
            ))}
          </div>
        </div>
      )}
      <div className="space-y-1">
        <span className="text-[8px] font-semibold text-slate-400">Presets</span>
        <div className="flex gap-1 flex-wrap">
          {PRESET_COLORS.map(c => (
            <button key={c} onClick={() => onSelect(c)}
              className="w-5 h-5 rounded-md border border-slate-300 transition-transform hover:scale-110 active:scale-95 cursor-pointer"
              style={{ backgroundColor: c }}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
