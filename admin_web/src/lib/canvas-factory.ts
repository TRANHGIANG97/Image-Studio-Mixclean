import {
  FabricImage,
  IText,
  Rect,
  Circle,
  Triangle,
  Line,
  Polygon,
  Path,
  Shadow,
  Point,
} from 'fabric';
import { toast } from 'sonner';
import { arrowPath, getDiamondPoints, getHexagonPoints, getStarPoints } from '@/lib/fabric-shape-utils';
import { extractActiveObjectProps } from '@/lib/canvas-object-props';
import type { TypographyPreset } from '@/lib/typography-presets';

export type ShapeSubtype =
  | 'rect'
  | 'circle'
  | 'triangle'
  | 'line'
  | 'star'
  | 'arrow'
  | 'diamond'
  | 'hexagon';

import type { DroppedAsset } from '@/lib/canvas-upload';
export type { DroppedAsset };
export {
  isDroppableImageFile,
  uploadCanvasImageFile,
  ensureRemoteImageUrl,
  getDroppedImageFiles,
  uploadInlineImageUrl,
  isInlineImageUrl,
} from '@/lib/canvas-upload';

export function canvasCoordsFromDropEvent(
  event: { clientX: number; clientY: number },
  canvasElement: HTMLCanvasElement,
  fabricCanvas: any,
  fallbackWidth: number,
  fallbackHeight: number,
): { x: number; y: number } {
  const rect = canvasElement.getBoundingClientRect();
  const canvasWidth = fabricCanvas.getWidth?.() || fallbackWidth;
  const canvasHeight = fabricCanvas.getHeight?.() || fallbackHeight;
  if (!rect.width || !rect.height) {
    return { x: canvasWidth / 2, y: canvasHeight / 2 };
  }
  return {
    x: ((event.clientX - rect.left) / rect.width) * canvasWidth,
    y: ((event.clientY - rect.top) / rect.height) * canvasHeight,
  };
}

export interface SidebarAsset {
  name: string;
  folder: string;
  file_url: string;
}

export interface CanvasCommitHandlers {
  onCommit: (canvas: any, activeObject?: any) => void;
}

export interface LayerSelectionHandlers {
  setActiveObjectId: (id: string) => void;
  setActiveObjectProps: (props: Record<string, unknown> | null) => void;
}

const SHAPE_NAMES: Record<ShapeSubtype, string> = {
  rect: 'Hình chữ nhật',
  circle: 'Hình tròn',
  triangle: 'Hình tam giác',
  line: 'Đường thẳng',
  star: 'Hình ngôi sao',
  diamond: 'Hình thoi',
  hexagon: 'Hình lục giác',
  arrow: 'Hình mũi tên',
};

export function createLayerId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `layer_${crypto.randomUUID()}`;
  }
  return `layer_${Math.random().toString(36).substring(2, 15)}`;
}

export function createTextObject(baseWidth: number, baseHeight: number): IText {
  const textObj = new IText('Nhập chữ...', {
    left: baseWidth / 2,
    top: baseHeight / 2,
    originX: 'center',
    originY: 'center',
    fontFamily: 'Outfit, sans-serif',
    fontSize: 80,
    fill: '#6366f1',
    hasControls: true,
    hasBorders: true,
    selectable: true,
    fontWeight: 'normal',
    fontStyle: 'normal',
    underline: false,
    textAlign: 'left',
    lineHeight: 1.16,
    charSpacing: 0,
    padding: 20,
    objectCaching: false,
  });

  (textObj as any).layerId = createLayerId();
  (textObj as any).layerType = 'TEXT';
  (textObj as any).layerName = 'Text Layer';
  return textObj;
}

export function createShapeObject(
  type: ShapeSubtype,
  baseWidth: number,
  baseHeight: number
): FabricObjectLike {
  const commonProps = {
    left: baseWidth / 2,
    top: baseHeight / 2,
    originX: 'center' as const,
    originY: 'center' as const,
    fill: '#6366f1',
    hasControls: true,
    hasBorders: true,
    selectable: true,
  };

  let shape: any;
  if (type === 'rect') {
    shape = new Rect({ ...commonProps, width: 200, height: 200 });
  } else if (type === 'circle') {
    shape = new Circle({ ...commonProps, radius: 100 });
  } else if (type === 'triangle') {
    shape = new Triangle({ ...commonProps, width: 200, height: 200 });
  } else if (type === 'line') {
    shape = new Line([-100, 0, 100, 0], {
      ...commonProps,
      stroke: '#6366f1',
      strokeWidth: 6,
    });
  } else if (type === 'star') {
    shape = new Polygon(getStarPoints(100, 40), { ...commonProps, width: 200, height: 200 });
  } else if (type === 'diamond') {
    shape = new Polygon(getDiamondPoints(100), { ...commonProps, width: 200, height: 200 });
  } else if (type === 'hexagon') {
    shape = new Polygon(getHexagonPoints(100), { ...commonProps, width: 200, height: 200 });
  } else {
    shape = new Path(arrowPath, { ...commonProps, width: 200, height: 120 });
  }

  shape.layerId = createLayerId();
  shape.layerType = 'DECORATION';
  shape.shapeSubtype = type;
  shape.layerName = `${SHAPE_NAMES[type] || 'Shape'} Layer`;
  return shape;
}

type FabricObjectLike = any;

function fitImageScale(
  img: FabricObjectLike,
  baseWidth: number,
  baseHeight: number,
  maxFraction: number
): void {
  if (img.width && img.width > baseWidth * maxFraction) {
    img.scaleToWidth(baseWidth * maxFraction);
  } else if (img.height && img.height > baseHeight * maxFraction) {
    img.scaleToHeight(baseHeight * maxFraction);
  }
}

async function loadImageWithMeta(
  url: string,
  position: { left: number; top: number },
  meta: {
    layerId: string;
    layerType: string;
    layerName: string;
  }
): Promise<FabricObjectLike> {
  const img = await FabricImage.fromURL(url, { crossOrigin: 'anonymous' });
  img.set({
    left: position.left,
    top: position.top,
    originX: 'center',
    originY: 'center',
  });
  (img as any).layerId = meta.layerId;
  (img as any).layerType = meta.layerType;
  (img as any).layerName = meta.layerName;
  (img as any).src = url;
  (img as any).defaultImageUrl = url;
  return img;
}

function addObjectToCanvas(canvas: any, obj: FabricObjectLike, handlers: CanvasCommitHandlers): void {
  canvas.add(obj);
  handlers.onCommit(canvas, obj);
}

export function addTextToCanvas(
  canvas: any,
  baseWidth: number,
  baseHeight: number,
  handlers: CanvasCommitHandlers
): void {
  const textObj = createTextObject(baseWidth, baseHeight);
  addObjectToCanvas(canvas, textObj, handlers);
  toast.success('Đã thêm layer chữ!');
}

export function addShapeToCanvas(
  canvas: any,
  type: ShapeSubtype,
  baseWidth: number,
  baseHeight: number,
  handlers: CanvasCommitHandlers
): void {
  const shape = createShapeObject(type, baseWidth, baseHeight);
  addObjectToCanvas(canvas, shape, handlers);
  toast.success(`Đã thêm hình dạng: ${SHAPE_NAMES[type] || type}`);
}

export async function addImageFromUrl(
  canvas: any,
  url: string,
  baseWidth: number,
  baseHeight: number,
  handlers: CanvasCommitHandlers
): Promise<boolean> {
  if (!url) return false;

  try {
    const img = await loadImageWithMeta(
      url,
      { left: baseWidth / 2, top: baseHeight / 2 },
      { layerId: createLayerId(), layerType: 'IMAGE', layerName: 'Image Layer' }
    );
    fitImageScale(img, baseWidth, baseHeight, 0.5);
    addObjectToCanvas(canvas, img, handlers);
    toast.success('Đã thêm ảnh từ URL!');
    return true;
  } catch {
    toast.error('Không thể chèn ảnh từ URL này. Hãy kiểm tra lại liên kết.');
    return false;
  }
}

export async function addImageLayerFromUrl(
  canvas: any,
  url: string,
  layerName: string,
  baseWidth: number,
  baseHeight: number,
  handlers: CanvasCommitHandlers,
  selection: LayerSelectionHandlers
): Promise<void> {
  try {
    const img = await loadImageWithMeta(
      url,
      { left: baseWidth / 2, top: baseHeight / 2 },
      { layerId: createLayerId(), layerType: 'IMAGE', layerName }
    );
    fitImageScale(img, baseWidth, baseHeight, 0.8);
    addObjectToCanvas(canvas, img, handlers);
    selection.setActiveObjectId((img as any).layerId);
    selection.setActiveObjectProps(extractActiveObjectProps(img));
    toast.success('Đã tạo layer mới từ vùng cắt!');
  } catch (err) {
    console.error('Failed to add crop layer:', err);
    toast.error('Lỗi khi chèn layer mới vào canvas');
  }
}

export async function replaceImageLayerFromUrl(
  canvas: any,
  url: string,
  handlers: CanvasCommitHandlers
): Promise<void> {
  const activeObj = canvas.getActiveObject() as any;
  if (!activeObj || (activeObj.type !== 'image' && activeObj.layerType !== 'IMAGE')) {
    toast.error('Không tìm thấy layer ảnh đang chọn để thay thế.');
    return;
  }

  try {
    const newImg = await FabricImage.fromURL(url, { crossOrigin: 'anonymous' });
    newImg.set({
      left: activeObj.left,
      top: activeObj.top,
      originX: activeObj.originX || 'left',
      originY: activeObj.originY || 'top',
      scaleX: activeObj.scaleX,
      scaleY: activeObj.scaleY,
      angle: activeObj.angle,
      opacity: activeObj.opacity,
      flipX: activeObj.flipX,
      flipY: activeObj.flipY,
    });

    (newImg as any).layerId = activeObj.layerId;
    (newImg as any).layerType = activeObj.layerType;
    (newImg as any).layerName = activeObj.layerName;
    (newImg as any).src = url;
    (newImg as any).defaultImageUrl = url;

    canvas.remove(activeObj);
    canvas.add(newImg);
    handlers.onCommit(canvas, newImg);
    toast.success('Đã thay thế ảnh layer thành công!');
  } catch (err) {
    console.error('Failed to replace image layer:', err);
    toast.error('Lỗi khi thay thế ảnh. Vui lòng thử lại.');
  }
}

export async function addDroppedImageToCanvas(
  canvas: any,
  asset: DroppedAsset,
  x: number,
  y: number,
  baseWidth: number,
  baseHeight: number,
  handlers: CanvasCommitHandlers
): Promise<void> {
  const url = asset.file_url || asset.fileUrl;
  if (!url) return;

  try {
    const img = await loadImageWithMeta(
      url,
      { left: x, top: y },
      {
        layerId: createLayerId(),
        layerType: asset.folder === 'backgrounds' ? 'DECORATION' : 'IMAGE',
        layerName: asset.name || 'Dropped Asset',
      }
    );
    fitImageScale(img, baseWidth, baseHeight, 0.5);
    addObjectToCanvas(canvas, img, handlers);
    toast.success(`Đã thêm layer "${asset.name || 'Ảnh'}"`);
  } catch (err) {
    console.error('Failed to add dropped asset:', err);
    toast.error('Không thể thả ảnh vào canvas. Vui lòng thử lại.');
  }
}

/** Find topmost image layer under canvas coordinates. */
export function findImageLayerAtPoint(canvas: any, x: number, y: number): FabricObjectLike | null {
  const pointer = new Point(x, y);
  const objects = canvas.getObjects();
  for (let i = objects.length - 1; i >= 0; i--) {
    const obj = objects[i];
    if (obj._isBackground) continue;
    if (obj.type === 'image' || obj.layerType === 'IMAGE') {
      if (obj.containsPoint(pointer)) return obj;
    }
  }
  return null;
}

/** Replace image pixels in-place (keeps transform) when dropping asset onto layer. */
export async function replaceDroppedImageOnLayer(
  canvas: any,
  targetObj: FabricObjectLike,
  url: string,
  handlers: CanvasCommitHandlers,
  onUpdateActive?: (url: string) => void
): Promise<boolean> {
  let loadingToast: string | number | undefined;
  try {
    loadingToast = toast.loading('Đang thay thế hình ảnh layer...');
    const newImg = await FabricImage.fromURL(url, { crossOrigin: 'anonymous' });
    targetObj.setElement(newImg._element || newImg.getElement());
    targetObj.set({ src: url, defaultImageUrl: url });
    targetObj.applyFilters?.();
    canvas.renderAll();
    onUpdateActive?.(url);
    handlers.onCommit(canvas, targetObj);
    toast.dismiss(loadingToast);
    toast.success('Đã thay thế ảnh layer thành công!');
    return true;
  } catch (err) {
    console.error(err);
    if (loadingToast !== undefined) toast.dismiss(loadingToast);
    toast.error('Lỗi khi thay thế ảnh layer.');
    return false;
  }
}

export async function setCanvasBackgroundFromUrl(
  canvas: any,
  url: string,
  layerName: string,
  baseWidth: number,
  baseHeight: number,
  handlers: CanvasCommitHandlers
): Promise<void> {
  const bgImg = await FabricImage.fromURL(url, { crossOrigin: 'anonymous' });
  const scaleX = baseWidth / (bgImg.width || baseWidth);
  const scaleY = baseHeight / (bgImg.height || baseHeight);
  const bgScale = Math.max(scaleX, scaleY);

  bgImg.set({
    originX: 'left',
    originY: 'top',
    left: 0,
    top: 0,
    scaleX: bgScale,
    scaleY: bgScale,
    selectable: false,
    evented: false,
    hasControls: false,
    hasBorders: false,
  });
  (bgImg as any)._isBackground = true;
  (bgImg as any).layerId = 'background_layer';
  (bgImg as any).layerName = layerName;
  (bgImg as any).src = url;
  (bgImg as any).defaultImageUrl = url;

  canvas.backgroundImage = bgImg;
  canvas.renderAll();
  handlers.onCommit(canvas);
  toast.success('Đã cập nhật ảnh nền canvas!');
}

export async function addCenteredImageLayer(
  canvas: any,
  url: string,
  meta: { layerName: string; layerType?: string },
  baseWidth: number,
  baseHeight: number,
  handlers: CanvasCommitHandlers,
  options?: { scaleFraction?: number; forceScaleToWidth?: number }
): Promise<void> {
  const img = await loadImageWithMeta(
    url,
    { left: baseWidth / 2, top: baseHeight / 2 },
    {
      layerId: createLayerId(),
      layerType: meta.layerType || 'IMAGE',
      layerName: meta.layerName,
    }
  );
  if (options?.forceScaleToWidth) {
    img.scaleToWidth(options.forceScaleToWidth);
  } else {
    fitImageScale(img, baseWidth, baseHeight, options?.scaleFraction ?? 0.5);
  }
  addObjectToCanvas(canvas, img, handlers);
}

export async function addAssetToCanvas(
  canvas: any,
  asset: SidebarAsset,
  baseWidth: number,
  baseHeight: number,
  handlers: CanvasCommitHandlers,
  options?: { scaleFraction?: number; forceScaleToWidth?: number }
): Promise<void> {
  if (asset.folder === 'backgrounds') {
    await setCanvasBackgroundFromUrl(
      canvas,
      asset.file_url,
      `BG: ${asset.name}`,
      baseWidth,
      baseHeight,
      handlers
    );
    return;
  }

  await addCenteredImageLayer(
    canvas,
    asset.file_url,
    { layerName: asset.name, layerType: 'IMAGE' },
    baseWidth,
    baseHeight,
    handlers,
    options
  );
}

export function addTextPresetToCanvas(
  canvas: any,
  preset: TypographyPreset,
  baseWidth: number,
  baseHeight: number,
  handlers: CanvasCommitHandlers
): void {
  const { shadow, text, ...restConfig } = preset.config;
  const textObj = new IText(text, {
    left: baseWidth / 2,
    top: baseHeight / 2,
    originX: 'center',
    originY: 'center',
    ...(restConfig as object),
    shadow: shadow ? new Shadow(shadow) : undefined,
    padding: 20,
    objectCaching: false,
  });

  (textObj as any).layerId = createLayerId();
  (textObj as any).layerType = 'TEXT';
  (textObj as any).layerName = preset.name;
  (textObj as any)._originalText = preset.config.text;

  addObjectToCanvas(canvas, textObj, handlers);
}
