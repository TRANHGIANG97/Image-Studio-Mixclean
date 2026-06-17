'use client';

import { useState } from 'react';
import { FileArchive, Loader2 } from 'lucide-react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Category } from '@/hooks/useCategories';
import { useImportTemplate } from '@/hooks/useTemplates';

interface ImportTemplateModalProps {
  isOpen: boolean;
  onClose: () => void;
  categories: Category[];
}

export function ImportTemplateModal({ isOpen, onClose, categories }: ImportTemplateModalProps) {
  const [zipFile, setZipFile] = useState<File | null>(null);
  const [importCategoryId, setImportCategoryId] = useState('');

  const importMutation = useImportTemplate();

  const handleImport = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!zipFile) return;

    await importMutation.mutateAsync({ file: zipFile, categoryId: importCategoryId });
    setZipFile(null);
    setImportCategoryId('');
    onClose();
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => {
      if (!open) {
        setZipFile(null);
        setImportCategoryId('');
        onClose();
      }
    }}>
      <DialogContent className="bg-white border border-slate-200 text-slate-800 rounded-2xl sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-lg font-bold text-slate-800 flex items-center gap-2">
            <FileArchive className="w-5 h-5 text-indigo-400" /> Nhập Template từ ZIP
          </DialogTitle>
          <DialogDescription className="text-xs text-slate-500">
            Chọn file ZIP bundle xuất từ Android hoặc từ các bản sao lưu để khôi phục.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleImport} className="space-y-4 py-2">
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-600">Chọn file ZIP</label>
            <Input
              type="file"
              accept=".zip"
              onChange={(e) => setZipFile(e.target.files ? e.target.files[0] : null)}
              className="bg-white border-slate-200 text-slate-500 file:text-xs file:font-semibold file:text-indigo-400 file:bg-indigo-600/10 file:border-0 file:rounded-lg file:px-3 file:py-1 cursor-pointer rounded-xl"
              required
            />
          </div>
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-600">Gán vào danh mục (Tùy chọn)</label>
            <select
              value={importCategoryId}
              onChange={(e) => setImportCategoryId(e.target.value)}
              className="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-sm text-slate-700 focus:outline-none focus:border-indigo-600"
            >
              <option value="">Giữ nguyên danh mục gốc</option>
              {categories.map((cat) => (
                <option key={cat.id} value={cat.id}>
                  {cat.name}
                </option>
              ))}
            </select>
          </div>

          <DialogFooter className="mt-6">
            <Button type="button" variant="ghost" onClick={onClose} className="text-slate-500 hover:text-slate-800 rounded-xl">
              Hủy
            </Button>
            <Button type="submit" disabled={importMutation.isPending || !zipFile} className="bg-indigo-600 hover:bg-indigo-500 rounded-xl px-6 text-white">
              {importMutation.isPending && <Loader2 className="w-4 h-4 animate-spin mr-2" />}
              Tiến hành Nhập
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
