'use client';

import { Search, FolderOpen, ArrowUpDown, Filter, SlidersHorizontal } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { useFolders } from '@/hooks/useAssets';

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

// Moved inside component dynamically using useFolders hook

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

  return (
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
        {/* Folder Filter */}
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
  );
}
