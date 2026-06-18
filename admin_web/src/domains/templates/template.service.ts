import { createSupabaseAdmin } from '@/lib/supabase';
import { CloudTemplate } from '@/types/cloud-template';
import JSZip from 'jszip';
import { gcd, buildInitialFabricState, remapClonedTemplateIds } from './template.helpers';

// ─── Types ────────────────────────────────────────────

export interface TemplateFilters {
  search?: string;
  categoryId?: string;
  status?: string;
  page?: number;
  limit?: number;
  sortBy?: string;
  sortOrder?: string;
}

export interface CreateTemplateInput {
  templateId: string;
  categoryId: string;
  title: string;
  baseWidth?: number;
  baseHeight?: number;
  backgroundUrl?: string | null;
}

export interface UpdateTemplateInput {
  title?: string;
  category_id?: string;
  status?: 'draft' | 'published';
  environment?: 'debug' | 'release' | 'all';
  thumbnail_url?: string | null;
  canvas_data?: CloudTemplate;
  fabric_state?: Record<string, unknown>;
}

export interface CloneTemplateInput {
  sourceId: string;
  newTemplateId: string;
  newTitle: string;
}

// ─── Service ──────────────────────────────────────────

const DB = () => createSupabaseAdmin();

/**
 * List templates with optional filters.
 */
export async function listTemplates(filters: TemplateFilters = {}) {
  const { search, categoryId, status, page = 1, limit = 20, sortBy = 'updated_at', sortOrder = 'desc' } = filters;
  
  const allowedSortFields = ['updated_at', 'created_at', 'title'];
  const finalSortBy = allowedSortFields.includes(sortBy) ? sortBy : 'updated_at';
  const finalSortOrder = sortOrder === 'asc' ? 'asc' : 'desc';

  // We use count: 'exact' to get the total number of items matching filters
  let query = DB().from('templates').select('*, categories(id, name)', { count: 'exact' });

  if (search) query = query.ilike('title', `%${search}%`);
  if (categoryId) query = query.eq('category_id', categoryId);
  if (status) query = query.eq('status', status);

  const from = (page - 1) * limit;
  const to = from + limit - 1;

  const { data, error, count } = await query
    .range(from, to)
    .order(finalSortBy, { ascending: finalSortOrder === 'asc' });

  if (error) throw error;

  return {
    templates: data,
    total: count || 0,
    hasMore: count ? from + data.length < count : false,
  };
}

/**
 * Get a single template by UUID.
 */
export async function getTemplateById(id: string) {
  const { data, error } = await DB()
    .from('templates')
    .select('*, categories(id, name)')
    .eq('id', id)
    .single();

  if (error) throw error;
  return data;
}

/**
 * Create a new template with initial CloudTemplate JSON.
 */
export async function createTemplate(input: CreateTemplateInput) {
  const width = input.baseWidth || 1080;
  const height = input.baseHeight || 1920;
  const divisor = gcd(width, height);
  const aspectRatio = `${width / divisor}:${height / divisor}`;

  const initialCanvasData: CloudTemplate = {
    templateId: input.templateId,
    categoryId: input.categoryId,
    metadata: {
      title: input.title,
      thumbnailUrl: input.backgroundUrl || '',
      status: 'draft',
      schemaVersion: 1,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    },
    canvas: {
      baseWidth: width,
      baseHeight: height,
      aspectRatio,
      backgroundUrl: input.backgroundUrl || null,
    },
    layers: [],
  };

  const { data, error } = await DB()
    .from('templates')
    .insert({
      template_id: input.templateId,
      category_id: input.categoryId,
      title: input.title,
      status: 'draft',
      thumbnail_url: input.backgroundUrl || null,
      canvas_data: initialCanvasData,
      fabric_state: buildInitialFabricState(input.backgroundUrl),
    })
    .select()
    .single();

  if (error) {
    if (error.code === '23505') {
      throw Object.assign(new Error('Template ID already exists'), { statusCode: 400, code: 'DUPLICATE_TEMPLATE_ID' });
    }
    throw error;
  }

  return data;
}

/**
 * Update a template (partial update by UUID).
 */
export async function updateTemplate(id: string, input: UpdateTemplateInput) {
  let finalCanvasData = input.canvas_data;
  if (input.title !== undefined && finalCanvasData === undefined) {
    const existing = await getTemplateById(id);
    if (existing && existing.canvas_data) {
      const canvasData = { ...existing.canvas_data } as CloudTemplate;
      if (canvasData.metadata) {
        canvasData.metadata.title = input.title;
        canvasData.metadata.updatedAt = Date.now();
      }
      finalCanvasData = canvasData;
    }
  }

  const { data, error } = await DB()
    .from('templates')
    .update({
      ...(input.title !== undefined && { title: input.title }),
      ...(input.category_id !== undefined && { category_id: input.category_id }),
      ...(input.status !== undefined && { status: input.status }),
      ...(input.environment !== undefined && { environment: input.environment }),
      ...(input.thumbnail_url !== undefined && { thumbnail_url: input.thumbnail_url }),
      ...(finalCanvasData !== undefined && { canvas_data: finalCanvasData }),
      ...(input.fabric_state !== undefined && { fabric_state: input.fabric_state }),
      updated_at: new Date().toISOString(),
    })
    .eq('id', id)
    .select()
    .single();

  if (error) throw error;
  return data;
}

export async function deleteTemplate(id: string) {
  const { error } = await DB().from('templates').delete().eq('id', id);
  if (error) throw error;
}

/**
 * Delete multiple templates by UUIDs.
 */
export async function deleteTemplatesBulk(ids: string[]) {
  if (!ids || ids.length === 0) return;
  const { error } = await DB().from('templates').delete().in('id', ids);
  if (error) throw error;
}

/**
 * Clone a template with new ID and title.
 */
export async function cloneTemplate(input: CloneTemplateInput) {
  // 1. Fetch source
  const source = await getTemplateById(input.sourceId);
  if (!source) {
    throw Object.assign(new Error('Source template not found'), { statusCode: 404 });
  }

  // 2. Clone canvas_data + fabric_state with remapped layer IDs
  const { canvasData: clonedCanvasData, fabricState: clonedFabricState } = remapClonedTemplateIds(
    source.canvas_data as CloudTemplate,
    source.fabric_state,
    input.newTemplateId
  );
  if (clonedCanvasData.metadata) {
    clonedCanvasData.metadata = {
      ...clonedCanvasData.metadata,
      title: input.newTitle,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    };
  }

  // 3. Insert
  const { data, error } = await DB()
    .from('templates')
    .insert({
      template_id: input.newTemplateId,
      category_id: source.category_id,
      title: input.newTitle,
      status: 'draft',
      thumbnail_url: source.thumbnail_url,
      canvas_data: clonedCanvasData,
      fabric_state: clonedFabricState,
    })
    .select()
    .single();

  if (error) {
    if (error.code === '23505') {
      throw Object.assign(new Error(`Template ID "${input.newTemplateId}" already exists`), {
        statusCode: 400,
        code: 'DUPLICATE_TEMPLATE_ID',
      });
    }
    throw error;
  }

  return data;
}

// ─── ZIP Export ───────────────────────────────────────

/**
 * Export a template as a ZIP bundle (template.json + assets).
 */
export async function exportTemplateZip(id: string): Promise<{ buffer: ArrayBuffer; filename: string }> {
  const template = await getTemplateById(id);
  if (!template) {
    throw Object.assign(new Error('Template not found'), { statusCode: 404 });
  }

  const templateData = template.canvas_data as CloudTemplate;
  const templateId = templateData.templateId || template.template_id;
  const exportTemplate = JSON.parse(JSON.stringify(templateData)) as CloudTemplate;

  const zip = new JSZip();
  const assetsFolder = zip.folder('assets');
  if (!assetsFolder) throw new Error('Failed to create assets folder in ZIP');

  const urlMap = new Map<string, { fileName: string; localPath: string }>();
  let assetCount = 0;

  const registerUrl = (url: string | null | undefined): string | null => {
    if (!url || !url.startsWith('http')) return url || null;
    if (urlMap.has(url)) return urlMap.get(url)!.localPath;

    const urlPath = url.split('?')[0];
    const ext = urlPath.split('.').pop() || 'webp';
    assetCount++;
    const fileName = `asset_${assetCount}.${ext}`;
    const localPath = `assets/${fileName}`;
    urlMap.set(url, { fileName, localPath });
    return localPath;
  };

  // Replace URLs with local paths
  if (exportTemplate.canvas.backgroundUrl) {
    exportTemplate.canvas.backgroundUrl = registerUrl(exportTemplate.canvas.backgroundUrl);
  }
  if (exportTemplate.layers) {
    for (const layer of exportTemplate.layers) {
      if (layer.payload) {
        if (layer.payload.imageUrl) layer.payload.imageUrl = registerUrl(layer.payload.imageUrl) || undefined;
        if (layer.payload.defaultImageUrl) layer.payload.defaultImageUrl = registerUrl(layer.payload.defaultImageUrl) || undefined;
      }
    }
  }

  // Download and add assets to ZIP
  for (const [url, info] of urlMap.entries()) {
    try {
      const res = await fetch(url);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const buffer = await res.arrayBuffer();
      assetsFolder.file(info.fileName, buffer);
    } catch (dlError) {
      console.error(`Failed to download asset: ${url}`, dlError);
    }
  }

  zip.file('template.json', JSON.stringify(exportTemplate, null, 2));

  if (template.fabric_state) {
    const fabricState =
      typeof template.fabric_state === 'string'
        ? template.fabric_state
        : JSON.stringify(template.fabric_state, null, 2);
    zip.file('fabric_state.json', fabricState);
  }

  const buffer = await zip.generateAsync({ type: 'arraybuffer' });

  return { buffer, filename: `${templateId}.zip` };
}

// ─── ZIP Import ───────────────────────────────────────

export interface ImportZipInput {
  fileBuffer: ArrayBuffer;
  categoryId?: string | null;
}

/**
 * Import a template from a ZIP bundle.
 */
export async function importTemplateZip(input: ImportZipInput) {
  const zip = await JSZip.loadAsync(input.fileBuffer);

  const jsonFile = zip.file('template.json');
  if (!jsonFile) {
    throw Object.assign(new Error('Invalid ZIP: template.json not found'), { statusCode: 400 });
  }

  const jsonText = await jsonFile.async('text');
  const cloudTemplate = JSON.parse(jsonText) as CloudTemplate;

  let importedFabricState: unknown = buildInitialFabricState(cloudTemplate.canvas?.backgroundUrl || '');
  const fabricStateFile = zip.file('fabric_state.json');
  if (fabricStateFile) {
    try {
      const fabricStateText = await fabricStateFile.async('text');
      importedFabricState = JSON.parse(fabricStateText);
    } catch {
      console.warn('Invalid fabric_state.json in ZIP — using initial fabric state');
    }
  }

  let categoryId = input.categoryId || cloudTemplate.categoryId;
  const supabaseAdmin = DB();

  // Upload assets from ZIP to Supabase Storage
  const uploadZipAsset = async (relPath: string): Promise<string> => {
    const cleanPath = relPath.startsWith('/') ? relPath.slice(1) : relPath;
    const zipEntry = zip.file(cleanPath);
    if (!zipEntry) throw new Error(`Asset ${relPath} not found in ZIP`);

    const fileBuffer = await zipEntry.async('nodebuffer');
    const fileName = cleanPath.split('/').pop() || 'asset.webp';
    const fileExtension = fileName.split('.').pop() || 'webp';
    const mimeType = `image/${fileExtension === 'jpg' ? 'jpeg' : fileExtension}`;
    const uniqueKey = `imported/${Date.now()}_${fileName}`;

    const { error: uploadError } = await supabaseAdmin.storage
      .from('assets')
      .upload(uniqueKey, fileBuffer, { contentType: mimeType, upsert: true });

    if (uploadError) throw new Error(`Upload failed: ${uploadError.message}`);

    const { data: { publicUrl } } = supabaseAdmin.storage.from('assets').getPublicUrl(uniqueKey);

    // Register in assets library
    await supabaseAdmin.from('assets').insert({
      name: fileName,
      folder: 'imported',
      file_url: publicUrl,
      file_size: fileBuffer.length,
      mime_type: mimeType,
    }).maybeSingle();

    return publicUrl;
  };

  // Process background
  if (cloudTemplate.canvas?.backgroundUrl && !cloudTemplate.canvas.backgroundUrl.startsWith('http')) {
    cloudTemplate.canvas.backgroundUrl = await uploadZipAsset(cloudTemplate.canvas.backgroundUrl);
  }

  // Process layers
  for (const layer of cloudTemplate.layers || []) {
    if (!layer.payload) continue;
    if (layer.payload.imageUrl && !layer.payload.imageUrl.startsWith('http')) {
      layer.payload.imageUrl = await uploadZipAsset(layer.payload.imageUrl);
    }
    if (layer.payload.defaultImageUrl && !layer.payload.defaultImageUrl.startsWith('http')) {
      layer.payload.defaultImageUrl = await uploadZipAsset(layer.payload.defaultImageUrl);
    }
  }

  // Validate/resolve category
  const { data: catData } = await supabaseAdmin.from('categories').select('id').eq('id', categoryId);
  if (!catData || catData.length === 0) {
    const { data: firstCat } = await supabaseAdmin.from('categories').select('id').limit(1);
    if (firstCat?.length) {
      categoryId = firstCat[0].id;
    } else {
      throw Object.assign(new Error('No categories exist. Create a category first.'), { statusCode: 400 });
    }
  }

  const status = cloudTemplate.metadata?.status || 'draft';
  const title = cloudTemplate.metadata?.title || 'Imported Template';

  const { data, error } = await supabaseAdmin
    .from('templates')
    .upsert(
      {
        template_id: cloudTemplate.templateId,
        category_id: categoryId,
        title,
        status,
        thumbnail_url: cloudTemplate.canvas?.backgroundUrl || cloudTemplate.metadata?.thumbnailUrl || null,
        canvas_data: cloudTemplate,
        fabric_state: importedFabricState,
        updated_at: new Date().toISOString(),
      },
      { onConflict: 'template_id' }
    )
    .select()
    .single();

  if (error) throw error;
  return data;
}

// ─── Demo/Collage ─────────────────────────────────────

const FALLBACK_CATEGORY_NAMES = ['Ẩm thực', 'Food', 'Món ăn', 'Collage'];

async function resolveCategoryId(supabase: ReturnType<typeof DB>, requestedCategoryId?: string | null) {
  if (requestedCategoryId) {
    const { data: cat } = await supabase.from('categories').select('id').eq('id', requestedCategoryId).maybeSingle();
    if (cat?.id) return cat.id;
  }

  const { data: categories } = await supabase.from('categories').select('id, name, "order"').order('order', { ascending: true });
  const existing = categories || [];

  for (const wanted of FALLBACK_CATEGORY_NAMES) {
    const match = existing.find((c: any) => c.name?.toLowerCase().includes(wanted.toLowerCase()));
    if (match?.id) return match.id;
  }

  if (existing.length > 0) return existing[0].id;

  const { data: maxOrder } = await supabase.from('categories').select('"order"').order('order', { ascending: false }).limit(1).maybeSingle();
  const nextOrder = (maxOrder?.order ?? 0) + 1;
  const { data: inserted, error: catError } = await supabase
    .from('categories')
    .insert({ name: 'Ẩm thực', order: nextOrder })
    .select('id')
    .single();

  if (catError) throw catError;
  return inserted!.id;
}

export async function createDemoCollage(input: { templateId?: string; categoryId?: string; origin: string }) {
  const supabase = DB();
  const categoryId = await resolveCategoryId(supabase, input.categoryId);
  const { buildFoodCameraCollageTemplate, foodCameraCollageTemplateId } = await import('@/lib/demo-templates/food-camera-collage');

  const effectiveTemplateId = input.templateId?.trim() || foodCameraCollageTemplateId;
  const template = buildFoodCameraCollageTemplate({
    origin: input.origin,
    categoryId,
    templateId: effectiveTemplateId,
  });

  const { data, error } = await supabase
    .from('templates')
    .upsert(
      {
        template_id: template.templateId,
        category_id: categoryId,
        title: template.metadata.title,
        status: template.metadata.status,
        thumbnail_url: template.metadata.thumbnailUrl,
        canvas_data: template,
        fabric_state: buildInitialFabricState(template.canvas.backgroundUrl || ''),
        updated_at: new Date().toISOString(),
      },
      { onConflict: 'template_id' }
    )
    .select('*, categories(id, name)')
    .single();

  if (error) throw error;
  return data;
}
