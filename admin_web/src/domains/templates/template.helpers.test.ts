import { describe, expect, it } from 'vitest';
import { buildInitialFabricState, hasRenderableFabricState } from '@/domains/templates/template.helpers';

describe('template helpers', () => {
  it('treats bootstrap fabric state as non-renderable', () => {
    expect(hasRenderableFabricState(buildInitialFabricState())).toBe(false);
  });

  it('accepts fabric state with drawable objects', () => {
    expect(hasRenderableFabricState({
      version: '7.4.0',
      objects: [{ type: 'image' }],
      background: '#ffffff',
    })).toBe(true);
  });

  it('rejects invalid serialized fabric state', () => {
    expect(hasRenderableFabricState('{not-json')).toBe(false);
  });
});
