/**
 * Helper utilities for template operations.
 */

import type { CloudTemplate } from '@/types/cloud-template';

export type TemplatePublishEnvironment = 'debug' | 'release' | 'all';

/** Resolve publish environment from DB column, falling back to canvas metadata. */
export function resolveTemplateEnvironment(
  environment?: TemplatePublishEnvironment | null,
  canvasData?: CloudTemplate | null
): TemplatePublishEnvironment | undefined {
  return environment ?? canvasData?.metadata?.environment;
}

/** True when a template is published to the debug app channel. */
export function isTemplateDebugPublished(
  status: 'draft' | 'published',
  environment?: TemplatePublishEnvironment | null,
  canvasData?: CloudTemplate | null
): boolean {
  if (status !== 'published') return false;
  return resolveTemplateEnvironment(environment, canvasData) === 'debug';
}

/** Keep canvas metadata status/environment aligned with top-level template fields. */
export function syncCanvasMetadata(
  canvasData: CloudTemplate,
  updates: { status?: 'draft' | 'published'; environment?: TemplatePublishEnvironment }
): CloudTemplate {
  if (!canvasData.metadata) return canvasData;

  return {
    ...canvasData,
    metadata: {
      ...canvasData.metadata,
      ...(updates.status !== undefined && { status: updates.status }),
      ...(updates.environment !== undefined && { environment: updates.environment }),
      updatedAt: Date.now(),
    },
  };
}

/** Calculate GCD for aspect ratio. */
export function gcd(a: number, b: number): number {
  return b === 0 ? a : gcd(b, a % b);
}

/** Build a minimal Fabric.js state for new templates. */
export function buildInitialFabricState(backgroundUrl?: string | null) {
  return {
    version: '7.4.0',
    objects: [],
    background: backgroundUrl ? '#ffffff' : '#ffffff',
  };
}

/**
 * Return true when a Fabric state contains actual drawable objects.
 * Empty bootstrap states are used during PSD import and should fall back to canvas_data.
 */
export function hasRenderableFabricState(fabricState: unknown): boolean {
  if (!fabricState) return false;

  try {
    const state =
      typeof fabricState === 'string'
        ? JSON.parse(fabricState)
        : fabricState as { objects?: unknown[] };

    return Array.isArray(state?.objects) && state.objects.length > 0;
  } catch {
    return false;
  }
}

function generateLayerId(): string {
  return `layer_${Date.now()}_${Math.random().toString(36).slice(2, 11)}`;
}

/**
 * Remap layer IDs when cloning a template so the copy does not share IDs with the source.
 */
export function remapClonedTemplateIds(
  canvasData: CloudTemplate,
  fabricState: unknown,
  newTemplateId: string
): { canvasData: CloudTemplate; fabricState: unknown | null } {
  const cloned = JSON.parse(JSON.stringify(canvasData)) as CloudTemplate;
  cloned.templateId = newTemplateId;

  const idMap = new Map<string, string>();
  for (const layer of cloned.layers || []) {
    const newId = generateLayerId();
    idMap.set(layer.layerId, newId);
    layer.layerId = newId;
  }

  if (!fabricState) {
    return { canvasData: cloned, fabricState: null };
  }

  const fs = JSON.parse(JSON.stringify(fabricState)) as { objects?: Array<{ layerId?: string }> };
  if (fs.objects) {
    for (const obj of fs.objects) {
      if (!obj.layerId || obj.layerId === 'background_layer') continue;
      obj.layerId = idMap.get(obj.layerId) ?? generateLayerId();
    }
  }

  return { canvasData: cloned, fabricState: fs };
}
