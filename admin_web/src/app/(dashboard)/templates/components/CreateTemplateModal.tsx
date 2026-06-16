'use client';

import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { createTemplateSchema, CreateTemplateInput } from '@/domains/templates/template.validator';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Loader2 } from 'lucide-react';
import { Category } from '@/hooks/useCategories';
import { useCreateTemplate } from '@/hooks/useTemplates';
import { useUploadAsset } from '@/hooks/useAssets';

interface CreateTemplateModalProps {
  isOpen: boolean;
  onClose: () => void;
  categories: Category[];
}

export function CreateTemplateModal({ isOpen, onClose, categories }: CreateTemplateModalProps) {
  const [aspectRatioPreset, setAspectRatioPreset] = useState<'9_16' | '1_1' | '4_5' | 'custom'>('1_1');
  const [bgFile, setBgFile] = useState<File | null>(null);

  const createMutation = useCreateTemplate();
  const uploadMutation = useUploadAsset();

  const form = useForm<CreateTemplateInput>({
    resolver: zodResolver(createTemplateSchema),
    defaultValues: {
      templateId: '',
      title: '',
      categoryId: '',
      baseWidth: 1080,
      baseHeight: 1080,
    },
  });

  useEffect(() => {
    if (isOpen) {
      form.reset({
        templateId: `TPL_${Math.random().toString(36).substring(2, 8).toUpperCase()}`,
        title: '',
        categoryId: categories.length > 0 ? categories[0].id : '',
        baseWidth: 1080,
        baseHeight: 1080,
      });
      setAspectRatioPreset('1_1');
      setBgFile(null);
    }
  }, [isOpen, categories, form]);

  const onSubmit = async (data: CreateTemplateInput) => {
    try {
      let backgroundUrl = '';
      if (bgFile) {
        const uploadResult = await uploadMutation.mutateAsync({ file: bgFile, folder: 'backgrounds' }) as { fileUrl: string };
        backgroundUrl = uploadResult.fileUrl;
      }
      
      await createMutation.mutateAsync({
        ...data,
        backgroundUrl,
      });

      onClose();
    } catch (error) {
      // Error is handled by hooks toast
    }
  };

  const isPending = createMutation.isPending || uploadMutation.isPending;

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="bg-slate-900 border border-slate-800 text-slate-100 rounded-2xl sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-lg font-bold text-slate-100">Tạo Template mới</DialogTitle>
          <DialogDescription className="text-xs text-slate-400">
            Điền các thông số cơ bản để khởi tạo một bản nháp canvas.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-2">
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-300">ID Template (Unique)</label>
            <Input
              {...form.register('templateId')}
              placeholder="ví dụ: TPL_SUMMER_01"
              className="bg-slate-950 border-slate-800 text-slate-200 focus-visible:ring-indigo-600 rounded-xl"
            />
            {form.formState.errors.templateId && <p className="text-xs text-rose-500">{form.formState.errors.templateId.message}</p>}
          </div>
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-300">Tiêu đề template</label>
            <Input
              {...form.register('title')}
              placeholder="ví dụ: Thời trang hè rực rỡ"
              className="bg-slate-950 border-slate-800 text-slate-200 focus-visible:ring-indigo-600 rounded-xl"
            />
            {form.formState.errors.title && <p className="text-xs text-rose-500">{form.formState.errors.title.message}</p>}
          </div>
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-300">Danh mục</label>
            <select
              {...form.register('categoryId')}
              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-600"
            >
              <option value="" disabled>Chọn danh mục</option>
              {categories.map((cat) => (
                <option key={cat.id} value={cat.id}>
                  {cat.name}
                </option>
              ))}
            </select>
            {form.formState.errors.categoryId && <p className="text-xs text-rose-500">{form.formState.errors.categoryId.message}</p>}
          </div>
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-300">Tỷ lệ khung hình (Aspect Ratio)</label>
            <select
              value={aspectRatioPreset}
              onChange={(e) => {
                const val = e.target.value as '9_16' | '1_1' | '4_5' | 'custom';
                setAspectRatioPreset(val);
                if (val === '9_16') {
                  form.setValue('baseWidth', 1080);
                  form.setValue('baseHeight', 1920);
                } else if (val === '1_1') {
                  form.setValue('baseWidth', 1080);
                  form.setValue('baseHeight', 1080);
                } else if (val === '4_5') {
                  form.setValue('baseWidth', 1080);
                  form.setValue('baseHeight', 1350);
                }
              }}
              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-600"
            >
              <option value="9_16">Màn hình dọc 9:16 (1080 x 1920)</option>
              <option value="1_1">Vuông 1:1 (1080 x 1080)</option>
              <option value="4_5">Chân dung 4:5 (1080 x 1350)</option>
              <option value="custom">Tùy chỉnh (Nhập kích thước)</option>
            </select>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <label className="text-xs font-semibold text-slate-300">Chiều rộng (Width px)</label>
              <Input
                type="number"
                {...form.register('baseWidth', { valueAsNumber: true })}
                className="bg-slate-950 border-slate-800 text-slate-200 focus-visible:ring-indigo-600 rounded-xl"
              />
            </div>
            <div className="space-y-2">
              <label className="text-xs font-semibold text-slate-300">Chiều cao (Height px)</label>
              <Input
                type="number"
                {...form.register('baseHeight', { valueAsNumber: true })}
                className="bg-slate-950 border-slate-800 text-slate-200 focus-visible:ring-indigo-600 rounded-xl"
              />
            </div>
          </div>
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-300">Ảnh nền Background (Tùy chọn)</label>
            <Input
              type="file"
              accept="image/*"
              onChange={(e) => setBgFile(e.target.files ? e.target.files[0] : null)}
              className="bg-slate-950 border-slate-800 text-slate-400 file:text-xs file:font-semibold file:text-indigo-400 file:bg-indigo-600/10 file:border-0 file:rounded-lg file:px-3 file:py-1 cursor-pointer rounded-xl"
            />
          </div>

          <DialogFooter className="mt-6">
            <Button type="button" variant="ghost" onClick={onClose} className="text-slate-400 hover:text-white rounded-xl">
              Hủy
            </Button>
            <Button type="submit" disabled={isPending} className="bg-indigo-600 hover:bg-indigo-500 rounded-xl px-6 text-white">
              {isPending && <Loader2 className="w-4 h-4 animate-spin mr-2" />}
              Tạo mới
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
