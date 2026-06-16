import { z } from 'zod';

export const uploadFontSchema = z.object({
  file: z.instanceof(File, { message: 'Font file is required' }),
  name: z.string().min(1, 'name is required'),
  family_slug: z.string().min(1, 'family_slug is required'),
});

export type UploadFontInput = z.infer<typeof uploadFontSchema>;
