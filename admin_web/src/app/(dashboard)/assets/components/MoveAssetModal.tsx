'use client';

import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { FolderOpen, Loader2 } from 'lucide-react';
import { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { useUpdateAssetsFolder, useFolders } from '@/hooks/useAssets';

interface MoveAssetModalProps {
  isOpen: boolean;
  onClose: () => void;
  assetIds: string[];
  onSuccess?: () => void;
}

export function MoveAssetModal({ isOpen, onClose, assetIds, onSuccess }: MoveAssetModalProps) {
  const { data: folders = [] } = useFolders();
  const updateFolderMutation = useUpdateAssetsFolder();

  const [selectedFolder, setSelectedFolder] = useState('uncategorized');
  const [isCustomFolder, setIsCustomFolder] = useState(false);
  const [customFolder, setCustomFolder] = useState('');

  useEffect(() => {
    if (isOpen) {
      setSelectedFolder('uncategorized');
      setIsCustomFolder(false);
      setCustomFolder('');
    }
  }, [isOpen]);

  const getFolderLabel = (folderName: string) => {
    switch (folderName) {
      case 'backgrounds': return 'Backgrounds (Nền)';
      case 'stickers': return 'Stickers (Nhãn dán)';
      case 'fonts': return 'Fonts (Phông chữ)';
      case 'uncategorized': return 'Chưa phân loại';
      default: return folderName.charAt(0).toUpperCase() + folderName.slice(1);
    }
  };

  const handleMove = async () => {
    const destFolder = isCustomFolder ? (customFolder.trim() || 'uncategorized') : selectedFolder;
    if (!destFolder) return;

    updateFolderMutation.mutate(
      { ids: assetIds, folder: destFolder },
      {
        onSuccess: () => {
          onSuccess?.();
          onClose();
        },
      }
    );
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent className="bg-slate-900 border border-slate-800 text-slate-100 rounded-2xl sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-lg font-bold">Di chuyển tài nguyên</DialogTitle>
          <DialogDescription className="text-sm text-slate-400">
            Di chuyển <span className="font-semibold text-slate-200">{assetIds.length}</span> tài nguyên đã chọn sang thư mục khác.
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-4 py-4">
          <div className="grid gap-2">
            <label className="text-xs font-medium text-slate-400 flex items-center gap-2">
              <FolderOpen className="w-4 h-4 text-indigo-400" />
              Thư mục đích
            </label>
            <select
              value={isCustomFolder ? 'new_folder' : selectedFolder}
              onChange={(e) => {
                if (e.target.value === 'new_folder') {
                  setIsCustomFolder(true);
                } else {
                  setIsCustomFolder(false);
                  setSelectedFolder(e.target.value);
                }
              }}
              className="w-full rounded-2xl border border-slate-700 bg-slate-950 px-4 py-3 text-sm text-slate-200 outline-none focus:border-indigo-500"
            >
              {folders.map((f) => (
                <option key={f} value={f}>
                  {getFolderLabel(f)}
                </option>
              ))}
              <option value="new_folder" className="text-indigo-400 font-semibold">
                + Tạo thư mục mới
              </option>
            </select>

            {isCustomFolder && (
              <input
                type="text"
                value={customFolder}
                onChange={(e) => setCustomFolder(e.target.value.toLowerCase().replace(/[^a-z0-9_-]/g, ''))}
                placeholder="Nhập tên thư mục mới (viết liền không dấu)..."
                className="w-full rounded-2xl border border-slate-700 bg-slate-950 px-4 py-3 text-sm text-slate-200 outline-none focus:border-indigo-500 animate-in slide-in-from-top-1 duration-200"
              />
            )}
          </div>
        </div>

        <div className="flex justify-end gap-3 mt-4">
          <Button variant="ghost" onClick={onClose} disabled={updateFolderMutation.isPending} className="text-slate-400 animate-none">
            Hủy
          </Button>
          <Button
            onClick={handleMove}
            disabled={updateFolderMutation.isPending || (isCustomFolder && !customFolder.trim())}
            className="bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl"
          >
            {updateFolderMutation.isPending && <Loader2 className="w-4 h-4 animate-spin mr-2" />}
            Xác nhận di chuyển
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
