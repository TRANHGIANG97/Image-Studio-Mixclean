import { describe, expect, it } from 'vitest';
import {
  CURRENT_SCHEMA_VERSION,
  hasRadialFadeGradient,
  validateCloudTemplate,
} from '@/lib/schema/template-contract';

type LayerOverrides = {
  layerId?: string;
  type?: string;
  zIndex?: number;
  transform?: Record<string, unknown>;
  payload?: Record<string, unknown>;
};

function makeLayer(overrides: LayerOverrides = {}) {
  return {
    layerId: overrides.layerId ?? 'layer_1',
    type: overrides.type ?? 'IMAGE',
    zIndex: overrides.zIndex ?? 0,
    transform: {
      anchorX: 0.5,
      anchorY: 0.5,
      scale: 1,
      rotation: 0,
      ...(overrides.transform ?? {}),
    },
    payload: {
      alpha: 1,
      imageUrl: 'https://cdn.example.com/img.webp',
      baseWidth: 500,
      baseHeight: 500,
      blendMode: 'normal',
      visible: true,
      locked: false,
      shadowIntensity: 0,
      shadowAngle: 45,
      shadowDistance: 0,
      shadowBlur: 15,
      ...(overrides.payload ?? {}),
    },
  };
}

function makeTemplate(layers: unknown[] = [makeLayer()]) {
  return {
    templateId: 'tpl_test_1',
    categoryId: 'cat_1',
    metadata: {
      title: 'Test template',
      thumbnailUrl: 'https://cdn.example.com/thumb.webp',
      status: 'draft',
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
    layers,
  };
}

const radialFadeGradient = {
  type: 'radial',
  colorStops: [
    { offset: 0, color: 'rgba(0, 0, 0, 0.6)' },
    { offset: 1, color: 'rgba(0, 0, 0, 0)' },
  ],
  coords: { x1: 0.5, y1: 0.5, r1: 0, x2: 0.5, y2: 0.5, r2: 0.5 },
};

describe('CURRENT_SCHEMA_VERSION', () => {
  it('is the single source of truth pinned at 1', () => {
    expect(CURRENT_SCHEMA_VERSION).toBe(1);
  });
});

describe('validateCloudTemplate', () => {
  it('passes a valid template with no errors or warnings', () => {
    const result = validateCloudTemplate(makeTemplate());
    expect(result.errors).toEqual([]);
    expect(result.warnings).toEqual([]);
    expect(result.data).not.toBeNull();
    expect(result.data?.layers).toHaveLength(1);
    expect(result.data?.metadata.schemaVersion).toBe(CURRENT_SCHEMA_VERSION);
  });

  it('rejects non-object input with an error', () => {
    expect(validateCloudTemplate(null).data).toBeNull();
    expect(validateCloudTemplate(null).errors.length).toBeGreaterThan(0);
    expect(validateCloudTemplate('garbage').data).toBeNull();
    expect(validateCloudTemplate([1, 2]).data).toBeNull();
  });

  it('clamps alpha 2.5 down to 1 with a warning instead of blocking', () => {
    const template = makeTemplate([makeLayer({ payload: { alpha: 2.5 } })]);
    const result = validateCloudTemplate(template);
    expect(result.errors).toEqual([]);
    expect(result.warnings.some((w) => w.includes('alpha'))).toBe(true);
    expect(result.data?.layers[0].payload.alpha).toBe(1);
  });

  it('clamps out-of-range scale and wraps oversized rotation', () => {
    const template = makeTemplate([
      makeLayer({ transform: { scale: 500, rotation: 450 } }),
    ]);
    const result = validateCloudTemplate(template);
    expect(result.errors).toEqual([]);
    expect(result.data?.layers[0].transform.scale).toBe(50);
    // 450° wraps to 90° (same visual result) instead of hard-clamping to 360°.
    expect(result.data?.layers[0].transform.rotation).toBe(90);
    expect(result.warnings).toHaveLength(2);
  });

  it('replaces non-finite numbers with field defaults', () => {
    const template = makeTemplate([makeLayer({ payload: { alpha: NaN } })]);
    const result = validateCloudTemplate(template);
    expect(result.errors).toEqual([]);
    expect(result.data?.layers[0].payload.alpha).toBe(1);
    expect(result.warnings.some((w) => w.includes('alpha'))).toBe(true);
  });

  it('lets unknown layer types pass through with a warning, not a crash', () => {
    const template = makeTemplate([makeLayer({ type: 'FUTURE_TYPE' })]);
    const result = validateCloudTemplate(template);
    expect(result.errors).toEqual([]);
    expect(result.warnings.some((w) => w.includes('FUTURE_TYPE'))).toBe(true);
    expect(result.data?.layers[0].type).toBe('FUTURE_TYPE');
  });

  it('normalizes unknown blend modes to "normal" with a warning', () => {
    const template = makeTemplate([makeLayer({ payload: { blendMode: 'plasma-burn' } })]);
    const result = validateCloudTemplate(template);
    expect(result.errors).toEqual([]);
    expect(result.warnings.some((w) => w.includes('plasma-burn'))).toBe(true);
    expect(result.data?.layers[0].payload.blendMode).toBe('normal');
  });

  it('blocks replaceable layers without defaultImageUrl or imageUrl', () => {
    const template = makeTemplate([
      makeLayer({
        type: 'PLACEHOLDER_OBJECT',
        payload: { imageUrl: null, defaultImageUrl: null, replaceable: true },
      }),
    ]);
    const result = validateCloudTemplate(template);
    expect(result.data).toBeNull();
    expect(result.errors.some((e) => e.includes('layer_1'))).toBe(true);
  });

  it('blocks payload.replaceable === true without image even when type is IMAGE', () => {
    const template = makeTemplate([
      makeLayer({
        type: 'IMAGE',
        payload: { imageUrl: null, defaultImageUrl: null, replaceable: true },
      }),
    ]);
    const result = validateCloudTemplate(template);
    expect(result.data).toBeNull();
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it('warns and auto-fixes shadowIntensity on radial fade-to-transparent gradients', () => {
    const template = makeTemplate([
      makeLayer({
        type: 'SHADOW_REGION',
        payload: {
          shadowIntensity: 0.94,
          fillGradient: radialFadeGradient,
          sourceKind: 'shadow-region',
          shapeType: 'ellipse',
        },
      }),
    ]);
    const result = validateCloudTemplate(template);
    expect(result.errors).toEqual([]);
    expect(result.warnings.some((w) => w.includes('shadowIntensity'))).toBe(true);
    // Double-shadow bug (07/2026): contract auto-fixes instead of relying on app heuristics.
    expect(result.data?.layers[0].payload.shadowIntensity).toBe(0);
  });

  it('warns when SHADOW_REGION is missing sourceKind shadow-region', () => {
    const template = makeTemplate([
      makeLayer({ type: 'SHADOW_REGION', payload: { sourceKind: null } }),
    ]);
    const result = validateCloudTemplate(template);
    expect(result.errors).toEqual([]);
    expect(result.warnings.some((w) => w.includes('sourceKind'))).toBe(true);
  });

  it('blocks structurally broken templates (missing transform)', () => {
    const broken = makeTemplate([{ layerId: 'layer_1', type: 'IMAGE', zIndex: 0, payload: {} }]);
    const result = validateCloudTemplate(broken);
    expect(result.data).toBeNull();
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it('includes layerId context in issue messages', () => {
    const template = makeTemplate([
      makeLayer({ layerId: 'layer_hero', payload: { alpha: 5 } }),
    ]);
    const result = validateCloudTemplate(template);
    expect(result.warnings.some((w) => w.includes('layer_hero'))).toBe(true);
  });

  it('preserves passthrough fields the mobile app does not know about', () => {
    const template = makeTemplate([
      makeLayer({ payload: { _originalText: 'hello', customFutureField: 42 } }),
    ]);
    const result = validateCloudTemplate(template);
    const payload = result.data?.layers[0].payload as Record<string, unknown>;
    expect(payload._originalText).toBe('hello');
    expect(payload.customFutureField).toBe(42);
  });
});

describe('hasRadialFadeGradient', () => {
  it('detects radial gradients fading to transparent', () => {
    expect(hasRadialFadeGradient(radialFadeGradient)).toBe(true);
  });

  it('rejects linear gradients and opaque radial gradients', () => {
    expect(hasRadialFadeGradient({ ...radialFadeGradient, type: 'linear' })).toBe(false);
    expect(
      hasRadialFadeGradient({
        type: 'radial',
        colorStops: [
          { offset: 0, color: 'rgba(0, 0, 0, 0.6)' },
          { offset: 1, color: 'rgba(0, 0, 0, 1)' },
        ],
      })
    ).toBe(false);
    expect(hasRadialFadeGradient(null)).toBe(false);
    expect(hasRadialFadeGradient('radial')).toBe(false);
  });

  it('supports 8-digit hex and stop opacity notation', () => {
    expect(
      hasRadialFadeGradient({
        type: 'radial',
        colorStops: [
          { offset: 0, color: '#000000ff' },
          { offset: 1, color: '#00000000' },
        ],
      })
    ).toBe(true);
    expect(
      hasRadialFadeGradient({
        type: 'radial',
        colorStops: [
          { offset: 0, color: '#000000', opacity: 1 },
          { offset: 1, color: '#000000', opacity: 0 },
        ],
      })
    ).toBe(true);
  });
});
