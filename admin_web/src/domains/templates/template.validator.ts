import { z } from 'zod';

/**
 * Validation schema for creating/updating a template.
 */

export const createTemplateSchema = z.object({
  templateId: z.string().min(1, 'templateId is required'),
  categoryId: z.string().min(1, 'categoryId is required'),
  title: z.string().optional().default(''),
  baseWidth: z.number().positive().optional(),
  baseHeight: z.number().positive().optional(),
  backgroundUrl: z.string().url().optional().nullable(),
});

export const updateTemplateSchema = z.object({
  title: z.string().optional(),
  category_id: z.string().optional(),
  status: z.enum(['draft', 'published']).optional(),
  environment: z.enum(['debug', 'release', 'all']).optional(),
  thumbnail_url: z.string().optional().nullable(),
  canvas_data: z.unknown().optional(),
  fabric_state: z.unknown().optional(),
});

export const cloneTemplateSchema = z.object({
  sourceId: z.string().min(1, 'sourceId is required'),
  newTemplateId: z.string().min(1, 'newTemplateId is required'),
  newTitle: z.string().min(1, 'newTitle is required'),
});

export const listTemplatesSchema = z.object({
  search: z.string().optional().default(''),
  categoryId: z.string().optional().default(''),
  status: z.string().optional().default(''),
});

export type CreateTemplateInput = z.infer<typeof createTemplateSchema>;
export type UpdateTemplateInput = z.infer<typeof updateTemplateSchema>;
export type CloneTemplateInput = z.infer<typeof cloneTemplateSchema>;
export type ListTemplatesInput = z.infer<typeof listTemplatesSchema>;
