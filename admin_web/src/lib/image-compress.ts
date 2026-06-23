import { fileStem, readImageDimensions } from '@/lib/image-file-utils';

const SKIP_COMPRESS_PATTERN = /^image\/(svg\+xml|webp)$/i;

export type CompressImageOptions = {
  quality?: number;
  maxEdge?: number;
};

export type CompressImageResult = {
  file: File;
  originalSize: number;
  compressedSize: number;
  skipped: boolean;
};

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}

export function formatCompressSummary(result: CompressImageResult): string {
  if (result.skipped) {
    return `${result.file.name} · ${formatBytes(result.compressedSize)}`;
  }
  const saved = Math.max(0, Math.round((1 - result.compressedSize / result.originalSize) * 100));
  return `${result.file.name} · ${formatBytes(result.compressedSize)} (WebP, -${saved}%)`;
}

export function shouldCompressImageFile(file: File): boolean {
  return file.type.startsWith('image/') && !SKIP_COMPRESS_PATTERN.test(file.type);
}

export async function compressImageFileToWebp(
  file: File,
  options: CompressImageOptions = {},
): Promise<CompressImageResult> {
  const quality = options.quality ?? 0.82;
  const maxEdge = options.maxEdge ?? 0;

  if (!shouldCompressImageFile(file)) {
    return {
      file,
      originalSize: file.size,
      compressedSize: file.size,
      skipped: true,
    };
  }

  const { width, height } = await readImageDimensions(file);
  let targetWidth = width;
  let targetHeight = height;

  if (maxEdge > 0) {
    const longest = Math.max(width, height);
    if (longest > maxEdge) {
      const scale = maxEdge / longest;
      targetWidth = Math.max(1, Math.round(width * scale));
      targetHeight = Math.max(1, Math.round(height * scale));
    }
  }

  const blob = await encodeCanvasToWebp(file, targetWidth, targetHeight, quality);
  const webpFile = new File([blob], `${fileStem(file.name)}.webp`, {
    type: 'image/webp',
    lastModified: Date.now(),
  });

  if (webpFile.size >= file.size && SKIP_COMPRESS_PATTERN.test(file.type)) {
    return {
      file,
      originalSize: file.size,
      compressedSize: file.size,
      skipped: true,
    };
  }

  return {
    file: webpFile,
    originalSize: file.size,
    compressedSize: webpFile.size,
    skipped: false,
  };
}

async function encodeCanvasToWebp(
  file: File,
  width: number,
  height: number,
  quality: number,
): Promise<Blob> {
  const bitmap = await createImageBitmap(file);
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;

  const ctx = canvas.getContext('2d');
  if (!ctx) {
    bitmap.close();
    throw new Error('Trình duyệt không hỗ trợ nén ảnh.');
  }

  ctx.drawImage(bitmap, 0, 0, width, height);
  bitmap.close();

  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (!blob) {
          reject(new Error('Không chuyển được ảnh sang WebP.'));
          return;
        }
        resolve(blob);
      },
      'image/webp',
      quality,
    );
  });
}
