export type { CloudTemplate, CloudLayer, CloudTransform, CloudPayload, TemplateMetadata, TemplateCanvas } from '@/types/cloud-template';

export interface CreateTemplateInput {
  title: string;
  categoryId: string;
  status?: 'draft' | 'published';
}

export interface UpdateTemplateInput {
  title?: string;
  categoryId?: string;
  status?: 'draft' | 'published';
  canvas_data?: unknown;
  fabric_state?: unknown;
  thumbnail_url?: string;
}
