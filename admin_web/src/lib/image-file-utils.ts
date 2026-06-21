export function fileStem(fileName: string, extensions: RegExp = /\.[^.]+$/) {
  return fileName.replace(extensions, '').replace(/[_-]+/g, ' ').trim();
}

export function readImageDimensions(file: File): Promise<{ width: number; height: number }> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => {
      URL.revokeObjectURL(url);
      resolve({
        width: Math.max(1, image.naturalWidth || 1),
        height: Math.max(1, image.naturalHeight || 1),
      });
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error('Không đọc được kích thước ảnh.'));
    };
    image.src = url;
  });
}
