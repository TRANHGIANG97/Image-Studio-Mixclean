import { describe, expect, it } from 'vitest';
import {
  buildInitialFabricState,
  hasRenderableFabricState,
  isTemplateDebugPublished,
  resolveTemplateEnvironment,
  syncCanvasMetadata,
} from '@/domains/templates/template.helpers';
import type { CloudTemplate } from '@/types/cloud-template';

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

  it('prefers top-level environment over stale canvas metadata', () => {
    const canvasData = {
      metadata: { environment: 'debug' },
    } as CloudTemplate;

    expect(resolveTemplateEnvironment('release', canvasData)).toBe('release');
    expect(isTemplateDebugPublished('published', 'release', canvasData)).toBe(false);
  });

  it('syncs canvas metadata during bulk publish updates', () => {
    const canvasData = {
      metadata: {
        title: 'Demo',
        thumbnailUrl: '',
        status: 'published',
        environment: 'debug',
        schemaVersion: 1,
        createdAt: 1,
        updatedAt: 1,
      },
    } as CloudTemplate;

    const synced = syncCanvasMetadata(canvasData, {
      status: 'published',
      environment: 'release',
    });

    expect(synced.metadata?.environment).toBe('release');
    expect(synced.metadata?.status).toBe('published');
    expect(synced.metadata?.updatedAt).toBeGreaterThan(1);
  });
});
