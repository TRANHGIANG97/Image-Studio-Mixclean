import { createSupabaseAdmin } from '@/lib/supabase';

const DB = () => createSupabaseAdmin();

export interface CreateCategoryInput {
  name: string;
  order?: number;
}

export interface UpdateCategoryInput {
  id: string;
  name?: string;
  order?: number;
}

/**
 * List all categories ordered by the `order` column.
 */
export async function listCategories() {
  const { data, error } = await DB()
    .from('categories')
    .select('*')
    .order('order', { ascending: true });

  if (error) throw error;
  return data;
}

/**
 * Create a new category.
 */
export async function createCategory(input: CreateCategoryInput) {
  const { data, error } = await DB()
    .from('categories')
    .insert({ name: input.name, order: input.order ?? 0 })
    .select()
    .single();

  if (error) throw error;
  return data;
}

/**
 * Update an existing category.
 */
export async function updateCategory(input: UpdateCategoryInput) {
  const updates: Record<string, unknown> = {};
  if (input.name !== undefined) updates.name = input.name;
  if (input.order !== undefined) updates.order = input.order;

  const { data, error } = await DB()
    .from('categories')
    .update(updates)
    .eq('id', input.id)
    .select()
    .single();

  if (error) throw error;
  return data;
}

/**
 * Delete a category. Checks for dependent templates first.
 */
export async function deleteCategory(id: string) {
  const supabase = DB();

  // Check for dependent templates
  const { count, error: countError } = await supabase
    .from('templates')
    .select('*', { count: 'exact', head: true })
    .eq('category_id', id);

  if (countError) throw countError;

  if (count && count > 0) {
    throw Object.assign(
      new Error('Cannot delete category: It is referenced by one or more templates.'),
      { statusCode: 400, code: 'CATEGORY_IN_USE' }
    );
  }

  const { error } = await supabase.from('categories').delete().eq('id', id);
  if (error) throw error;
}
