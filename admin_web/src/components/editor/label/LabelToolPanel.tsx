'use client';

import React, { useEffect } from 'react';
import { Keyboard } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { ColorPalette } from '@/components/canvas/properties/ColorPalette';
import { rgbaToHex } from '@/components/canvas/properties/color-utils';
import { TextPropertiesSection } from '@/components/canvas/properties/TextPropertiesSection';
import { useEditorPropertyActions } from '@/components/editor/useEditorPropertyActions';
import { useEditorUiStore } from '@/store/editor-ui.store';
import { useLayersStore } from '@/store/layers.store';
import { resolveLayerType } from '@/lib/canvas-object-props';
import { t } from '@/i18n/editor';
import LabelIconTabBar, { type LabelEditTabId } from './LabelIconTabBar';
import ShapeGalleryRow from '../shape/ShapeGalleryRow';
import type { ShapeSubtype } from '@/lib/canvas-factory';

interface LabelToolPanelProps {
  onDirty?: () => void;
}

function isTextSelection(
  activeObjectProps: Record<string, unknown> | null,
  canvas: ReturnType<typeof useEditorPropertyActions>['canvas'],
): boolean {
  if (!activeObjectProps) return false;
  if (activeObjectProps.layerType === 'TEXT') return true;
  const obj = canvas?.getActiveObject();
  return obj ? resolveLayerType(obj) === 'TEXT' : false;
}

export default function LabelToolPanel({ onDirty }: LabelToolPanelProps) {
  const labelActiveTab = useEditorUiStore((s) => s.labelActiveTab);
  const setLabelActiveTab = useEditorUiStore((s) => s.setLabelActiveTab);
  const canvasActions = useEditorUiStore((s) => s.canvasActions);

  const { canvas, activeObjectProps, handlePropChange, recordChange } =
    useEditorPropertyActions(onDirty);

  const hasText = isTextSelection(activeObjectProps, canvas);

  useEffect(() => {
    if (labelActiveTab === 'EDIT' && hasText) {
      canvasActions.startTextEdit?.();
    }
  }, [labelActiveTab, hasText, canvasActions]);

  if (!hasText || !activeObjectProps) {
    return (
      <div className="space-y-3 py-2">
        <p className="text-[11px]" style={{ color: 'var(--editor-text-secondary)' }}>
          {t('studio_label_no_selection')}
        </p>
        <ShapeGalleryRow
          onSelect={(shape) => {
            if (shape === 'text_only') canvasActions.addText?.();
            else canvasActions.addShape?.(shape);
          }}
        />
      </div>
    );
  }

  return (
    <div className="flex flex-col min-h-0">
      <LabelIconTabBar activeTab={labelActiveTab} onTabSelected={setLabelActiveTab} />
      <div className="flex-1 overflow-y-auto py-3 space-y-3">
        <LabelTabContent
          tab={labelActiveTab}
          activeObjectProps={activeObjectProps}
          onPropChange={handlePropChange}
          onRecordChange={recordChange}
          onStartEdit={() => canvasActions.startTextEdit?.()}
        />
      </div>
    </div>
  );
}

function LabelTabContent({
  tab,
  activeObjectProps,
  onPropChange,
  onRecordChange,
  onStartEdit,
}: {
  tab: LabelEditTabId;
  activeObjectProps: Record<string, unknown>;
  onPropChange: (name: string, value: unknown) => void;
  onRecordChange: () => void;
  onStartEdit: () => void;
}) {
  const { updateActiveObject } = useLayersStore();

  switch (tab) {
    case 'EDIT':
      return (
        <div className="space-y-3">
          <p className="text-[11px] leading-relaxed" style={{ color: 'var(--editor-text-secondary)' }}>
            {t('studio_label_edit_hint')}
          </p>
          <Button
            type="button"
            variant="outline"
            onClick={onStartEdit}
            className="w-full rounded-xl h-9 text-xs font-semibold gap-2"
            style={{ borderColor: 'var(--editor-border)' }}
          >
            <Keyboard className="w-4 h-4" style={{ color: 'var(--editor-accent)' }} />
            {t('studio_label_edit_focus')}
          </Button>
        </div>
      );

    case 'FONT':
    case 'SIZE':
    case 'TEXT_STYLE':
    case 'FORMAT':
    case 'ALIGN':
    case 'BG_COLOR':
      return (
        <TextPropertiesSection
          showContent
          activeObjectProps={activeObjectProps}
          onPropChange={onPropChange}
          onRecordChange={onRecordChange}
          section={
            tab === 'FONT'
              ? 'font'
              : tab === 'SIZE'
                ? 'size'
                : tab === 'TEXT_STYLE'
                  ? 'style'
                  : tab === 'FORMAT'
                    ? 'format'
                    : tab === 'ALIGN'
                      ? 'align'
                      : 'bg'
          }
          flat
        />
      );

    case 'TEXT_COLOR':
      return (
        <div className="space-y-2">
          <span className="text-[9px] font-semibold" style={{ color: 'var(--editor-text-secondary)' }}>
            {t('studio_label_tab_text_color')}
          </span>
          <div className="flex gap-2">
            <input
              type="color"
              value={rgbaToHex((activeObjectProps.fill as string) || '#6366f1')}
              onChange={(e) => onPropChange('fill', e.target.value)}
              className="w-9 h-9 rounded-lg border cursor-pointer overflow-hidden"
              style={{ borderColor: 'var(--editor-border)' }}
            />
            <Input
              value={(activeObjectProps.fill as string) || '#6366f1'}
              onChange={(e) => onPropChange('fill', e.target.value)}
              className="flex-1 h-9 text-xs font-mono rounded-xl"
            />
          </div>
          <ColorPalette
            onSelect={(c) => onPropChange('fill', c)}
            selectedColor={(activeObjectProps.fill as string) || undefined}
          />
        </div>
      );

    case 'ELEVATION':
      return (
        <p className="text-[11px]" style={{ color: 'var(--editor-text-secondary)' }}>
          {t('studio_elevation_coming')}
        </p>
      );

    case 'TEXT_FORM':
      return (
        <p className="text-[11px]" style={{ color: 'var(--editor-text-secondary)' }}>
          {t('studio_text_form_coming')}
        </p>
      );

    case 'SHAPE':
      return (
        <ShapeGalleryRow
          selected={(activeObjectProps.shapeSubtype as ShapeSubtype) || null}
          onSelect={(shape) => {
            if (shape === 'text_only') return;
            onPropChange('shapeSubtype', shape);
            if (shape === 'rect' || shape === 'circle') {
              const rx = shape === 'circle' ? 999 : 12;
              updateActiveObject({ rx, ry: rx });
              onRecordChange();
            }
          }}
        />
      );

    default:
      return null;
  }
}
