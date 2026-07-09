import { describe, expect, it } from 'vitest';
import { detectStateDrift, validateTemplateForPublish } from '@/lib/template-validate';
import { CURRENT_SCHEMA_VERSION } from '@/lib/schema/template-contract';
import type { CloudTemplate } from '@/types/cloud-template';

type LayerOverrides = {
  layerId?: string;
  type?: string;
  payload?: Record<string, unknown>;
};

function makeLayer(overrides: LayerOverrides = {}) {
  return {
    layerId: overrides.layerId ?? 'layer_1',
    type: overrides.type ?? 'IMAGE',
    zIndex: 0,
    transform: { anchorX: 0.5, anchorY: 0.5, scale: 1, rotation: 0 },
    payload: {
      alpha: 1,
      imageUrl: 'https://cdn.example.com/img.webp',
      baseWidth: 500,
      baseHeight: 500,
      blendMode: 'normal',
      ...(overrides.payload ?? {}),
    },
  };
}

function makeTemplate(layers: ReturnType<typeof makeLayer>[] = [makeLayer()]): CloudTemplate {
  return {
    templateId: 'tpl_test_1',
    categoryId: 'cat_1',
    metadata: {
      title: 'Test template',
      thumbnailUrl: 'https://cdn.example.com/thumb.webp',
      status: 'published',
      schemaVersion: CURRENT_SCHEMA_VERSION,
      createdAt: 1720000000000,
      updatedAt: 1720000000000,
    },
    canvas: {
      baseWidth: 1080,
      baseHeight: 1920,
      aspectRatio: '9:16',
      backgroundUrl: 'https://cdn.example.com/bg.webp',
      backgroundColorArgb: null,
    },
    layers: layers as CloudTemplate['layers'],
  };
}

type FabricObjectStub = {
  _isBackground?: boolean;
  layerId?: string;
  layerType?: string;
  isReplaceable?: boolean;
};

function makeCanvas(objects: FabricObjectStub[]) {
  return { getObjects: () => objects };
}

describe('validateTemplateForPublish', () => {
  it('accepts a valid template with a matching canvas', () => {
    const template = makeTemplate();
    const canvas = makeCanvas([{ layerId: 'layer_1', layerType: 'IMAGE' }]);
    const result = validateTemplateForPublish(canvas, template);
    expect(result.valid).toBe(true);
    expect(result.errors).toEqual([]);
    expect(result.warnings).toEqual([]);
  });

  it('still blocks templates without a background (tier 2 preserved)', () => {
    const template = makeTemplate();
    template.canvas.backgroundUrl = null;
    template.canvas.backgroundColorArgb = null;
    const canvas = makeCanvas([{ layerId: 'layer_1' }]);
    const result = validateTemplateForPublish(canvas, template);
    expect(result.valid).toBe(false);
    expect(result.errors.some((e) => e.includes('nền'))).toBe(true);
  });

  it('blocks structural contract violations (tier 1)', () => {
    const template = makeTemplate();
    // Simulate corrupted persisted data: transform is gone entirely.
    delete (template.layers[0] as unknown as Record<string, unknown>).transform;
    const canvas = makeCanvas([{ layerId: 'layer_1' }]);
    const result = validateTemplateForPublish(canvas, template);
    expect(result.valid).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it('surfaces contract clamping as warnings without blocking publish', () => {
    const template = makeTemplate([makeLayer({ payload: { alpha: 3 } })]);
    const canvas = makeCanvas([{ layerId: 'layer_1' }]);
    const result = validateTemplateForPublish(canvas, template);
    expect(result.valid).toBe(true);
    expect(result.warnings.some((w) => w.includes('alpha'))).toBe(true);
  });

  it('blocks replaceable layers missing defaultImageUrl (tier 1 + tier 2)', () => {
    const template = makeTemplate([
      makeLayer({
        type: 'PLACEHOLDER_OBJECT',
        payload: { imageUrl: null, defaultImageUrl: null, replaceable: true },
      }),
    ]);
    const canvas = makeCanvas([{ layerId: 'layer_1', layerType: 'PLACEHOLDER_OBJECT' }]);
    const result = validateTemplateForPublish(canvas, template);
    expect(result.valid).toBe(false);
    expect(result.errors.some((e) => e.includes('layer_1'))).toBe(true);
  });

  it('blocks replaceable layers with non-uploaded (data:) URLs', () => {
    const template = makeTemplate([
      makeLayer({
        type: 'PLACEHOLDER_OBJECT',
        payload: { imageUrl: null, defaultImageUrl: 'data:image/png;base64,AAAA', replaceable: true },
      }),
    ]);
    const canvas = makeCanvas([{ layerId: 'layer_1' }]);
    const result = validateTemplateForPublish(canvas, template);
    expect(result.valid).toBe(false);
    expect(result.errors.some((e) => e.includes('URL không hợp lệ'))).toBe(true);
  });

  it('blocks image-like layers with baseWidth or baseHeight of 0', () => {
    const template = makeTemplate([
      makeLayer({ payload: { baseWidth: 0, baseHeight: 500 } }),
    ]);
    const canvas = makeCanvas([{ layerId: 'layer_1' }]);
    const result = validateTemplateForPublish(canvas, template);
    expect(result.valid).toBe(false);
    expect(result.errors.some((e) => e.includes('baseWidth/baseHeight'))).toBe(true);
  });

  it('blocks when canvas layer count differs from cloud layer count', () => {
    const template = makeTemplate();
    const canvas = makeCanvas([{ layerId: 'layer_1' }, { layerId: 'layer_2' }]);
    const result = validateTemplateForPublish(canvas, template);
    expect(result.valid).toBe(false);
    expect(result.errors.some((e) => e.includes('Số layer không khớp'))).toBe(true);
  });

  it('warns about replaceable-flag drift between canvas and publish data', () => {
    const template = makeTemplate([makeLayer({ type: 'IMAGE' })]);
    const canvas = makeCanvas([
      { layerId: 'layer_1', layerType: 'IMAGE', isReplaceable: true },
    ]);
    const result = validateTemplateForPublish(canvas, template);
    expect(result.warnings.some((w) => w.includes('đối tượng thay thế'))).toBe(true);
  });

  it('works without a canvas (API-only validation) when layers are empty', () => {
    const template = makeTemplate([]);
    const result = validateTemplateForPublish(null, template);
    expect(result.valid).toBe(true);
  });
});

describe('detectStateDrift', () => {
  it('returns no warnings when canvas and cloud template match', () => {
    const template = makeTemplate([
      makeLayer({ layerId: 'a' }),
      makeLayer({ layerId: 'b', type: 'PLACEHOLDER_OBJECT', payload: { replaceable: true } }),
    ]);
    const canvas = makeCanvas([
      { layerId: 'a', layerType: 'IMAGE' },
      { layerId: 'b', layerType: 'PLACEHOLDER_OBJECT' },
    ]);
    expect(detectStateDrift(canvas, template)).toEqual([]);
  });

  it('returns no warnings without a canvas', () => {
    expect(detectStateDrift(null, makeTemplate())).toEqual([]);
  });

  it('warns on layer count mismatch', () => {
    const template = makeTemplate([makeLayer({ layerId: 'a' })]);
    const canvas = makeCanvas([{ layerId: 'a' }, { layerId: 'b' }]);
    const warnings = detectStateDrift(canvas, template);
    expect(warnings.some((w) => w.includes('2') && w.includes('1'))).toBe(true);
  });

  it('warns on layerId set mismatch in both directions', () => {
    const template = makeTemplate([makeLayer({ layerId: 'cloud_only' })]);
    const canvas = makeCanvas([{ layerId: 'canvas_only' }]);
    const warnings = detectStateDrift(canvas, template);
    expect(warnings.some((w) => w.includes('canvas_only'))).toBe(true);
    expect(warnings.some((w) => w.includes('cloud_only'))).toBe(true);
  });

  it('skips cloud-to-canvas id comparison when canvas objects lack layerIds', () => {
    const template = makeTemplate([makeLayer({ layerId: 'generated_later' })]);
    const canvas = makeCanvas([{}]);
    const warnings = detectStateDrift(canvas, template);
    expect(warnings.some((w) => w.includes('generated_later'))).toBe(false);
  });

  it('warns when replaceable flags disagree for the same layerId', () => {
    const template = makeTemplate([
      makeLayer({ layerId: 'a', type: 'PLACEHOLDER_OBJECT', payload: { replaceable: true } }),
    ]);
    const canvas = makeCanvas([{ layerId: 'a', layerType: 'IMAGE' }]);
    const warnings = detectStateDrift(canvas, template);
    expect(warnings.some((w) => w.includes('"a"'))).toBe(true);
  });

  it('ignores background objects on the canvas', () => {
    const template = makeTemplate([makeLayer({ layerId: 'a' })]);
    const canvas = makeCanvas([
      { _isBackground: true },
      { layerId: 'a', layerType: 'IMAGE' },
    ]);
    expect(detectStateDrift(canvas, template)).toEqual([]);
  });
});
