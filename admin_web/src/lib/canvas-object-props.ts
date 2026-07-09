/** True only when shadow metadata was set explicitly (e.g. "Tạo vùng bóng"). */
export function isFabricShadowRegion(obj: any): boolean {
  if (!obj) return false;
  return (
    obj.isShadowRegion === true ||
    obj.sourceKind === 'shadow-region' ||
    obj.layerType === 'SHADOW_REGION'
  );
}

export function isFabricTextObject(obj: any): boolean {
  if (!obj) return false;
  if (obj.type === 'i-text' || obj.type === 'textbox' || obj.type === 'text') return true;
  if (typeof obj.text === 'string' && obj.text.trim().length > 0) return true;
  return false;
}

export function resolveLayerType(obj: any): string {
  if (!obj) return 'DECORATION';
  if (isFabricShadowRegion(obj)) return 'SHADOW_REGION';
  if (isFabricTextObject(obj)) return 'TEXT';
  if (obj.layerType === 'PLACEHOLDER_OBJECT' || obj.isReplaceable === true) {
    return 'PLACEHOLDER_OBJECT';
  }
  if (obj.layerType) return obj.layerType;
  if (obj.type === 'image') return 'IMAGE';
  return 'DECORATION';
}

/** Image layer that can be swapped on Android studio_edit. */
export function isFabricReplaceableImage(obj: any): boolean {
  if (!obj) return false;
  return (
    obj.layerType === 'PLACEHOLDER_OBJECT' ||
    obj.isReplaceable === true ||
    (obj.type === 'image' && obj.layerType === 'IMAGE' && obj.isReplaceable === true)
  );
}

/** Fabric image-like layer (product photo, placeholder, or plain image). */
export function isFabricImageLayer(obj: any): boolean {
  if (!obj) return false;
  return (
    obj.type === 'image' ||
    obj.layerType === 'IMAGE' ||
    obj.layerType === 'PLACEHOLDER_OBJECT' ||
    obj.isReplaceable === true
  );
}

/** Snapshot Fabric object fields used by PropertiesPanel. */
export function extractActiveObjectProps(activeObj: any): Record<string, unknown> | null {
  if (!activeObj) return null;

  return {
    left: Math.round(activeObj.left),
    top: Math.round(activeObj.top),
    scaleX: activeObj.scaleX,
    scaleY: activeObj.scaleY,
    angle: activeObj.angle,
    opacity: activeObj.opacity,
    flipX: activeObj.flipX,
    flipY: activeObj.flipY,
    layerId: activeObj.layerId,
    layerType: resolveLayerType(activeObj),
    layerName: activeObj.layerName,
    isReplaceable: activeObj.isReplaceable === true || activeObj.layerType === 'PLACEHOLDER_OBJECT',
    text: activeObj.text,
    fontFamily: activeObj.fontFamily,
    src: activeObj.src,
    defaultImageUrl: activeObj.defaultImageUrl,
    cropRatio: activeObj.cropRatio || null,
    fill: activeObj.fill || null,
    fontSize: activeObj.fontSize || null,
    fontWeight: activeObj.fontWeight || 'normal',
    fontStyle: activeObj.fontStyle || 'normal',
    underline: activeObj.underline || false,
    textAlign: activeObj.textAlign || 'left',
    lineHeight: activeObj.lineHeight || 1.16,
    charSpacing: activeObj.charSpacing || 0,
    rx: activeObj.rx || 0,
    ry: activeObj.ry || 0,
    stroke: activeObj.stroke || null,
    strokeWidth: activeObj.strokeWidth || 0,
    strokeDashArray: activeObj.strokeDashArray || null,
    imageFilters: activeObj.imageFilters || {},
    textBackgroundColor: activeObj.textBackgroundColor || null,
    linethrough: activeObj.linethrough || false,
    textTransform: activeObj.textTransform || 'none',
    _originalText: activeObj._originalText || activeObj.text || '',
    blendMode: activeObj.blendMode || 'normal',
    globalCompositeOperation: activeObj.globalCompositeOperation || 'source-over',
    shadow: activeObj.shadow
      ? {
          color: activeObj.shadow.color,
          blur: activeObj.shadow.blur,
          offsetX: activeObj.shadow.offsetX,
          offsetY: activeObj.shadow.offsetY,
        }
      : null,
  };
}
