import {
  FabricImage,
  IText,
  Shadow,
  Rect,
  Circle,
  Triangle,
  Line,
  Polygon,
  Path,
} from 'fabric';
import { argbIntToRgba } from '@/lib/template-converter';
import { getCompositeOperation } from '@/lib/blend-mode-utils';
import { arrowPath, getDiamondPoints, getHexagonPoints, getStarPoints } from '@/lib/fabric-shape-utils';
import { ensureFontLoaded } from '@/lib/font-loader';
import { useEditorStore } from '@/store/editor.store';
import { hasRenderableFabricState } from '@/domains/templates/template.helpers';

export interface LoadTemplateOptions {
  canvasInstance: any;
  template: any;
  baseWidth: number;
  baseHeight: number;
  syncLayersFromCanvas: (canvas: any) => void;
  setLoadingLayers: (loading: boolean) => void;
  onLoadedWithoutFabricState?: () => void;
  onLayerLoadError?: (error: string) => void;
}

export async function loadTemplateIntoCanvas(options: LoadTemplateOptions) {
  const { canvasInstance, template, baseWidth, baseHeight, syncLayersFromCanvas, setLoadingLayers, onLoadedWithoutFabricState, onLayerLoadError } = options;
    setLoadingLayers(true);
    let usedFabricState = false;

    const applyShadowIfPresent = (obj: any, payload: any) => {
      if (!payload) return;
      const hasShadow = (payload.shadowDistance !== undefined && payload.shadowDistance > 0) ||
                        (payload.shadowIntensity !== undefined && payload.shadowIntensity > 0.05) ||
                        (payload.shadowBlur !== undefined && payload.shadowBlur > 0);

      if (hasShadow) {
        const distance = payload.shadowDistance || 0;
        const angle = payload.shadowAngle || 45;
        const angleRad = (angle * Math.PI) / 180;
        const offsetX = Math.round(distance * Math.cos(angleRad));
        const offsetY = Math.round(distance * Math.sin(angleRad));

        const { rgba } = argbIntToRgba(payload.shadowColorArgb || -16777216, payload.shadowIntensity !== undefined ? payload.shadowIntensity : 0.4);

        obj.set('shadow', new Shadow({
          color: rgba,
          blur: payload.shadowBlur !== undefined ? payload.shadowBlur : 15,
          offsetX,
          offsetY,
        }));
      }
    };

    // Use saved fabric_state only when it contains drawable objects.
    if (hasRenderableFabricState(template.fabric_state)) {
      try {
        const state = typeof template.fabric_state === 'string'
          ? JSON.parse(template.fabric_state)
          : template.fabric_state;
        
        // Prevent tainted canvas: inject crossOrigin into serialized state objects
        if (state && state.objects) {
          state.objects.forEach((obj: any) => {
            if (obj.type === 'image' || obj.type === 'FabricImage' || obj.src) {
              obj.crossOrigin = 'anonymous';
            }
          });
        }
        
        if (canvasInstance.disposed) return;
        await canvasInstance.loadFromJSON(state);
        if (canvasInstance.disposed) return;
        if (!canvasInstance.backgroundColor) {
          canvasInstance.backgroundColor = '#ffffff';
        }

        // Restore template background image if missing in fabric_state
        const canvasData = template.canvas_data;
        if (!canvasInstance.backgroundImage && canvasData?.canvas?.backgroundUrl) {
          try {
            const bgImg = await FabricImage.fromURL(canvasData.canvas.backgroundUrl, {
              crossOrigin: 'anonymous'
            });
            if (!canvasInstance.disposed) {
              const scaleX = baseWidth / (bgImg.width || baseWidth);
              const scaleY = baseHeight / (bgImg.height || baseHeight);
              const bgScale = Math.max(scaleX, scaleY);
              const scaledWidth = (bgImg.width || baseWidth) * bgScale;
              const scaledHeight = (bgImg.height || baseHeight) * bgScale;

              bgImg.set({
                originX: 'left',
                originY: 'top',
                left: (baseWidth - scaledWidth) / 2,
                top: (baseHeight - scaledHeight) / 2,
                scaleX: bgScale,
                scaleY: bgScale,
                selectable: false,
                evented: false,
                hasControls: false,
                hasBorders: false,
              });
              (bgImg as any)._isBackground = true;
              (bgImg as any).layerId = 'background_layer';
              (bgImg as any).layerName = 'Background';
              canvasInstance.backgroundImage = bgImg;
            }
          } catch (bgErr) {
            console.error('Failed to load background image in fabric_state:', bgErr);
            onLayerLoadError?.(`Không thể tải ảnh nền (từ fabric_state): ${canvasData.canvas.backgroundUrl}`);
          }
        }

        // Load fonts and set padding for all text objects loaded from JSON (including groups)
        if (typeof document !== 'undefined') {
          const processObject = (obj: any) => {
            if ((obj.type === 'i-text' || obj.layerType === 'TEXT') && obj.fontFamily) {
              obj.set({ padding: 20 });
              ensureFontLoaded(obj.fontFamily).then(() => {
                document.fonts.load(`12px "${obj.fontFamily}"`).then(() => {
                  if (typeof obj.initDimensions === 'function') {
                    obj.initDimensions();
                    obj.setCoords();
                  }
                  canvasInstance.renderAll();
                }).catch(() => {});
              });
            } else if (obj.forEachObject) {
              obj.forEachObject(processObject);
            }
          };
          canvasInstance.getObjects().forEach(processObject);
        }

        canvasInstance.renderAll();
        syncLayersFromCanvas(canvasInstance);
        useEditorStore.getState().clearHistory();
        useEditorStore.getState().pushState();
        setLoadingLayers(false);
        usedFabricState = true;
        return;
      } catch (err) {
        if (!canvasInstance.disposed) {
          console.error('Failed to load canvas from fabric_state, falling back to canvas_data:', err);
        }
      }
    }

    const canvasData = template.canvas_data;
    if (!canvasData) {
      setLoadingLayers(false);
      return;
    }

    try {
      // Load Background Image – use setBackgroundImage to avoid duplicate render
      if (canvasData.canvas?.backgroundUrl) {
        if (canvasInstance.disposed) return;
        try {
          const bgImg = await FabricImage.fromURL(canvasData.canvas.backgroundUrl, {
            crossOrigin: 'anonymous'
          });
          if (canvasInstance.disposed) return;

          // Scale to fill the full canvas area
          const scaleX = baseWidth  / (bgImg.width  || baseWidth);
          const scaleY = baseHeight / (bgImg.height || baseHeight);
          const bgScale = Math.max(scaleX, scaleY);
          const scaledWidth = (bgImg.width || baseWidth) * bgScale;
          const scaledHeight = (bgImg.height || baseHeight) * bgScale;

          bgImg.set({
            originX: 'left',
            originY: 'top',
            left: (baseWidth - scaledWidth) / 2,
            top: (baseHeight - scaledHeight) / 2,
            scaleX: bgScale,
            scaleY: bgScale,
            selectable: false,
            evented: false,
            hasControls: false,
            hasBorders: false,
          });
          (bgImg as any)._isBackground = true;
          (bgImg as any).layerId = 'background_layer';
          (bgImg as any).layerName = 'Background';

          // Fabric v7: set backgroundImage directly (setBackgroundImage removed in v7)
          canvasInstance.backgroundImage = bgImg;
          canvasInstance.renderAll();
        } catch (bgErr) {
          console.error('Failed to load canvasData background:', bgErr);
          onLayerLoadError?.(`Không thể tải ảnh nền: ${canvasData.canvas.backgroundUrl}`);
        }
      }

      // Load Layers in zIndex order
      const layers = [...(canvasData.layers || [])].sort((a, b) => a.zIndex - b.zIndex);
      
      for (const layer of layers) {
        const transform = layer.transform;
        const payload = layer.payload;

        const centerX = transform.anchorX * baseWidth;
        const centerY = transform.anchorY * baseHeight;
        const scale = transform.scale;
        const rotation = transform.rotation;
        const isVisible = payload.visible !== false;
        const isLocked = payload.locked === true;
        const isTextLayer =
          layer.type === 'TEXT' ||
          (typeof payload.text === 'string' && payload.text.trim().length > 0);

        if (isTextLayer) {
          const textFill = payload.textColorArgb !== undefined && payload.textColorArgb !== null
            ? argbIntToRgba(payload.textColorArgb).rgba
            : (payload.fill || '#1e1b4b');
          const textObj = new IText(payload.text || 'Text Layer', {
            left: centerX,
            top: centerY,
            originX: 'center',
            originY: 'center',
            fontFamily: payload.font || 'Outfit',
            fontSize: payload.fontSize || 60,
            fill: textFill,
            scaleX: scale,
            scaleY: scale,
            angle: rotation,
            opacity: payload.alpha !== undefined ? payload.alpha : 1.0,
            hasControls: true,
            hasBorders: true,
            fontWeight: payload.fontWeight || 'normal',
            fontStyle: payload.fontStyle || 'normal',
            underline: payload.underline || false,
            textAlign: payload.textAlign || 'left',
            lineHeight: (payload.lineHeight && payload.lineHeight >= 0.85) ? payload.lineHeight : 1.16,
            charSpacing: payload.charSpacing || 0,
            textBackgroundColor: payload.textBackgroundColor || null,
            linethrough: payload.linethrough || false,
            textTransform: payload.textTransform || 'none',
            visible: isVisible,
            selectable: !isLocked,
            evented: !isLocked,
            _originalText: payload._originalText || payload.text || '',
            stroke: payload.stroke || null,
            strokeWidth: payload.strokeWidth || 0,
            strokeDashArray: payload.strokeDashArray || null,
            globalCompositeOperation: getCompositeOperation(payload.blendMode),
            padding: 20,
          });

          (textObj as any).layerId = layer.layerId;
          (textObj as any).layerType = 'TEXT';
          (textObj as any).layerName = layer.name || 'Text';
          (textObj as any).groupPath = payload.groupPath || null;
          (textObj as any).sourceKind = payload.sourceKind || null;
          (textObj as any).blendMode = payload.blendMode || 'normal';
          
          applyShadowIfPresent(textObj, payload);
          canvasInstance.add(textObj);

          if (payload.font && typeof document !== 'undefined') {
            ensureFontLoaded(payload.font).then(() => {
              document.fonts.load(`12px "${payload.font}"`).then(() => {
                if (typeof textObj.initDimensions === 'function') {
                  textObj.initDimensions();
                  textObj.setCoords();
                }
                canvasInstance.renderAll();
              }).catch(() => {});
            });
          }
        } else {
          // Check if it's a vector shape first
          const shapeType = payload.shapeType;
          if (shapeType) {
            try {
              let shapeObj: any;
              const commonShapeProps = {
                left: centerX,
                top: centerY,
                originX: 'center' as const,
                originY: 'center' as const,
                scaleX: scale,
                scaleY: scale,
                angle: rotation,
                opacity: payload.alpha !== undefined ? payload.alpha : 1.0,
                visible: isVisible,
                selectable: !isLocked,
                evented: !isLocked,
                fill: payload.fillColor || '#6366f1',
                hasControls: true,
                hasBorders: true,
                stroke: payload.stroke || null,
                strokeWidth: payload.strokeWidth || 0,
                strokeDashArray: payload.strokeDashArray || null,
                globalCompositeOperation: getCompositeOperation(payload.blendMode),
              };

              if (shapeType === 'rect') {
                shapeObj = new Rect({
                  ...commonShapeProps,
                  width: payload.baseWidth || 200,
                  height: payload.baseHeight || 200,
                  rx: payload.rx || 0,
                  ry: payload.ry || 0,
                });
              } else if (shapeType === 'circle') {
                shapeObj = new Circle({
                  ...commonShapeProps,
                  radius: (payload.baseWidth || 200) / 2,
                });
              } else if (shapeType === 'triangle') {
                shapeObj = new Triangle({
                  ...commonShapeProps,
                  width: payload.baseWidth || 200,
                  height: payload.baseHeight || 200,
                });
              } else if (shapeType === 'line') {
                shapeObj = new Line([-100, 0, 100, 0], {
                  ...commonShapeProps,
                  stroke: payload.fillColor || '#6366f1',
                  strokeWidth: payload.strokeWidth || 6,
                });
              } else if (shapeType === 'star') {
                shapeObj = new Polygon(getStarPoints(100, 40), {
                  ...commonShapeProps,
                  width: payload.baseWidth || 200,
                  height: payload.baseHeight || 200,
                });
              } else if (shapeType === 'diamond') {
                shapeObj = new Polygon(getDiamondPoints(100), {
                  ...commonShapeProps,
                  width: payload.baseWidth || 200,
                  height: payload.baseHeight || 200,
                });
              } else if (shapeType === 'hexagon') {
                shapeObj = new Polygon(getHexagonPoints(100), {
                  ...commonShapeProps,
                  width: payload.baseWidth || 200,
                  height: payload.baseHeight || 200,
                });
              } else if (shapeType === 'arrow') {
                shapeObj = new Path(arrowPath, {
                  ...commonShapeProps,
                  width: payload.baseWidth || 200,
                  height: payload.baseHeight || 120,
                });
              }

              if (shapeObj) {
                shapeObj.layerId = layer.layerId;
                shapeObj.layerType = layer.type || 'DECORATION';
                shapeObj.layerName = layer.name || 'Shape';
                shapeObj.shapeSubtype = shapeType;
                shapeObj.groupPath = payload.groupPath || null;
                shapeObj.sourceKind = payload.sourceKind || null;
                (shapeObj as any).blendMode = payload.blendMode || 'normal';
                applyShadowIfPresent(shapeObj, payload);
                canvasInstance.add(shapeObj);
                continue;
              }
            } catch (err) {
              console.error('Failed to load vector shape layer:', err);
            }
          }

          // Load Image / Decoration
          const imgUrl = payload.imageUrl || payload.defaultImageUrl;
          const baseW = payload.baseWidth ?? 0;
          const baseH = payload.baseHeight ?? 0;
          if (baseW <= 1 && baseH <= 1) {
            continue;
          }
          if (imgUrl) {
            try {
              if (canvasInstance.disposed) return;
              const imgObj = await FabricImage.fromURL(imgUrl, {
                crossOrigin: 'anonymous'
              });
              if (canvasInstance.disposed) return;

                imgObj.set({
                  left: centerX,
                  top: centerY,
                  originX: 'center',
                  originY: 'center',
                  scaleX: scale,
                  scaleY: scale,
                  angle: rotation,
                  opacity: payload.alpha !== undefined ? payload.alpha : 1.0,
                  flipX: payload.flippedH || false,
                  flipY: payload.flippedV || false,
                  visible: isVisible,
                  selectable: !isLocked,
                  evented: !isLocked,
                  hasControls: true,
                  hasBorders: true,
                  stroke: payload.stroke || null,
                  strokeWidth: payload.strokeWidth || 0,
                  strokeDashArray: payload.strokeDashArray || null,
                  globalCompositeOperation: getCompositeOperation(payload.blendMode),
                });

              (imgObj as any).layerId = layer.layerId;
              const isReplaceable =
                layer.type === 'PLACEHOLDER_OBJECT' || payload.replaceable === true;
              (imgObj as any).layerType = layer.type || 'DECORATION';
              (imgObj as any).isReplaceable = isReplaceable;
              (imgObj as any).layerName = layer.name || 'Image';
              (imgObj as any).src = payload.imageUrl;
              (imgObj as any).defaultImageUrl = payload.defaultImageUrl;
              (imgObj as any).cropRatio = payload.cropRatio || null;
              (imgObj as any).groupPath = payload.groupPath || null;
              (imgObj as any).sourceKind = payload.sourceKind || null;
              (imgObj as any).blendMode = payload.blendMode || 'normal';

              // Apply shadow if present
              applyShadowIfPresent(imgObj, payload);

              // Apply image filters if present
              if (payload.imageFilters) {
                if (canvasInstance.disposed) return;
                const { filters } = await import('fabric');
                if (canvasInstance.disposed) return;
                imgObj.filters = [];
                const filterState = payload.imageFilters || {};
                if (filterState.brightness !== undefined && filterState.brightness !== 0) {
                  imgObj.filters.push(new filters.Brightness({ brightness: filterState.brightness }));
                }
                if (filterState.contrast !== undefined && filterState.contrast !== 0) {
                  imgObj.filters.push(new filters.Contrast({ contrast: filterState.contrast }));
                }
                if (filterState.saturation !== undefined && filterState.saturation !== 0) {
                  imgObj.filters.push(new filters.Saturation({ saturation: filterState.saturation }));
                }
                if (filterState.blur !== undefined && filterState.blur !== 0) {
                  imgObj.filters.push(new filters.Blur({ blur: filterState.blur }));
                }
                if (filterState.grayscale) {
                  imgObj.filters.push(new filters.Grayscale());
                }
                if (filterState.sepia) {
                  imgObj.filters.push(new filters.Sepia());
                }
                if (filterState.toneColor) {
                  if (filterState.toneGrayscale) {
                     imgObj.filters.push(new filters.Grayscale());
                  }
                  imgObj.filters.push(new filters.BlendColor({
                    color: filterState.toneColor,
                    mode: 'multiply',
                    alpha: typeof filterState.toneAlpha === 'number' ? filterState.toneAlpha : 1
                  }));
                }
                imgObj.applyFilters();
                (imgObj as any).imageFilters = payload.imageFilters;
              }

              canvasInstance.add(imgObj);
            } catch (imgLoadErr) {
              console.error(`Failed to load image layer: ${imgUrl}`, imgLoadErr);
              onLayerLoadError?.(`Không thể tải layer "${layer.name || 'Ảnh'}" từ liên kết: ${imgUrl}`);
            }
          } else {
            onLayerLoadError?.(`Layer "${layer.name || 'Không tên'}" bị bỏ qua vì thiếu liên kết hình ảnh.`);
          }
        }
      }

      canvasInstance.renderAll();
      syncLayersFromCanvas(canvasInstance);
      useEditorStore.getState().clearHistory();
      useEditorStore.getState().pushState();
    } catch (err) {
      console.error('Error rendering template layers:', err);
    } finally {
      setLoadingLayers(false);
      if (!usedFabricState) {
        onLoadedWithoutFabricState?.();
      }
    }
  };