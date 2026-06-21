import { useInfiniteQuery, useMutation, useQueryClient, useQuery } from '@tanstack/react-query';
import { toast } from 'sonner';
import { apiClient } from '@/lib/apiClient';

export type Asset = {
  id: string;
  name: string;
  folder: string;
  file_url: string;
  file_size: number;
  mime_type: string;
  category_id: string | null;
  created_at: string;
};

type AssetsResponse = { assets: Asset[]; total: number; hasMore: boolean };

export type AssetFilters = {
  search?: string;
  folder?: string;
  categoryId?: string;
  mimeType?: string;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
  page?: number;
  limit?: number;
};

// --- HOOKS ---

export function useAssets(filters: AssetFilters = {}) {
  return useInfiniteQuery({
    queryKey: ['assets', { ...filters, page: undefined }],
    queryFn: ({ pageParam = 1 }) => {
      const query = new URLSearchParams();
      if (filters.search) query.append('search', filters.search);
      if (filters.folder) query.append('folder', filters.folder);
      if (filters.categoryId) query.append('categoryId', filters.categoryId);
      if (filters.mimeType) query.append('mimeType', filters.mimeType);
      if (filters.sortBy) query.append('sortBy', filters.sortBy);
      if (filters.sortOrder) query.append('sortOrder', filters.sortOrder);
      query.append('page', String(pageParam));
      query.append('limit', String(filters.limit || 20));

      return apiClient.get<AssetsResponse>(`/api/assets?${query.toString()}`);
    },
    initialPageParam: 1,
    getNextPageParam: (lastPage, allPages) => {
      return lastPage.hasMore ? allPages.length + 1 : undefined;
    },
  });
}

export function useFolders() {
  return useQuery({
    queryKey: ['folders'],
    queryFn: () => apiClient.get<{ folders: string[] }>('/api/assets/folders').then((res) => res.folders),
  });
}

export function useUploadAsset() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: { file: File; folder?: string; categoryId?: string | null }) => {
      const formData = new FormData();
      formData.append('file', payload.file);
      if (payload.folder) formData.append('folder', payload.folder);
      if (payload.categoryId) formData.append('categoryId', payload.categoryId);
      return apiClient.upload<{ success: boolean; fileUrl: string; asset: Asset; isDuplicate: boolean }>('/api/upload', formData);
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['assets'] });
      queryClient.invalidateQueries({ queryKey: ['folders'] });
      if (data?.isDuplicate) {
        toast.info('Tài nguyên đã tồn tại (không tạo bản sao trùng lặp)!');
      } else {
        toast.success('Upload tài nguyên thành công!');
      }
    },
    onError: (error: any) => {
      toast.error(`Lỗi upload: ${error.message}`);
    },
  });
}

export function useDeleteAsset() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/assets?id=${id}`),
    onMutate: async (id) => {
      await queryClient.cancelQueries({ queryKey: ['assets'] });
      const snapshots = queryClient.getQueriesData<any>({ queryKey: ['assets'] });
      queryClient.setQueriesData<any>({ queryKey: ['assets'] }, (prev: any) => {
        if (!prev) return prev;
        if ('pages' in prev) {
          return {
            ...prev,
            pages: prev.pages.map((page: any) => ({
              ...page,
              assets: page.assets.filter((a: any) => a.id !== id),
            })),
          };
        }
        if (Array.isArray(prev)) {
          return prev.filter((a: any) => a.id !== id);
        }
        return prev;
      });
      return { snapshots };
    },
    onError: (error: any, _id, context) => {
      if (context?.snapshots) {
        context.snapshots.forEach(([key, data]) => {
          queryClient.setQueryData(key, data);
        });
      }
      toast.error(`Lỗi xóa: ${error.message}`);
    },
    onSuccess: () => {
      toast.success('Xóa tài nguyên thành công!');
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['assets'] });
    },
  });
}

export function useDeleteAssetsBulk() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (ids: string[]) => apiClient.delete(`/api/assets?ids=${ids.join(',')}`),
    onMutate: async (ids) => {
      await queryClient.cancelQueries({ queryKey: ['assets'] });
      const snapshots = queryClient.getQueriesData<any>({ queryKey: ['assets'] });
      queryClient.setQueriesData<any>({ queryKey: ['assets'] }, (prev: any) => {
        if (!prev) return prev;
        if ('pages' in prev) {
          return {
            ...prev,
            pages: prev.pages.map((page: any) => ({
              ...page,
              assets: page.assets.filter((a: any) => !ids.includes(a.id)),
            })),
          };
        }
        if (Array.isArray(prev)) {
          return prev.filter((a: any) => !ids.includes(a.id));
        }
        return prev;
      });
      return { snapshots };
    },
    onError: (error: any, _ids, context) => {
      if (context?.snapshots) {
        context.snapshots.forEach(([key, data]) => {
          queryClient.setQueryData(key, data);
        });
      }
      toast.error(`Lỗi xóa: ${error.message}`);
    },
    onSuccess: () => {
      toast.success('Xóa hàng loạt tài nguyên thành công!');
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['assets'] });
    },
  });
}

export function useUpdateAssetsFolder() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: { ids: string[]; folder: string }) =>
      apiClient.put<{ success: boolean; count?: number }>('/api/assets', payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['assets'] });
      queryClient.invalidateQueries({ queryKey: ['folders'] });
      toast.success('Cập nhật phân loại tài nguyên thành công!');
    },
    onError: (error: any) => {
      toast.error(`Lỗi di chuyển tài nguyên: ${error.message}`);
    },
  });
}

export function useCreateFolder() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: { folderName: string }) =>
      apiClient.post<{ success: boolean; folder: any }>('/api/assets/folders', payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['folders'] });
      toast.success('Tạo thư mục thành công!');
    },
    onError: (error: any) => {
      toast.error(`Lỗi tạo thư mục: ${error.message}`);
    },
  });
}

export function useRenameFolder() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: { oldFolderName: string; newFolderName: string }) =>
      apiClient.put<{ success: boolean }>('/api/assets/folders', payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['assets'] });
      queryClient.invalidateQueries({ queryKey: ['folders'] });
      toast.success('Đổi tên thư mục thành công!');
    },
    onError: (error: any) => {
      toast.error(`Lỗi đổi tên thư mục: ${error.message}`);
    },
  });
}

export function useDeleteFolder() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: { folderName: string; deleteFiles: boolean }) =>
      apiClient.delete<{ success: boolean }>(
        `/api/assets/folders?folderName=${encodeURIComponent(payload.folderName)}&deleteFiles=${payload.deleteFiles}`
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['assets'] });
      queryClient.invalidateQueries({ queryKey: ['folders'] });
      toast.success('Xóa thư mục thành công!');
    },
    onError: (error: any) => {
      toast.error(`Lỗi xóa thư mục: ${error.message}`);
    },
  });
}

