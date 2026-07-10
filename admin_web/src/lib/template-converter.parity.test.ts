import { describe, expect, it } from 'vitest';
import {
  colorToArgbInt,
  extractShadowParams,
  extractTransform,
  fabricToCloudTemplateValidated,
} from '@/lib/template-converter';
import { validateCloudTemplate, CURRENT_SCHEMA_VERSION } from '@/lib/schema/template-contract';
import { auditMobileParityFields } from '@/lib/mobile-parity';
import { prepareTemplateForSave } from '@/lib/template-persist';
import { arrowPath } from '@/lib/fabric-shape-utils';
import {
  GROUND_SHADOW_FIXTURE,
  PARITY_FIXTURE,
  REPLACEABLE_IMAGE_FIXTURE,
  TRACKING_FIXTURE,
} from '@/lib/fixtures/parity-fixtures';
import type { CloudTemplate } from '@/types/cloud-template';

type FabricStub = {
  _isBackground?: boolean;
  layerId?: string;
  layerType?: string;
  type?: string;
  shapeSubtype?: string;
  left: number;
  top: number;
  width: number;
  height: number;
  scaleX?: number;
  scaleY?: number;
  angle?: number;
  originX?: string;
  originY?: string;
  opacity?: number;
  fill?: string | { type?: string; colorStops?: Array<{ offset?: number; color: string }>; coords?: unknown };
  stroke?: string;
  strokeWidth?: number;
  shadow?: { offsetX?: number; offsetY?: number; color?: string; blur?: number };
  blendMode?: string;
  globalCompositeOperation?: string;
  isReplaceable?: boolean;
  isShadowRegion?: boolean;
  sourceKind?: string;
  src?: string;
  defaultImageUrl?: string;
  cropRatio?: string;
  cropOffsetX?: number;
  cropOffsetY?: number;
  path?: string;
  points?: Array<{ x: number; y: number }>;
  rx?: number;
  ry?: number;
  text?: string;
  fontSize?: number;
  charSpacing?: number;
};

function makeCanvas(objects: FabricStub[]) {
  return {
    getObjects: () => objects,
    backgroundImage: null,
    backgroundColor: '#ffffff',
  };
}

function makeTemplateMeta(overrides: Partial<CloudTemplate> = {}): CloudTemplate {
  return {
    templateId: 'tpl_test',
    categoryId: 'cat_1',
    metadata: {
      title: 'Test',
      thumbnailUrl: '',
      status: 'draft',
      schemaVersion: CURRENT_SCHEMA_VERSION,
      createdAt: 1,
      updatedAt: 1,
    },
    canvas: {
      baseWidth: 1080,
      baseHeight: 1920,
      aspectRatio: '9:16',
      backgroundUrl: null,
      backgroundColorArgb: null,
    },
    layers: [],
    ...overrides,
  } as CloudTemplate;
}

function fabricShape(
  layerId: string,
  shapeSubtype: string,
  opts: Partial<FabricStub> = {}
): FabricStub {
  return {
    layerId,
    type: shapeSubtype === 'line' ? 'line' : shapeSubtype === 'polygon' ? 'polygon' : 'rect',
    shapeSubtype,
    left: 540,
    top: 960,
    width: 200,
    height: 120,
    scaleX: 1,
    scaleY: 1,
    originX: 'center',
    originY: 'center',
    fill: '#6366f1',
    ...opts,
  };
}

describe('template-converter pure functions', () => {
  it('colorToArgbInt converts rgba and hex', () => {
    expect(colorToArgbInt('#ff0000')).toBe(-65536);
    const rgba = colorToArgbInt('rgba(0, 0, 0, 0.5)');
    expect(rgba).toBeLessThan(0);
  });

  it('extractTransform maps center to anchor 0-1', () => {
    const t = extractTransform(
      {
        left: 540,
        top: 960,
        width: 200,
        height: 100,
        scaleX: 1,
        scaleY: 1,
        originX: 'center',
        originY: 'center',
        angle: 15,
      },
      1080,
      1920
    );
    expect(t.anchorX).toBeCloseTo(0.5, 2);
    expect(t.anchorY).toBeCloseTo(0.5, 2);
    expect(t.rotation).toBe(15);
  });

  it('extractShadowParams reads fabric shadow', () => {
    const s = extractShadowParams({
      width: 100,
      height: 100,
      left: 0,
      top: 0,
      shadow: { offsetX: 10, offsetY: 0, color: 'rgba(0,0,0,0.4)', blur: 22 },
    });
    expect(s.shadowBlur).toBe(22);
    expect(s.shadowDistance).toBeCloseTo(10, 1);
    expect(s.shadowIntensity).toBeCloseTo(0.4, 2);
  });
});

describe('parity fixtures validate against contract', () => {
  const fixtures = [
    ['PARITY_FIXTURE', PARITY_FIXTURE],
    ['GROUND_SHADOW_FIXTURE', GROUND_SHADOW_FIXTURE],
    ['REPLACEABLE_IMAGE_FIXTURE', REPLACEABLE_IMAGE_FIXTURE],
    ['TRACKING_FIXTURE', TRACKING_FIXTURE],
  ] as const;

  for (const [name, fixture] of fixtures) {
    it(`${name} passes validateCloudTemplate`, () => {
      const full = {
        ...fixture,
        metadata: {
          title: name,
          thumbnailUrl: '',
          status: 'published' as const,
          schemaVersion: CURRENT_SCHEMA_VERSION,
          createdAt: 1,
          updatedAt: 1,
        },
      };
      const result = validateCloudTemplate(full);
      expect(result.errors).toEqual([]);
      expect(result.data).not.toBeNull();
    });
  }

  it('PARITY_FIXTURE audit flags junk 1x1 decoration', () => {
    const template = makeTemplateMeta({
      templateId: PARITY_FIXTURE.templateId,
      categoryId: PARITY_FIXTURE.categoryId,
      canvas: { ...PARITY_FIXTURE.canvas, backgroundUrl: null },
      layers: PARITY_FIXTURE.layers as CloudTemplate['layers'],
    });
    const gaps = auditMobileParityFields(template);
    expect(gaps.some((g) => g.includes('junk_1x1_image'))).toBe(true);
  });

  it('TRACKING_FIXTURE has charSpacing for mobile', () => {
    const template = makeTemplateMeta({
      templateId: TRACKING_FIXTURE.templateId,
      layers: TRACKING_FIXTURE.layers as CloudTemplate['layers'],
    });
    const gaps = auditMobileParityFields(template);
    expect(gaps.filter((g) => g.includes('charSpacing'))).toEqual([]);
    expect(template.layers[0].payload.charSpacing).toBe(200);
  });
});

describe('fabricToCloudTemplateValidated shape coverage', () => {
  const baseArgs = {
    canvasBaseWidth: 1080,
    canvasBaseHeight: 1920,
    templateId: 'shape_cov',
    categoryId: 'cat',
    title: 'Shape coverage',
    status: 'draft' as const,
  };

  const shapeCases: Array<{ id: string; stub: FabricStub; expectedShape: string }> = [
    { id: 's_rect', stub: fabricShape('s_rect', 'rect'), expectedShape: 'rect' },
    { id: 's_circle', stub: fabricShape('s_circle', 'circle', { type: 'circle' }), expectedShape: 'circle' },
    { id: 's_triangle', stub: fabricShape('s_triangle', 'triangle', { type: 'triangle' }), expectedShape: 'triangle' },
    {
      id: 's_line',
      stub: fabricShape('s_line', 'line', {
        type: 'line',
        stroke: '#000',
        strokeWidth: 8,
        fill: '#6366f1',
      }),
      expectedShape: 'line',
    },
    {
      id: 's_arrow',
      stub: fabricShape('s_arrow', 'arrow', {
        type: 'path',
        path: arrowPath,
      }),
      expectedShape: 'arrow',
    },
    {
      id: 's_polygon',
      stub: fabricShape('s_polygon', 'polygon', {
        type: 'polygon',
        points: [
          { x: 0, y: 0 },
          { x: 100, y: 0 },
          { x: 50, y: 80 },
        ],
      }),
      expectedShape: 'polygon',
    },
    {
      id: 's_ellipse',
      stub: fabricShape('s_ellipse', 'ellipse', { type: 'ellipse' }),
      expectedShape: 'ellipse',
    },
  ];

  for (const { id, stub, expectedShape } of shapeCases) {
    it(`maps ${expectedShape} shapeType`, () => {
      const { template } = fabricToCloudTemplateValidated(makeCanvas([stub]), ...Object.values(baseArgs));
      const layer = template.layers.find((l) => l.layerId === id);
      expect(layer?.payload.shapeType).toBe(expectedShape);
    });
  }

  it('maps gradient rect with blendMode and shadow', () => {
    const stub = fabricShape('shape_gradient_rect', 'rect', {
      left: 540,
      top: 768,
      width: 320,
      height: 180,
      angle: 15,
      fill: {
        type: 'linear',
        colorStops: [
          { offset: 0, color: '#ff0000' },
          { offset: 1, color: '#0000ff' },
        ],
        coords: { x1: 0, y1: 0, x2: 1, y2: 1 },
      },
      blendMode: 'multiply',
      shadow: { offsetX: 8.5, offsetY: 8.5, color: 'rgba(0,0,0,0.4)', blur: 22 },
    });
    const { template } = fabricToCloudTemplateValidated(makeCanvas([stub]), ...Object.values(baseArgs));
    const layer = template.layers[0];
    expect(layer.payload.shapeType).toBe('rect');
    expect(layer.payload.blendMode).toBe('multiply');
    expect(layer.payload.shadowBlur).toBe(22);
    expect((layer.payload as { fillGradient?: unknown }).fillGradient).toBeTruthy();
  });

  it('maps TEXT layer with charSpacing', () => {
    const stub: FabricStub = {
      layerId: 'tracking_text',
      layerType: 'TEXT',
      type: 'i-text',
      left: 500,
      top: 500,
      width: 400,
      height: 120,
      scaleX: 1,
      scaleY: 1,
      originX: 'center',
      originY: 'center',
      text: 'TEST',
      fontSize: 120,
      charSpacing: 200,
      fill: '#ffffff',
    };
    const { template } = fabricToCloudTemplateValidated(makeCanvas([stub]), ...Object.values(baseArgs));
    expect(template.layers[0].type).toBe('TEXT');
    expect(template.layers[0].payload.charSpacing).toBe(200);
  });

  it('maps PLACEHOLDER_OBJECT replaceable product', () => {
    const stub: FabricStub = {
      layerId: 'layer_product',
      layerType: 'PLACEHOLDER_OBJECT',
      isReplaceable: true,
      type: 'image',
      left: 540,
      top: 1056,
      width: 320,
      height: 320,
      scaleX: 1,
      scaleY: 1,
      originX: 'center',
      originY: 'center',
      src: 'https://example.com/product.png',
      shadow: { offsetX: 8.5, offsetY: 8.5, color: 'rgba(0,0,0,0.3)', blur: 15 },
    };
    const { template } = fabricToCloudTemplateValidated(makeCanvas([stub]), ...Object.values(baseArgs));
    expect(template.layers[0].type).toBe('PLACEHOLDER_OBJECT');
    expect(template.layers[0].payload.replaceable).toBe(true);
  });

  it('exports cropOffsetX/Y on image layers', () => {
    const stub: FabricStub = {
      layerId: 'img_crop',
      type: 'image',
      left: 540,
      top: 960,
      width: 400,
      height: 400,
      scaleX: 1,
      scaleY: 1,
      originX: 'center',
      originY: 'center',
      src: 'https://example.com/img.png',
      cropRatio: 'RATIO_1_1',
      cropOffsetX: 12,
      cropOffsetY: -8,
    };
    const { template } = fabricToCloudTemplateValidated(makeCanvas([stub]), ...Object.values(baseArgs));
    expect(template.layers[0].payload.cropRatio).toBe('RATIO_1_1');
    expect(template.layers[0].payload.cropOffsetX).toBe(12);
    expect(template.layers[0].payload.cropOffsetY).toBe(-8);
  });

  it('filters junk 1x1 decoration layers on export', () => {
    const stub: FabricStub = {
      layerId: 'junk_1x1_image',
      type: 'image',
      left: 540,
      top: 1824,
      width: 1,
      height: 1,
      scaleX: 1,
      scaleY: 1,
      originX: 'center',
      originY: 'center',
      src: 'https://example.com/1x1.png',
    };
    const { template } = fabricToCloudTemplateValidated(makeCanvas([stub]), ...Object.values(baseArgs));
    expect(template.layers).toHaveLength(0);
  });
});

describe('prepareTemplateForSave', () => {
  it('reports layer count diff when converter filters layers', () => {
    const canvas = makeCanvas([
      {
        layerId: 'keep',
        type: 'rect',
        shapeSubtype: 'rect',
        left: 540,
        top: 960,
        width: 100,
        height: 100,
        scaleX: 1,
        scaleY: 1,
        originX: 'center',
        originY: 'center',
        fill: '#fff',
      },
      {
        layerId: 'junk',
        type: 'image',
        left: 540,
        top: 960,
        width: 1,
        height: 1,
        scaleX: 1,
        scaleY: 1,
        originX: 'center',
        originY: 'center',
        src: 'https://example.com/x.png',
      },
    ]);

    const result = prepareTemplateForSave(canvas, {
      canvasBaseWidth: 1080,
      canvasBaseHeight: 1920,
      templateId: 't1',
      categoryId: 'c1',
      title: 'T',
      status: 'draft',
      silent: true,
    });

    expect(result.layerCountDiff).toEqual({ fabric: 2, cloud: 1 });
    expect(result.template.layers).toHaveLength(1);
  });
});
