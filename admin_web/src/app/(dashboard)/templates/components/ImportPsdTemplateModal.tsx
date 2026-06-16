'use client';

import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { FileImage, Loader2 } from 'lucide-react';
import { Category } from '@/hooks/useCategories';
import { useImportPsdTemplate } from '@/hooks/useTemplates';

interface ImportPsdTemplateModalProps {
  isOpen: boolean;
  onClose: () => void;
  categories: Category[];
}

type FormValues = {
  templateId: string;
  title: string;
  categoryId: string;
};

function buildTemplateId() {
  return `TPL_${Math.random().toString(36).substring(2, 8).toUpperCase()}`;
}

function fileStem(fileName: string) {
  return fileName.replace(/\.(psd|psb)$/i, '').replace(/[_-]+/g, ' ').trim();
}

export function ImportPsdTemplateModal({ isOpen, onClose, categories }: ImportPsdTemplateModalProps) {
  const [psdFile, setPsdFile] = useState<File | null>(null);
  const importMutation = useImportPsdTemplate();

  const form = useForm<FormValues>({
    defaultValues: {
      templateId: '',
      title: '',
      categoryId: '',
    },
  });

  useEffect(() => {
    if (isOpen) {
      form.reset({
        templateId: buildTemplateId(),
        title: '',
        categoryId: categories.length > 0 ? categories[0].id : '',
      });
      setPsdFile(null);
    }
  }, [isOpen, categories, form]);

  const handleFileChange = (file: File | null) => {
    setPsdFile(file);
    if (file) {
      const stem = fileStem(file.name);
      if (!form.getValues('title')) {
        form.setValue('title', stem || 'Imported PSD Template');
      }
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!psdFile) return;

    await importMutation.mutateAsync({
      file: psdFile,
      categoryId: form.getValues('categoryId'),
      templateId: form.getValues('templateId'),
      title: form.getValues('title'),
    });

    setPsdFile(null);
    onClose();
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => {
      if (!open) {
        setPsdFile(null);
        onClose();
      }
    }}>
      <DialogContent className="bg-slate-900 border border-slate-800 text-slate-100 rounded-2xl sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-lg font-bold text-slate-100 flex items-center gap-2">
            <FileImage className="w-5 h-5 text-indigo-400" /> Nhập Template từ PSD
          </DialogTitle>
          <DialogDescription className="text-xs text-slate-400">
            Import file .psd hoặc .psb nhiều layer để giữ từng layer có thể chỉnh sửa trong trình thiết kế.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4 py-2">
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-300">ID Template</label>
            <Input
              {...form.register('templateId')}
              className="bg-slate-950 border-slate-800 text-slate-200 focus-visible:ring-indigo-600 rounded-xl"
            />
          </div>
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-300">Tiêu đề template</label>
            <Input
              {...form.register('title')}
              placeholder="Tên lấy từ file PSD"
              className="bg-slate-950 border-slate-800 text-slate-200 focus-visible:ring-indigo-600 rounded-xl"
            />
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
          </div>
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-300">Chọn file PSD/PSB</label>
            <Input
              type="file"
              accept=".psd,.psb"
              onChange={(e) => handleFileChange(e.target.files ? e.target.files[0] : null)}
              className="bg-slate-950 border-slate-800 text-slate-400 file:text-xs file:font-semibold file:text-indigo-400 file:bg-indigo-600/10 file:border-0 file:rounded-lg file:px-3 file:py-1 cursor-pointer rounded-xl"
              required
            />
          </div>

          <DialogFooter className="mt-6">
            <Button type="button" variant="ghost" onClick={onClose} className="text-slate-400 hover:text-white rounded-xl">
              Hủy
            </Button>
            <Button type="submit" disabled={importMutation.isPending || !psdFile} className="bg-indigo-600 hover:bg-indigo-500 rounded-xl px-6 text-white">
              {importMutation.isPending && <Loader2 className="w-4 h-4 animate-spin mr-2" />}
              Tiến hành Nhập
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
