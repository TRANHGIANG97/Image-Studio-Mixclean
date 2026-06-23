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
import { fileStem, readImageDimensions } from '@/lib/image-file-utils';
import { compressImageFileToWebp, formatCompressSummary } from '@/lib/image-compress';

interface CreateTemplateModalProps {
  isOpen: boolean;
  onClose: () => void;
  categories: Category[];
}

type AspectRatioPreset = 'original' | '9_16' | '1_1' | '4_5' | 'custom';

export function CreateTemplateModal({ isOpen, onClose, categories }: CreateTemplateModalProps) {
  const [aspectRatioPreset, setAspectRatioPreset] = useState<AspectRatioPreset>('1_1');
  const [bgFile, setBgFile] = useState<File | null>(null);
  const [bgCompressing, setBgCompressing] = useState(false);
  const [bgFileInfo, setBgFileInfo] = useState<string | null>(null);

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
      setBgFileInfo(null);
      setBgCompressing(false);
    }
  }, [isOpen, categories, form]);

  const applyOriginalDimensions = async (file: File) => {
    try {
      const { width, height } = await readImageDimensions(file);
      form.setValue('baseWidth', width);
      form.setValue('baseHeight', height);
    } catch {
      // Keep current dimensions if the browser cannot decode the image.
    }
  };

  const handleBgFileChange = async (file: File | null) => {
    setBgFile(null);
    setBgFileInfo(null);
    if (!file) return;

    setBgCompressing(true);
    try {
      const compressed = await compressImageFileToWebp(file);
      const readyFile = compressed.file;
      setBgFile(readyFile);
      setBgFileInfo(formatCompressSummary(compressed));

      const stem = fileStem(file.name);
      if (!form.getValues('title')?.trim()) {
        form.setValue('title', stem);
      }
      if (aspectRatioPreset === 'original') {
        await applyOriginalDimensions(readyFile);
      }
    } catch {
      setBgFile(file);
      setBgFileInfo(`${file.name} · không nén được, dùng file gốc`);
      if (aspectRatioPreset === 'original') {
        await applyOriginalDimensions(file);
      }
    } finally {
      setBgCompressing(false);
    }
  };

  const onSubmit = async (data: CreateTemplateInput) => {
    try {
      const resolvedTitle = data.title?.trim() || (bgFile ? fileStem(bgFile.name) : '');
      if (!resolvedTitle) {
        form.setError('title', { message: 'Tiêu đề template không được để trống' });
        return;
      }

      let backgroundUrl = '';
      if (bgFile) {
        const uploadResult = await uploadMutation.mutateAsync({ file: bgFile, folder: 'backgrounds' }) as { fileUrl: string };
        backgroundUrl = uploadResult.fileUrl;
      }
      
      await createMutation.mutateAsync({
        ...data,
        title: resolvedTitle,
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
      <DialogContent className="bg-white border border-slate-200 text-slate-800 rounded-2xl sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-lg font-bold text-slate-800">Tạo Template mới</DialogTitle>
          <DialogDescription className="text-xs text-slate-500">
            Điền các thông số cơ bản để khởi tạo một bản nháp canvas.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-2">
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-600">ID Template (Unique)</label>
            <Input
              {...form.register('templateId')}
              placeholder="ví dụ: TPL_SUMMER_01"
              className="bg-white border-slate-200 text-slate-700 focus-visible:ring-indigo-600 rounded-xl"
            />
            {form.formState.errors.templateId && <p className="text-xs text-rose-500">{form.formState.errors.templateId.message}</p>}
          </div>
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-600">Tiêu đề template</label>
            <Input
              {...form.register('title')}
              placeholder="Để trống sẽ lấy tên file ảnh nền"
              className="bg-white border-slate-200 text-slate-700 focus-visible:ring-indigo-600 rounded-xl"
            />
            {form.formState.errors.title && <p className="text-xs text-rose-500">{form.formState.errors.title.message}</p>}
          </div>
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-600">Danh mục</label>
            <select
              {...form.register('categoryId')}
              className="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-sm text-slate-700 focus:outline-none focus:border-indigo-600"
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
            <label className="text-xs font-semibold text-slate-600">Tỷ lệ khung hình (Aspect Ratio)</label>
            <select
              value={aspectRatioPreset}
              onChange={async (e) => {
                const val = e.target.value as AspectRatioPreset;
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
                } else if (val === 'original' && bgFile) {
                  await applyOriginalDimensions(bgFile);
                }
              }}
              className="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-sm text-slate-700 focus:outline-none focus:border-indigo-600"
            >
              <option value="original">Tỉ lệ gốc (từ file ảnh nền)</option>
              <option value="9_16">Màn hình dọc 9:16 (1080 x 1920)</option>
              <option value="1_1">Vuông 1:1 (1080 x 1080)</option>
              <option value="4_5">Chân dung 4:5 (1080 x 1350)</option>
              <option value="custom">Tùy chỉnh (Nhập kích thước)</option>
            </select>
            {aspectRatioPreset === 'original' && !bgFile && (
              <p className="text-[10px] text-slate-400">Chọn ảnh nền để áp dụng kích thước gốc của file.</p>
            )}
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <label className="text-xs font-semibold text-slate-600">Chiều rộng (Width px)</label>
              <Input
                type="number"
                {...form.register('baseWidth', { valueAsNumber: true })}
                disabled={aspectRatioPreset === 'original' && !!bgFile}
                className="bg-white border-slate-200 text-slate-700 focus-visible:ring-indigo-600 rounded-xl disabled:opacity-60"
              />
            </div>
            <div className="space-y-2">
              <label className="text-xs font-semibold text-slate-600">Chiều cao (Height px)</label>
              <Input
                type="number"
                {...form.register('baseHeight', { valueAsNumber: true })}
                disabled={aspectRatioPreset === 'original' && !!bgFile}
                className="bg-white border-slate-200 text-slate-700 focus-visible:ring-indigo-600 rounded-xl disabled:opacity-60"
              />
            </div>
          </div>
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-600">Ảnh nền Background (Tùy chọn)</label>
            <Input
              type="file"
              accept="image/*"
              disabled={bgCompressing}
              onChange={(e) => { void handleBgFileChange(e.target.files ? e.target.files[0] : null); }}
              className="bg-white border-slate-200 text-slate-500 file:text-xs file:font-semibold file:text-indigo-400 file:bg-indigo-600/10 file:border-0 file:rounded-lg file:px-3 file:py-1 cursor-pointer rounded-xl disabled:opacity-60"
            />
            {bgCompressing && (
              <p className="text-[10px] text-indigo-500 flex items-center gap-1">
                <Loader2 className="w-3 h-3 animate-spin" />
                Đang nén và chuyển sang WebP...
              </p>
            )}
            {!bgCompressing && bgFileInfo && (
              <p className="text-[10px] text-slate-400">{bgFileInfo}</p>
            )}
            {!bgCompressing && !bgFileInfo && (
              <p className="text-[10px] text-slate-400">PNG/JPG sẽ tự chuyển WebP và nén nhẹ khi chọn file.</p>
            )}
          </div>

          <DialogFooter className="mt-6">
            <Button type="button" variant="ghost" onClick={onClose} className="text-slate-500 hover:text-slate-800 rounded-xl">
              Hủy
            </Button>
            <Button type="submit" disabled={isPending || bgCompressing} className="bg-indigo-600 hover:bg-indigo-500 rounded-xl px-6 text-white">
              {isPending && <Loader2 className="w-4 h-4 animate-spin mr-2" />}
              Tạo mới
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
