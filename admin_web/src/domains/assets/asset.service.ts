import { createSupabaseAdmin } from '@/lib/supabase';
import crypto from 'crypto';
import { r2Client, r2BucketName, r2PublicUrl } from '@/lib/r2';
import { PutObjectCommand, DeleteObjectCommand, HeadObjectCommand } from '@aws-sdk/client-s3';
import { toSlug } from '@/lib/utils';
import { isBackgroundFolder } from '@/domains/assets/background-folders';

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
  query = query.not('name', 'eq', '.folder_placeholder');

  if (search) query = query.ilike('name', `%${search}%`);
  if (folder) {
    if (folder === 'stickers') {
      query = query.in('folder', ['stickers', 'sticker']);
    } else if (isBackgroundFolder(folder)) {
      // Case-insensitive match for backgrounds_ecommerce vs Backgrounds_ecommerce
      query = query.ilike('folder', folder);
    } else {
      query = query.eq('folder', folder);
    }
  } else {
    // Exclude PSD temporary layers from default list to prevent clutter
    query = query.not('folder', 'eq', 'imported-psd').not('folder', 'eq', 'imported-psd-temp');
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
 * Upload a file asset to Cloudflare R2 and register in the assets table.
 */
export async function uploadAsset(file: File, folder: string, categoryId?: string | null, registerAsset = true) {
  const bytes = await file.arrayBuffer();
  const buffer = Buffer.from(bytes);

  const dotIndex = file.name.lastIndexOf('.');
  const namePart = dotIndex !== -1 ? file.name.slice(0, dotIndex) : file.name;
  const extPart = dotIndex !== -1 ? file.name.slice(dotIndex + 1).toLowerCase() : '';

  const hash = crypto.createHash('sha256').update(buffer).digest('hex');
  const fileNameWithHash = `hash_${hash}${extPart ? `.${extPart}` : ''}`;
  
  // Custom prefix for structured storage on R2
  const uniqueKey = `assets/${fileNameWithHash}`; 
  
  // Format the file URL using public R2 Custom Domain
  const publicUrl = `${r2PublicUrl.replace(/\/$/, '')}/${uniqueKey}`;

  const supabaseAdmin = DB();

  // 1. Check if EXACT same asset exists in the SAME folder
  if (registerAsset) {
    const { data: sameFolderAsset } = await supabaseAdmin
      .from('assets')
      .select('*')
      .eq('file_url', publicUrl)
      .eq('folder', folder)
      .limit(1)
      .maybeSingle();

    if (sameFolderAsset) {
      return {
        fileUrl: publicUrl,
        asset: sameFolderAsset,
        isDuplicate: true,
      };
    }

    // Check if it exists in another folder (reuse R2 file, insert new DB record)
    const { data: otherFolderAsset } = await supabaseAdmin
      .from('assets')
      .select('*')
      .eq('file_url', publicUrl)
      .limit(1)
      .maybeSingle();

    if (otherFolderAsset) {
      const { data: assetData, error: insertError } = await supabaseAdmin
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

      if (insertError) throw insertError;

      return {
        fileUrl: publicUrl,
        asset: assetData,
        isDuplicate: false,
      };
    }
  }

  // 2. Check Storage (R2) to see if the physical file already exists
  let fileExists = false;
  try {
    const headCommand = new HeadObjectCommand({
      Bucket: r2BucketName,
      Key: uniqueKey,
    });
    await r2Client.send(headCommand);
    fileExists = true;
  } catch (err: any) {
    if (err.name !== 'NotFound') {
      console.error('Error checking R2 file existence:', err);
    }
  }

  if (!fileExists) {
    try {
      const putCommand = new PutObjectCommand({
        Bucket: r2BucketName,
        Key: uniqueKey,
        Body: buffer,
        ContentType: file.type,
      });
      await r2Client.send(putCommand);
    } catch (uploadError: any) {
      console.error('R2 upload failed:', uploadError);
      throw Object.assign(
        new Error(`R2 Storage upload failed: ${uploadError.message}. Ensure R2 credentials and bucket configs are correct.`),
        { statusCode: 500 }
      );
    }
  }

  if (!registerAsset) {
    return {
      fileUrl: publicUrl,
      asset: { name: file.name, file_url: publicUrl, folder },
      isDuplicate: false,
    };
  }

  const { data: assetData, error: insertError } = await supabaseAdmin
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

  if (insertError) {
    throw insertError;
  }

  return {
    fileUrl: publicUrl,
    asset: assetData,
    isDuplicate: false,
  };
}

/**
 * Check if a file URL is referenced by any other assets (excluding the ones being deleted)
 * or by any canvas templates.
 */
async function isFileUrlReferenced(supabaseAdmin: any, fileUrl: string, excludeAssetIds: string[] = []): Promise<boolean> {
  // Check if used by other assets
  let assetQuery = supabaseAdmin
    .from('assets')
    .select('id', { count: 'exact', head: true })
    .eq('file_url', fileUrl);

  if (excludeAssetIds.length > 0) {
    assetQuery = assetQuery.not('id', 'in', `(${excludeAssetIds.join(',')})`);
  }

  const { count: assetCount } = await assetQuery;
  if (assetCount && assetCount > 0) return true;

  // Check if used by any templates
  const { data: templates } = await supabaseAdmin
    .from('templates')
    .select('canvas_data');

  if (templates) {
    for (const t of templates) {
      const canvasData = t.canvas_data as any;
      if (canvasData?.canvas?.backgroundUrl === fileUrl) return true;
      if (canvasData?.layers) {
        for (const layer of canvasData.layers) {
          if (layer.payload?.imageUrl === fileUrl || layer.payload?.defaultImageUrl === fileUrl) {
            return true;
          }
        }
      }
    }
  }

  return false;
}

/**
 * Delete an asset: remove from R2 + DB.
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

  // 2. Check if the URL is referenced elsewhere
  const referenced = await isFileUrlReferenced(supabaseAdmin, asset.file_url, [id]);

  if (!referenced) {
    // Extract R2 storage key (relative path from base URL)
    let key = '';
    const baseUrl = r2PublicUrl.replace(/\/$/, '');
    if (asset.file_url.startsWith(baseUrl)) {
      key = asset.file_url.substring(baseUrl.length).replace(/^\//, '');
    } else {
      // Fallback: extract key from general URL structure
      const idx = asset.file_url.indexOf('/assets/');
      if (idx !== -1) {
        key = asset.file_url.substring(idx + 1);
      }
    }

    if (key) {
      try {
        const deleteCommand = new DeleteObjectCommand({
          Bucket: r2BucketName,
          Key: key,
        });
        await r2Client.send(deleteCommand);
      } catch (err) {
        console.error('R2 storage delete exception:', err);
      }
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

  // 1. Fetch file URLs
  const { data: assets, error: fetchError } = await supabaseAdmin
    .from('assets')
    .select('id, file_url')
    .in('id', ids);

  if (fetchError || !assets) {
    throw Object.assign(new Error('Assets not found'), { statusCode: 404 });
  }

  // 2. Extract storage keys only for non-referenced assets
  const keys: string[] = [];
  const baseUrl = r2PublicUrl.replace(/\/$/, '');
  
  for (const asset of assets) {
    const referenced = await isFileUrlReferenced(supabaseAdmin, asset.file_url, ids);
    if (!referenced) {
      let key = '';
      if (asset.file_url.startsWith(baseUrl)) {
        key = asset.file_url.substring(baseUrl.length).replace(/^\//, '');
      } else {
        const idx = asset.file_url.indexOf('/assets/');
        if (idx !== -1) {
          key = asset.file_url.substring(idx + 1);
        }
      }
      if (key) keys.push(key);
    }
  }

  // 3. Remove from Storage in bulk
  if (keys.length > 0) {
    for (const key of keys) {
      try {
        const deleteCommand = new DeleteObjectCommand({
          Bucket: r2BucketName,
          Key: key,
        });
        await r2Client.send(deleteCommand);
      } catch (err) {
        console.error(`R2 storage bulk delete exception for key ${key}:`, err);
      }
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
  const allFolders = new Set<string>();
  const defaultFolders = ['backgrounds', 'stickers', 'fonts', 'uncategorized'];
  defaultFolders.forEach(f => allFolders.add(f));

  let page = 0;
  const pageSize = 1000;
  let hasMore = true;

  while (hasMore) {
    const { data, error } = await DB()
      .from('assets')
      .select('folder')
      .range(page * pageSize, (page + 1) * pageSize - 1);

    if (error) throw error;

    if (!data || data.length === 0) {
      hasMore = false;
    } else {
      data.forEach((item) => {
        if (item.folder) {
          allFolders.add(item.folder);
        }
      });
      if (data.length < pageSize) {
        hasMore = false;
      } else {
        page++;
      }
    }
  }

  return Array.from(allFolders);
}

/**
 * Create a new virtual folder by inserting a folder placeholder asset.
 */
export async function createAssetFolder(folderName: string) {
  const slug = toSlug(folderName);
  const { data, error } = await DB()
    .from('assets')
    .insert({
      name: '.folder_placeholder',
      folder: slug,
      file_url: 'placeholder',
    })
    .select()
    .single();

  if (error) throw error;
  return data;
}

/**
 * Rename a virtual folder by updating the folder column of all assets inside it.
 */
export async function renameAssetFolder(oldFolderName: string, newFolderName: string) {
  const oldSlug = oldFolderName.trim();
  const newSlug = toSlug(newFolderName);

  const { data, error } = await DB()
    .from('assets')
    .update({ folder: newSlug })
    .eq('folder', oldSlug)
    .select();

  if (error) throw error;
  return data;
}

/**
 * Delete a virtual folder. Supports soft delete (moving assets to uncategorized) or hard delete.
 */
export async function deleteAssetFolder(folderName: string, deleteFiles: boolean = false) {
  const slug = folderName.trim();
  const supabaseAdmin = DB();

  if (deleteFiles) {
    // Hard delete: Fetch all assets in this folder and delete them
    let query = supabaseAdmin.from('assets').select('id');
    if (slug === 'stickers') {
      query = query.in('folder', ['stickers', 'sticker']);
    } else {
      query = query.eq('folder', slug);
    }
    const { data: assets, error: fetchError } = await query;

    if (fetchError) throw fetchError;

    if (assets && assets.length > 0) {
      const ids = assets.map((a) => a.id);
      await deleteAssetsBulk(ids);
    }
  } else {
    // Soft delete: Move non-placeholder assets to 'uncategorized'
    let moveQuery = supabaseAdmin
      .from('assets')
      .update({ folder: 'uncategorized' })
      .not('name', 'eq', '.folder_placeholder');

    if (slug === 'stickers') {
      moveQuery = moveQuery.in('folder', ['stickers', 'sticker']);
    } else {
      moveQuery = moveQuery.eq('folder', slug);
    }
    const { error: moveError } = await moveQuery;

    if (moveError) throw moveError;

    // Delete the placeholder assets
    let deleteQuery = supabaseAdmin
      .from('assets')
      .delete()
      .eq('name', '.folder_placeholder');

    if (slug === 'stickers') {
      deleteQuery = deleteQuery.in('folder', ['stickers', 'sticker']);
    } else {
      deleteQuery = deleteQuery.eq('folder', slug);
    }
    const { error: deleteError } = await deleteQuery;

    if (deleteError) throw deleteError;
  }
}

