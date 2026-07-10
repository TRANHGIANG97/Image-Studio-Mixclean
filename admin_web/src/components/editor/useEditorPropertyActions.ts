'use client';

import { useCallback } from 'react';
import { ActiveSelection } from 'fabric';
import { useEditorStore } from '@/store/editor.store';
import { useLayersStore } from '@/store/layers.store';
import { ensureFontLoaded } from '@/lib/font-loader';
import { resolveLayerType } from '@/lib/canvas-object-props';
import { recordCanvasHistory, scheduleHistoryCommit, shouldDebounceProp } from '@/lib/canvas-commands';
import { getCompositeOperation } from '@/lib/blend-mode-utils';
import { hexToRgba, rgbaToHex } from '@/components/canvas/properties/color-utils';
import { useRecentColors } from '@/components/canvas/properties/useRecentColors';

export function useEditorPropertyActions(onDirty?: () => void) {
  const { canvas } = useEditorStore();
  const { activeObjectId, activeObjectProps, updateActiveObject } = useLayersStore();
  const { addRecentColor } = useRecentColors();

  const recordChange = useCallback(() => {
    recordCanvasHistory(onDirty);
  }, [onDirty]);

  const handlePropChange = useCallback(
    (name: string, value: unknown) => {
      if (!activeObjectProps) return;
      if (name === 'fill' && typeof value === 'string') addRecentColor(value);
      if (name === 'stroke' && typeof value === 'string') addRecentColor(value);

      const applyProps = (props: Record<string, unknown>) => {
        updateActiveObject(props);
      };

      if (name === 'fontFamily' && typeof value === 'string') {
        ensureFontLoaded(value).then(() => {
          applyProps({ fontFamily: value });
          recordChange();
        });
        return;
      }

      const activeCanvasObject = canvas?.getActiveObject();
      const isText =
        activeObjectProps?.layerType === 'TEXT' ||
        (activeCanvasObject ? resolveLayerType(activeCanvasObject) === 'TEXT' : false);
      const props =
        name === 'layerType'
          ? { layerType: value, isReplaceable: value === 'PLACEHOLDER_OBJECT' }
          : name === 'isReplaceable'
            ? {
                isReplaceable: value,
                layerType: value ? 'PLACEHOLDER_OBJECT' : 'IMAGE',
              }
            : isText && (name === 'stroke' || name === 'strokeWidth')
              ? { [name]: value, paintFirst: 'stroke' }
              : { [name]: value };

      applyProps(props);

      if (shouldDebounceProp(name)) {
        scheduleHistoryCommit(`prop:${activeObjectId}:${name}`, recordChange, 300);
      } else {
        recordChange();
      }
    },
    [activeObjectId, activeObjectProps, addRecentColor, canvas, recordChange, updateActiveObject],
  );

  const getShadowOpacity = (colorStr: string): number => {
    if (!colorStr) return 40;
    if (colorStr.startsWith('rgba')) {
      const match = colorStr.match(/rgba?\((?:\d+,\s*){3}([\d.]+)\)/);
      if (match) return Math.round(parseFloat(match[1]) * 100);
    }
    if (colorStr.startsWith('rgb')) return 100;
    if (colorStr.startsWith('#') && colorStr.length === 9) {
      return Math.round((parseInt(colorStr.slice(7, 9), 16) / 255) * 100);
    }
    return 100;
  };

  const setShadowOpacity = (colorStr: string, opacityPercent: number): string => {
    const opacity = opacityPercent / 100;
    let r = 0,
      g = 0,
      b = 0;
    if (colorStr.startsWith('rgba') || colorStr.startsWith('rgb')) {
      const match = colorStr.match(/\d+/g);
      if (match && match.length >= 3) {
        r = parseInt(match[0]);
        g = parseInt(match[1]);
        b = parseInt(match[2]);
      }
    } else if (colorStr.startsWith('#')) {
      const hex = colorStr.replace('#', '');
      if (hex.length === 3) {
        r = parseInt(hex[0] + hex[0], 16);
        g = parseInt(hex[1] + hex[1], 16);
        b = parseInt(hex[2] + hex[2], 16);
      } else if (hex.length === 6 || hex.length === 8) {
        r = parseInt(hex.slice(0, 2), 16);
        g = parseInt(hex.slice(2, 4), 16);
        b = parseInt(hex.slice(4, 6), 16);
      }
    }
    return `rgba(${r}, ${g}, ${b}, ${opacity.toFixed(2)})`;
  };

  const handleShadowChange = useCallback(
    (shadowProp: string, value: unknown) => {
      const currentShadow = activeObjectProps?.shadow || {
        color: 'rgba(0, 0, 0, 0.4)',
        blur: 15,
        offsetX: 10,
        offsetY: 10,
      };
      let updatedShadow = { ...currentShadow, [shadowProp]: value };
      if (shadowProp === 'distance' || shadowProp === 'angle') {
        const distance =
          shadowProp === 'distance' ? Number(value) : (currentShadow.distance || 0);
        const angle = shadowProp === 'angle' ? Number(value) : (currentShadow.angle || 45);
        const angleRad = (angle * Math.PI) / 180;
        updatedShadow.offsetX = Math.round(distance * Math.cos(angleRad));
        updatedShadow.offsetY = Math.round(distance * Math.sin(angleRad));
        updatedShadow.distance = distance;
        updatedShadow.angle = angle;
      }
      updateActiveObject({ shadow: updatedShadow });
      scheduleHistoryCommit(`shadow:${activeObjectId}:${shadowProp}`, recordChange, 300);
    },
    [activeObjectId, activeObjectProps?.shadow, recordChange, updateActiveObject],
  );

  const applyShadowPreset = useCallback(
    (type: 'none' | 'soft' | 'medium' | 'hard' | 'glow') => {
      if (type === 'none') {
        updateActiveObject({ shadow: null });
        recordChange();
        return;
      }
      let color = 'rgba(0, 0, 0, 0.15)';
      let blur = 12;
      let distance = 6;
      let angle = 45;

      if (type === 'medium') {
        color = 'rgba(0, 0, 0, 0.30)';
        blur = 20;
        distance = 12;
      } else if (type === 'hard') {
        color = 'rgba(0, 0, 0, 0.45)';
        blur = 10;
        distance = 15;
      } else if (type === 'glow') {
        const baseColor =
          typeof activeObjectProps?.fill === 'string' ? activeObjectProps.fill : '#6366f1';
        color = hexToRgba(rgbaToHex(baseColor), 'rgba(99, 102, 241, 0.50)');
        blur = 25;
        distance = 0;
        angle = 0;
      }

      const angleRad = (angle * Math.PI) / 180;
      updateActiveObject({
        shadow: {
          color,
          blur,
          offsetX: Math.round(distance * Math.cos(angleRad)),
          offsetY: Math.round(distance * Math.sin(angleRad)),
          distance,
          angle,
        },
      });
      recordChange();
    },
    [activeObjectProps?.fill, recordChange, updateActiveObject],
  );

  const handleAlign = useCallback(
    (type: 'left' | 'right' | 'center-h' | 'top' | 'bottom' | 'center-v') => {
      if (!canvas) return;
      const activeObject = canvas.getActiveObject();
      if (!activeObject) return;
      if (activeObject.type === 'activeselection') {
        const selection = activeObject;
        const objects = [...selection.getObjects()];
        const rects = objects.map((o) => o.getBoundingRect());
        const minLeft = Math.min(...rects.map((r) => r.left));
        const maxRight = Math.max(...rects.map((r) => r.left + r.width));
        const minTop = Math.min(...rects.map((r) => r.top));
        const maxBottom = Math.max(...rects.map((r) => r.top + r.height));
        const centerH = minLeft + (maxRight - minLeft) / 2;
        const centerV = minTop + (maxBottom - minTop) / 2;
        canvas.discardActiveObject();
        objects.forEach((obj, idx) => {
          const rect = rects[idx];
          if (type === 'left') obj.left = obj.left - rect.left + minLeft;
          else if (type === 'right') obj.left = obj.left - rect.left + (maxRight - rect.width);
          else if (type === 'center-h') obj.left = obj.left - rect.left + (centerH - rect.width / 2);
          else if (type === 'top') obj.top = obj.top - rect.top + minTop;
          else if (type === 'bottom') obj.top = obj.top - rect.top + (maxBottom - rect.height);
          else if (type === 'center-v') obj.top = obj.top - rect.top + (centerV - rect.height / 2);
          obj.setCoords();
        });
        const newSelection = new ActiveSelection(objects, { canvas });
        canvas.setActiveObject(newSelection);
        canvas.renderAll();
        recordChange();
      } else {
        const rect = activeObject.getBoundingRect();
        if (type === 'center-h') canvas.centerObjectH(activeObject);
        else if (type === 'center-v') canvas.centerObjectV(activeObject);
        else {
          if (type === 'left') activeObject.left = activeObject.left - rect.left;
          else if (type === 'right')
            activeObject.left = activeObject.left - rect.left + (canvas.width - rect.width);
          else if (type === 'top') activeObject.top = activeObject.top - rect.top;
          else if (type === 'bottom')
            activeObject.top = activeObject.top - rect.top + (canvas.height - rect.height);
        }
        activeObject.setCoords();
        canvas.renderAll();
        updateActiveObject({
          left: Math.round(activeObject.left),
          top: Math.round(activeObject.top),
        });
        recordChange();
      }
    },
    [canvas, recordChange, updateActiveObject],
  );

  const moveLayerZ = useCallback(
    (direction: 'up' | 'down' | 'top' | 'bottom') => {
      if (!canvas) return;
      const activeObject = canvas.getActiveObject();
      if (!activeObject || (activeObject as { _isBackground?: boolean })._isBackground) return;
      if (direction === 'up' && canvas.bringForward) canvas.bringForward(activeObject);
      else if (direction === 'down' && canvas.sendBackwards) canvas.sendBackwards(activeObject);
      else if (direction === 'top' && canvas.bringToFront) canvas.bringToFront(activeObject);
      else if (direction === 'bottom' && canvas.sendToBack) canvas.sendToBack(activeObject);
      canvas.renderAll();
      recordChange();
    },
    [canvas, recordChange],
  );

  const shadowMetrics = () => {
    const offsetX = activeObjectProps?.shadow?.offsetX || 0;
    const offsetY = activeObjectProps?.shadow?.offsetY || 0;
    const currentDistance = Math.round(Math.sqrt(offsetX * offsetX + offsetY * offsetY));
    let currentAngle = Math.round(Math.atan2(offsetY, offsetX) * (180 / Math.PI));
    if (currentAngle < 0) currentAngle += 360;
    return { currentDistance, currentAngle };
  };

  return {
    canvas,
    activeObjectId,
    activeObjectProps,
    updateActiveObject,
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
  };
}
