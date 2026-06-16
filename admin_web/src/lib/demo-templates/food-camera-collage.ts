import { CloudLayer, CloudTemplate } from '@/types/cloud-template';

const TEMPLATE_ID = 'demo_food_camera_collage_v1';
const TEMPLATE_TITLE = 'Delicious Collage';
const CANVAS_WIDTH = 1080;
const CANVAS_HEIGHT = 1920;
const ASSET_BASE = '/demo-assets/food-camera-collage';
const BACKGROUND_URL = 'https://dummyimage.com/1080x1920/090f24/090f24.png';

const PHOTO_PLATE = 'https://images.unsplash.com/photo-1498837167922-ddd27525d352?auto=format&fit=crop&w=1200&q=80';
const PHOTO_PORTRAIT = 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=1200&q=80';
const PHOTO_GRID = 'https://images.unsplash.com/photo-1547592180-85f173990554?auto=format&fit=crop&w=1600&q=80';
const PHOTO_TABLE = 'https://images.unsplash.com/photo-1504674900247-0877df9cc836?auto=format&fit=crop&w=1600&q=80';

function gcd(a: number, b: number): number {
  return b === 0 ? a : gcd(b, a % b);
}

function assetUrl(origin: string, fileName: string): string {
  return new URL(`${ASSET_BASE}/${fileName}`, origin).toString();
}

function commonImagePayload(options: {
  imageUrl: string;
  baseWidth: number;
  baseHeight: number;
  alpha?: number;
  cropRatio?: string | null;
  shadowIntensity?: number;
  shadowAngle?: number;
  shadowDistance?: number;
  shadowColorArgb?: number;
  flippedH?: boolean;
  flippedV?: boolean;
}) {
  return {
    defaultImageUrl: options.imageUrl,
    imageUrl: options.imageUrl,
    alpha: options.alpha ?? 1,
    shadowIntensity: options.shadowIntensity ?? 0.18,
    shadowAngle: options.shadowAngle ?? 120,
    shadowDistance: options.shadowDistance ?? 16,
    shadowColorArgb: options.shadowColorArgb ?? 0x66000000,
    cropRatio: options.cropRatio ?? null,
    flippedH: options.flippedH ?? false,
    flippedV: options.flippedV ?? false,
    baseWidth: options.baseWidth,
    baseHeight: options.baseHeight,
  };
}

function createImageLayer(params: {
  layerId: string;
  type?: 'IMAGE' | 'DECORATION' | 'PLACEHOLDER_OBJECT';
  zIndex: number;
  anchorX: number;
  anchorY: number;
  scale: number;
  rotation?: number;
  imageUrl: string;
  baseWidth: number;
  baseHeight: number;
  alpha?: number;
  cropRatio?: string | null;
  shadowIntensity?: number;
  shadowAngle?: number;
  shadowDistance?: number;
  shadowColorArgb?: number;
}): CloudLayer {
  return {
    layerId: params.layerId,
    type: params.type || 'IMAGE',
    zIndex: params.zIndex,
    transform: {
      anchorX: params.anchorX,
      anchorY: params.anchorY,
      scale: params.scale,
      rotation: params.rotation ?? 0,
    },
    payload: commonImagePayload({
      imageUrl: params.imageUrl,
      baseWidth: params.baseWidth,
      baseHeight: params.baseHeight,
      alpha: params.alpha,
      cropRatio: params.cropRatio,
      shadowIntensity: params.shadowIntensity,
      shadowAngle: params.shadowAngle,
      shadowDistance: params.shadowDistance,
      shadowColorArgb: params.shadowColorArgb,
    }),
  };
}

function createTextLayer(params: {
  layerId: string;
  zIndex: number;
  anchorX: number;
  anchorY: number;
  scale: number;
  rotation?: number;
  text: string;
  font?: string;
  alpha?: number;
  shadowIntensity?: number;
  shadowAngle?: number;
  shadowDistance?: number;
  shadowColorArgb?: number;
}): CloudLayer {
  return {
    layerId: params.layerId,
    type: 'TEXT',
    zIndex: params.zIndex,
    transform: {
      anchorX: params.anchorX,
      anchorY: params.anchorY,
      scale: params.scale,
      rotation: params.rotation ?? 0,
    },
    payload: {
      text: params.text,
      font: params.font || 'cursive',
      alpha: params.alpha ?? 1,
      shadowIntensity: params.shadowIntensity ?? 0.22,
      shadowAngle: params.shadowAngle ?? 120,
      shadowDistance: params.shadowDistance ?? 8,
      shadowColorArgb: params.shadowColorArgb ?? 0x55330000,
    },
  };
}

export function buildFoodCameraCollageTemplate({
  origin,
  categoryId,
  templateId = TEMPLATE_ID,
}: {
  origin: string;
  categoryId: string;
  templateId?: string;
}): CloudTemplate {
  const divisor = gcd(CANVAS_WIDTH, CANVAS_HEIGHT);
  const aspectRatio = `${CANVAS_WIDTH / divisor}:${CANVAS_HEIGHT / divisor}`;

  const backgroundUrl = BACKGROUND_URL;
  const thumbnailUrl = assetUrl(origin, 'preview.svg');
  const arrowUrl = assetUrl(origin, 'arrow.svg');
  const sparklesUrl = assetUrl(origin, 'sparkles.svg');
  const boardUrl = assetUrl(origin, 'board.svg');

  const layers: CloudLayer[] = [
    createImageLayer({
      layerId: 'white_board',
      zIndex: 0,
      anchorX: 0.5,
      anchorY: 0.5,
      scale: 0.95,
      rotation: 0,
      imageUrl: boardUrl,
      baseWidth: 1080,
      baseHeight: 1920,
      alpha: 1,
      shadowIntensity: 0.22,
      shadowAngle: 90,
      shadowDistance: 28,
    }),
    createImageLayer({
      layerId: 'top_left_food_peek',
      zIndex: 1,
      anchorX: 0.15,
      anchorY: 0.22,
      scale: 0.98,
      rotation: -18,
      imageUrl: PHOTO_TABLE,
      baseWidth: 1600,
      baseHeight: 1200,
      cropRatio: '4:3',
      shadowIntensity: 0.18,
      shadowDistance: 16,
    }),
    createImageLayer({
      layerId: 'top_center_food_peek',
      zIndex: 2,
      anchorX: 0.43,
      anchorY: 0.10,
      scale: 0.78,
      rotation: 6,
      imageUrl: PHOTO_PLATE,
      baseWidth: 1200,
      baseHeight: 1200,
      cropRatio: '1:1',
      shadowIntensity: 0.15,
      shadowDistance: 12,
    }),
    createImageLayer({
      layerId: 'center_portrait',
      zIndex: 3,
      anchorX: 0.53,
      anchorY: 0.24,
      scale: 0.86,
      rotation: 0,
      imageUrl: PHOTO_PORTRAIT,
      baseWidth: 1200,
      baseHeight: 1500,
      cropRatio: '4:5',
      shadowIntensity: 0.24,
      shadowAngle: 135,
      shadowDistance: 18,
    }),
    createImageLayer({
      layerId: 'middle_food_card',
      zIndex: 4,
      anchorX: 0.50,
      anchorY: 0.53,
      scale: 0.92,
      rotation: -11,
      imageUrl: PHOTO_GRID,
      baseWidth: 1600,
      baseHeight: 1200,
      cropRatio: '4:3',
      shadowIntensity: 0.26,
      shadowAngle: 135,
      shadowDistance: 18,
    }),
    createImageLayer({
      layerId: 'arrow_doodle',
      type: 'DECORATION',
      zIndex: 5,
      anchorX: 0.47,
      anchorY: 0.62,
      scale: 0.76,
      rotation: 0,
      imageUrl: arrowUrl,
      baseWidth: 900,
      baseHeight: 520,
      alpha: 1,
      shadowIntensity: 0,
      shadowDistance: 0,
    }),
    createImageLayer({
      layerId: 'sparkles_left',
      type: 'DECORATION',
      zIndex: 6,
      anchorX: 0.18,
      anchorY: 0.71,
      scale: 0.52,
      rotation: 0,
      imageUrl: sparklesUrl,
      baseWidth: 400,
      baseHeight: 400,
      alpha: 1,
      shadowIntensity: 0,
      shadowDistance: 0,
    }),
    createImageLayer({
      layerId: 'bottom_left_plate',
      zIndex: 7,
      anchorX: 0.28,
      anchorY: 0.80,
      scale: 0.92,
      rotation: -4,
      imageUrl: PHOTO_PLATE,
      baseWidth: 1200,
      baseHeight: 1200,
      cropRatio: '1:1',
      shadowIntensity: 0.2,
      shadowDistance: 16,
    }),
    createTextLayer({
      layerId: 'delicious_text',
      zIndex: 8,
      anchorX: 0.57,
      anchorY: 0.63,
      scale: 0.92,
      rotation: -8,
      text: 'delicious',
      font: 'cursive',
      alpha: 1,
      shadowIntensity: 0.12,
      shadowDistance: 5,
      shadowColorArgb: 0x55ffde59,
    }),
    createImageLayer({
      layerId: 'bottom_right_food_strip',
      zIndex: 9,
      anchorX: 0.76,
      anchorY: 0.84,
      scale: 0.86,
      rotation: 7,
      imageUrl: PHOTO_GRID,
      baseWidth: 1600,
      baseHeight: 1200,
      cropRatio: '4:3',
      shadowIntensity: 0.18,
      shadowDistance: 14,
    }),
  ];

  return {
    templateId,
    categoryId,
    metadata: {
      title: TEMPLATE_TITLE,
      thumbnailUrl,
      status: 'published',
      schemaVersion: 1,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    },
    canvas: {
      baseWidth: CANVAS_WIDTH,
      baseHeight: CANVAS_HEIGHT,
      aspectRatio,
      backgroundUrl,
    },
    layers,
  };
}

export const foodCameraCollageTemplateId = TEMPLATE_ID;
