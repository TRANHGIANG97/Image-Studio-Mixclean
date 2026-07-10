'use client';

import React from 'react';
import {
  Paintbrush,
  PenLine,
  Sun,
  Layers,
  AlignCenter,
  Shapes,
} from 'lucide-react';
import { EDITOR_MOTION } from '@/lib/editor-tokens';
import { t } from '@/i18n/editor';

export type ShapeEditTabId =
  | 'FILL'
  | 'STROKE'
  | 'SHADOW'
  | 'ELEVATION'
  | 'ARRANGE'
  | 'SHAPE';

export const SHAPE_DEFAULT_TABS: ShapeEditTabId[] = [
  'FILL',
  'STROKE',
  'SHADOW',
  'ELEVATION',
  'ARRANGE',
  'SHAPE',
];

const TAB_META: Record<
  ShapeEditTabId,
  { icon: React.ElementType; labelKey: Parameters<typeof t>[0] }
> = {
  FILL: { icon: Paintbrush, labelKey: 'studio_shape_tab_fill' },
  STROKE: { icon: PenLine, labelKey: 'studio_shape_tab_stroke' },
  SHADOW: { icon: Sun, labelKey: 'studio_shape_tab_shadow' },
  ELEVATION: { icon: Layers, labelKey: 'studio_shape_tab_elevation' },
  ARRANGE: { icon: AlignCenter, labelKey: 'studio_shape_tab_arrange' },
  SHAPE: { icon: Shapes, labelKey: 'studio_shape_tab_shape' },
};

interface ShapeIconTabBarProps {
  tabs?: ShapeEditTabId[];
  activeTab: ShapeEditTabId;
  onTabSelected: (tab: ShapeEditTabId) => void;
}

export default function ShapeIconTabBar({
  tabs = SHAPE_DEFAULT_TABS,
  activeTab,
  onTabSelected,
}: ShapeIconTabBarProps) {
  return (
    <div
      className="flex items-stretch gap-0 overflow-x-auto no-scrollbar border-b shrink-0"
      style={{ borderColor: 'var(--editor-border)', background: 'var(--editor-panel-bg)' }}
      role="tablist"
      aria-label="Shape editing tabs"
    >
      {tabs.map((tab) => {
        const active = activeTab === tab;
        const { icon: Icon, labelKey } = TAB_META[tab];
        const label = t(labelKey);
        return (
          <button
            key={tab}
            type="button"
            role="tab"
            aria-selected={active}
            aria-label={label}
            onClick={() => onTabSelected(tab)}
            className="relative flex flex-col items-center justify-center gap-0.5 min-w-[56px] h-10 px-1 shrink-0"
            style={{
              background: active ? 'var(--editor-accent-soft)' : 'transparent',
              transition: `background 120ms ease, transform 150ms ${EDITOR_MOTION.springEmphasized}`,
            }}
          >
            <Icon
              className="w-[18px] h-[18px]"
              style={{ color: active ? 'var(--editor-accent)' : 'var(--editor-text-secondary)' }}
            />
            <span
              className="text-[8px] font-semibold leading-none"
              style={{ color: active ? 'var(--editor-accent)' : 'var(--editor-text-secondary)' }}
            >
              {label}
            </span>
            {active && (
              <span
                className="absolute bottom-0 left-1/2 -translate-x-1/2 h-[2px] w-8 rounded-full"
                style={{ background: 'var(--editor-accent)' }}
              />
            )}
          </button>
        );
      })}
    </div>
  );
}

export function shapeTabsForLayer(supportsEffects = true): ShapeEditTabId[] {
  if (supportsEffects) return SHAPE_DEFAULT_TABS;
  return SHAPE_DEFAULT_TABS.filter((t) => t !== 'SHADOW' && t !== 'ELEVATION');
}
