/**
 * Helper utilities for template operations.
 */

/** Calculate GCD for aspect ratio. */
export function gcd(a: number, b: number): number {
  return b === 0 ? a : gcd(b, a % b);
}

/** Build a minimal Fabric.js state for new templates. */
export function buildInitialFabricState(backgroundUrl?: string | null) {
  return {
    version: '7.4.0',
    objects: [],
    background: backgroundUrl ? '#ffffff' : '#ffffff',
  };
}
