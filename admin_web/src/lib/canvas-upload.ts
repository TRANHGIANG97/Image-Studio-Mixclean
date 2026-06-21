const IMAGE_FILE_PATTERN = /\.(png|jpe?g|gif|webp|svg|bmp|avif)$/i;

export interface DroppedAsset {
  id?: string;
  name?: string;
  folder?: string;
  file_url?: string;
  fileUrl?: string;
}

export function isDroppableImageFile(file: File): boolean {
  if (file.type.startsWith('image/')) return true;
  return IMAGE_FILE_PATTERN.test(file.name);
}

export function isInlineImageUrl(url: string | null | undefined): boolean {
  if (!url) return false;
  return url.startsWith('data:') || url.startsWith('blob:');
}

function inferExtensionFromDataUrl(dataUrl: string): string {
  const mime = dataUrl.match(/^data:([^;,]+)/)?.[1] || '';
  if (mime.includes('webp')) return 'webp';
  if (mime.includes('jpeg') || mime.includes('jpg')) return 'jpg';
  if (mime.includes('svg')) return 'svg';
  if (mime.includes('gif')) return 'gif';
  return 'png';
}

export function dataUrlToBlob(dataUrl: string): Blob {
  const [header, body] = dataUrl.split(',');
  if (!body) throw new Error('Invalid data URL');
  const mime = header.match(/:(.*?);/)?.[1] || 'image/png';
  const binary = atob(body);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return new Blob([bytes], { type: mime });
}

export async function blobUrlToBlob(blobUrl: string): Promise<Blob> {
  const res = await fetch(blobUrl);
  if (!res.ok) throw new Error('Cannot read blob URL');
  return res.blob();
}

/** Upload a local image file for use as a canvas layer. */
export async function uploadCanvasImageFile(file: File, folder = 'template-layers'): Promise<string | null> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('folder', folder);
  formData.append('registerAsset', 'false');
  const res = await fetch('/api/upload', { method: 'POST', body: formData });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    console.error('[canvas-upload] upload failed:', data?.error || res.statusText);
    return null;
  }
  return data.fileUrl ?? null;
}

/** Upload a data URL or blob URL to storage; returns remote https URL. */
export async function uploadInlineImageUrl(
  inlineUrl: string,
  filename: string,
  folder = 'template-layers',
): Promise<string | null> {
  try {
    const blob = inlineUrl.startsWith('blob:')
      ? await blobUrlToBlob(inlineUrl)
      : dataUrlToBlob(inlineUrl);
    const file = new File([blob], filename, { type: blob.type || 'image/png' });
    return uploadCanvasImageFile(file, folder);
  } catch (err) {
    console.error('[canvas-upload] inline upload failed:', err);
    return null;
  }
}

export async function ensureRemoteImageUrl(
  url: string,
  filename: string,
  folder = 'template-layers',
): Promise<string | null> {
  if (!url) return null;
  if (!isInlineImageUrl(url)) return url;
  return uploadInlineImageUrl(url, filename, folder);
}

/** Collect image files from a drag event (files list + items fallback). */
export function getDroppedImageFiles(dataTransfer: DataTransfer): File[] {
  const fromFiles = Array.from(dataTransfer.files).filter(isDroppableImageFile);
  if (fromFiles.length > 0) return fromFiles;

  const fromItems: File[] = [];
  for (const item of Array.from(dataTransfer.items || [])) {
    if (item.kind !== 'file') continue;
    const file = item.getAsFile();
    if (file && isDroppableImageFile(file)) fromItems.push(file);
  }
  return fromItems;
}

/**
 * Upload any data:/blob: image URLs on canvas objects before serializing for mobile.
 */
export async function uploadInlineImageLayers(
  canvas: { getObjects: () => any[]; renderAll?: () => void },
  templateId: string,
): Promise<void> {
  const { FabricImage } = await import('fabric');
  const objects = canvas.getObjects();

  for (const obj of objects) {
    if (obj._isBackground) continue;
    const isImageObject =
      obj.type === 'image' ||
      obj.layerType === 'IMAGE' ||
      obj.layerType === 'PLACEHOLDER_OBJECT' ||
      (obj.layerType === 'DECORATION' && (obj.src || obj.defaultImageUrl));

    if (!isImageObject) continue;

    const layerId = obj.layerId || `layer_${Date.now()}`;
    let remoteSrc: string | null = null;

    if (isInlineImageUrl(obj.src)) {
      const ext = inferExtensionFromDataUrl(obj.src);
      remoteSrc = await uploadInlineImageUrl(
        obj.src,
        `${templateId}_${layerId}.${ext}`,
        'template-layers',
      );
      if (remoteSrc) {
        const img = await FabricImage.fromURL(remoteSrc, { crossOrigin: 'anonymous' });
        obj.setElement(img._element || img.getElement());
        obj.set({ src: remoteSrc });
      }
    } else if (typeof obj.src === 'string' && obj.src) {
      remoteSrc = obj.src;
    }

    if (isInlineImageUrl(obj.defaultImageUrl)) {
      const ext = inferExtensionFromDataUrl(obj.defaultImageUrl);
      const uploaded = await uploadInlineImageUrl(
        obj.defaultImageUrl,
        `${templateId}_${layerId}_default.${ext}`,
        'template-layers',
      );
      if (uploaded) obj.defaultImageUrl = uploaded;
    }

    if (remoteSrc && !obj.defaultImageUrl) {
      obj.defaultImageUrl = remoteSrc;
    }
  }

  canvas.renderAll?.();
}
