'use client';

import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { createCategorySchema, CreateCategoryInput } from '@/domains/categories/category.validator';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Loader2 } from 'lucide-react';
import { Category, useCreateCategory, useUpdateCategory } from '@/hooks/useCategories';
import { z } from 'zod';

interface CategoryFormModalProps {
  isOpen: boolean;
  onClose: () => void;
  categoryToEdit?: Category | null;
}

export function CategoryFormModal({ isOpen, onClose, categoryToEdit }: CategoryFormModalProps) {
  const isEditing = !!categoryToEdit;
  const createMutation = useCreateCategory();
  const updateMutation = useUpdateCategory();

  const form = useForm<CreateCategoryInput>({
    resolver: zodResolver(createCategorySchema),
    defaultValues: {
      name: '',
      order: 0,
    },
  });

  useEffect(() => {
    if (isOpen && categoryToEdit) {
      form.reset({
        name: categoryToEdit.name,
        order: categoryToEdit.order || 0,
      });
    } else if (isOpen && !categoryToEdit) {
      form.reset({
        name: '',
        order: 0,
      });
    }
  }, [isOpen, categoryToEdit, form]);

  const onSubmit = (data: CreateCategoryInput) => {
    if (isEditing) {
      updateMutation.mutate(
        { id: categoryToEdit.id, ...data },
        {
          onSuccess: () => {
            onClose();
          },
        }
      );
    } else {
      createMutation.mutate(data, {
        onSuccess: () => {
          onClose();
        },
      });
    }
  };

  const isPending = createMutation.isPending || updateMutation.isPending;

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="bg-slate-900 border border-slate-800 text-slate-100 rounded-2xl sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-lg font-bold text-slate-100">
            {isEditing ? 'Sửa danh mục' : 'Thêm danh mục mới'}
          </DialogTitle>
          <DialogDescription className="text-xs text-slate-400">
            {isEditing ? 'Cập nhật thông tin danh mục.' : 'Điền tên và thứ tự để tạo danh mục phân loại template mới.'}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-2">
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-300">Tên danh mục</label>
            <Input
              {...form.register('name')}
              placeholder="ví dụ: Sinh nhật, Lễ hội..."
              className="bg-slate-950 border-slate-800 text-slate-200 focus-visible:ring-indigo-600 rounded-xl"
            />
            {form.formState.errors.name && (
              <p className="text-xs text-rose-500">{form.formState.errors.name.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-300">Thứ tự hiển thị (Order)</label>
            <Input
              type="number"
              {...form.register('order', { valueAsNumber: true })}
              placeholder="0"
              className="bg-slate-950 border-slate-800 text-slate-200 focus-visible:ring-indigo-600 rounded-xl"
            />
            {form.formState.errors.order && (
              <p className="text-xs text-rose-500">{form.formState.errors.order.message}</p>
            )}
          </div>

          <DialogFooter className="mt-6">
            <Button
              type="button"
              variant="ghost"
              onClick={onClose}
              disabled={isPending}
              className="text-slate-400 hover:text-white rounded-xl"
            >
              Hủy
            </Button>
            <Button
              type="submit"
              disabled={isPending}
              className="bg-indigo-600 hover:bg-indigo-500 rounded-xl px-6 text-white"
            >
              {isPending && <Loader2 className="w-4 h-4 animate-spin mr-2" />}
              {isEditing ? 'Lưu thay đổi' : 'Tạo mới'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
