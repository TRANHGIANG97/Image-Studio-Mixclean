'use client';

import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Loader2 } from 'lucide-react';
import { useUpdateTemplate, Template } from '@/hooks/useTemplates';

const renameTemplateSchema = z.object({
  title: z.string().min(1, 'Tên template không được để trống'),
});

type RenameTemplateInput = z.infer<typeof renameTemplateSchema>;

interface RenameTemplateModalProps {
  isOpen: boolean;
  onClose: () => void;
  template: Template | null;
}

export function RenameTemplateModal({ isOpen, onClose, template }: RenameTemplateModalProps) {
  const updateMutation = useUpdateTemplate();

  const form = useForm<RenameTemplateInput>({
    resolver: zodResolver(renameTemplateSchema),
    defaultValues: {
      title: '',
    },
  });

  useEffect(() => {
    if (isOpen && template) {
      form.reset({
        title: template.title,
      });
    }
  }, [isOpen, template, form]);

  const onSubmit = async (data: RenameTemplateInput) => {
    if (!template) return;
    await updateMutation.mutateAsync({
      id: template.id,
      title: data.title,
    });
    onClose();
  };

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="bg-white border border-slate-200 text-slate-800 rounded-2xl sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-lg font-bold text-slate-800">Đổi tên Template</DialogTitle>
          <DialogDescription className="text-xs text-slate-500">
            Cập nhật tên hiển thị cho template này.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-2">
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-600">Tên template</label>
            <Input
              {...form.register('title')}
              className="bg-white border-slate-200 text-slate-700 focus-visible:ring-indigo-600 rounded-xl"
            />
            {form.formState.errors.title && <p className="text-xs text-rose-500">{form.formState.errors.title.message}</p>}
          </div>

          <DialogFooter className="mt-6">
            <Button type="button" variant="ghost" onClick={onClose} disabled={updateMutation.isPending} className="text-slate-500 hover:text-slate-800 rounded-xl">
              Hủy
            </Button>
            <Button type="submit" disabled={updateMutation.isPending} className="bg-indigo-600 hover:bg-indigo-500 rounded-xl px-6 text-white">
              {updateMutation.isPending && <Loader2 className="w-4 h-4 animate-spin mr-2" />}
              Lưu thay đổi
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
