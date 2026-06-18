/** Infer layer type from Fabric object metadata. */
export function resolveLayerType(obj: any): string {
  if (!obj) return 'DECORATION';
  if (obj.layerType) return obj.layerType;
  if (obj.type === 'image') return 'IMAGE';
  if (obj.type === 'i-text' || obj.type === 'textbox' || obj.type === 'text') return 'TEXT';
  return 'DECORATION';
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
