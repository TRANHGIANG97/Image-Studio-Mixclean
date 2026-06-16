'use client';

import React, { useState, useCallback } from 'react';
import { ChevronDown } from 'lucide-react';

const STORAGE_ACCORDION = 'editor_props_accordion';

export function CollapsibleSection({
  id, icon, title, children, defaultOpen = false,
}: {
  id: string; icon: React.ReactNode; title: string; children: React.ReactNode; defaultOpen?: boolean;
}) {
  const [isOpen, setIsOpen] = useState(() => {
    try {
      const saved = localStorage.getItem(STORAGE_ACCORDION);
      if (saved) { const state = JSON.parse(saved); if (state[id] !== undefined) return state[id]; }
    } catch {}
    return defaultOpen;
  });

  const toggle = useCallback(() => {
    setIsOpen((prev: boolean) => {
      const next = !prev;
      try {
        const saved = localStorage.getItem(STORAGE_ACCORDION);
        const state = saved ? JSON.parse(saved) : {};
        state[id] = next;
        localStorage.setItem(STORAGE_ACCORDION, JSON.stringify(state));
      } catch {}
      return next;
    });
  }, [id]);

  return (
    <div className="rounded-2xl bg-slate-900 border border-slate-800/80 overflow-hidden">
      <button onClick={toggle}
        className="w-full flex items-center gap-2 px-4 py-2.5 hover:bg-slate-800/50 transition-colors cursor-pointer text-left"
      >
        <span className="text-indigo-400">{icon}</span>
        <span className="text-[10px] font-extrabold text-slate-400 uppercase tracking-wider flex-1">{title}</span>
        <ChevronDown className={`w-3.5 h-3.5 text-slate-500 transition-transform duration-200 ${isOpen ? 'rotate-0' : '-rotate-90'}`} />
      </button>
      {isOpen && <div className="px-4 pb-4 space-y-4 animate-in fade-in slide-in-from-top-1 duration-150">{children}</div>}
    </div>
  );
}
