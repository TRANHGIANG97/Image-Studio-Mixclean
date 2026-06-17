import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { apiClient } from '@/lib/apiClient';
import { CloudTemplate } from '@/types/cloud-template';

// Re-use existing strict CloudTemplate type instead of `any`
export type Template = {
  id: string;
  template_id: string;
  title: string;
  status: 'draft' | 'published';
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
    mutationFn: async (payload: { file: File; categoryId?: string; templateId?: string; title?: string }) => {
      // 1. Get signed upload URL from backend
      const { signedUrl, storagePath } = await apiClient.get<{ signedUrl: string; storagePath: string }>(
        `/api/templates/import-psd?fileName=${encodeURIComponent(payload.file.name)}`
      );

      // 2. Upload file directly to Supabase storage signed URL using standard PUT fetch
      // We use standard fetch here to avoid apiClient's 30s timeout for potentially large files.
      const uploadRes = await fetch(signedUrl, {
        method: 'PUT',
        body: payload.file,
        headers: {
          'Content-Type': payload.file.type || 'application/octet-stream',
        },
      });

      if (!uploadRes.ok) {
        const errorText = await uploadRes.text().catch(() => '');
        throw new Error(`Upload to storage failed (${uploadRes.status}): ${errorText || uploadRes.statusText}`);
      }

      // 3. Request route API to process the imported PSD file from storage
      return apiClient.post('/api/templates/import-psd', {
        storagePath,
        categoryId: payload.categoryId,
        templateId: payload.templateId,
        title: payload.title,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['templates'] });
      toast.success('Nhập template từ PSD thành công!');
    },
    onError: (error: any) => toast.error(`Lỗi nhập PSD: ${error.message}`),
  });
}
