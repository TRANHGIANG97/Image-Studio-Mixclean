import { createSupabaseAdmin } from '@/lib/supabase';

const DB = () => createSupabaseAdmin();

export interface ListAssetsFilters {
  search?: string;
  folder?: string;
  categoryId?: string;
  mimeType?: string;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
  page?: number;
  limit?: number;
}

/**
 * List assets with optional filters.
 */
export async function listAssets(filters: ListAssetsFilters = {}) {
  const { 
    search, 
    folder, 
    categoryId, 
    mimeType,
    sortBy = 'created_at', 
    sortOrder = 'desc', 
    page = 1, 
    limit = 20 
  } = filters;
  
  let query = DB().from('assets').select('*', { count: 'exact' });

  if (search) query = query.ilike('name', `%${search}%`);
  if (folder) {
    if (folder === 'stickers') {
      query = query.in('folder', ['stickers', 'sticker']);
    } else {
      query = query.eq('folder', folder);
    }
  }
  if (categoryId) query = query.eq('category_id', categoryId);

  if (mimeType) {
    if (mimeType === 'image') {
      query = query.ilike('mime_type', 'image/%').not('mime_type', 'eq', 'image/svg+xml');
    } else if (mimeType === 'svg') {
      query = query.eq('mime_type', 'image/svg+xml');
    } else if (mimeType === 'font') {
      query = query.ilike('mime_type', 'font/%');
    } else {
      query = query.eq('mime_type', mimeType);
    }
  }

  const from = (page - 1) * limit;
  const to = from + limit - 1;

  const { data, error, count } = await query
    .range(from, to)
    .order(sortBy, { ascending: sortOrder === 'asc' });

  if (error) throw error;
  
  return {
    assets: data,
    total: count || 0,
    hasMore: count ? from + data.length < count : false,
  };
}

/**
 * Upload a file asset to Supabase Storage and register in the assets table.
 */
export async function uploadAsset(file: File, folder: string, categoryId?: string | null, registerAsset = true) {
  const bytes = await file.arrayBuffer();
  const buffer = Buffer.from(bytes);

  const dotIndex = file.name.lastIndexOf('.');
  const namePart = dotIndex !== -1 ? file.name.slice(0, dotIndex) : file.name;
  const extPart = dotIndex !== -1 ? file.name.slice(dotIndex + 1) : '';

  const cleanName = namePart.replace(/[^a-zA-Z0-9]/g, '_').replace(/_{2,}/g, '_').toLowerCase();
  const uniqueKey = `${folder}/${Date.now()}_${cleanName}${extPart ? `.${extPart}` : ''}`;

  const supabaseAdmin = DB();

  const { error: uploadError } = await supabaseAdmin.storage
    .from('assets')
    .upload(uniqueKey, buffer, { contentType: file.type, upsert: true });

  if (uploadError) {
    throw Object.assign(
      new Error(`Storage upload failed: ${uploadError.message}. Ensure the 'assets' bucket exists and is public.`),
      { statusCode: 500 }
    );
  }

  const { data: { publicUrl } } = supabaseAdmin.storage.from('assets').getPublicUrl(uniqueKey);

  if (!registerAsset) {
    return {
      fileUrl: publicUrl,
      asset: { name: file.name, file_url: publicUrl, folder },
    };
  }

  const { data: assetData } = await supabaseAdmin
    .from('assets')
    .insert({
      name: file.name,
      folder,
      file_url: publicUrl,
      file_size: file.size,
      mime_type: file.type,
      category_id: categoryId || null,
    })
    .select()
    .single();

  return {
    fileUrl: publicUrl,
    asset: assetData || { name: file.name, file_url: publicUrl, folder },
  };
}

/**
 * Delete an asset: remove from Storage + DB.
 */
export async function deleteAsset(id: string) {
  const supabaseAdmin = DB();

  // 1. Fetch file URL to extract storage key
  const { data: asset, error: fetchError } = await supabaseAdmin
    .from('assets')
    .select('file_url')
    .eq('id', id)
    .single();

  if (fetchError || !asset) {
    throw Object.assign(new Error('Asset not found'), { statusCode: 404 });
  }

  // 2. Extract storage key and remove from Storage
  let key = '';
  const marker = '/storage/v1/object/public/assets/';
  const idx = asset.file_url.indexOf(marker);
  if (idx !== -1) {
    key = asset.file_url.substring(idx + marker.length);
  } else {
    const parts = asset.file_url.split('/');
    if (parts.length >= 2) {
      key = `${parts[parts.length - 2]}/${parts[parts.length - 1]}`;
    }
  }

  if (key) {
    try {
      const { error: storageError } = await supabaseAdmin.storage.from('assets').remove([key]);
      if (storageError) console.error('Storage delete error:', storageError);
    } catch (err) {
      console.error('Storage delete exception:', err);
    }
  }

  // 3. Delete DB record
  const { error: deleteError } = await supabaseAdmin.from('assets').delete().eq('id', id);
  if (deleteError) throw deleteError;
}

/**
 * Delete multiple assets: remove from Storage + DB in bulk.
 */
export async function deleteAssetsBulk(ids: string[]) {
  if (!ids || ids.length === 0) return;
  const supabaseAdmin = DB();

  // 1. Fetch file URLs to extract storage keys
  const { data: assets, error: fetchError } = await supabaseAdmin
    .from('assets')
    .select('file_url')
    .in('id', ids);

  if (fetchError || !assets) {
    throw Object.assign(new Error('Assets not found'), { statusCode: 404 });
  }

  // 2. Extract storage keys
  const keys: string[] = [];
  const marker = '/storage/v1/object/public/assets/';
  
  for (const asset of assets) {
    let key = '';
    const idx = asset.file_url.indexOf(marker);
    if (idx !== -1) {
      key = asset.file_url.substring(idx + marker.length);
    } else {
      const parts = asset.file_url.split('/');
      if (parts.length >= 2) {
        key = `${parts[parts.length - 2]}/${parts[parts.length - 1]}`;
      }
    }
    if (key) keys.push(key);
  }

  // 3. Remove from Storage in bulk
  if (keys.length > 0) {
    try {
      const { error: storageError } = await supabaseAdmin.storage.from('assets').remove(keys);
      if (storageError) console.error('Storage bulk delete error:', storageError);
    } catch (err) {
      console.error('Storage bulk delete exception:', err);
    }
  }

  // 4. Delete DB records
  const { error: deleteError } = await supabaseAdmin.from('assets').delete().in('id', ids);
  if (deleteError) throw deleteError;
}

/**
 * Update asset attributes (e.g., category_id, folder).
 */
export async function updateAsset(id: string, updates: { categoryId?: string | null; folder?: string }) {
  const updatePayload: any = {};
  if (updates.categoryId !== undefined) updatePayload.category_id = updates.categoryId;
  if (updates.folder !== undefined) updatePayload.folder = updates.folder;

  const { data, error } = await DB()
    .from('assets')
    .update(updatePayload)
    .eq('id', id)
    .select()
    .single();

  if (error) throw error;
  return data;
}

/**
 * Bulk update asset attributes.
 */
export async function updateAssetsBulk(ids: string[], updates: { categoryId?: string | null; folder?: string }) {
  const updatePayload: any = {};
  if (updates.categoryId !== undefined) updatePayload.category_id = updates.categoryId;
  if (updates.folder !== undefined) updatePayload.folder = updates.folder;

  const { data, error } = await DB()
    .from('assets')
    .update(updatePayload)
    .in('id', ids)
    .select();

  if (error) throw error;
  return data;
}

/**
 * Get list of unique folders from assets.
 */
export async function listAssetFolders() {
  const { data, error } = await DB()
    .from('assets')
    .select('folder');

  if (error) throw error;

  const defaultFolders = ['backgrounds', 'stickers', 'fonts', 'uncategorized'];
  const dbFolders = data?.map((item) => item.folder).filter(Boolean) || [];
  return Array.from(new Set([...defaultFolders, ...dbFolders]));
}

