import { CloudTemplate } from '@/types/cloud-template';

export interface TemplateValidationResult {
  valid: boolean;
  errors: string[];
}

/**
 * Validates template data before publishing to mobile apps.
 */
export function validateTemplateForPublish(
  canvas: { getObjects: () => Array<{ _isBackground?: boolean }> } | null,
  cloudTemplate: CloudTemplate
): TemplateValidationResult {
  const errors: string[] = [];

  const hasBackground =
    Boolean(cloudTemplate.canvas?.backgroundUrl) ||
    (cloudTemplate.canvas?.backgroundColorArgb !== undefined &&
      cloudTemplate.canvas?.backgroundColorArgb !== null);

  if (!hasBackground) {
    errors.push('Template cần có ảnh nền hoặc màu nền trước khi publish.');
  }

  const fabricObjectCount = canvas
    ? canvas.getObjects().filter((o) => o._isBackground !== true).length
    : 0;
  const cloudLayerCount = cloudTemplate.layers?.length ?? 0;

  if (fabricObjectCount !== cloudLayerCount) {
    errors.push(
      `Số layer không khớp (canvas: ${fabricObjectCount}, mobile: ${cloudLayerCount}). Hãy lưu lại trước khi publish.`
    );
  }

  for (const layer of cloudTemplate.layers || []) {
    const isReplaceable =
      layer.type === 'PLACEHOLDER_OBJECT' || layer.payload?.replaceable === true;
    const url = layer.payload?.imageUrl || layer.payload?.defaultImageUrl;
    const isImageLike =
      layer.type === 'IMAGE' ||
      layer.type === 'PLACEHOLDER_OBJECT' ||
      (layer.type === 'DECORATION' && Boolean(url));

    if (isReplaceable) {
      const defaultUrl = layer.payload?.defaultImageUrl || layer.payload?.imageUrl;
      if (!defaultUrl) {
        errors.push(
          `Đối tượng thay thế (${layer.layerId}) cần có ảnh mặc định (defaultImageUrl) trước khi publish.`
        );
      } else {
        const isValid = defaultUrl.startsWith('http') || defaultUrl.startsWith('/');
        if (!isValid) {
          const preview = defaultUrl.length > 48 ? `${defaultUrl.slice(0, 48)}...` : defaultUrl;
          errors.push(
            `Đối tượng thay thế có URL không hợp lệ (${layer.layerId}): "${preview}". Hãy lưu lại template để upload ảnh lên server.`
          );
        }
      }
      continue;
    }

    if (!url || !isImageLike) continue;
    const isValid = url.startsWith('http') || url.startsWith('/');
    if (!isValid) {
      const preview = url.length > 48 ? `${url.slice(0, 48)}...` : url;
      errors.push(
        `Layer ảnh có URL không hợp lệ (${layer.layerId}): "${preview}". Hãy lưu lại template để upload ảnh lên server.`
      );
    }
  }

  return { valid: errors.length === 0, errors };
}
