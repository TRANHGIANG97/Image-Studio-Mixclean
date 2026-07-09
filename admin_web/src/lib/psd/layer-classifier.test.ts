import { describe, expect, it } from 'vitest';
import {
  classifyPsdLayer,
  isGroundShadowGradient,
  PsdLayerSignals,
} from './layer-classifier';

function signals(overrides: Partial<PsdLayerSignals> = {}): PsdLayerSignals {
  return {
    isText: false,
    hasWarp: false,
    warpStyle: null,
    hasMixedStyles: false,
    hasComplexEffects: false,
    hasDropShadow: false,
    isAdjustment: false,
    hasMask: false,
    isClipping: false,
    blendMode: 'normal',
    ...overrides,
  };
}

describe('classifyPsdLayer — text layers', () => {
  it('plain text keeps vector', () => {
    const result = classifyPsdLayer(signals({ isText: true }));
    expect(result.strategy).toBe('keep-vector');
    expect(result.effectsBaked).toBe(false);
  });

  it('text with simple drop shadow keeps vector', () => {
    const result = classifyPsdLayer(signals({ isText: true, hasDropShadow: true }));
    expect(result.strategy).toBe('keep-vector');
    expect(result.effectsBaked).toBe(false);
  });

  it('warp text rasterizes with effects baked', () => {
    const result = classifyPsdLayer(
      signals({ isText: true, hasWarp: true, warpStyle: 'warpArc' })
    );
    expect(result.strategy).toBe('rasterize');
    expect(result.effectsBaked).toBe(true);
    expect(result.reasons.join(' ')).toContain('warpArc');
  });

  it('mixed styles rasterize', () => {
    const result = classifyPsdLayer(signals({ isText: true, hasMixedStyles: true }));
    expect(result.strategy).toBe('rasterize');
  });

  it('complex effects (stroke/bevel/glow) rasterize text', () => {
    const result = classifyPsdLayer(signals({ isText: true, hasComplexEffects: true }));
    expect(result.strategy).toBe('rasterize');
    expect(result.effectsBaked).toBe(true);
  });
});

describe('classifyPsdLayer — image layers', () => {
  it('plain image keeps vector, no reasons', () => {
    const result = classifyPsdLayer(signals());
    expect(result.strategy).toBe('keep-vector');
    expect(result.reasons).toHaveLength(0);
  });

  it('adjustment layer rasterizes with effects baked', () => {
    const result = classifyPsdLayer(signals({ isAdjustment: true }));
    expect(result.strategy).toBe('rasterize');
    expect(result.effectsBaked).toBe(true);
  });

  it('exotic blend mode stays image but warns', () => {
    const result = classifyPsdLayer(signals({ blendMode: 'color-dodge' }));
    expect(result.strategy).toBe('keep-vector');
    expect(result.reasons.join(' ')).toContain('color-dodge');
  });

  it('safe blend modes produce no blend warning', () => {
    for (const mode of ['normal', 'multiply', 'screen', 'overlay', 'darken', 'lighten']) {
      const result = classifyPsdLayer(signals({ blendMode: mode }));
      expect(result.reasons.join(' ')).not.toContain('blend mode');
    }
  });

  it('mask warns but keeps image (pixels already masked)', () => {
    const result = classifyPsdLayer(signals({ hasMask: true }));
    expect(result.strategy).toBe('keep-vector');
    expect(result.reasons.join(' ')).toContain('mặt nạ');
  });
});

describe('isGroundShadowGradient', () => {
  it('detects radial fade-to-transparent (ground shadow signature)', () => {
    expect(
      isGroundShadowGradient({
        type: 'radial',
        colorStops: [
          { offset: 0, color: 'rgba(0,0,0,0.28)' },
          { offset: 0.55, color: 'rgba(0,0,0,0.16)' },
          { offset: 1, color: 'rgba(0,0,0,0)' },
        ],
      })
    ).toBe(true);
  });

  it('rejects linear gradients', () => {
    expect(
      isGroundShadowGradient({
        type: 'linear',
        colorStops: [
          { offset: 0, color: '#ff0000' },
          { offset: 1, color: 'rgba(0,0,255,0)' },
        ],
      })
    ).toBe(false);
  });

  it('rejects radial gradients that stay opaque', () => {
    expect(
      isGroundShadowGradient({
        type: 'radial',
        colorStops: [
          { offset: 0, color: '#000000' },
          { offset: 1, color: '#333333' },
        ],
      })
    ).toBe(false);
  });

  it('handles 8-digit hex transparency', () => {
    expect(
      isGroundShadowGradient({
        type: 'radial',
        colorStops: [
          { offset: 0, color: '#00000048' },
          { offset: 1, color: '#00000000' },
        ],
      })
    ).toBe(true);
  });

  it('rejects null/missing gradients', () => {
    expect(isGroundShadowGradient(null)).toBe(false);
    expect(isGroundShadowGradient({ type: 'radial', colorStops: [] })).toBe(false);
  });
});
