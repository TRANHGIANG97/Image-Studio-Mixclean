'use client';

import { useDropzone } from 'react-dropzone';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { UploadCloud, X, Loader2, FolderOpen } from 'lucide-react';
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { useUploadAsset } from '@/hooks/useAssets';
import { useCategories } from '@/hooks/useCategories';

interface AssetUploaderModalProps {
  isOpen: boolean;
  onClose: () => void;
  folder?: string;
}

export function AssetUploaderModal({ isOpen, onClose, folder = 'uncategorized' }: AssetUploaderModalProps) {
  const [files, setFiles] = useState<File[]>([]);
  const [categoryId, setCategoryId] = useState('');
  const uploadMutation = useUploadAsset();
  const { data: categories = [], isLoading: categoriesLoading } = useCategories();

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    accept: {
      'image/*': ['.png', '.jpg', '.jpeg', '.webp', '.svg'],
      'font/ttf': ['.ttf'],
      'font/otf': ['.otf'],
      'font/woff': ['.woff'],
      'font/woff2': ['.woff2'],
    },
    onDrop: (acceptedFiles) => {
      setFiles((prev) => [...prev, ...acceptedFiles]);
    },
  });

  const removeFile = (index: number) => {
    setFiles(files.filter((_, i) => i !== index));
  };

  const handleUploadAll = async () => {
    for (const file of files) {
      await uploadMutation.mutateAsync({ file, folder, categoryId: categoryId || null });
    }
    setFiles([]);
    setCategoryId('');
    onClose();
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => {
      if (!open) {
        setFiles([]);
        setCategoryId('');
        onClose();
      }
    }}>
      <DialogContent className="bg-slate-900 border border-slate-800 text-slate-100 rounded-2xl sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="text-lg font-bold">Tải lên tài nguyên mới</DialogTitle>
          <DialogDescription className="text-sm text-slate-400">
            Thư mục đích: <span className="font-mono text-indigo-400">{folder}</span>
          </DialogDescription>
        </DialogHeader>

        <div 
          {...getRootProps()} 
          className={`border-2 border-dashed rounded-2xl p-8 text-center transition-colors cursor-pointer ${
            isDragActive ? 'border-indigo-500 bg-indigo-500/10' : 'border-slate-700 hover:border-indigo-400 hover:bg-slate-800/50'
          }`}
        >
          <input {...getInputProps()} />
          <UploadCloud className="w-10 h-10 mx-auto text-indigo-400 mb-4" />
          <p className="text-sm font-medium text-slate-300">
            {isDragActive ? 'Thả file vào đây...' : 'Kéo thả file vào đây, hoặc click để chọn'}
          </p>
          <p className="text-xs text-slate-500 mt-2">Hỗ trợ: PNG, JPG, WEBP, SVG, TTF, OTF</p>
        </div>

        <div className="grid gap-2 mt-4">
          <label className="text-xs font-medium text-slate-400 flex items-center gap-2">
            <FolderOpen className="w-4 h-4 text-indigo-400" />
            Phân loại tài nguyên
          </label>
          <select
            value={categoryId}
            onChange={(e) => setCategoryId(e.target.value)}
            disabled={categoriesLoading}
            className="w-full rounded-2xl border border-slate-700 bg-slate-950 px-4 py-3 text-sm text-slate-200 outline-none focus:border-indigo-500 disabled:opacity-60"
          >
            <option value="">Chưa phân loại</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </div>

        {files.length > 0 && (
          <div className="mt-4 max-h-48 overflow-y-auto space-y-2 pr-2">
            {files.map((file, idx) => (
              <div key={idx} className="flex items-center justify-between bg-slate-950 p-3 rounded-xl border border-slate-800">
                <div className="flex flex-col truncate pr-4">
                  <span className="text-sm font-medium text-slate-200 truncate">{file.name}</span>
                  <span className="text-xs text-slate-500">{(file.size / 1024).toFixed(1)} KB</span>
                </div>
                <button onClick={() => removeFile(idx)} className="text-slate-500 hover:text-rose-400">
                  <X className="w-4 h-4" />
                </button>
              </div>
            ))}
          </div>
        )}

        <div className="flex justify-end gap-3 mt-6">
          <Button variant="ghost" onClick={onClose} disabled={uploadMutation.isPending} className="text-slate-400">
            Hủy
          </Button>
          <Button 
            onClick={handleUploadAll} 
            disabled={files.length === 0 || uploadMutation.isPending}
            className="bg-indigo-600 hover:bg-indigo-500 text-white"
          >
            {uploadMutation.isPending && <Loader2 className="w-4 h-4 animate-spin mr-2" />}
            Bắt đầu tải lên ({files.length})
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
