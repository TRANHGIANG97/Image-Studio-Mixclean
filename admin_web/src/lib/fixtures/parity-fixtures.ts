/** Shared fixtures mirroring studio_edit CloudTemplateParityTest.kt */
export const PARITY_FIXTURE = {
  templateId: 'parity_tpl',
  categoryId: 'cat_shapes',
  canvas: { baseWidth: 1080, baseHeight: 1920, aspectRatio: '9:16' },
  layers: [
    {
      layerId: 'shape_gradient_rect',
      type: 'DECORATION',
      zIndex: 0,
      transform: { anchorX: 0.5, anchorY: 0.4, scale: 1, rotation: 15 },
      payload: {
        shapeType: 'rect',
        baseWidth: 320,
        baseHeight: 180,
        fillColor: '#ff0000',
        fillGradient: {
          type: 'linear',
          colorStops: [
            { offset: 0, color: '#ff0000' },
            { offset: 1, color: '#0000ff' },
          ],
          coords: { x1: 0, y1: 0, x2: 1, y2: 1 },
        },
        shadowIntensity: 0.4,
        shadowAngle: 45,
        shadowDistance: 12,
        shadowBlur: 22,
        shadowColorArgb: -872415232,
        blendMode: 'multiply',
      },
    },
    {
      layerId: 'shape_triangle',
      type: 'DECORATION',
      zIndex: 1,
      transform: { anchorX: 0.3, anchorY: 0.5, scale: 1, rotation: 0 },
      payload: { shapeType: 'triangle', baseWidth: 200, baseHeight: 200, fillColor: '#22c55e' },
    },
    {
      layerId: 'shape_line',
      type: 'DECORATION',
      zIndex: 2,
      transform: { anchorX: 0.7, anchorY: 0.5, scale: 1, rotation: 0 },
      payload: {
        shapeType: 'line',
        baseWidth: 240,
        baseHeight: 40,
        fillColor: '#6366f1',
        strokeWidth: 8,
      },
    },
    {
      layerId: 'shape_arrow',
      type: 'DECORATION',
      zIndex: 3,
      transform: { anchorX: 0.5, anchorY: 0.65, scale: 1, rotation: 0 },
      payload: {
        shapeType: 'arrow',
        baseWidth: 200,
        baseHeight: 120,
        fillColor: '#f59e0b',
        pathData: 'M -100 -20 L 20 -20 L 20 -60 L 100 0 L 20 60 L 20 20 L -100 20 Z',
      },
    },
    {
      layerId: 'text_gradient',
      type: 'TEXT',
      zIndex: 4,
      transform: { anchorX: 0.5, anchorY: 0.8, scale: 1, rotation: 0 },
      payload: {
        text: 'SALE',
        fontSize: 72,
        textColorArgb: -1,
        baseWidth: 300,
        baseHeight: 120,
        shapeType: 'pill',
        fillColor: '#e11d48',
        textColorGradient: {
          type: 'linear',
          colorStops: [
            { offset: 0, color: '#ffffff' },
            { offset: 1, color: '#fde68a' },
          ],
          coords: { x1: 0, y1: 0, x2: 0, y2: 1 },
        },
        shadowBlur: 18,
        shadowIntensity: 0.5,
        shadowDistance: 10,
        shadowAngle: 90,
      },
    },
    {
      layerId: 'decoration_text_fallback',
      type: 'DECORATION',
      zIndex: 5,
      transform: { anchorX: 0.5, anchorY: 0.9, scale: 1, rotation: 0 },
      payload: {
        text: 'Hello',
        fontSize: 48,
        textColorArgb: -1,
        baseWidth: 200,
        baseHeight: 80,
      },
    },
    {
      layerId: 'junk_1x1_image',
      type: 'DECORATION',
      zIndex: 6,
      transform: { anchorX: 0.5, anchorY: 0.95, scale: 1, rotation: 0 },
      payload: {
        imageUrl: 'https://example.com/1x1.png',
        baseWidth: 1,
        baseHeight: 1,
      },
    },
  ],
} as const;

export const GROUND_SHADOW_FIXTURE = {
  templateId: 'ground_shadow_tpl',
  categoryId: 'cat',
  canvas: { baseWidth: 1080, baseHeight: 1920, aspectRatio: '9:16' },
  layers: [
    {
      layerId: 'layer_ground_shadow',
      type: 'DECORATION',
      zIndex: 0,
      transform: { anchorX: 0.5, anchorY: 0.72, scale: 1, rotation: 0 },
      payload: {
        shapeType: 'ellipse',
        baseWidth: 420,
        baseHeight: 90,
        fillColor: 'rgba(0,0,0,0.28)',
        fillGradient: {
          type: 'radial',
          colorStops: [
            { offset: 0, color: 'rgba(0,0,0,0.28)' },
            { offset: 0.55, color: 'rgba(0,0,0,0.16)' },
            { offset: 1, color: 'rgba(0,0,0,0)' },
          ],
          coords: { x1: 0.5, y1: 0.5, r1: 0, x2: 0.5, y2: 0.5, r2: 0.5 },
        },
        shadowIntensity: 0.94,
        shadowAngle: 45,
        shadowDistance: 12,
        shadowBlur: 15,
        alpha: 0.95,
      },
    },
    {
      layerId: 'layer_product',
      type: 'PLACEHOLDER_OBJECT',
      zIndex: 1,
      transform: { anchorX: 0.5, anchorY: 0.55, scale: 1, rotation: 0 },
      payload: {
        imageUrl: 'https://example.com/product.png',
        baseWidth: 320,
        baseHeight: 320,
        shadowIntensity: 0.3,
        shadowAngle: 45,
        shadowDistance: 12,
        replaceable: true,
      },
    },
  ],
} as const;

export const REPLACEABLE_IMAGE_FIXTURE = {
  templateId: 'replaceable_image_tpl',
  categoryId: 'cat',
  canvas: { baseWidth: 1080, baseHeight: 1920, aspectRatio: '9:16' },
  layers: [
    {
      layerId: 'layer_replaceable_image',
      type: 'IMAGE',
      zIndex: 0,
      transform: { anchorX: 0.5, anchorY: 0.5, scale: 1, rotation: 0 },
      payload: {
        imageUrl: 'https://example.com/sample.png',
        defaultImageUrl: 'https://example.com/sample.png',
        baseWidth: 400,
        baseHeight: 400,
        replaceable: true,
      },
    },
  ],
} as const;

export const TRACKING_FIXTURE = {
  templateId: 'tracking_tpl',
  categoryId: 'cat_text',
  canvas: { baseWidth: 1000, baseHeight: 1000, aspectRatio: '1:1' },
  layers: [
    {
      layerId: 'tracking_text',
      type: 'TEXT',
      zIndex: 0,
      transform: { anchorX: 0.5, anchorY: 0.5, scale: 1, rotation: 0 },
      payload: {
        text: 'TEST',
        fontSize: 120,
        charSpacing: 200,
        baseWidth: 400,
        baseHeight: 120,
      },
    },
  ],
} as const;
