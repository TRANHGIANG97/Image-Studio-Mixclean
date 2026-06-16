'use client';

/**
 * Convert Fabric gradient coords to angle in degrees.
 */
export const getGradientAngle = (coords: any): number => {
  if (!coords) return 0;
  const x1 = coords.x1 ?? 0, y1 = coords.y1 ?? 0;
  const x2 = coords.x2 ?? 1, y2 = coords.y2 ?? 0;
  const dx = x2 - x1, dy = y2 - y1;
  let angle = Math.round(Math.atan2(dy, dx) * (180 / Math.PI));
  if (angle < 0) angle += 360;
  return angle;
};

/**
 * Convert rgba string to hex.
 */
export const rgbaToHex = (rgba: string): string => {
  if (!rgba || (!rgba.startsWith('rgba') && !rgba.startsWith('rgb'))) {
    if (rgba && rgba.startsWith('#')) return rgba.slice(0, 7);
    return '#000000';
  }
  const match = rgba.match(/\d+/g);
  if (match && match.length >= 3) {
    return '#' + [0, 1, 2].map(i => Math.min(255, Math.max(0, parseInt(match[i]))).toString(16).padStart(2, '0')).join('');
  }
  return '#000000';
};

/**
 * Convert hex to rgba, preserving alpha from current rgba value.
 */
export const hexToRgba = (hex: string, currentRgba: string): string => {
  const r = parseInt(hex.slice(1, 3), 16) || 0;
  const g = parseInt(hex.slice(3, 5), 16) || 0;
  const b = parseInt(hex.slice(5, 7), 16) || 0;
  let alpha = 0.4;
  if (currentRgba && currentRgba.startsWith('rgba')) {
    const match = currentRgba.match(/[\d.]+/g);
    if (match && match.length >= 4) alpha = parseFloat(match[3]) || 0.4;
  }
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
};
