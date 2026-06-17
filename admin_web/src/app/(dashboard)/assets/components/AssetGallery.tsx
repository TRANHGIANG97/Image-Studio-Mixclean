'use client';

import { useState } from 'react';
import { Asset, useDeleteAsset, useDeleteAssetsBulk } from '@/hooks/useAssets';
import { Trash2, Image as ImageIcon, FileType, Copy, Check, Loader2, FolderInput } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { toast } from 'sonner';
import { MoveAssetModal } from './MoveAssetModal';

interface AssetGalleryProps {
  assets: Asset[];
  isLoading: boolean;
}

function formatFileSize(bytes: number) {
  if (!bytes || bytes === 0) return '0 KB';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

const getBadgeColor = (ext: string) => {
  switch (ext) {
    case 'SVG': return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/25';
    case 'PNG': return 'bg-sky-500/10 text-sky-400 border-sky-500/25';
    case 'JPG':
    case 'JPEG': return 'bg-blue-500/10 text-blue-400 border-blue-500/25';
    case 'WEBP': return 'bg-purple-500/10 text-purple-400 border-purple-500/25';
    case 'TTF':
    case 'OTF':
    case 'WOFF':
    case 'WOFF2': return 'bg-amber-500/10 text-amber-400 border-amber-500/25';
    default: return 'bg-slate-500/10 text-slate-500 border-slate-500/25';
  }
};

export function AssetGallery({ assets, isLoading }: AssetGalleryProps) {
  const deleteMutation = useDeleteAsset();
  const bulkDeleteMutation = useDeleteAssetsBulk();
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [isMoveModalOpen, setIsMoveModalOpen] = useState(false);
  const [targetIdsForMove, setTargetIdsForMove] = useState<string[]>([]);

  const handleOpenMoveModal = (ids: string[]) => {
    setTargetIdsForMove(ids);
    setIsMoveModalOpen(true);
  };

  const toggleSelect = (id: string) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    );
  };

  const toggleSelectAll = () => {
    if (selectedIds.length === assets.length) {
      setSelectedIds([]);
    } else {
      setSelectedIds(assets.map((a) => a.id));
    }
  };

  const handleCopyLink = (id: string, url: string) => {
    navigator.clipboard.writeText(url);
    setCopiedId(id);
    toast.success('Đã sao chép liên kết vào bộ nhớ tạm!');
    setTimeout(() => setCopiedId(null), 2000);
  };

  const handleBulkDelete = () => {
    if (confirm(`Bạn có chắc chắn muốn xóa ${selectedIds.length} tài nguyên đã chọn?`)) {
      bulkDeleteMutation.mutate(selectedIds, {
        onSuccess: () => {
          setSelectedIds([]);
        },
      });
    }
  };

  if (isLoading) {
    return (
      <div className="flex justify-center items-center py-24 text-slate-500">
        <Loader2 className="w-6 h-6 animate-spin text-indigo-500 mr-2" />
        Đang tải danh sách tài nguyên...
      </div>
    );
  }

  if (assets.length === 0) {
    return (
      <div className="text-center py-24 bg-white border border-slate-200 rounded-3xl text-slate-500">
        <ImageIcon className="w-12 h-12 mx-auto text-slate-700 mb-4 animate-pulse" />
        Chưa có tài nguyên nào trong thư mục này.
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Gallery Header Select All Controls */}
      <div className="flex items-center justify-between bg-white/50 border border-slate-200/80 px-4 py-3.5 rounded-2xl text-xs backdrop-blur-sm transition-all duration-300">
        <div className="flex items-center gap-3">
          <button
            onClick={toggleSelectAll}
            className="flex items-center gap-2 text-slate-600 hover:text-slate-800 transition-colors focus:outline-none"
          >
            <span
              className={`w-5 h-5 rounded-md flex items-center justify-center border transition-all ${
                selectedIds.length === assets.length && assets.length > 0
                  ? 'bg-indigo-600 border-indigo-500 text-white'
                  : 'bg-white border-slate-200'
              }`}
            >
              {selectedIds.length === assets.length && assets.length > 0 && (
                <Check className="w-3.5 h-3.5 stroke-[3]" />
              )}
            </span>
            <span className="font-semibold text-slate-600 hover:text-slate-800 transition-colors">
              Chọn tất cả ({assets.length})
            </span>
          </button>
          {selectedIds.length > 0 && (
            <span className="text-slate-400 font-medium bg-white px-2 py-0.5 rounded-md border border-slate-200/80">
              Đang chọn {selectedIds.length} tài nguyên
            </span>
          )}
        </div>
      </div>

      {/* Grid List */}
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
        {assets.map((asset) => {
          const extension = asset.name.split('.').pop()?.toUpperCase() || 'FILE';
          const isSelected = selectedIds.includes(asset.id);
          
          return (
            <div
              key={asset.id}
              onClick={() => toggleSelect(asset.id)}
              className={`group relative bg-white border rounded-2xl overflow-hidden cursor-pointer transition-all duration-300 select-none ${
                isSelected
                  ? 'border-indigo-500 shadow-lg shadow-indigo-600/10 bg-indigo-950/10'
                  : 'border-slate-200 hover:border-slate-300 hover:bg-white/80 hover:shadow-md'
              }`}
            >
              {/* Select Checkbox Indicator */}
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  toggleSelect(asset.id);
                }}
                className={`absolute top-2.5 left-2.5 z-20 w-6 h-6 rounded-lg flex items-center justify-center transition-all duration-300 border ${
                  isSelected
                    ? 'bg-indigo-600 border-indigo-500 text-white shadow-lg shadow-indigo-600/30 scale-100 opacity-100'
                    : 'bg-white/80 border-slate-200 text-slate-500 hover:border-slate-600 hover:text-slate-800 opacity-0 group-hover:opacity-100 scale-90'
                }`}
              >
                {isSelected ? (
                  <Check className="w-3.5 h-3.5 stroke-[3]" />
                ) : (
                  <div className="w-1.5 h-1.5 rounded-sm bg-transparent" />
                )}
              </button>

              {/* Action Overlay */}
              <div 
                className="absolute inset-0 bg-white/40 opacity-0 group-hover:opacity-100 transition-all duration-300 flex items-center justify-center gap-3 z-10 backdrop-blur-[2px]"
                onClick={(e) => e.stopPropagation()}
              >
                <Button
                  type="button"
                  variant="secondary"
                  size="icon"
                  className="rounded-xl w-10 h-10 shadow-xl bg-white/90 border border-slate-300/80 text-slate-600 hover:text-slate-800 hover:bg-slate-100 hover:border-slate-500 transition-all duration-200"
                  onClick={() => handleCopyLink(asset.id, asset.file_url)}
                  title="Sao chép liên kết CDN"
                >
                  {copiedId === asset.id ? (
                    <Check className="w-4 h-4 text-emerald-400 stroke-[3.5]" />
                  ) : (
                    <Copy className="w-4 h-4" />
                  )}
                </Button>

                <Button
                  type="button"
                  variant="secondary"
                  size="icon"
                  className="rounded-xl w-10 h-10 shadow-xl bg-white/90 border border-slate-300/80 text-indigo-400 hover:text-slate-800 hover:bg-indigo-600/20 hover:border-indigo-500 transition-all duration-200"
                  onClick={() => handleOpenMoveModal([asset.id])}
                  title="Di chuyển thư mục"
                >
                  <FolderInput className="w-4 h-4" />
                </Button>

                <Button
                  type="button"
                  variant="destructive"
                  size="icon"
                  className="rounded-xl w-10 h-10 shadow-xl bg-rose-600/90 hover:bg-rose-600 border border-rose-500 text-white transition-all duration-200"
                  onClick={() => {
                    if (confirm('Bạn có chắc chắn muốn xóa tài nguyên này?')) {
                      deleteMutation.mutate(asset.id);
                    }
                  }}
                  disabled={deleteMutation.isPending}
                  title="Xóa tài nguyên"
                >
                  {deleteMutation.isPending ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <Trash2 className="w-4 h-4" />
                  )}
                </Button>
              </div>

              {/* Thumbnail Preview */}
              <div
                className="aspect-square bg-white flex items-center justify-center relative overflow-hidden group-hover:scale-[1.02] transition-transform duration-500"
                style={{
                  backgroundImage:
                    'url("data:image/svg+xml,%3Csvg xmlns=\'http://www.w3.org/2000/svg\' width=\'16\' height=\'16\'%3E%3Crect width=\'8\' height=\'8\' fill=\'%230b0f19\'/%3E%3Crect x=\'8\' y=\'8\' width=\'8\' height=\'8\' fill=\'%230b0f19\'/%3E%3Crect x=\'8\' width=\'8\' height=\'8\' fill=\'%230f172a\'/%3E%3Crect y=\'8\' width=\'8\' height=\'8\' fill=\'%230f172a\'/%3E%3C/svg%3E")',
                }}
              >
                {asset.mime_type?.startsWith('image/') ? (
                  <img
                    src={asset.file_url}
                    alt={asset.name}
                    className="w-full h-full object-contain p-2"
                    loading="lazy"
                  />
                ) : (
                  <div className="flex flex-col items-center justify-center text-slate-500">
                    <FileType className="w-9 h-9 mb-2 text-indigo-400/80" />
                    <span className="text-[10px] font-bold uppercase tracking-widest bg-white border border-slate-200 px-2 py-0.5 rounded-full text-slate-500">
                      {asset.mime_type?.split('/')[1] || 'FILE'}
                    </span>
                  </div>
                )}
              </div>

              {/* Asset Info */}
              <div className="p-3 bg-white/90 border-t border-slate-200/60 flex flex-col gap-1.5">
                <p className="text-xs font-semibold text-slate-700 truncate" title={asset.name}>
                  {asset.name}
                </p>
                <div className="flex items-center justify-between gap-2">
                  <span className="text-[10px] text-slate-400 font-medium">
                    {formatFileSize(asset.file_size)}
                  </span>
                  <span
                    className={`text-[9px] px-1.5 py-0.5 rounded-md font-mono border font-bold uppercase transition-all ${getBadgeColor(
                      extension
                    )}`}
                  >
                    {extension}
                  </span>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Floating Bulk Action Bar */}
      {selectedIds.length > 0 && (
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-50 bg-white/95 border border-indigo-500/35 px-6 py-4 rounded-2xl shadow-2xl flex items-center justify-between gap-6 backdrop-blur-md animate-in fade-in slide-in-from-bottom-5 duration-300 min-w-[320px] md:min-w-[420px]">
          <div className="flex items-center gap-3">
            <div className="w-2.5 h-2.5 rounded-full bg-indigo-500 animate-pulse" />
            <span className="text-sm font-semibold text-slate-700">
              Đã chọn {selectedIds.length} tài nguyên
            </span>
          </div>

          <div className="flex items-center gap-2">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setSelectedIds([])}
              className="text-slate-500 hover:text-slate-800 rounded-xl text-xs px-3.5 hover:bg-slate-100"
              disabled={bulkDeleteMutation.isPending}
            >
              Hủy chọn
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => handleOpenMoveModal(selectedIds)}
              className="border-indigo-500/20 bg-indigo-500/10 text-indigo-300 hover:text-slate-800 hover:bg-indigo-500/20 rounded-xl text-xs px-4 py-2 font-medium flex items-center gap-1.5 shadow-lg shadow-indigo-600/5"
            >
              <FolderInput className="w-3.5 h-3.5" />
              Di chuyển
            </Button>
            <Button
              variant="destructive"
              size="sm"
              onClick={handleBulkDelete}
              disabled={bulkDeleteMutation.isPending}
              className="bg-rose-600 hover:bg-rose-500 text-white border border-rose-500/35 rounded-xl text-xs px-4 py-2 font-medium flex items-center gap-1.5 shadow-lg shadow-rose-600/15"
            >
              {bulkDeleteMutation.isPending ? (
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
              ) : (
                <Trash2 className="w-3.5 h-3.5" />
              )}
              Xóa đã chọn
            </Button>
          </div>
        </div>
      )}

      {/* Move Asset Modal */}
      <MoveAssetModal
        isOpen={isMoveModalOpen}
        onClose={() => {
          setIsMoveModalOpen(false);
          setTargetIdsForMove([]);
        }}
        assetIds={targetIdsForMove}
        onSuccess={() => {
          setSelectedIds([]);
        }}
      />
    </div>
  );
}
