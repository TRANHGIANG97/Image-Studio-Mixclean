'use client';

import { useEffect, useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { FileImage, Loader2 } from 'lucide-react';
import { Category } from '@/hooks/useCategories';
import { useImportPsdTemplate } from '@/hooks/useTemplates';
import { fileStem } from '@/lib/image-file-utils';

interface ImportPsdTemplateModalProps {
  isOpen: boolean;
  onClose: () => void;
  categories: Category[];
}

type FormValues = {
  templateId: string;
  title: string;
  categoryId: string;
  exportLayers: boolean;
  exportFolderName?: string;
};

const importPsdTemplateSchema = z.object({
  templateId: z.string().trim().min(1, 'ID template is required'),
  title: z.string(),
  categoryId: z.string().trim().min(1, 'Danh mục là bắt buộc'),
  exportLayers: z.boolean(),
  exportFolderName: z.string().optional(),
});

function buildTemplateId() {
  return `TPL_${Math.random().toString(36).substring(2, 8).toUpperCase()}`;
}

function psdFileStem(fileName: string) {
  return fileStem(fileName, /\.(psd|psb)$/i);
}

export function ImportPsdTemplateModal({ isOpen, onClose, categories }: ImportPsdTemplateModalProps) {
  const [psdFile, setPsdFile] = useState<File | null>(null);
  const importMutation = useImportPsdTemplate();

  const form = useForm<FormValues>({
    resolver: zodResolver(importPsdTemplateSchema),
    defaultValues: {
      templateId: '',
      title: '',
      categoryId: '',
      exportLayers: false,
      exportFolderName: '',
    },
  });

  const exportLayers = useWatch({ control: form.control, name: 'exportLayers' });

  useEffect(() => {
    if (isOpen) {
      form.reset({
        templateId: buildTemplateId(),
        title: '',
        categoryId: categories.length > 0 ? categories[0].id : '',
        exportLayers: false,
        exportFolderName: '',
      });
      setPsdFile(null);
    }
  }, [isOpen, categories, form]);

  const handleFileChange = (file: File | null) => {
    setPsdFile(file);
    if (!file) return;

    const stem = psdFileStem(file.name);
    if (!form.getValues('title')?.trim()) {
      form.setValue('title', stem || 'Imported PSD Template');
    }
    form.setValue(
      'exportFolderName',
      stem ? `psd-layer-${stem.toLowerCase().replace(/[^a-z0-9]+/g, '-')}` : 'psd-layers',
    );
  };

  const handleSubmit = form.handleSubmit(async (values) => {
    if (!psdFile) return;

    const resolvedTitle = values.title?.trim() || psdFileStem(psdFile.name) || 'Imported PSD Template';

    await importMutation.mutateAsync({
      file: psdFile,
      categoryId: values.categoryId.trim(),
      templateId: values.templateId.trim(),
      title: resolvedTitle,
      exportLayers: values.exportLayers,
      exportFolderName: values.exportFolderName,
    });

    setPsdFile(null);
    onClose();
  });

  return (
    <Dialog open={isOpen} onOpenChange={(open) => {
      if (!open) {
        setPsdFile(null);
        onClose();
      }
    }}>
      <DialogContent className="bg-white border border-slate-200 text-slate-800 rounded-2xl sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-lg font-bold text-slate-800 flex items-center gap-2">
            <FileImage className="w-5 h-5 text-indigo-400" /> Nhập Template từ PSD
          </DialogTitle>
          <DialogDescription className="text-xs text-slate-500">
            Import file .psd hoặc .psb nhiều layer để giữ từng layer có thể chỉnh sửa trong trình thiết kế.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4 py-2">
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-600">ID Template</label>
            <Input
              {...form.register('templateId')}
              className="bg-white border-slate-200 text-slate-700 focus-visible:ring-indigo-600 rounded-xl"
            />
            {form.formState.errors.templateId && <p className="text-xs text-rose-500">{form.formState.errors.templateId.message}</p>}
          </div>
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-600">Tiêu đề template</label>
            <Input
              {...form.register('title')}
              placeholder="Để trống sẽ lấy tên file PSD"
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
            <label className="text-xs font-semibold text-slate-600">Chọn file PSD/PSB</label>
            <Input
              type="file"
              accept=".psd,.psb"
              onChange={(e) => handleFileChange(e.target.files ? e.target.files[0] : null)}
              className="bg-white border-slate-200 text-slate-500 file:text-xs file:font-semibold file:text-indigo-400 file:bg-indigo-600/10 file:border-0 file:rounded-lg file:px-3 file:py-1 cursor-pointer rounded-xl"
              required
            />
          </div>

          <div className="flex items-center space-x-2 pt-2">
            <input
              type="checkbox"
              id="exportLayers"
              {...form.register('exportLayers')}
              className="w-4 h-4 text-indigo-600 border-slate-300 rounded focus:ring-indigo-500 cursor-pointer"
            />
            <label htmlFor="exportLayers" className="text-xs font-semibold text-slate-600 cursor-pointer select-none">
              Xuất các layer ảnh vào Thư viện tài nguyên (Asset Library)
            </label>
          </div>

          {exportLayers && (
            <div className="space-y-2 pl-6 animate-in fade-in slide-in-from-top-1 duration-200">
              <label className="text-xs font-semibold text-slate-600">Tên thư mục lưu trữ tài nguyên</label>
              <Input
                {...form.register('exportFolderName')}
                placeholder="Ví dụ: psd-layer-summer"
                className="bg-white border-slate-200 text-slate-700 focus-visible:ring-indigo-600 rounded-xl"
                required
              />
              <p className="text-[10px] text-slate-400">
                Các lớp ảnh con của file PSD sẽ được đăng ký vào thư mục này để bạn tái sử dụng ở các template khác.
              </p>
            </div>
          )}

          <DialogFooter className="mt-6">
            <Button type="button" variant="ghost" onClick={onClose} className="text-slate-500 hover:text-slate-800 rounded-xl">
              Hủy
            </Button>
            <Button
              type="submit"
              disabled={importMutation.isPending || !psdFile}
              className="bg-indigo-600 hover:bg-indigo-500 rounded-xl px-6 text-white"
            >
              {importMutation.isPending && <Loader2 className="w-4 h-4 animate-spin mr-2" />}
              Tiến hành Nhập
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
