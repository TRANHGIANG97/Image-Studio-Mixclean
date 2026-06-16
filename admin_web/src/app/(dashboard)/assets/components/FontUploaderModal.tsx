'use client';

import { useDropzone } from 'react-dropzone';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { UploadCloud, X, Loader2, Type } from 'lucide-react';
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { toast } from 'sonner';

interface FontUploaderModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export function FontUploaderModal({ isOpen, onClose }: FontUploaderModalProps) {
  const [file, setFile] = useState<File | null>(null);
  const [fontName, setFontName] = useState('');
  const [familySlug, setFamilySlug] = useState('');
  const [isUploading, setIsUploading] = useState(false);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    accept: {
      'font/ttf': ['.ttf'],
      'font/otf': ['.otf'],
      'font/woff': ['.woff'],
    },
    maxFiles: 1,
    onDrop: (acceptedFiles) => {
      if (acceptedFiles.length > 0) {
        const f = acceptedFiles[0];
        setFile(f);
        
        // Auto generate name and slug from filename
        const baseName = f.name.replace(/\.[^/.]+$/, '');
        setFontName(baseName.replace(/[-_]/g, ' '));
        setFamilySlug(baseName.toLowerCase().replace(/[^a-z0-9]/g, ''));
      }
    },
  });

  const removeFile = () => {
    setFile(null);
    setFontName('');
    setFamilySlug('');
  };

  const handleUpload = async () => {
    if (!file || !fontName || !familySlug) {
      toast.error('Vui lòng nhập đầy đủ tên font và slug!');
      return;
    }

    setIsUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('name', fontName);
      formData.append('family_slug', familySlug);

      const res = await fetch('/api/v1/fonts', {
        method: 'POST',
        body: formData,
      });

      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Upload failed');

      toast.success('Tải lên font chữ thành công!');
      setFile(null);
      onClose();
    } catch (err: any) {
      toast.error(`Lỗi tải font: ${err.message}`);
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => {
      if (!open && !isUploading) {
        setFile(null);
        onClose();
      }
    }}>
      <DialogContent className="bg-slate-900 border border-slate-800 text-slate-100 rounded-2xl sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="text-lg font-bold flex items-center gap-2">
            <Type className="w-5 h-5 text-indigo-400" /> Tải lên Font Nghệ thuật
          </DialogTitle>
          <DialogDescription className="text-sm text-slate-400">
            Font sẽ được tự động thêm vào thư viện Editor.
          </DialogDescription>
        </DialogHeader>

        {!file ? (
          <div 
            {...getRootProps()} 
            className={`border-2 border-dashed rounded-2xl p-8 text-center transition-colors cursor-pointer ${
              isDragActive ? 'border-indigo-500 bg-indigo-500/10' : 'border-slate-700 hover:border-indigo-400 hover:bg-slate-800/50'
            }`}
          >
            <input {...getInputProps()} />
            <UploadCloud className="w-10 h-10 mx-auto text-indigo-400 mb-4" />
            <p className="text-sm font-medium text-slate-300">
              {isDragActive ? 'Thả file font vào đây...' : 'Kéo thả file .ttf / .otf vào đây'}
            </p>
          </div>
        ) : (
          <div className="space-y-4 mt-2">
            <div className="flex items-center justify-between bg-slate-950 p-3 rounded-xl border border-slate-800">
              <div className="flex flex-col truncate pr-4">
                <span className="text-sm font-medium text-slate-200 truncate">{file.name}</span>
                <span className="text-xs text-slate-500">{(file.size / 1024).toFixed(1)} KB</span>
              </div>
              <button onClick={removeFile} disabled={isUploading} className="text-slate-500 hover:text-rose-400">
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="space-y-3">
              <div className="space-y-1">
                <label className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider">Tên hiển thị (Tùy ý)</label>
                <input
                  type="text"
                  value={fontName}
                  onChange={(e) => setFontName(e.target.value)}
                  disabled={isUploading}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-600"
                  placeholder="Ví dụ: Roboto Bold"
                />
              </div>
              <div className="space-y-1">
                <label className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider">Font Family Slug (Dùng trong CSS)</label>
                <input
                  type="text"
                  value={familySlug}
                  onChange={(e) => setFamilySlug(e.target.value)}
                  disabled={isUploading}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm font-mono text-slate-200 focus:outline-none focus:border-indigo-600"
                  placeholder="Ví dụ: roboto_bold"
                />
                <p className="text-[10px] text-slate-500 mt-1">Lưu ý: Viết liền không dấu, không khoảng trắng.</p>
              </div>
            </div>
          </div>
        )}

        <div className="flex justify-end gap-3 mt-6">
          <Button variant="ghost" onClick={onClose} disabled={isUploading} className="text-slate-400">
            Hủy
          </Button>
          <Button 
            onClick={handleUpload} 
            disabled={!file || !fontName || !familySlug || isUploading}
            className="bg-indigo-600 hover:bg-indigo-500 text-white"
          >
            {isUploading && <Loader2 className="w-4 h-4 animate-spin mr-2" />}
            Bắt đầu tải lên
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
