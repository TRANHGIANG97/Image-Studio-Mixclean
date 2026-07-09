/**
 * Rasterization Service — Phase 1 of DATA_PIPELINE_PLAN.md.
 *
 * Converts raw RGBA pixel buffers (from @webtoon/psd composite) into
 * compressed WebP blobs ready for upload to R2/CDN.
 *
 * WebP is preferred over PNG: transparent WebP is ~30-50% smaller, and both
 * Coil (Android) and browsers decode it natively. For layers with very smooth
 * gradients (soft shadows / glows) lossy WebP can introduce banding, so
 * callers can pass a higher quality or fall back to PNG per-layer.
 */

export interface RasterizeOptions {
  /** WebP quality 0..1. Default 0.85 (matches historical pipeline output). */
  quality?: number;
  /** Use 'image/png' for layers where lossy WebP shows banding. */
  mimeType?: 'image/webp' | 'image/png';
}

/** Render raw RGBA pixels onto an offscreen canvas and encode as a Blob. */
export async function pixelsToBlob(
  pixels: Uint8ClampedArray,
  width: number,
  height: number,
  options: RasterizeOptions = {}
): Promise<Blob | null> {
  if (typeof document === 'undefined') return null;
  const { quality = 0.85, mimeType = 'image/webp' } = options;

  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');
  if (!ctx) return null;

  const imgData = ctx.createImageData(width, height);
  imgData.data.set(pixels);
  ctx.putImageData(imgData, 0, 0);

  return new Promise<Blob | null>((resolve) => {
    canvas.toBlob(resolve, mimeType, quality);
  });
}

/** Composite pixels resized to max 720px wide, white background — for thumbnails. */
export async function pixelsToThumbnailBlob(
  pixels: Uint8ClampedArray,
  width: number,
  height: number
): Promise<Blob | null> {
  if (typeof document === 'undefined') return null;

  const sourceCanvas = document.createElement('canvas');
  sourceCanvas.width = width;
  sourceCanvas.height = height;
  const sCtx = sourceCanvas.getContext('2d');
  if (!sCtx) return null;

  const imgData = sCtx.createImageData(width, height);
  imgData.data.set(pixels);
  sCtx.putImageData(imgData, 0, 0);

  const targetWidth = Math.min(720, width);
  const targetHeight = Math.round((targetWidth / width) * height);

  const destCanvas = document.createElement('canvas');
  destCanvas.width = targetWidth;
  destCanvas.height = targetHeight;
  const dCtx = destCanvas.getContext('2d');
  if (!dCtx) return null;

  dCtx.fillStyle = '#ffffff';
  dCtx.fillRect(0, 0, targetWidth, targetHeight);
  dCtx.drawImage(sourceCanvas, 0, 0, targetWidth, targetHeight);

  return new Promise<Blob | null>((resolve) => {
    destCanvas.toBlob(resolve, 'image/webp', 0.88);
  });
}
