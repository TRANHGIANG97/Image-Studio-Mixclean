'use client';

import React from 'react';
import {
  Keyboard,
  Bold,
  AlignLeft,
  Paintbrush,
  Layers,
  Sparkles,
  Type,
} from 'lucide-react';
import { EDITOR_MOTION } from '@/lib/editor-tokens';
import { t } from '@/i18n/editor';

export type LabelEditTabId =
  | 'EDIT'
  | 'FONT'
  | 'SIZE'
  | 'TEXT_STYLE'
  | 'FORMAT'
  | 'ALIGN'
  | 'TEXT_COLOR'
  | 'BG_COLOR'
  | 'ELEVATION'
  | 'TEXT_FORM';

export const LABEL_EDIT_TABS: LabelEditTabId[] = [
  'EDIT',
  'FONT',
  'SIZE',
  'TEXT_STYLE',
  'FORMAT',
  'ALIGN',
  'TEXT_COLOR',
  'BG_COLOR',
  'ELEVATION',
  'TEXT_FORM',
];

const TAB_LABEL_KEYS: Record<LabelEditTabId, Parameters<typeof t>[0]> = {
  EDIT: 'studio_label_tab_edit',
  FONT: 'studio_label_tab_font',
  SIZE: 'studio_label_tab_size',
  TEXT_STYLE: 'studio_label_tab_text_style',
  FORMAT: 'studio_label_tab_format',
  ALIGN: 'studio_label_tab_align',
  TEXT_COLOR: 'studio_label_tab_text_color',
  BG_COLOR: 'studio_label_tab_bg_color',
  ELEVATION: 'studio_label_tab_elevation',
  TEXT_FORM: 'studio_label_tab_text_form',
};

function TabIcon({ tab, active }: { tab: LabelEditTabId; active: boolean }) {
  const color = active ? 'var(--editor-accent)' : 'var(--editor-text-secondary)';
  const size = 'w-[18px] h-[18px]';
  switch (tab) {
    case 'EDIT':
      return <Keyboard className={size} style={{ color }} />;
    case 'FONT':
      return (
        <span className="text-[15px] font-serif leading-none" style={{ color }}>
          Ff
        </span>
      );
    case 'SIZE':
      return (
        <span className="text-[14px] font-medium leading-none" style={{ color }}>
          aA
        </span>
      );
    case 'TEXT_STYLE':
      return (
        <span className="text-[15px] font-serif leading-none" style={{ color }}>
          H
        </span>
      );
    case 'FORMAT':
      return <Bold className={size} style={{ color }} />;
    case 'ALIGN':
      return <AlignLeft className={size} style={{ color }} />;
    case 'TEXT_COLOR':
      return <Type className={size} style={{ color }} />;
    case 'BG_COLOR':
      return <Paintbrush className={size} style={{ color }} />;
    case 'ELEVATION':
      return <Layers className={size} style={{ color }} />;
    case 'TEXT_FORM':
      return <Sparkles className={size} style={{ color }} />;
    default:
      return null;
  }
}

interface LabelIconTabBarProps {
  activeTab: LabelEditTabId;
  onTabSelected: (tab: LabelEditTabId) => void;
}

export default function LabelIconTabBar({ activeTab, onTabSelected }: LabelIconTabBarProps) {
  return (
    <div
      className="flex items-stretch gap-0 overflow-x-auto no-scrollbar border-b shrink-0"
      style={{ borderColor: 'var(--editor-border)', background: 'var(--editor-panel-bg)' }}
      role="tablist"
      aria-label="Label editing tabs"
    >
      {LABEL_EDIT_TABS.map((tab) => {
        const active = activeTab === tab;
        const label = t(TAB_LABEL_KEYS[tab]);
        return (
          <button
            key={tab}
            type="button"
            role="tab"
            aria-selected={active}
            aria-label={label}
            onClick={() => onTabSelected(tab)}
            className="relative flex flex-col items-center justify-center gap-0.5 min-w-[52px] h-10 px-1 shrink-0 transition-colors"
            style={{
              background: active ? 'var(--editor-accent-soft)' : 'transparent',
              transition: `background 120ms ease, transform 150ms ${EDITOR_MOTION.springEmphasized}`,
            }}
          >
            <TabIcon tab={tab} active={active} />
            <span
              className="text-[8px] font-semibold leading-none max-w-[48px] truncate"
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
