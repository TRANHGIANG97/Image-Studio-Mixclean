'use client';

import { useState, useEffect } from 'react';
import { Search, FolderOpen, ArrowUpDown, Filter, SlidersHorizontal, FolderPlus, Pencil, Trash2, Loader2, Settings, Plus, X, Lock } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { useFolders, useCreateFolder, useRenameFolder, useDeleteFolder } from '@/hooks/useAssets';
import { toSlug } from '@/lib/utils';

interface AssetFilterProps {
  search: string;
  onSearchChange: (val: string) => void;
  folder: string;
  onFolderChange: (val: string) => void;
  mimeType: string;
  onMimeTypeChange: (val: string) => void;
  sortBy: string;
  onSortByChange: (val: string) => void;
  sortOrder: 'asc' | 'desc';
  onSortOrderChange: (val: 'asc' | 'desc') => void;
}

const MIME_TYPE_OPTIONS = [
  { value: '', label: 'Tất cả định dạng' },
  { value: 'image', label: 'Hình ảnh (PNG, JPG...)' },
  { value: 'svg', label: 'Vector SVG' },
  { value: 'font', label: 'Font chữ (TTF, OTF...)' },
];

const SORT_BY_OPTIONS = [
  { value: 'created_at', label: 'Ngày tải lên' },
  { value: 'name', label: 'Tên tài nguyên' },
  { value: 'file_size', label: 'Dung lượng file' },
];

const DEFAULT_FOLDERS = ['backgrounds', 'stickers', 'fonts', 'uncategorized'];

export function AssetFilter({
  search,
  onSearchChange,
  folder,
  onFolderChange,
  mimeType,
  onMimeTypeChange,
  sortBy,
  onSortByChange,
  sortOrder,
  onSortOrderChange,
}: AssetFilterProps) {
  const { data: folders = [] } = useFolders();

  const createFolderMutation = useCreateFolder();
  const renameFolderMutation = useRenameFolder();
  const deleteFolderMutation = useDeleteFolder();

  // Unified Folder Manager Modal State
  const [isManagerOpen, setIsManagerOpen] = useState(false);
  const [newFolderName, setNewFolderName] = useState('');
  
  // Inline editing / deleting states inside manager
  const [editingFolder, setEditingFolder] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState('');
  const [deletingFolder, setDeletingFolder] = useState<string | null>(null);
  const [deleteFiles, setDeleteFiles] = useState(false);

  // Reset helper states when manager closes/opens
  useEffect(() => {
    if (!isManagerOpen) {
      setNewFolderName('');
      setEditingFolder(null);
      setRenameValue('');
      setDeletingFolder(null);
      setDeleteFiles(false);
    }
  }, [isManagerOpen]);

  const getFolderLabel = (folderName: string) => {
    switch (folderName) {
      case 'backgrounds': return 'Backgrounds (Nền)';
      case 'stickers': return 'Stickers (Nhãn dán)';
      case 'fonts': return 'Fonts (Phông chữ)';
      case 'uncategorized': return 'Chưa phân loại';
      case 'doi_song_so': return 'Đời sống số';
      default: return folderName.charAt(0).toUpperCase() + folderName.slice(1);
    }
  };

  const handleCreate = () => {
    if (!newFolderName.trim()) return;
    createFolderMutation.mutate(
      { folderName: newFolderName.trim() },
      {
        onSuccess: (data) => {
          setNewFolderName('');
          if (data?.success && data?.folder?.folder) {
            onFolderChange(data.folder.folder);
          }
        },
      }
    );
  };

  const handleRename = (oldFolder: string) => {
    if (!renameValue.trim() || renameValue.trim() === oldFolder) {
      setEditingFolder(null);
      return;
    }
    renameFolderMutation.mutate(
      { oldFolderName: oldFolder, newFolderName: renameValue.trim() },
      {
        onSuccess: () => {
          const slug = toSlug(renameValue);
          if (folder === oldFolder) {
            onFolderChange(slug);
          }
          setEditingFolder(null);
        },
      }
    );
  };

  const handleDelete = (targetFolder: string) => {
    deleteFolderMutation.mutate(
      { folderName: targetFolder, deleteFiles },
      {
        onSuccess: () => {
          if (folder === targetFolder) {
            onFolderChange('');
          }
          setDeletingFolder(null);
        },
      }
    );
  };

  return (
    <>
      <div className="flex flex-col xl:flex-row items-stretch xl:items-center gap-4 bg-white/60 border border-slate-200/80 p-4 rounded-3xl backdrop-blur-md shadow-xl transition-all duration-300">
        {/* Search */}
        <div className="relative flex-1 min-w-[240px]">
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500 transition-colors" />
          <Input
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="Tìm tên tài nguyên..."
            className="pl-11 bg-white/80 border-slate-200 text-slate-700 placeholder:text-slate-400 rounded-2xl w-full focus-visible:ring-indigo-500/50 focus-visible:border-indigo-500 transition-all duration-300"
          />
        </div>

        <div className="flex flex-wrap items-center gap-3">
          {/* Folder Filter & Management */}
          <div className="flex items-center gap-1.5">
            <div className="flex items-center gap-2 bg-white/80 border border-slate-200 rounded-2xl px-3.5 py-2 text-xs transition-all hover:border-slate-300">
              <FolderOpen className="w-4 h-4 text-indigo-400" />
              <select
                value={folder}
                onChange={(e) => onFolderChange(e.target.value)}
                className="bg-transparent border-0 text-slate-600 focus:outline-none pr-3 cursor-pointer text-xs font-medium"
              >
                <option value="" className="bg-white text-slate-600">
                  Tất cả thư mục
                </option>
                {folders.map((f) => (
                  <option key={f} value={f} className="bg-white text-slate-600">
                    {getFolderLabel(f)}
                  </option>
                ))}
              </select>
            </div>

            {/* Folder Unified Manager Button */}
            <button
              type="button"
              onClick={() => setIsManagerOpen(true)}
              className="flex items-center justify-center p-2 bg-white/80 border border-slate-200 hover:border-indigo-300 hover:bg-indigo-50 text-indigo-500 rounded-2xl transition-all h-8 w-8"
              title="Quản lý thư mục"
            >
              <Settings className="w-4 h-4" />
            </button>
          </div>

          {/* MimeType Format Filter */}
          <div className="flex items-center gap-2 bg-white/80 border border-slate-200 rounded-2xl px-3.5 py-2 text-xs transition-all hover:border-slate-300">
            <Filter className="w-4 h-4 text-pink-400" />
            <select
              value={mimeType}
              onChange={(e) => onMimeTypeChange(e.target.value)}
              className="bg-transparent border-0 text-slate-600 focus:outline-none pr-3 cursor-pointer text-xs font-medium"
            >
              {MIME_TYPE_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value} className="bg-white text-slate-600">
                  {opt.label}
                </option>
              ))}
            </select>
          </div>

          {/* Sort By Filter */}
          <div className="flex items-center gap-2 bg-white/80 border border-slate-200 rounded-2xl px-3.5 py-2 text-xs transition-all hover:border-slate-300">
            <SlidersHorizontal className="w-4 h-4 text-emerald-400" />
            <select
              value={sortBy}
              onChange={(e) => onSortByChange(e.target.value)}
              className="bg-transparent border-0 text-slate-600 focus:outline-none pr-3 cursor-pointer text-xs font-medium"
            >
              {SORT_BY_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value} className="bg-white text-slate-600">
                  {opt.label}
                </option>
              ))}
            </select>
          </div>

          {/* Sort Order Toggler */}
          <button
            onClick={() => onSortOrderChange(sortOrder === 'asc' ? 'desc' : 'asc')}
            className="flex items-center gap-2 bg-white/80 border border-slate-200 rounded-2xl px-3.5 py-2 text-xs text-slate-600 transition-all hover:border-slate-300 hover:text-slate-800"
            title={sortOrder === 'asc' ? 'Sắp xếp tăng dần' : 'Sắp xếp giảm dần'}
          >
            <ArrowUpDown className="w-4 h-4 text-amber-400" />
            <span className="font-medium">
              {sortOrder === 'asc' ? 'Tăng dần' : 'Giảm dần'}
            </span>
          </button>
        </div>
      </div>

      {/* --- UNIFIED FOLDER MANAGER DIALOG --- */}
      <Dialog open={isManagerOpen} onOpenChange={(open) => { if (!open) setIsManagerOpen(false); }}>
        <DialogContent className="bg-white border border-slate-200 text-slate-800 rounded-2xl sm:max-w-md max-h-[90vh] flex flex-col p-6 overflow-hidden">
          <DialogHeader className="pb-2 border-b border-slate-100">
            <DialogTitle className="text-lg font-bold flex items-center gap-2 text-slate-800">
              <FolderOpen className="w-5 h-5 text-indigo-500" />
              Quản lý danh sách thư mục
            </DialogTitle>
            <DialogDescription className="text-xs text-slate-400">
              Tạo mới, đổi tên hoặc xóa các thư mục lưu trữ tài nguyên ảo của bạn.
            </DialogDescription>
          </DialogHeader>

          {/* Folder List Container */}
          <div className="flex-1 overflow-y-auto my-3 pr-1 space-y-2.5 max-h-[45vh]">
            {folders.map((f) => {
              const isSystem = DEFAULT_FOLDERS.includes(f);
              const isEditing = editingFolder === f;
              const isDeleting = deletingFolder === f;

              return (
                <div key={f} className="transition-all duration-200">
                  {isDeleting ? (
                    // Inline Delete State
                    <div className="flex flex-col gap-2 p-3 bg-rose-50/70 border border-rose-100 rounded-2xl animate-in fade-in slide-in-from-top-1 duration-200">
                      <span className="text-xs text-rose-700 font-semibold leading-relaxed">
                        Bạn có chắc chắn muốn xóa thư mục "{getFolderLabel(f)}"?
                      </span>
                      <div className="flex items-center justify-between gap-3 pt-1">
                        <label className="flex items-center gap-1.5 text-[11px] text-slate-500 font-medium cursor-pointer select-none">
                          <input
                            type="checkbox"
                            checked={deleteFiles}
                            onChange={(e) => setDeleteFiles(e.target.checked)}
                            className="rounded text-rose-600 focus:ring-rose-500 h-3.5 w-3.5 cursor-pointer"
                          />
                          Xóa cả tệp tin bên trong
                        </label>
                        <div className="flex gap-1.5">
                          <Button
                            size="xs"
                            onClick={() => setDeletingFolder(null)}
                            variant="outline"
                            className="h-7 text-xs border-slate-200 bg-white"
                            disabled={deleteFolderMutation.isPending}
                          >
                            Hủy
                          </Button>
                          <Button
                            size="xs"
                            onClick={() => handleDelete(f)}
                            className="h-7 text-xs bg-rose-600 hover:bg-rose-500 text-white rounded-lg"
                            disabled={deleteFolderMutation.isPending}
                          >
                            {deleteFolderMutation.isPending ? <Loader2 className="w-3 h-3 animate-spin mr-1" /> : <Trash2 className="w-3 h-3 mr-1" />}
                            Xóa
                          </Button>
                        </div>
                      </div>
                    </div>
                  ) : isEditing ? (
                    // Inline Edit State
                    <div className="flex items-center gap-2 p-2 bg-indigo-50/50 border border-indigo-100 rounded-2xl animate-in fade-in duration-200">
                      <Input
                        value={renameValue}
                        onChange={(e) => setRenameValue(e.target.value)}
                        placeholder="Nhập tên thư mục mới..."
                        className="h-8 text-xs rounded-xl border-slate-200 bg-white flex-1 focus-visible:ring-indigo-500/50"
                        autoFocus
                      />
                      <Button
                        size="sm"
                        onClick={() => handleRename(f)}
                        className="h-8 text-xs bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl px-3"
                        disabled={renameFolderMutation.isPending}
                      >
                        {renameFolderMutation.isPending ? <Loader2 className="w-3 h-3 animate-spin" /> : 'Lưu'}
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => setEditingFolder(null)}
                        className="h-8 text-xs text-slate-500 hover:bg-slate-100 rounded-xl px-2"
                        disabled={renameFolderMutation.isPending}
                      >
                        <X className="w-3.5 h-3.5" />
                      </Button>
                    </div>
                  ) : (
                    // Normal Row State
                    <div className="flex items-center justify-between p-2.5 bg-slate-50/50 border border-slate-100 rounded-2xl hover:bg-slate-100/50 transition-colors">
                      <div className="flex items-center gap-2">
                        <FolderOpen className="w-4 h-4 text-indigo-400" />
                        <span className="text-xs font-semibold text-slate-700">
                          {getFolderLabel(f)}
                        </span>
                      </div>

                      <div className="flex items-center gap-1">
                        {isSystem ? (
                          <span className="text-[10px] bg-slate-100 text-slate-400 font-medium px-2 py-0.5 rounded-full border border-slate-200/80 flex items-center gap-1">
                            <Lock className="w-2.5 h-2.5" /> Mặc định
                          </span>
                        ) : (
                          <>
                            <button
                              type="button"
                              onClick={() => {
                                setRenameValue(f);
                                setEditingFolder(f);
                              }}
                              className="p-1.5 text-slate-400 hover:text-amber-500 hover:bg-amber-50 rounded-lg transition-all"
                              title="Đổi tên thư mục"
                            >
                              <Pencil className="w-3.5 h-3.5" />
                            </button>
                            <button
                              type="button"
                              onClick={() => {
                                setDeleteFiles(false);
                                setDeletingFolder(f);
                              }}
                              className="p-1.5 text-slate-400 hover:text-rose-500 hover:bg-rose-50 rounded-lg transition-all"
                              title="Xóa thư mục"
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                            </button>
                          </>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>

          {/* Create Folder Input (Sticky at the bottom) */}
          <div className="pt-4 border-t border-slate-100 space-y-2 bg-white">
            <label className="text-[11px] font-semibold text-slate-500">Tạo thư mục mới</label>
            <div className="flex gap-2">
              <Input
                type="text"
                value={newFolderName}
                onChange={(e) => setNewFolderName(e.target.value)}
                placeholder="Ví dụ: Tết cổ truyền, Sticker Mùa Hè..."
                className="h-9 text-xs rounded-xl border-slate-200 focus-visible:ring-indigo-500/50"
              />
              <Button
                onClick={handleCreate}
                disabled={createFolderMutation.isPending || !newFolderName.trim()}
                className="h-9 px-4 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-xs font-semibold flex items-center gap-1"
              >
                {createFolderMutation.isPending ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <>
                    <Plus className="w-3.5 h-3.5" />
                    Tạo
                  </>
                )}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
