import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { apiClient } from '@/lib/apiClient';

export type Category = {
  id: string;
  name: string;
  order: number;
  created_at: string;
  updated_at: string;
};

type CategoriesResponse = { categories: Category[] };

// --- HOOKS ---

export function useCategories() {
  return useQuery({
    queryKey: ['categories'],
    queryFn: () =>
      apiClient
        .get<CategoriesResponse>('/api/categories')
        .then((res) => res.categories || []),
  });
}

export function useCreateCategory() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: { name: string; order?: number }) =>
      apiClient.post('/api/categories', payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      toast.success('Thêm danh mục thành công!');
    },
    onError: (error: any) => {
      toast.error(`Lỗi: ${error.message}`);
    },
  });
}

export function useUpdateCategory() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: { id: string; name?: string; order?: number }) =>
      apiClient.put('/api/categories', payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      toast.success('Cập nhật danh mục thành công!');
    },
    onError: (error: any) => {
      toast.error(`Lỗi: ${error.message}`);
    },
  });
}

export function useDeleteCategory() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) =>
      apiClient.delete(`/api/categories?id=${id}`),
    onMutate: async (id) => {
      await queryClient.cancelQueries({ queryKey: ['categories'] });
      const snapshot = queryClient.getQueryData<Category[]>(['categories']);
      queryClient.setQueryData<Category[]>(['categories'], (prev) =>
        prev ? prev.filter((c) => c.id !== id) : []
      );
      return { snapshot };
    },
    onError: (error: any, _id, context) => {
      // Rollback
      if (context?.snapshot) {
        queryClient.setQueryData(['categories'], context.snapshot);
      }
      toast.error(`Lỗi xóa: ${error.message}`);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      toast.success('Xóa danh mục thành công!');
    },
  });
}
