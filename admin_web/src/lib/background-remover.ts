'use client';

let scriptLoadingPromise: Promise<void> | null = null;

function loadMediaPipeScript(): Promise<void> {
  if (typeof window === 'undefined') return Promise.reject('Not running in browser');
  if ((window as any).SelfieSegmentation) return Promise.resolve();

  if (scriptLoadingPromise) return scriptLoadingPromise;

  scriptLoadingPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'https://cdn.jsdelivr.net/npm/@mediapipe/selfie_segmentation/selfie_segmentation.js';
    script.crossOrigin = 'anonymous';
    script.onload = () => resolve();
    script.onerror = (err) => {
      scriptLoadingPromise = null;
      reject(err);
    };
    document.head.appendChild(script);
  });

  return scriptLoadingPromise;
}

/**
 * Removes the background of an image client-side using MediaPipe Selfie Segmentation.
 * Returns the transparent image as a base64 Data URL.
 */
export async function removeImageBackground(imageSrc: string): Promise<string> {
  await loadMediaPipeScript();
  const SelfieSegmentation = (window as any).SelfieSegmentation;
  if (!SelfieSegmentation) {
    throw new Error('SelfieSegmentation library could not be loaded');
  }

  // Load target image
  const img = new Image();
  img.crossOrigin = 'anonymous';
  img.src = imageSrc;
  await new Promise((resolve, reject) => {
    img.onload = resolve;
    img.onerror = reject;
  });

  const canvas = document.createElement('canvas');
  canvas.width = img.naturalWidth;
  canvas.height = img.naturalHeight;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Could not create 2D context');

  const segmenter = new SelfieSegmentation({
    locateFile: (file: string) => `https://cdn.jsdelivr.net/npm/@mediapipe/selfie_segmentation/${file}`,
  });

  segmenter.setOptions({
    modelSelection: 1, // 1 for landscape/general use (highly accurate)
  });

  let resultUrl = '';
  await new Promise<void>((resolve, reject) => {
    segmenter.onResults((results: any) => {
      try {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.drawImage(results.segmentationMask, 0, 0, canvas.width, canvas.height);

        // Keep foreground only
        ctx.globalCompositeOperation = 'source-in';
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);

        resultUrl = canvas.toDataURL('image/png');
        resolve();
      } catch (err) {
        reject(err);
      }
    });

    segmenter.send({ image: img }).catch(reject);
  });

  try {
    await segmenter.close();
  } catch (e) {
    console.warn('Error closing segmenter:', e);
  }

  return resultUrl;
}
