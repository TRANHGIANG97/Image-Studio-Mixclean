'use client';

import { useState } from 'react';
import { FolderOpen, Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useCategories, Category } from '@/hooks/useCategories';
import { CategoryTable } from './components/CategoryTable';
import { CategoryFormModal } from './components/CategoryFormModal';

export default function CategoriesPage() {
  const { data: categories = [], isLoading } = useCategories();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [categoryToEdit, setCategoryToEdit] = useState<Category | null>(null);

  const handleOpenCreate = () => {
    setCategoryToEdit(null);
    setIsModalOpen(true);
  };

  const handleOpenEdit = (category: Category) => {
    setCategoryToEdit(category);
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setCategoryToEdit(null);
  };

  return (
    <div className="space-y-8 animate-in fade-in duration-500 max-w-5xl mx-auto">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <FolderOpen className="w-6 h-6 text-indigo-400" />
            <h1 className="text-3xl font-extrabold tracking-tight text-slate-100">Quản lý Danh mục</h1>
          </div>
          <p className="text-slate-400 text-sm mt-1">
            Thiết lập danh mục để phân loại templates.
          </p>
        </div>

        <div className="flex items-center gap-2.5">
          <Button
            onClick={handleOpenCreate}
            className="bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl flex items-center gap-2 px-5"
          >
            <Plus className="w-4 h-4" /> Thêm Danh Mục
          </Button>
        </div>
      </div>

      {/* Main Content */}
      <CategoryTable 
        categories={categories} 
        isLoading={isLoading} 
        onEdit={handleOpenEdit} 
      />

      {/* Form Modal */}
      <CategoryFormModal 
        isOpen={isModalOpen} 
        onClose={handleCloseModal} 
        categoryToEdit={categoryToEdit} 
      />
    </div>
  );
}
