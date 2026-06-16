import { z } from 'zod';

export const createAssetSchema = z.object({
  file: z.instanceof(File, { message: 'File is required' }),
  folder: z.string().optional().default('uncategorized'),
  categoryId: z.string().optional().nullable(),
});

export const listAssetsSchema = z.object({
  search: z.string().optional().default(''),
  folder: z.string().optional().default(''),
  categoryId: z.string().optional().default(''),
});

export const updateAssetSchema = z.object({
  id: z.string().optional(),
  ids: z.array(z.string()).optional(),
  folder: z.string().optional(),
  categoryId: z.string().optional().nullable(),
});

export const deleteAssetSchema = z.object({
  id: z.string().min(1, 'id is required'),
});

export type CreateAssetInput = z.infer<typeof createAssetSchema>;
export type ListAssetsInput = z.infer<typeof listAssetsSchema>;
export type UpdateAssetInput = z.infer<typeof updateAssetSchema>;
