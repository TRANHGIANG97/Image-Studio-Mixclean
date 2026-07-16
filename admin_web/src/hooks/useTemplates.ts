import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { apiClient } from '@/lib/apiClient';
import { CloudTemplate } from '@/types/cloud-template';
import { CURRENT_SCHEMA_VERSION } from '@/lib/schema/template-contract';
import { parsePsdOnClient, ClientPsdImportResult } from '@/lib/psd-client-import';

// Re-use existing strict CloudTemplate type instead of `any`
export type Template = {
  id: string;
  template_id: string;
  title: string;
  status: 'draft' | 'published';
  environment?: 'debug' | 'release' | 'all';
  thumbnail_url: string | null;
  canvas_data: CloudTemplate;       // was `any` — now strict
  updated_at: string;
  categories?: {
    id: string;
    name: string;
  };
};

type TemplatesResponse = { templates: Template[]; total: number; hasMore: boolean };
type TemplateResponse  = { template: Template };

export type TemplateFilters = {
  search?: string;
  categoryId?: string;
  status?: string;
  page?: number;
  limit?: number;
  sortBy?: string;
  sortOrder?: string;
};

// --- QUERY HOOKS ---

export function useTemplates(filters: TemplateFilters = {}) {
  return useInfiniteQuery({
    queryKey: ['templates', { ...filters, page: undefined }], // omit page from key since it's managed by infinite query
    queryFn: ({ pageParam = 1 }) => {
      const query = new URLSearchParams();
      if (filters.search)     query.append('search',     filters.search);
      if (filters.categoryId) query.append('categoryId', filters.categoryId);
      if (filters.status)     query.append('status',     filters.status);
      if (filters.sortBy)     query.append('sortBy',     filters.sortBy);
      if (filters.sortOrder)  query.append('sortOrder',  filters.sortOrder);
      query.append('page', String(pageParam));
      query.append('limit', String(filters.limit || 20));

      return apiClient.get<TemplatesResponse>(`/api/templates?${query.toString()}`);
    },
    initialPageParam: 1,
    getNextPageParam: (lastPage, allPages) => {
      return lastPage.hasMore ? allPages.length + 1 : undefined;
    },
  });
}

// --- MUTATION HOOKS ---

export function useCreateTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: any) => apiClient.post<TemplateResponse>('/api/templates', payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['templates'] });
      toast.success('Tạo template thành công!');
    },
    onError: (error: any) => toast.error(`Lỗi tạo template: ${error.message}`),
  });
}

export function useCloneTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: { sourceId: string; newTemplateId: string; newTitle: string }) =>
      apiClient.post('/api/templates/clone', payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['templates'] });
      toast.success('Nhân bản template thành công!');
    },
    onError: (error: any) => toast.error(`Lỗi nhân bản: ${error.message}`),
  });
}

export function useDeleteTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/templates/${id}`),
    onMutate: async (id) => {
      await queryClient.cancelQueries({ queryKey: ['templates'] });
      const snapshots = queryClient.getQueriesData<any>({ queryKey: ['templates'] });
      queryClient.setQueriesData<any>({ queryKey: ['templates'] }, (prev: any) => {
        if (!prev) return prev;
        if ('pages' in prev) {
          return {
            ...prev,
            pages: prev.pages.map((page: any) => ({
              ...page,
              templates: page.templates.filter((t: any) => t.id !== id),
            })),
          };
        }
        if (Array.isArray(prev)) {
          return prev.filter((t: any) => t.id !== id);
        }
        return prev;
      });
      return { snapshots };
    },
    onError: (error: any, _id, context) => {
      if (context?.snapshots) {
        context.snapshots.forEach(([key, data]) => queryClient.setQueryData(key, data));
      }
      toast.error(`Lỗi xóa: ${error.message}`);
    },
    onSuccess: () => {
      toast.success('Xóa template thành công!');
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['templates'] });
    },
  });
}

export function useBulkDeleteTemplates() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (ids: string[]) => apiClient.delete('/api/templates', { ids }),
    onMutate: async (ids) => {
      await queryClient.cancelQueries({ queryKey: ['templates'] });
      const snapshots = queryClient.getQueriesData<any>({ queryKey: ['templates'] });
      queryClient.setQueriesData<any>({ queryKey: ['templates'] }, (prev: any) => {
        if (!prev) return prev;
        if ('pages' in prev) {
          return {
            ...prev,
            pages: prev.pages.map((page: any) => ({
              ...page,
              templates: page.templates.filter((t: any) => !ids.includes(t.id)),
            })),
          };
        }
        if (Array.isArray(prev)) {
          return prev.filter((t: any) => !ids.includes(t.id));
        }
        return prev;
      });
      return { snapshots };
    },
    onError: (error: any, _ids, context) => {
      if (context?.snapshots) {
        context.snapshots.forEach(([key, data]) => queryClient.setQueryData(key, data));
      }
      toast.error(`Lỗi xóa hàng loạt: ${error.message}`);
    },
    onSuccess: () => {
      toast.success('Xóa hàng loạt template thành công!');
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['templates'] });
    },
  });
}

export function useBulkUpdateTemplatesStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: { ids: string[]; status?: 'draft' | 'published'; environment?: 'debug' | 'release' | 'all' }) =>
      apiClient.put<{ message: string }>('/api/templates', payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['templates'] });
      toast.success('Cập nhật trạng thái hàng loạt thành công!');
    },
    onError: (error: any) => toast.error(`Lỗi cập nhật hàng loạt: ${error.message}`),
  });
}

export function useUpdateTemplateStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: { id: string; status: string; environment: string; canvas_data: CloudTemplate }) =>
      apiClient.put<TemplateResponse>(`/api/templates/${payload.id}`, {
        status:      payload.status,
        environment: payload.environment,
        canvas_data: payload.canvas_data,
      }),
    onMutate: async (payload) => {
      await queryClient.cancelQueries({ queryKey: ['templates'] });
      const snapshots = queryClient.getQueriesData<any>({ queryKey: ['templates'] });
      queryClient.setQueriesData<any>({ queryKey: ['templates'] }, (prev: any) => {
        if (!prev) return prev;
        if ('pages' in prev) {
          return {
            ...prev,
            pages: prev.pages.map((page: any) => ({
              ...page,
              templates: page.templates.map((t: any) =>
                t.id === payload.id
                  ? {
                      ...t,
                      status: payload.status as 'draft' | 'published',
                      canvas_data: payload.canvas_data,
                    }
                  : t
              ),
            })),
          };
        }
        if (Array.isArray(prev)) {
          return prev.map((t: any) =>
            t.id === payload.id
              ? {
                  ...t,
                  status: payload.status as 'draft' | 'published',
                  canvas_data: payload.canvas_data,
                }
              : t
          );
        }
        return prev;
      });
      return { snapshots };
    },
    onError: (error: any, _payload, context) => {
      if (context?.snapshots) {
        context.snapshots.forEach(([key, data]) => queryClient.setQueryData(key, data));
      }
      toast.error(`Lỗi cập nhật trạng thái: ${error.message}`);
    },
    onSuccess: () => {
      toast.success('Cập nhật trạng thái thành công!');
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['templates'] });
    },
  });
}

export function useUpdateTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: { id: string; title?: string; category_id?: string; status?: string; environment?: string; canvas_data?: CloudTemplate }) =>
      apiClient.put<TemplateResponse>(`/api/templates/${payload.id}`, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['templates'] });
      toast.success('Cập nhật template thành công!');
    },
    onError: (error: any) => toast.error(`Lỗi cập nhật template: ${error.message}`),
  });
}

export function useImportTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: { file: File; categoryId?: string }) => {
      const formData = new FormData();
      formData.append('file', payload.file);
      if (payload.categoryId) formData.append('categoryId', payload.categoryId);
      return apiClient.upload('/api/templates/import', formData);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['templates'] });
      toast.success('Nhập template từ ZIP thành công!');
    },
    onError: (error: any) => toast.error(`Lỗi nhập template: ${error.message}`),
  });
}

export function useImportPsdTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: { 
      file?: File; 
      categoryId?: string; 
      templateId?: string; 
      title?: string;
      exportLayers?: boolean;
      exportFolderName?: string;
      preParsedResult?: ClientPsdImportResult;
      onLog?: (msg: string) => void | Promise<void>;
    }) => {
      const startedAt = Date.now();
      const log = async (msg: string) => {
        const elapsed = ((Date.now() - startedAt) / 1000).toFixed(1);
        await payload.onLog?.(`[${elapsed}s] ${msg}`);
      };

      // 1. Parse PSD on the client-side or use pre-parsed result
      await log(payload.preParsedResult ? 'Dùng kết quả parse sẵn...' : 'Bắt đầu parse PSD trên trình duyệt...');
      const importResult = payload.preParsedResult || await parsePsdOnClient(
        payload.file!,
        payload.categoryId,
        payload.templateId,
        payload.title,
        payload.onLog
      );

      await log(
        `Parse hoàn tất: ${importResult.canvasWidth}×${importResult.canvasHeight}, ` +
          `${importResult.layers.length} layer` +
          (importResult.backgroundBlob ? ', có background' : ', không có background'),
      );
      for (const warning of importResult.warnings ?? []) {
        await log(`⚠ ${warning}`);
      }

      // 2. Upload composite thumbnail
      let thumbnailUrl = '';
      if (importResult.thumbnailBlob) {
        await log(`Đang tải thumbnail lên server (${(importResult.thumbnailBlob.size / 1024).toFixed(1)} KB)...`);
        const thumbFormData = new FormData();
        const slug = importResult.title.toLowerCase().replace(/[^a-z0-9]+/g, '_');
        thumbFormData.append('file', new File([importResult.thumbnailBlob], `${slug}_thumbnail.webp`, { type: 'image/webp' }));
        thumbFormData.append('folder', 'imported-psd');
        thumbFormData.append('registerAsset', 'false'); // Don't show in gallery
        const uploadThumbRes = await apiClient.upload<{ fileUrl: string }>('/api/upload', thumbFormData);
        thumbnailUrl = uploadThumbRes.fileUrl;
        await log('✓ Đã tải xong thumbnail.');
      }

      // 3. Upload background if it exists
      let backgroundUrl = '';
      if (importResult.backgroundBlob) {
        await log(`Đang tải background lên server (${(importResult.backgroundBlob.size / 1024).toFixed(1)} KB)...`);
        const bgFormData = new FormData();
        bgFormData.append('file', new File([importResult.backgroundBlob], `psd_background_${Date.now()}.webp`, { type: 'image/webp' }));
        bgFormData.append('folder', 'imported-psd');
        bgFormData.append('registerAsset', 'false');
        const uploadBgRes = await apiClient.upload<{ fileUrl: string }>('/api/upload', bgFormData);
        backgroundUrl = uploadBgRes.fileUrl;
        await log('✓ Đã tải xong background.');
      }

      // 4. Upload each image layer
      const finalLayers = [];
      const exportFolder = payload.exportLayers && payload.exportFolderName
        ? payload.exportFolderName.trim().replace(/\//g, '_')
        : '';

      const totalLayers = importResult.layers.filter(l => l.type === 'IMAGE' && l.imageBlob).length;
      let uploadedCount = 0;
      if (totalLayers > 0) {
        await log(`Bắt đầu upload ${totalLayers} layer ảnh lên server...`);
      }

      for (const layer of importResult.layers) {
        if (layer.type === 'IMAGE' && layer.imageBlob) {
          uploadedCount++;
          const sizeKb = (layer.imageBlob.size / 1024).toFixed(1);
          await log(`[${uploadedCount}/${totalLayers}] Upload "${layer.name}" (${sizeKb} KB)...`);
          const layerFormData = new FormData();
          layerFormData.append('file', new File([layer.imageBlob], layer.fileName || 'layer.webp', { type: 'image/webp' }));
          
          if (exportFolder) {
            layerFormData.append('folder', exportFolder);
            layerFormData.append('registerAsset', 'true'); // Register layer image in gallery so it can be re-used
            if (payload.categoryId) {
              layerFormData.append('categoryId', payload.categoryId);
            }
          } else {
            layerFormData.append('folder', 'imported-psd-temp');
            layerFormData.append('registerAsset', 'false');
          }

          try {
            const uploadLayerRes = await apiClient.upload<{ fileUrl: string }>('/api/upload', layerFormData);
            layer.payload.imageUrl = uploadLayerRes.fileUrl;
            layer.payload.defaultImageUrl = uploadLayerRes.fileUrl;
            await log(`[${uploadedCount}/${totalLayers}] Thành công: "${layer.name}"`);
          } catch (uploadErr) {
            console.error(`Failed to upload PSD layer image "${layer.name}":`, uploadErr);
            await log(`[${uploadedCount}/${totalLayers}] Lỗi upload "${layer.name}": ${(uploadErr as any).message || uploadErr}`);
          }
        }

        // Remove Blobs/fileNames before saving to template database JSON
        const { imageBlob, fileName, ...cleanLayer } = layer as any;
        finalLayers.push(cleanLayer);
      }

      // 5. Construct final CloudTemplate canvas data
      const canvasData = {
        templateId: importResult.templateId,
        categoryId: importResult.categoryId,
        metadata: {
          title: importResult.title,
          thumbnailUrl: thumbnailUrl || null,
          status: 'draft',
          schemaVersion: CURRENT_SCHEMA_VERSION,
          createdAt: Date.now(),
          updatedAt: Date.now(),
        },
        canvas: {
          baseWidth: importResult.canvasWidth,
          baseHeight: importResult.canvasHeight,
          aspectRatio: importResult.aspectRatio,
          backgroundUrl: backgroundUrl || null,
          backgroundColorArgb: null,
        },
        layers: finalLayers,
      };

      // 6. Create the template database record via backend API
      await log('Đang lưu template vào database...');
      const created = await apiClient.post('/api/templates', {
        templateId: importResult.templateId,
        categoryId: importResult.categoryId,
        title: importResult.title,
        baseWidth: importResult.canvasWidth,
        baseHeight: importResult.canvasHeight,
        backgroundUrl: backgroundUrl || null,
        thumbnailUrl: thumbnailUrl || null,
        canvasData,
      });
      await log(`Hoàn thành nhập template! Tổng thời gian ${((Date.now() - startedAt) / 1000).toFixed(1)}s`);
      return created;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['templates'] });
      toast.success('Nhập template từ PSD thành công!');
    },
    onError: (error: any) => toast.error(`Lỗi nhập PSD: ${error.message}`),
  });
}
