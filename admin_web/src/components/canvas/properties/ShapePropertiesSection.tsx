'use client';

import React, { useCallback } from 'react';
import { Maximize2, Palette, Pipette, Settings } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { CollapsibleSection } from './CollapsibleSection';
import { ColorPalette } from './ColorPalette';
import { getGradientAngle, rgbaToHex } from './color-utils';
import { scheduleHistoryCommit } from '@/lib/canvas-commands';
import { useLayersStore } from '@/store/layers.store';
import { useEditorStore } from '@/store/editor.store';

interface ShapePropertiesSectionProps {
  showBasic: boolean;
  activeObjectProps: Record<string, any>;
  activeObjectId: string | null;
  onPropChange: (name: string, value: any) => void;
  onRecordChange: () => void;
  addRecentColor: (color: string) => void;
  section?: 'all' | 'fill' | 'stroke' | 'corner';
  flat?: boolean;
}

export function ShapePropertiesSection({
  showBasic,
  activeObjectProps,
  activeObjectId,
  onPropChange,
  onRecordChange,
  addRecentColor,
  section = 'all',
  flat = false,
}: ShapePropertiesSectionProps) {
  const { canvas } = useEditorStore();
  const { updateActiveObject } = useLayersStore();

  const fillValue = activeObjectProps?.fill;
  const isGradient = typeof fillValue === 'object' && fillValue !== null && 'type' in fillValue;
  const fillType: 'solid' | 'linear' | 'radial' = isGradient
    ? fillValue.type === 'radial'
      ? 'radial'
      : 'linear'
    : 'solid';
  const color1 =
    (isGradient && fillValue.colorStops?.[0]?.color) ||
    (typeof fillValue === 'string' ? fillValue : '#6366f1');
  const color2 = (isGradient && fillValue.colorStops?.[1]?.color) || '#ffffff';
  const gradientAngle =
    isGradient && fillValue.type === 'linear' && fillValue.coords
      ? getGradientAngle(fillValue.coords)
      : 0;

  const handleGradientChange = useCallback(
    async (type: 'linear' | 'radial', c1: string, c2: string, angle: number) => {
      if (!canvas) return;
      const activeObject = canvas.getActiveObject();
      if (!activeObject) return;
      addRecentColor(c1);
      addRecentColor(c2);
      const { Gradient } = await import('fabric');
      let coords: any;
      if (type === 'linear') {
        const angleRad = (angle * Math.PI) / 180;
        coords = {
          x1: 0.5 - 0.5 * Math.cos(angleRad),
          y1: 0.5 - 0.5 * Math.sin(angleRad),
          x2: 0.5 + 0.5 * Math.cos(angleRad),
          y2: 0.5 + 0.5 * Math.sin(angleRad),
        };
      } else {
        coords = { x1: 0.5, y1: 0.5, r1: 0, x2: 0.5, y2: 0.5, r2: 0.5 };
      }
      updateActiveObject({
        fill: new Gradient({
          type,
          gradientUnits: 'percentage',
          coords,
          colorStops: [
            { offset: 0, color: c1 },
            { offset: 1, color: c2 },
          ],
        }),
      });
      onRecordChange();
    },
    [canvas, addRecentColor, updateActiveObject, onRecordChange]
  );

  const handleEyedropper = () => {
    // Future: canvas eyedropper implementation
  };

  if (!showBasic) return null;

  const show = (part: 'fill' | 'stroke' | 'corner') => section === 'all' || section === part;

  const inner = (
    <>
      {show('fill') && activeObjectProps.fill !== undefined && activeObjectProps.fill !== null && (
        <CollapsibleSection
          id="fill-stroke"
          icon={<Palette className="w-3 h-3" />}
          title="Màu sắc & Tô"
          defaultOpen={true}
        >
          <div className="flex p-0.5 bg-white border border-slate-200 rounded-xl">
            {(['solid', 'linear', 'radial'] as const).map((t) => (
              <button
                key={t}
                type="button"
                onClick={() => {
                  if (t !== fillType) {
                    if (t === 'solid') onPropChange('fill', color1);
                    else handleGradientChange(t, color1, color2, gradientAngle);
                  }
                }}
                className={`flex-1 py-1 rounded-lg text-[10px] font-bold transition-all cursor-pointer ${
                  fillType === t
                    ? 'bg-indigo-50 text-indigo-600 border border-indigo-200'
                    : 'text-slate-400 hover:text-slate-400'
                }`}
              >
                {t === 'solid' ? 'Đơn' : t === 'linear' ? 'Linear' : 'Radial'}
              </button>
            ))}
          </div>
          {fillType === 'solid' ? (
            <div className="space-y-1">
              <span className="text-[9px] font-semibold text-slate-400">Màu tô</span>
              <div className="flex gap-2">
                <input
                  type="color"
                  value={rgbaToHex(color1)}
                  onChange={(e) => onPropChange('fill', e.target.value)}
                  className="w-8 h-8 rounded-lg bg-transparent border border-slate-200 cursor-pointer overflow-hidden"
                />
                <Input
                  value={color1}
                  onChange={(e) => onPropChange('fill', e.target.value)}
                  className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-8 font-mono flex-1"
                />
                <Button
                  size="icon"
                  variant="ghost"
                  onClick={handleEyedropper}
                  className="w-8 h-8 rounded-lg text-slate-500 hover:text-slate-800 cursor-pointer"
                  title="Lấy màu"
                >
                  <Pipette className="w-3.5 h-3.5" />
                </Button>
              </div>
              <ColorPalette onSelect={(c) => onPropChange('fill', c)} selectedColor={color1} />
            </div>
          ) : (
            <div className="space-y-3">
              {([color1, color2] as string[]).map((c, i) => (
                <div key={i} className="space-y-1">
                  <span className="text-[9px] font-semibold text-slate-400">
                    {i === 0 ? 'Màu bắt đầu' : 'Màu kết thúc'}
                  </span>
                  <div className="flex gap-2">
                    <input
                      type="color"
                      value={rgbaToHex(c)}
                      onChange={(e) =>
                        handleGradientChange(
                          fillType,
                          i === 0 ? e.target.value : color1,
                          i === 0 ? color2 : e.target.value,
                          gradientAngle
                        )
                      }
                      className="w-8 h-8 rounded-lg bg-transparent border border-slate-200 cursor-pointer overflow-hidden"
                    />
                    <Input
                      value={c}
                      onChange={(e) =>
                        handleGradientChange(
                          fillType,
                          i === 0 ? e.target.value : color1,
                          i === 0 ? color2 : e.target.value,
                          gradientAngle
                        )
                      }
                      className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-8 font-mono flex-1"
                    />
                  </div>
                </div>
              ))}
              {fillType === 'linear' && (
                <div className="space-y-1.5">
                  <div className="flex justify-between text-[9px] font-semibold text-slate-400">
                    <span>Góc</span>
                    <span>{gradientAngle}°</span>
                  </div>
                  <div className="flex gap-3 items-center">
                    <input
                      type="range"
                      min="0"
                      max="360"
                      step="5"
                      value={gradientAngle}
                      onChange={(e) =>
                        handleGradientChange('linear', color1, color2, parseInt(e.target.value))
                      }
                      className="flex-1 h-1 bg-white rounded-lg appearance-none cursor-pointer accent-indigo-600"
                    />
                    <Input
                      type="number"
                      min="0"
                      max="360"
                      value={gradientAngle}
                      onChange={(e) =>
                        handleGradientChange(
                          'linear',
                          color1,
                          color2,
                          Math.max(0, Math.min(360, parseInt(e.target.value) || 0))
                        )
                      }
                      className="w-14 bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-7 text-center px-1"
                    />
                  </div>
                </div>
              )}
            </div>
          )}
        </CollapsibleSection>
      )}

      {show('corner') && activeObjectProps.rx !== undefined && (
        <CollapsibleSection id="corner-radius" icon={<Maximize2 className="w-3 h-3" />} title="Bo góc">
          <div className="space-y-1.5">
            <div className="flex justify-between text-[9px] font-semibold text-slate-400">
              <span>Corner Radius</span>
              <span>{Math.round(activeObjectProps.rx)}px</span>
            </div>
            <div className="flex gap-3 items-center">
              <input
                type="range"
                min="0"
                max="100"
                step="1"
                value={activeObjectProps.rx || 0}
                onChange={(e) => {
                  const v = parseInt(e.target.value) || 0;
                  updateActiveObject({ rx: v, ry: v });
                  scheduleHistoryCommit(`rx:${activeObjectId}`, onRecordChange, 300);
                }}
                className="flex-1 h-1 bg-white rounded-lg appearance-none cursor-pointer accent-indigo-600"
              />
              <Input
                type="number"
                min="0"
                max="200"
                value={Math.round(activeObjectProps.rx || 0)}
                onChange={(e) => {
                  const v = Math.max(0, parseInt(e.target.value) || 0);
                  updateActiveObject({ rx: v, ry: v });
                  scheduleHistoryCommit(`rx:${activeObjectId}`, onRecordChange, 300);
                }}
                className="w-14 bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-7 text-center px-1"
              />
            </div>
          </div>
        </CollapsibleSection>
      )}

      {show('stroke') && (activeObjectProps.layerType === 'TEXT' || activeObjectProps.layerType === 'DECORATION') && (
        <CollapsibleSection id="stroke" icon={<Settings className="w-3 h-3" />} title="Viền (Stroke)">
          <div className="space-y-1">
            <span className="text-[9px] font-semibold text-slate-400">Màu viền</span>
            <div className="flex gap-2">
              <input
                type="color"
                value={rgbaToHex(activeObjectProps.stroke || 'rgba(0,0,0,0)')}
                onChange={(e) => onPropChange('stroke', e.target.value)}
                className="w-8 h-8 rounded-lg bg-transparent border border-slate-200 cursor-pointer overflow-hidden"
              />
              <Input
                value={activeObjectProps.stroke || ''}
                onChange={(e) => onPropChange('stroke', e.target.value)}
                className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-8 font-mono flex-1"
                placeholder="Không viền"
              />
            </div>
          </div>
          <div className="space-y-1.5">
            <div className="flex justify-between text-[9px] font-semibold text-slate-400">
              <span>Độ dày</span>
              <span>{activeObjectProps.strokeWidth || 0}px</span>
            </div>
            <div className="flex gap-3 items-center">
              <input
                type="range"
                min="0"
                max="50"
                step="1"
                value={activeObjectProps.strokeWidth || 0}
                onChange={(e) => onPropChange('strokeWidth', parseInt(e.target.value) || 0)}
                className="flex-1 h-1 bg-white rounded-lg appearance-none cursor-pointer accent-indigo-600"
              />
              <Input
                type="number"
                min="0"
                max="100"
                value={activeObjectProps.strokeWidth || 0}
                onChange={(e) => onPropChange('strokeWidth', parseInt(e.target.value) || 0)}
                className="w-14 bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-7 text-center px-1"
              />
            </div>
          </div>
          <div className="space-y-1">
            <span className="text-[9px] font-semibold text-slate-400">Kiểu viền</span>
            <select
              value={
                activeObjectProps.strokeDashArray
                  ? JSON.stringify(activeObjectProps.strokeDashArray)
                  : ''
              }
              onChange={(e) =>
                onPropChange('strokeDashArray', e.target.value ? JSON.parse(e.target.value) : null)
              }
              className="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-400 focus:outline-none focus:border-indigo-600"
            >
              <option value="">Liền nét</option>
              <option value="[10,5]">Đứt nét thưa</option>
              <option value="[5,5]">Đứt nét dày</option>
              <option value="[2,2]">Chấm bi</option>
            </select>
          </div>
        </CollapsibleSection>
      )}
    </>
  );

  if (flat) {
    return <div className="space-y-3">{inner}</div>;
  }

  return inner;
}
