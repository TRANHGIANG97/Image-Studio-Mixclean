'use client';

import React, { useEffect, useMemo } from 'react';
import { ChevronUp, ChevronDown } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { ColorPalette } from '@/components/canvas/properties/ColorPalette';
import { ShapePropertiesSection } from '@/components/canvas/properties/ShapePropertiesSection';
import { rgbaToHex, hexToRgba } from '@/components/canvas/properties/color-utils';
import { useEditorPropertyActions } from '@/components/editor/useEditorPropertyActions';
import { useEditorUiStore } from '@/store/editor-ui.store';
import { resolveLayerType } from '@/lib/canvas-object-props';
import { t } from '@/i18n/editor';
import ShapeIconTabBar, {
  shapeTabsForLayer,
  type ShapeEditTabId,
} from './ShapeIconTabBar';
import ShapeGalleryRow from './ShapeGalleryRow';
import type { ShapeSubtype } from '@/lib/canvas-factory';

interface ShapeToolPanelProps {
  onDirty?: () => void;
}

function isShapeSelection(
  activeObjectProps: Record<string, unknown> | null,
  canvas: ReturnType<typeof useEditorPropertyActions>['canvas'],
): boolean {
  if (!activeObjectProps) return false;
  const layerType = activeObjectProps.layerType as string;
  if (layerType === 'DECORATION' || layerType === 'SHADOW_REGION') return true;
  const obj = canvas?.getActiveObject() as { type?: string } | undefined;
  if (!obj) return false;
  if (obj.type === 'rect' || obj.type === 'circle' || obj.type === 'triangle' || obj.type === 'polygon' || obj.type === 'line' || obj.type === 'path') {
    return true;
  }
  return resolveLayerType(obj) === 'DECORATION';
}

export default function ShapeToolPanel({ onDirty }: ShapeToolPanelProps) {
  const shapeActiveTab = useEditorUiStore((s) => s.shapeActiveTab);
  const setShapeActiveTab = useEditorUiStore((s) => s.setShapeActiveTab);
  const canvasActions = useEditorUiStore((s) => s.canvasActions);

  const actions = useEditorPropertyActions(onDirty);
  const {
    canvas,
    activeObjectId,
    activeObjectProps,
    handlePropChange,
    handleShadowChange,
    applyShadowPreset,
    handleAlign,
    moveLayerZ,
    recordChange,
    addRecentColor,
    getShadowOpacity,
    setShadowOpacity,
    shadowMetrics,
  } = actions;

  const hasShape = isShapeSelection(activeObjectProps, canvas);
  const shapeTabs = useMemo(() => shapeTabsForLayer(true), []);

  useEffect(() => {
    if (!shapeTabs.includes(shapeActiveTab)) {
      setShapeActiveTab('FILL');
    }
  }, [shapeActiveTab, shapeTabs, setShapeActiveTab]);

  if (!hasShape || !activeObjectProps) {
    return (
      <div className="space-y-3 py-2">
        <p className="text-[11px]" style={{ color: 'var(--editor-text-secondary)' }}>
          {t('studio_shape_no_selection')}
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

  const { currentDistance, currentAngle } = shadowMetrics();

  return (
    <div className="flex flex-col min-h-0">
      <ShapeIconTabBar
        tabs={shapeTabs}
        activeTab={shapeActiveTab}
        onTabSelected={setShapeActiveTab}
      />
      <div className="flex-1 overflow-y-auto py-3 space-y-3">
        {shapeActiveTab === 'FILL' && (
          <ShapePropertiesSection
            showBasic
            section="fill"
            flat
            activeObjectProps={activeObjectProps}
            activeObjectId={activeObjectId}
            onPropChange={handlePropChange}
            onRecordChange={recordChange}
            addRecentColor={addRecentColor}
          />
        )}

        {shapeActiveTab === 'STROKE' && (
          <ShapePropertiesSection
            showBasic
            section="stroke"
            flat
            activeObjectProps={activeObjectProps}
            activeObjectId={activeObjectId}
            onPropChange={handlePropChange}
            onRecordChange={recordChange}
            addRecentColor={addRecentColor}
          />
        )}

        {shapeActiveTab === 'SHADOW' && (
          <ShadowTabContent
            activeObjectProps={activeObjectProps}
            applyShadowPreset={applyShadowPreset}
            handleShadowChange={handleShadowChange}
            getShadowOpacity={getShadowOpacity}
            setShadowOpacity={setShadowOpacity}
            currentDistance={currentDistance}
            currentAngle={currentAngle}
          />
        )}

        {shapeActiveTab === 'ELEVATION' && (
          <p className="text-[11px]" style={{ color: 'var(--editor-text-secondary)' }}>
            {t('studio_elevation_coming')}
          </p>
        )}

        {shapeActiveTab === 'ARRANGE' && (
          <ArrangeTabContent handleAlign={handleAlign} moveLayerZ={moveLayerZ} />
        )}

        {shapeActiveTab === 'SHAPE' && (
          <ShapeGalleryRow
            selected={(activeObjectProps.shapeSubtype as ShapeSubtype) || 'rect'}
            onSelect={(shape) => {
              if (shape !== 'text_only') canvasActions.addShape?.(shape);
            }}
          />
        )}
      </div>
    </div>
  );
}

function ShadowTabContent({
  activeObjectProps,
  applyShadowPreset,
  handleShadowChange,
  getShadowOpacity,
  setShadowOpacity,
  currentDistance,
  currentAngle,
}: {
  activeObjectProps: Record<string, unknown>;
  applyShadowPreset: (type: 'none' | 'soft' | 'medium' | 'hard' | 'glow') => void;
  handleShadowChange: (prop: string, value: unknown) => void;
  getShadowOpacity: (color: string) => number;
  setShadowOpacity: (color: string, pct: number) => string;
  currentDistance: number;
  currentAngle: number;
}) {
  const shadow = activeObjectProps.shadow as Record<string, unknown> | null;
  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <span className="text-[9px] font-semibold" style={{ color: 'var(--editor-text-secondary)' }}>
          {t('studio_shape_tab_shadow')}
        </span>
        <input
          type="checkbox"
          checked={shadow !== null}
          onChange={(e) => {
            if (e.target.checked) applyShadowPreset('medium');
            else applyShadowPreset('none');
          }}
          className="rounded cursor-pointer"
        />
      </div>
      {shadow && (
        <>
          <div className="grid grid-cols-4 gap-1">
            {(['soft', 'medium', 'hard', 'glow'] as const).map((preset) => (
              <Button
                key={preset}
                size="sm"
                variant="outline"
                onClick={() => applyShadowPreset(preset)}
                className="rounded-lg text-[10px] h-7"
              >
                {preset}
              </Button>
            ))}
          </div>
          <div className="flex gap-2">
            <input
              type="color"
              value={rgbaToHex((shadow.color as string) || 'rgba(0,0,0,0.4)')}
              onChange={(e) =>
                handleShadowChange(
                  'color',
                  hexToRgba(e.target.value, (shadow.color as string) || 'rgba(0,0,0,0.4)'),
                )
              }
              className="w-9 h-9 rounded-lg border cursor-pointer"
            />
            <Input
              value={(shadow.color as string) || ''}
              onChange={(e) => handleShadowChange('color', e.target.value)}
              className="flex-1 h-9 text-xs font-mono rounded-xl"
            />
          </div>
          <SliderRow
            label="Blur"
            value={(shadow.blur as number) || 0}
            max={100}
            onChange={(v) => handleShadowChange('blur', v)}
          />
          <SliderRow
            label="Distance"
            value={currentDistance}
            max={150}
            onChange={(v) => handleShadowChange('distance', v)}
          />
          <SliderRow
            label="Angle"
            value={currentAngle}
            max={360}
            onChange={(v) => handleShadowChange('angle', v)}
          />
          <SliderRow
            label="Opacity"
            value={getShadowOpacity((shadow.color as string) || 'rgba(0,0,0,0.4)')}
            max={100}
            onChange={(v) =>
              handleShadowChange(
                'color',
                setShadowOpacity((shadow.color as string) || 'rgba(0,0,0,0.4)', v),
              )
            }
          />
        </>
      )}
      {!shadow && (
        <Button
          type="button"
          variant="outline"
          className="w-full rounded-xl h-8 text-xs"
          onClick={() => applyShadowPreset('soft')}
        >
          Enable shadow
        </Button>
      )}
    </div>
  );
}

function SliderRow({
  label,
  value,
  max,
  onChange,
}: {
  label: string;
  value: number;
  max: number;
  onChange: (v: number) => void;
}) {
  return (
    <div className="space-y-1">
      <div className="flex justify-between text-[9px] font-semibold" style={{ color: 'var(--editor-text-secondary)' }}>
        <span>{label}</span>
        <span>{value}</span>
      </div>
      <input
        type="range"
        min={0}
        max={max}
        value={value}
        onChange={(e) => onChange(parseInt(e.target.value) || 0)}
        className="w-full h-1 rounded-lg appearance-none cursor-pointer accent-indigo-600"
      />
    </div>
  );
}

function ArrangeTabContent({
  handleAlign,
  moveLayerZ,
}: {
  handleAlign: (type: 'left' | 'right' | 'center-h' | 'top' | 'bottom' | 'center-v') => void;
  moveLayerZ: (dir: 'up' | 'down' | 'top' | 'bottom') => void;
}) {
  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-2">
        <Button size="sm" variant="outline" className="rounded-xl h-8 text-[10px] gap-1" onClick={() => moveLayerZ('up')}>
          <ChevronUp className="w-3.5 h-3.5" /> {t('studio_arrange_z_up')}
        </Button>
        <Button size="sm" variant="outline" className="rounded-xl h-8 text-[10px] gap-1" onClick={() => moveLayerZ('down')}>
          <ChevronDown className="w-3.5 h-3.5" /> {t('studio_arrange_z_down')}
        </Button>
      </div>
      <div className="space-y-1.5">
        <span className="text-[9px] font-semibold" style={{ color: 'var(--editor-text-secondary)' }}>
          {t('studio_shape_tab_arrange')}
        </span>
        <div className="grid grid-cols-3 gap-1.5">
          {(
            [
              ['left', 'studio_arrange_align_left'],
              ['center-h', 'studio_arrange_align_center_h'],
              ['right', 'studio_arrange_align_right'],
              ['top', 'studio_arrange_align_top'],
              ['center-v', 'studio_arrange_align_center_v'],
              ['bottom', 'studio_arrange_align_bottom'],
            ] as const
          ).map(([align, key]) => (
            <Button
              key={align}
              size="sm"
              variant="outline"
              className="rounded-xl h-8 text-[10px]"
              onClick={() => handleAlign(align)}
            >
              {t(key)}
            </Button>
          ))}
        </div>
      </div>
    </div>
  );
}
