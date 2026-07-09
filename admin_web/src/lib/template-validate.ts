import { CloudTemplate } from '@/types/cloud-template';
import { validateCloudTemplate } from '@/lib/schema/template-contract';

export interface TemplateValidationResult {
  valid: boolean;
  /** Lỗi — CHẶN publish. */
  errors: string[];
  /** Cảnh báo — hiển thị cho admin, không chặn publish. */
  warnings: string[];
}

/** Bề mặt tối thiểu của Fabric canvas/object mà validation cần. */
interface FabricLikeObject {
  _isBackground?: boolean;
  layerId?: string;
  layerType?: string;
  isReplaceable?: boolean;
}

export interface FabricLikeCanvas {
  getObjects: () => FabricLikeObject[];
}

function isFabricObjectReplaceable(obj: FabricLikeObject): boolean {
  return obj.isReplaceable === true || obj.layerType === 'PLACEHOLDER_OBJECT';
}

function isCloudLayerReplaceable(layer: CloudTemplate['layers'][number]): boolean {
  return layer.type === 'PLACEHOLDER_OBJECT' || layer.payload?.replaceable === true;
}

/**
 * Phát hiện lệch trạng thái giữa Fabric canvas (nguồn của fabric_state) và
 * CloudTemplate (canvas_data mà App đọc) — DATA_PIPELINE_PLAN.md §2.4.
 * So khớp: số layer, tập layerId, và cờ replaceable từng layer.
 * Trả về danh sách warning (không chặn publish nhưng admin cần biết).
 */
export function detectStateDrift(
  fabricCanvas: FabricLikeCanvas | null,
  cloudTemplate: CloudTemplate
): string[] {
  const warnings: string[] = [];
  if (!fabricCanvas) return warnings;

  const objects = fabricCanvas.getObjects().filter((o) => o._isBackground !== true);
  const cloudLayers = cloudTemplate.layers ?? [];

  if (objects.length !== cloudLayers.length) {
    warnings.push(
      `Lệch trạng thái: canvas có ${objects.length} layer nhưng bản publish có ${cloudLayers.length} layer.`
    );
  }

  const fabricIds = new Set(
    objects.map((o) => o.layerId).filter((id): id is string => Boolean(id))
  );
  const cloudIds = new Set(cloudLayers.map((l) => l.layerId));

  for (const id of fabricIds) {
    if (!cloudIds.has(id)) {
      warnings.push(`Lệch trạng thái: layer "${id}" có trên canvas nhưng thiếu trong bản publish.`);
    }
  }
  // Chiều ngược lại chỉ đáng tin khi mọi object trên canvas đều đã có layerId
  // (converter tự sinh id cho object thiếu — so sánh khi đó sẽ báo nhầm).
  if (fabricIds.size === objects.length) {
    for (const id of cloudIds) {
      if (!fabricIds.has(id)) {
        warnings.push(`Lệch trạng thái: layer "${id}" có trong bản publish nhưng không còn trên canvas.`);
      }
    }
  }

  // So khớp cờ replaceable trên các layer khớp id (bug 3-nguồn-sự-thật 07/2026).
  const fabricById = new Map(
    objects.filter((o) => o.layerId).map((o) => [o.layerId as string, o])
  );
  for (const layer of cloudLayers) {
    const fabricObj = fabricById.get(layer.layerId);
    if (!fabricObj) continue;
    const fabricReplaceable = isFabricObjectReplaceable(fabricObj);
    const cloudReplaceable = isCloudLayerReplaceable(layer);
    if (fabricReplaceable !== cloudReplaceable) {
      warnings.push(
        `Lệch trạng thái: cờ "đối tượng thay thế" của layer "${layer.layerId}" khác nhau giữa canvas (${fabricReplaceable}) và bản publish (${cloudReplaceable}). Hãy lưu lại trước khi publish.`
      );
    }
  }

  return warnings;
}

/**
 * Validates template data before publishing to mobile apps.
 *
 * Tầng 1 (Zod contract): cấu trúc + khoảng giá trị — lỗi cấu trúc CHẶN publish,
 * giá trị lệch được clamp kèm warning (xem template-contract.ts).
 * Tầng 2 (business logic): background, layer count, URL, replaceable
 * defaultImageUrl, kích thước layer, và drift fabric_state ↔ canvas_data.
 */
export function validateTemplateForPublish(
  canvas: FabricLikeCanvas | null,
  cloudTemplate: CloudTemplate
): TemplateValidationResult {
  const errors: string[] = [];
  const warnings: string[] = [];

  // --- Tầng 1: Schema contract (Zod) ---
  const contract = validateCloudTemplate(cloudTemplate);
  errors.push(...contract.errors);
  warnings.push(...contract.warnings);

  // --- Tầng 2: Business logic ---
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

    // Layer ảnh với kích thước 0 sẽ vô hình / chia-cho-0 khi App tính scale.
    if (isImageLike && (layer.payload?.baseWidth === 0 || layer.payload?.baseHeight === 0)) {
      errors.push(
        `Layer ảnh (${layer.layerId}) có baseWidth/baseHeight = 0. Hãy xóa hoặc thay layer này trước khi publish.`
      );
    }

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

  // Drift fabric_state ↔ canvas_data (chỉ khi có canvas để so).
  warnings.push(...detectStateDrift(canvas, cloudTemplate));

  return { valid: errors.length === 0, errors, warnings };
}
