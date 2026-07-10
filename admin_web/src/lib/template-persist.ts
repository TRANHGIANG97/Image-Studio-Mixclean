import type { CloudTemplate } from '@/types/cloud-template';
import { fabricToCloudTemplateValidated } from '@/lib/template-converter';
import { detectStateDrift, type FabricLikeCanvas } from '@/lib/template-validate';
import { auditMobileParityFields } from '@/lib/mobile-parity';

export interface PrepareTemplateSaveOptions {
  canvasBaseWidth: number;
  canvasBaseHeight: number;
  templateId: string;
  categoryId: string;
  title: string;
  status: 'draft' | 'published';
  thumbnailUrl?: string | null;
  silent?: boolean;
}

export interface PrepareTemplateSaveResult {
  template: CloudTemplate;
  contractWarnings: string[];
  contractErrors: string[];
  driftWarnings: string[];
  parityGaps: string[];
  layerCountDiff: { fabric: number; cloud: number } | null;
}

function countFabricLayers(canvas: FabricLikeCanvas): number {
  return canvas.getObjects().filter((o) => o._isBackground !== true).length;
}

/**
 * Single persist path: Fabric canvas → validated CloudTemplate + drift/parity audit.
 */
export function prepareTemplateForSave(
  canvas: FabricLikeCanvas,
  options: PrepareTemplateSaveOptions
): PrepareTemplateSaveResult {
  const {
    canvasBaseWidth,
    canvasBaseHeight,
    templateId,
    categoryId,
    title,
    status,
    thumbnailUrl = null,
    silent = false,
  } = options;

  const { template, warnings: contractWarnings, errors: contractErrors } =
    fabricToCloudTemplateValidated(
      canvas,
      canvasBaseWidth,
      canvasBaseHeight,
      templateId,
      categoryId,
      title,
      status,
      thumbnailUrl
    );

  const fabricCount = countFabricLayers(canvas);
  const cloudCount = template.layers?.length ?? 0;
  const layerCountDiff =
    fabricCount !== cloudCount ? { fabric: fabricCount, cloud: cloudCount } : null;

  const driftWarnings = detectStateDrift(canvas, template);
  const parityGaps = auditMobileParityFields(template);

  if (layerCountDiff) {
    console.warn(
      `[template-persist] Layer count diff: fabric=${layerCountDiff.fabric} cloud=${layerCountDiff.cloud}`
    );
  }
  if (driftWarnings.length > 0) {
    console.warn('[template-persist] State drift:', driftWarnings);
  }
  if (parityGaps.length > 0) {
    console.warn('[template-persist] Mobile parity gaps:', parityGaps);
  }
  if (contractWarnings.length > 0) {
    console.warn('[template-persist] Contract warnings:', contractWarnings);
  }
  if (contractErrors.length > 0) {
    console.error('[template-persist] Contract errors:', contractErrors);
  }

  if (silent && (driftWarnings.length > 0 || layerCountDiff)) {
    console.info('[template-persist] Autosave skipped drift log only — caller handles persist gate');
  }

  return {
    template,
    contractWarnings,
    contractErrors,
    driftWarnings,
    parityGaps,
    layerCountDiff,
  };
}
