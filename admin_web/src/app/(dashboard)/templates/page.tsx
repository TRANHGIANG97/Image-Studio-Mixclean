'use client';

import { useState } from 'react';
import { Layers, Plus, FileArchive, FileImage, Trash2, Sparkles, Loader2, LayoutGrid, List } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useTemplates, useBulkDeleteTemplates, Template } from '@/hooks/useTemplates';
import { useCategories } from '@/hooks/useCategories';
import { useDashboardStore } from '@/store/dashboard.store';
import { useRouter } from 'next/navigation';

import { TemplateGrid } from './components/TemplateGrid';
import { TemplateFilter } from './components/TemplateFilter';
import { CreateTemplateModal } from './components/CreateTemplateModal';
import { ImportTemplateModal } from './components/ImportTemplateModal';
import { ImportPsdTemplateModal } from './components/ImportPsdTemplateModal';
import { CloneTemplateModal } from './components/CloneTemplateModal';
import { RenameTemplateModal } from './components/RenameTemplateModal';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';

export default function TemplatesPage() {
  const router = useRouter();

  // Filters
  const [search, setSearch] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [status, setStatus] = useState('');
  const [sortBy, setSortBy] = useState('updated_at');
  const [sortOrder, setSortOrder] = useState('desc');

  // Data
  const { data: categories = [] } = useCategories();
  const { data, isLoading, hasNextPage, fetchNextPage, isFetchingNextPage } = useTemplates({
    search,
    categoryId,
    status,
    sortBy,
    sortOrder,
  });
  const templates = data?.pages.flatMap((page) => page.templates) || [];

  // Sắp xếp theo số tự nhiên (Natural numeric sort) ở frontend nếu sắp xếp theo tên
  const displayTemplates = [...templates];
  if (sortBy === 'title') {
    displayTemplates.sort((a, b) => {
      return a.title.localeCompare(b.title, undefined, { numeric: true, sensitivity: 'base' });
    });
    if (sortOrder === 'desc') {
      displayTemplates.reverse();
    }
  }

  // Zustand dashboard store — no more prop drilling
  const { selectedTemplateIds, clearSelection, templateViewMode, setTemplateViewMode } =
    useDashboardStore();

  // Modal states
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [isImportOpen, setIsImportOpen] = useState(false);
  const [isImportPsdOpen, setIsImportPsdOpen] = useState(false);
  const [sourceClone, setSourceClone] = useState<Template | null>(null);
  const [sourceRename, setSourceRename] = useState<Template | null>(null);
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [isBulkDeleteOpen, setIsBulkDeleteOpen] = useState(false);

  // Mutations
  const bulkDeleteMutation = useBulkDeleteTemplates();
  const [isCreatingDemo, setIsCreatingDemo] = useState(false);

  const handleCreateDemoCollage = async () => {
    try {
      setIsCreatingDemo(true);
      const res = await fetch('/api/templates/demo-food-camera-collage', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ categoryId: categoryId || categories[0]?.id || '' }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error);
      router.push(`/templates/${data.template.id}`);
    } catch (error) {
      console.error(error);
    } finally {
      setIsCreatingDemo(false);
    }
  };

  const handleBulkDelete = () => {
    bulkDeleteMutation.mutate(selectedTemplateIds, {
      onSuccess: () => {
        clearSelection();
        setIsBulkDeleteOpen(false);
      },
    });
  };

  return (
    <div className="space-y-8 animate-in fade-in duration-500">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <Layers className="w-6 h-6 text-indigo-400" />
            <h1 className="text-3xl font-extrabold tracking-tight text-slate-100">Quản lý Templates</h1>
          </div>
          <p className="text-slate-400 text-sm mt-1">
            Thiết kế, nhập hoặc xuất các file ZIP template đồng bộ với ứng dụng Android.
          </p>
        </div>

        <div className="flex items-center gap-2.5 flex-wrap">
          {/* View mode toggle */}
          <div className="flex items-center bg-slate-900 border border-slate-800 rounded-xl p-1">
            <button
              onClick={() => setTemplateViewMode('grid')}
              className={`p-1.5 rounded-lg transition-colors ${templateViewMode === 'grid' ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-slate-200'}`}
            >
              <LayoutGrid className="w-4 h-4" />
            </button>
            <button
              onClick={() => setTemplateViewMode('list')}
              className={`p-1.5 rounded-lg transition-colors ${templateViewMode === 'list' ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-slate-200'}`}
            >
              <List className="w-4 h-4" />
            </button>
          </div>

          {selectedTemplateIds.length > 0 && (
            <Button
              variant="destructive"
              onClick={() => setIsBulkDeleteOpen(true)}
              className="rounded-xl flex items-center gap-2 px-4 shadow-xl"
            >
              <Trash2 className="w-4 h-4" /> Xóa ({selectedTemplateIds.length})
            </Button>
          )}

          <Button
            onClick={handleCreateDemoCollage}
            disabled={isCreatingDemo}
            variant="outline"
            className="border-amber-500/20 bg-amber-500/10 text-amber-200 hover:text-white hover:bg-amber-500/20 rounded-xl flex items-center gap-2 px-4"
          >
            {isCreatingDemo ? <Loader2 className="w-4 h-4 animate-spin" /> : <Sparkles className="w-4 h-4 text-amber-300" />}
            Tạo collage mẫu
          </Button>
          <Button
            onClick={() => setIsImportOpen(true)}
            variant="outline"
            className="border-slate-800 bg-slate-900 text-slate-300 hover:text-white hover:bg-slate-800 rounded-xl flex items-center gap-2 px-4"
          >
            <FileArchive className="w-4 h-4 text-indigo-400" /> Import ZIP
          </Button>
          <Button
            onClick={() => setIsImportPsdOpen(true)}
            variant="outline"
            className="border-slate-800 bg-slate-900 text-slate-300 hover:text-white hover:bg-slate-800 rounded-xl flex items-center gap-2 px-4"
          >
            <FileImage className="w-4 h-4 text-indigo-400" /> Import PSD
          </Button>
          <Button
            onClick={() => setIsCreateOpen(true)}
            className="bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl flex items-center gap-2 px-4"
          >
            <Plus className="w-4 h-4" /> Thêm Template
          </Button>
        </div>
      </div>

      {/* Filters */}
      <TemplateFilter
        search={search}
        onSearchChange={setSearch}
        categoryId={categoryId}
        onCategoryChange={setCategoryId}
        status={status}
        onStatusChange={setStatus}
        categories={categories}
        sortBy={sortBy}
        sortOrder={sortOrder}
        onSortChange={(field, order) => {
          setSortBy(field);
          setSortOrder(order);
        }}
      />

      {/* Grid */}
      <TemplateGrid
        templates={displayTemplates}
        isLoading={isLoading}
        onCloneClick={(tpl) => setSourceClone(tpl)}
        onDeleteClick={(id) => setDeleteId(id)}
        onRenameClick={(tpl) => setSourceRename(tpl)}
      />

      {/* Load More Button */}
      {hasNextPage && (
        <div className="flex justify-center mt-8">
          <Button
            onClick={() => fetchNextPage()}
            disabled={isFetchingNextPage}
            variant="outline"
            className="rounded-xl border-slate-700 text-slate-300 hover:text-white hover:bg-slate-800"
          >
            {isFetchingNextPage ? (
              <><Loader2 className="w-4 h-4 mr-2 animate-spin" /> Đang tải thêm...</>
            ) : (
              'Tải thêm Templates'
            )}
          </Button>
        </div>
      )}

      {/* Modals */}
      <CreateTemplateModal isOpen={isCreateOpen} onClose={() => setIsCreateOpen(false)} categories={categories} />
      <ImportTemplateModal isOpen={isImportOpen} onClose={() => setIsImportOpen(false)} categories={categories} />
      <ImportPsdTemplateModal isOpen={isImportPsdOpen} onClose={() => setIsImportPsdOpen(false)} categories={categories} />
      <CloneTemplateModal isOpen={!!sourceClone} onClose={() => setSourceClone(null)} sourceTemplate={sourceClone} />
      <RenameTemplateModal isOpen={!!sourceRename} onClose={() => setSourceRename(null)} template={sourceRename} />

      {/* Bulk Delete Confirm */}
      <Dialog open={isBulkDeleteOpen} onOpenChange={setIsBulkDeleteOpen}>
        <DialogContent className="bg-slate-900 border border-slate-800 text-slate-100 rounded-2xl">
          <DialogHeader>
            <DialogTitle className="text-rose-500 flex items-center gap-2">Xóa hàng loạt</DialogTitle>
            <DialogDescription className="text-slate-300">
              Bạn sắp xóa vĩnh viễn {selectedTemplateIds.length} template đã chọn. Hành động này không thể hoàn tác.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setIsBulkDeleteOpen(false)} className="text-slate-400 hover:text-white">
              Hủy
            </Button>
            <Button onClick={handleBulkDelete} disabled={bulkDeleteMutation.isPending} className="bg-rose-600 hover:bg-rose-500 text-white">
              {bulkDeleteMutation.isPending ? 'Đang xóa...' : 'Xóa vĩnh viễn'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
