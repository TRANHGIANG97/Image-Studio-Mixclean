/** Maps CloudTemplate / editor blend mode names to Canvas composite operations. */
export function getCompositeOperation(blendMode?: string | null): GlobalCompositeOperation {
  if (!blendMode) return 'source-over';
  const mode = blendMode.toLowerCase();
  switch (mode) {
    case 'normal':
      return 'source-over';
    case 'multiply':
      return 'multiply';
    case 'screen':
      return 'screen';
    case 'overlay':
      return 'overlay';
    case 'darken':
      return 'darken';
    case 'lighten':
      return 'lighten';
    case 'color-dodge':
      return 'color-dodge';
    case 'color-burn':
      return 'color-burn';
    case 'hard-light':
      return 'hard-light';
    case 'soft-light':
      return 'soft-light';
    case 'difference':
      return 'difference';
    case 'exclusion':
      return 'exclusion';
    case 'hue':
      return 'hue';
    case 'saturation':
      return 'saturation';
    case 'color':
      return 'color';
    case 'luminosity':
      return 'luminosity';
    case 'linear-dodge':
      return 'lighter';
    case 'linear-burn':
      return 'multiply';
    default:
      return 'source-over';
  }
}
