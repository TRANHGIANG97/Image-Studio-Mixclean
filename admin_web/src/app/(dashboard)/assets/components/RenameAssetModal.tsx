'use client';

import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Loader2 } from 'lucide-react';
import { Asset, useRenameAsset } from '@/hooks/useAssets';

const renameAssetSchema = z.object({
  name: z.string().min(1, 'Tên tài nguyên không được để trống').max(255, 'Tên quá dài'),
});

type RenameAssetInput = z.infer<typeof renameAssetSchema>;

interface RenameAssetModalProps {
  isOpen: boolean;
  onClose: () => void;
  asset: Asset | null;
}

export function RenameAssetModal({ isOpen, onClose, asset }: RenameAssetModalProps) {
  const renameMutation = useRenameAsset();

  const form = useForm<RenameAssetInput>({
    resolver: zodResolver(renameAssetSchema),
    defaultValues: { name: '' },
  });

  useEffect(() => {
    if (isOpen && asset) {
      form.reset({ name: asset.name });
    }
  }, [isOpen, asset, form]);

  const onSubmit = async (data: RenameAssetInput) => {
    if (!asset) return;
    const trimmed = data.name.trim();
    if (trimmed === asset.name) {
      onClose();
      return;
    }
    await renameMutation.mutateAsync({ id: asset.id, name: trimmed });
    onClose();
  };

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="bg-white border border-slate-200 text-slate-800 rounded-2xl sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-lg font-bold text-slate-800">Đổi tên tài nguyên</DialogTitle>
          <DialogDescription className="text-xs text-slate-500">
            Cập nhật tên hiển thị. Liên kết CDN và file gốc không thay đổi.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-2">
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-600">Tên tài nguyên</label>
            <Input
              {...form.register('name')}
              autoFocus
              className="bg-white border-slate-200 text-slate-700 focus-visible:ring-indigo-600 rounded-xl"
            />
            {form.formState.errors.name && (
              <p className="text-xs text-rose-500">{form.formState.errors.name.message}</p>
            )}
          </div>

          <DialogFooter className="mt-6">
            <Button
              type="button"
              variant="ghost"
              onClick={onClose}
              disabled={renameMutation.isPending}
              className="text-slate-500 hover:text-slate-800 rounded-xl"
            >
              Hủy
            </Button>
            <Button
              type="submit"
              disabled={renameMutation.isPending}
              className="bg-indigo-600 hover:bg-indigo-500 rounded-xl px-6 text-white"
            >
              {renameMutation.isPending && <Loader2 className="w-4 h-4 animate-spin mr-2" />}
              Lưu thay đổi
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
