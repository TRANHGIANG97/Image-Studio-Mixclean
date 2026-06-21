import { CloudTemplate, CloudLayer } from '@/types/cloud-template';
import { arrowPath } from '@/lib/fabric-shape-utils';
import { removeCDN } from '@/lib/cdn-rewriter';
import { isFabricTextObject } from '@/lib/canvas-object-props';

// Helper to calculate GCD for aspect ratio
const gcd = (a: number, b: number): number => (b === 0 ? a : gcd(b, a % b));

/**
 * Converts a hex or rgba color to a 32-bit ARGB integer used by Android.
 * Example: "rgba(0, 0, 0, 0.5)" -> -2147483648
 */
export function colorToArgbInt(colorString: string, opacity: number = 1): number {
  if (!colorString) return -16777216; // Default black ARGB

  let r = 0, g = 0, b = 0, a = opacity;

  if (colorString.startsWith('rgba')) {
    const match = colorString.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)/);
    if (match) {
      r = parseInt(match[1]);
      g = parseInt(match[2]);
      b = parseInt(match[3]);
      a = match[4] !== undefined ? parseFloat(match[4]) : opacity;
    }
  } else if (colorString.startsWith('#')) {
    const hex = colorString.replace('#', '');
    if (hex.length === 3) {
      r = parseInt(hex[0] + hex[0], 16);
      g = parseInt(hex[1] + hex[1], 16);
      b = parseInt(hex[2] + hex[2], 16);
    } else if (hex.length === 6) {
      r = parseInt(hex.slice(0, 2), 16);
      g = parseInt(hex.slice(2, 4), 16);
      b = parseInt(hex.slice(4, 6), 16);
    } else if (hex.length === 8) {
      r = parseInt(hex.slice(0, 2), 16);
      g = parseInt(hex.slice(2, 4), 16);
      b = parseInt(hex.slice(4, 6), 16);
      a = parseInt(hex.slice(6, 8), 16) / 255;
    }
  }

  // Calculate 32-bit ARGB value (signed int)
  const alphaVal = Math.round(a * 255);
  const argb = (alphaVal << 24) | (r << 16) | (g << 8) | b;
  return argb;
}

/**
 * Converts a 32-bit ARGB integer to a standard CSS rgba string.
 */
export function argbIntToRgba(argb: number, opacityOverride?: number): { rgba: string; alpha: number } {
  // Convert signed int to unsigned 32-bit number
  const unsigned = argb >>> 0;
  const a = ((unsigned >> 24) & 0xff) / 255;
  const r = (unsigned >> 16) & 0xff;
  const g = (unsigned >> 8) & 0xff;
  const b = unsigned & 0xff;

  const finalAlpha = opacityOverride !== undefined ? opacityOverride : a;
  return {
    rgba: `rgba(${r}, ${g}, ${b}, ${finalAlpha.toFixed(2)})`,
    alpha: a
  };
}

function fabricPathToSvg(path: unknown): string | null {
  if (typeof path === 'string' && path.trim()) return path;
  if (!Array.isArray(path)) return null;
  return path
    .map((segment) => {
      if (!Array.isArray(segment) || segment.length === 0) return '';
      const [command, ...coords] = segment;
      return `${command} ${coords.join(' ')}`.trim();
    })
    .filter(Boolean)
    .join(' ');
}

function fabricPointsToFlat(points: unknown): number[] | null {
  if (!Array.isArray(points) || points.length === 0) return null;
  const flat: number[] = [];
  for (const point of points) {
    if (point && typeof point === 'object' && 'x' in point && 'y' in point) {
      flat.push(Number((point as { x: number; y: number }).x));
      flat.push(Number((point as { x: number; y: number }).y));
    }
  }
  return flat.length >= 6 ? flat : null;
}

function fabricBackgroundColorToArgb(backgroundColor: any): number | null {
  if (!backgroundColor) return null;
  if (typeof backgroundColor === 'string') return colorToArgbInt(backgroundColor);
  const firstStop = backgroundColor.colorStops?.[0]?.color;
  return typeof firstStop === 'string' ? colorToArgbInt(firstStop) : null;
}

function fabricImageUrl(image: any): string | null {
  if (!image) return null;
  if (typeof image.src === 'string' && image.src.trim()) return image.src;
  if (typeof image.defaultImageUrl === 'string' && image.defaultImageUrl.trim()) return image.defaultImageUrl;
  if (typeof image.getSrc === 'function') {
    const src = image.getSrc();
    if (typeof src === 'string' && src.trim()) return src;
  }
  const elementSrc = image._element?.src || image._originalElement?.src;
  return typeof elementSrc === 'string' && elementSrc.trim() ? elementSrc : null;
}

function getBlendModeFromComposite(gco?: string | null): string {
  if (!gco) return 'normal';
  switch (gco) {
    case 'source-over':
      return 'normal';
    case 'multiply':
      return 'multiply';
    case 'screen':
      return 'screen';
    case 'overlay':
      return 'overlay';
    case 'darken':
      return 'darken';
    case 'lighten':
      return 'lighten';
    case 'color-dodge':
      return 'color-dodge';
    case 'color-burn':
      return 'color-burn';
    case 'hard-light':
      return 'hard-light';
    case 'soft-light':
      return 'soft-light';
    case 'difference':
      return 'difference';
    case 'exclusion':
      return 'exclusion';
    case 'hue':
      return 'hue';
    case 'saturation':
      return 'saturation';
    case 'color':
      return 'color';
    case 'luminosity':
      return 'luminosity';
    case 'lighter':
      return 'linear-dodge';
    default:
      return 'normal';
  }
}

/**
 * Converts a Fabric.js Canvas state into the standard CloudTemplate JSON structure.
 */
export function fabricToCloudTemplate(
  fabricCanvas: any,
  canvasBaseWidth: number,
  canvasBaseHeight: number,
  templateId: string,
  categoryId: string,
  title: string,
  status: 'draft' | 'published' = 'draft',
  thumbnailUrl: string | null = null
): CloudTemplate {
  if (!fabricCanvas) {
    throw new Error('Fabric canvas is not initialized');
  }

  // Get background URL from background image
  const backgroundUrl = fabricImageUrl(fabricCanvas.backgroundImage);
  const backgroundColorArgb = fabricBackgroundColorToArgb(fabricCanvas.backgroundColor);
  const divisor = gcd(canvasBaseWidth, canvasBaseHeight);
  const aspectRatio = `${canvasBaseWidth / divisor}:${canvasBaseHeight / divisor}`;

  // Get layers (excluding background objects)
  const objects = fabricCanvas.getObjects();
  const layers: CloudLayer[] = objects
    .filter((obj: any) => obj._isBackground !== true)
    .map((obj: any, index: number) => {
      // Calculate absolute center of the object (origin independent)
      const originX = obj.originX || 'left';
      const originY = obj.originY || 'top';
      
      const width = obj.width * (obj.scaleX || 1);
      const height = obj.height * (obj.scaleY || 1);

      let centerX = obj.left;
      let centerY = obj.top;

      if (originX !== 'center') {
        centerX = obj.left + width / 2;
      }
      if (originY !== 'center') {
        centerY = obj.top + height / 2;
      }

      // Convert absolute center to relative coordinates (0.0 to 1.0)
      const anchorX = centerX / canvasBaseWidth;
      const anchorY = centerY / canvasBaseHeight;

      // Extract shadow parameters
      let shadowIntensity = 0;
      let shadowAngle = 45;
      let shadowDistance = 0;
      let shadowColorArgb = colorToArgbInt('rgba(0, 0, 0, 0.4)');
      let shadowBlur = 15;

      if (obj.shadow) {
        const shadow = obj.shadow;
        const offsetX = shadow.offsetX || 0;
        const offsetY = shadow.offsetY || 0;
        
        shadowDistance = Math.sqrt(offsetX * offsetX + offsetY * offsetY);
        // Angle in degrees from radians
        shadowAngle = Math.atan2(offsetY, offsetX) * (180 / Math.PI);
        if (shadowAngle < 0) shadowAngle += 360;

        shadowColorArgb = colorToArgbInt(shadow.color || 'rgba(0, 0, 0, 0.4)');
        shadowBlur = shadow.blur || 15;
        
        // Extract opacity from shadow color
        if (shadow.color) {
          const colorStr = shadow.color.trim();
          if (colorStr.startsWith('rgba')) {
            const match = colorStr.match(/rgba?\((?:\d+,\s*){3}([\d.]+)\)/);
            if (match) shadowIntensity = parseFloat(match[1]);
          } else if (colorStr.startsWith('rgb')) {
            shadowIntensity = 1.0;
          } else if (colorStr.startsWith('#')) {
            const hex = colorStr.replace('#', '');
            if (hex.length === 8) {
              shadowIntensity = parseFloat((parseInt(hex.slice(6, 8), 16) / 255).toFixed(2));
            } else {
              shadowIntensity = 1.0;
            }
          } else {
            shadowIntensity = 1.0;
          }
        } else {
          shadowIntensity = 0.4; // Default fallback if no color
        }
      }

      const layerId = obj.layerId || `layer_${Date.now()}_${index}`;
      let layerType = obj.layerType || 'DECORATION';
      const isReplaceable =
        obj.isReplaceable === true || layerType === 'PLACEHOLDER_OBJECT';
      const isShadowRegion =
        obj.isShadowRegion === true ||
        obj.sourceKind === 'shadow-region' ||
        layerType === 'SHADOW_REGION';
      if (isShadowRegion) {
        layerType = 'SHADOW_REGION';
      } else if (isFabricTextObject(obj)) {
        layerType = 'TEXT';
      }

      let strokeColorStr = null;
      if (typeof obj.stroke === 'string') {
        strokeColorStr = obj.stroke;
      } else if (obj.stroke && typeof obj.stroke === 'object' && obj.stroke.colorStops?.[0]?.color) {
        strokeColorStr = obj.stroke.colorStops[0].color;
      }

      const payload: any = {
        alpha: obj.opacity !== undefined ? obj.opacity : 1,
        flippedH: obj.flipX || false,
        flippedV: obj.flipY || false,
        visible: obj.visible !== false,
        locked: obj.lockMovementX === true || obj.lockMovementY === true || obj.selectable === false,
        groupPath: obj.groupPath || null,
        sourceKind: isShadowRegion ? 'shadow-region' : (obj.sourceKind || null),
        shadowIntensity,
        shadowAngle,
        shadowDistance,
        shadowColorArgb,
        shadowBlur,
        stroke: strokeColorStr,
        strokeWidth: obj.strokeWidth || 0,
        strokeDashArray: obj.strokeDashArray || null,
        blendMode: obj.blendMode || getBlendModeFromComposite(obj.globalCompositeOperation),
        replaceable: isReplaceable ? true : undefined,
      };

      if (layerType === 'TEXT') {
        payload.text = obj.text || '';
        payload.font = obj.fontFamily || 'sans-serif';
        payload.fontSize = obj.fontSize || 60;
        
        let textColorStr = '#ffffff';
        if (typeof obj.fill === 'string') {
          textColorStr = obj.fill;
        } else if (obj.fill && typeof obj.fill === 'object' && obj.fill.colorStops?.[0]?.color) {
          textColorStr = obj.fill.colorStops[0].color;
        }
        payload.textColorArgb = colorToArgbInt(textColorStr);

        payload.fontWeight = obj.fontWeight || 'normal';
        payload.fontStyle = obj.fontStyle || 'normal';
        payload.underline = obj.underline || false;
        payload.textAlign = obj.textAlign || 'left';
        payload.lineHeight = obj.lineHeight || 1.16;
        payload.charSpacing = obj.charSpacing || 0;
        payload.textBackgroundColor = obj.textBackgroundColor || null;
        payload.linethrough = obj.linethrough || false;
        payload.textTransform = obj.textTransform || 'none';
        payload._originalText = obj._originalText || obj.text || '';
        payload.baseWidth = Math.round(obj.width * (obj.scaleX || 1));
        payload.baseHeight = Math.round(obj.height * (obj.scaleY || 1));
        
        if (obj.fill && typeof obj.fill === 'object') {
          payload.textColorGradient = {
            type: obj.fill.type,
            colorStops: obj.fill.colorStops,
            coords: obj.fill.coords,
          };
        }
      } else {
        payload.imageUrl = obj.src || null;
        payload.defaultImageUrl = obj.defaultImageUrl || null;
        payload.baseWidth = Math.round(obj.width);
        payload.baseHeight = Math.round(obj.height);
        if (obj.cropRatio) {
          payload.cropRatio = obj.cropRatio;
        }
        if (obj.imageFilters) {
          payload.imageFilters = obj.imageFilters;
        }
        
        // Support vector shapes serialization
        const isShape = ['rect', 'circle', 'triangle', 'line', 'polygon', 'path', 'ellipse'].includes(obj.type) || obj.shapeSubtype;
        if (isShape || isShadowRegion) {
          const shapeType = obj.shapeSubtype || (isShadowRegion ? 'ellipse' : obj.type);
          payload.shapeType = shapeType;
          
          let fillColorStr = '#6366f1';
          if (typeof obj.fill === 'string') {
            fillColorStr = obj.fill;
          } else if (obj.fill && typeof obj.fill === 'object' && obj.fill.colorStops?.[0]?.color) {
            fillColorStr = obj.fill.colorStops[0].color;
          }
          if (shapeType === 'line') {
            fillColorStr = typeof obj.stroke === 'string' ? obj.stroke : fillColorStr;
            payload.strokeWidth = obj.strokeWidth ?? 6;
          }
          payload.fillColor = fillColorStr;
          
          if (obj.fill && typeof obj.fill === 'object') {
            payload.fillGradient = {
              type: obj.fill.type,
              colorStops: obj.fill.colorStops,
              coords: obj.fill.coords,
            };
          }

          if (shapeType === 'arrow' || obj.type === 'path') {
            payload.pathData = fabricPathToSvg(obj.path) ?? (shapeType === 'arrow' ? arrowPath : null);
          }
          if (obj.type === 'polygon' || shapeType === 'polygon') {
            payload.polygonPoints = fabricPointsToFlat(obj.points);
          }
          
          if (obj.type === 'rect' || obj.shapeSubtype === 'rect') {
            payload.rx = obj.rx || 0;
            payload.ry = obj.ry || 0;
          }
        }
      }

      return {
        layerId,
        type: layerType,
        zIndex: index,
        transform: {
          anchorX,
          anchorY,
          scale: obj.scaleX || 1.0,
          rotation: obj.angle || 0.0,
        },
        payload,
      };
    })
    .filter((layer, index, layers) => {
      const obj = objects.filter((o: any) => o._isBackground !== true)[index];
      if (!obj) return true;
      if (layer.type === 'TEXT' || layer.type === 'SHADOW_REGION') return true;
      const isShape =
        ['rect', 'circle', 'triangle', 'line', 'polygon', 'path', 'ellipse'].includes(obj.type) ||
        obj.shapeSubtype ||
        layer.payload.shapeType;
      if (isShape) return true;
      if (layer.payload.imageUrl || layer.payload.defaultImageUrl) return true;
      const w = Math.round(obj.width * (obj.scaleX || 1));
      const h = Math.round(obj.height * (obj.scaleY || 1));
      return !(w <= 1 && h <= 1);
    });

  return removeCDN({
    templateId,
    categoryId,
    metadata: {
      title,
      thumbnailUrl: thumbnailUrl || backgroundUrl || '',
      status,
      schemaVersion: 1,
      createdAt: Date.now(), // Fallbacks
      updatedAt: Date.now(),
    },
    canvas: {
      baseWidth: canvasBaseWidth,
      baseHeight: canvasBaseHeight,
      aspectRatio,
      backgroundUrl,
      backgroundColorArgb,
    },
    layers,
  }) as CloudTemplate;
}
