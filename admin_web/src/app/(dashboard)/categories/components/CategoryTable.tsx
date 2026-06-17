'use client';

import { Category } from '@/hooks/useCategories';
import { Pencil, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useState } from 'react';
import { useDeleteCategory } from '@/hooks/useCategories';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';

interface CategoryTableProps {
  categories: Category[];
  isLoading: boolean;
  onEdit: (category: Category) => void;
}

export function CategoryTable({ categories, isLoading, onEdit }: CategoryTableProps) {
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const deleteMutation = useDeleteCategory();

  const handleDelete = () => {
    if (deleteId) {
      deleteMutation.mutate(deleteId, {
        onSuccess: () => {
          setDeleteId(null);
        },
      });
    }
  };

  if (isLoading) {
    return (
      <div className="flex justify-center items-center py-24 text-slate-500">
        Đang tải danh sách...
      </div>
    );
  }

  if (categories.length === 0) {
    return (
      <div className="text-center py-24 bg-white border border-slate-200 rounded-3xl text-slate-500">
        Chưa có danh mục nào. Hãy tạo danh mục đầu tiên!
      </div>
    );
  }

  return (
    <>
      <div className="bg-white border border-slate-200 rounded-3xl overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-600">
            <thead className="bg-white/50 text-xs uppercase text-slate-400 font-semibold border-b border-slate-200">
              <tr>
                <th className="px-6 py-4">Tên danh mục</th>
                <th className="px-6 py-4 w-32 text-center">Thứ tự</th>
                <th className="px-6 py-4 w-48 text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {categories.map((cat) => (
                <tr key={cat.id} className="hover:bg-slate-100/30 transition-colors">
                  <td className="px-6 py-4 font-medium text-slate-700">
                    {cat.name}
                    <div className="text-[10px] text-slate-400 font-mono mt-1">ID: {cat.id}</div>
                  </td>
                  <td className="px-6 py-4 text-center">
                    <span className="px-2.5 py-1 rounded-lg bg-slate-100/50 text-slate-600 border border-slate-300/50">
                      {cat.order}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-right">
                    <div className="flex items-center justify-end gap-2">
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => onEdit(cat)}
                        className="text-indigo-400 hover:text-slate-800 hover:bg-indigo-500/20 rounded-xl w-8 h-8"
                      >
                        <Pencil className="w-4 h-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => setDeleteId(cat.id)}
                        className="text-rose-400 hover:text-slate-800 hover:bg-rose-500/20 rounded-xl w-8 h-8"
                      >
                        <Trash2 className="w-4 h-4" />
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* DELETE CONFIRMATION DIALOG */}
      <Dialog open={!!deleteId} onOpenChange={() => setDeleteId(null)}>
        <DialogContent className="bg-white border border-slate-200 text-slate-800 rounded-2xl sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="text-lg font-bold text-rose-500 flex items-center gap-2">
              <Trash2 className="w-5 h-5" /> Xóa Danh Mục
            </DialogTitle>
            <DialogDescription className="text-sm text-slate-600 pt-2">
              Bạn có chắc chắn muốn xóa danh mục này? Hành động này không thể hoàn tác. 
              <br/><br/>
              <span className="text-xs text-rose-400/80 bg-rose-500/10 px-2 py-1 rounded-md border border-rose-500/20">
                Lưu ý: Không thể xóa danh mục đang có template.
              </span>
            </DialogDescription>
          </DialogHeader>
          <DialogFooter className="mt-4">
            <Button
              type="button"
              variant="ghost"
              onClick={() => setDeleteId(null)}
              disabled={deleteMutation.isPending}
              className="text-slate-500 hover:text-slate-800 rounded-xl"
            >
              Hủy
            </Button>
            <Button
              type="button"
              onClick={handleDelete}
              disabled={deleteMutation.isPending}
              className="bg-rose-600 hover:bg-rose-500 rounded-xl px-6 text-white"
            >
              {deleteMutation.isPending ? 'Đang xóa...' : 'Xóa vĩnh viễn'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
