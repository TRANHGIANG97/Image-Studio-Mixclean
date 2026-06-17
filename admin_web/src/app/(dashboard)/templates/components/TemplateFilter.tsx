'use client';

import { Search, FolderOpen, Filter, ArrowUpDown } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Category } from '@/hooks/useCategories';

interface TemplateFilterProps {
  search: string;
  onSearchChange: (val: string) => void;
  categoryId: string;
  onCategoryChange: (val: string) => void;
  status: string;
  onStatusChange: (val: string) => void;
  categories: Category[];
  sortBy: string;
  sortOrder: string;
  onSortChange: (sortBy: string, sortOrder: string) => void;
}

export function TemplateFilter({
  search,
  onSearchChange,
  categoryId,
  onCategoryChange,
  status,
  onStatusChange,
  categories,
  sortBy,
  sortOrder,
  onSortChange,
}: TemplateFilterProps) {
  return (
    <div className="flex flex-col md:flex-row items-center gap-4 bg-white border border-slate-200 p-4 rounded-3xl shadow-sm">
      {/* Search */}
      <div className="relative w-full md:w-80">
        <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
        <Input
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
          placeholder="Tìm tên template..."
          className="pl-10 bg-white border-slate-200 text-slate-700 placeholder:text-slate-600 rounded-2xl w-full focus-visible:ring-indigo-600"
        />
      </div>

      <div className="flex flex-wrap items-center gap-3 w-full md:w-auto">
        {/* Category Filter */}
        <div className="flex items-center gap-2 bg-white border border-slate-200 rounded-2xl px-3 py-1.5 text-xs">
          <FolderOpen className="w-3.5 h-3.5 text-slate-400" />
          <select
            value={categoryId}
            onChange={(e) => onCategoryChange(e.target.value)}
            className="bg-transparent border-0 text-slate-600 focus:outline-none pr-3 cursor-pointer"
          >
            <option value="">Tất cả danh mục</option>
            {categories.map((cat) => (
              <option key={cat.id} value={cat.id}>
                {cat.name}
              </option>
            ))}
          </select>
        </div>

        {/* Status Filter */}
        <div className="flex items-center gap-2 bg-white border border-slate-200 rounded-2xl px-3 py-1.5 text-xs">
          <Filter className="w-3.5 h-3.5 text-slate-400" />
          <select
            value={status}
            onChange={(e) => onStatusChange(e.target.value)}
            className="bg-transparent border-0 text-slate-600 focus:outline-none pr-3 cursor-pointer"
          >
            <option value="">Tất cả trạng thái</option>
            <option value="draft">Bản nháp (Draft)</option>
            <option value="published">Đã xuất bản (Published)</option>
          </select>
        </div>

        {/* Sort Selector */}
        <div className="flex items-center gap-2 bg-white border border-slate-200 rounded-2xl px-3 py-1.5 text-xs">
          <ArrowUpDown className="w-3.5 h-3.5 text-slate-400" />
          <select
            value={`${sortBy}:${sortOrder}`}
            onChange={(e) => {
              const [field, order] = e.target.value.split(':');
              onSortChange(field, order);
            }}
            className="bg-transparent border-0 text-slate-600 focus:outline-none pr-3 cursor-pointer"
          >
            <option value="updated_at:desc">Ngày cập nhật (Mới nhất)</option>
            <option value="updated_at:asc">Ngày cập nhật (Cũ nhất)</option>
            <option value="created_at:desc">Ngày tạo (Mới nhất)</option>
            <option value="created_at:asc">Ngày tạo (Cũ nhất)</option>
            <option value="title:asc">Tên template (Số tăng dần)</option>
            <option value="title:desc">Tên template (Số giảm dần)</option>
          </select>
        </div>
      </div>
    </div>
  );
}
