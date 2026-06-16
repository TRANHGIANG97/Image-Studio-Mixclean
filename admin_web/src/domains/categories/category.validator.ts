import { z } from 'zod';

export const createCategorySchema = z.object({
  name: z.string().min(1, 'Category name is required'),
  order: z.number().int(),
});

export const updateCategorySchema = z.object({
  id: z.string().min(1, 'id is required'),
  name: z.string().optional(),
  order: z.number().int().optional(),
});

export const deleteCategorySchema = z.object({
  id: z.string().min(1, 'id is required'),
});

export type CreateCategoryInput = z.infer<typeof createCategorySchema>;
export type UpdateCategoryInput = z.infer<typeof updateCategorySchema>;
