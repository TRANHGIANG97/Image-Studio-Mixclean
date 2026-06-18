export const arrowPath = 'M -100 -20 L 20 -20 L 20 -60 L 100 0 L 20 60 L 20 20 L -100 20 Z';

export function getStarPoints(outerRadius = 100, innerRadius = 40): { x: number; y: number }[] {
  const points = [];
  const spikes = 5;
  let rot = (Math.PI / 2) * 3;
  const step = Math.PI / spikes;

  for (let i = 0; i < spikes; i++) {
    points.push({ x: Math.cos(rot) * outerRadius, y: Math.sin(rot) * outerRadius });
    rot += step;
    points.push({ x: Math.cos(rot) * innerRadius, y: Math.sin(rot) * innerRadius });
    rot += step;
  }
  return points;
}

export function getDiamondPoints(size = 100): { x: number; y: number }[] {
  return [
    { x: 0, y: -size },
    { x: size, y: 0 },
    { x: 0, y: size },
    { x: -size, y: 0 },
  ];
}

export function getHexagonPoints(size = 100): { x: number; y: number }[] {
  const points = [];
  for (let i = 0; i < 6; i++) {
    const angle = (Math.PI / 3) * i;
    points.push({ x: Math.cos(angle) * size, y: Math.sin(angle) * size });
  }
  return points;
}
