'use client';

import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { cloneTemplateSchema, CloneTemplateInput } from '@/domains/templates/template.validator';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Loader2 } from 'lucide-react';
import { useCloneTemplate, Template } from '@/hooks/useTemplates';

interface CloneTemplateModalProps {
  isOpen: boolean;
  onClose: () => void;
  sourceTemplate: Template | null;
}

export function CloneTemplateModal({ isOpen, onClose, sourceTemplate }: CloneTemplateModalProps) {
  const cloneMutation = useCloneTemplate();

  const form = useForm<CloneTemplateInput>({
    resolver: zodResolver(cloneTemplateSchema),
    defaultValues: {
      sourceId: '',
      newTemplateId: '',
      newTitle: '',
    },
  });

  useEffect(() => {
    if (isOpen && sourceTemplate) {
      form.reset({
        sourceId: sourceTemplate.id,
        newTemplateId: `TPL_${Math.random().toString(36).substring(2, 8).toUpperCase()}`,
        newTitle: `${sourceTemplate.title} (Bản sao)`,
      });
    }
  }, [isOpen, sourceTemplate, form]);

  const onSubmit = async (data: CloneTemplateInput) => {
    await cloneMutation.mutateAsync(data);
    onClose();
  };

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="bg-white border border-slate-200 text-slate-800 rounded-2xl sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-lg font-bold text-slate-800">Nhân bản Template</DialogTitle>
          <DialogDescription className="text-xs text-slate-500">
            Tạo một bản sao độc lập của template hiện tại.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-2">
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-600">ID Template Mới</label>
            <Input
              {...form.register('newTemplateId')}
              className="bg-white border-slate-200 text-slate-700 focus-visible:ring-indigo-600 rounded-xl"
            />
            {form.formState.errors.newTemplateId && <p className="text-xs text-rose-500">{form.formState.errors.newTemplateId.message}</p>}
          </div>

          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-600">Tiêu đề bản sao</label>
            <Input
              {...form.register('newTitle')}
              className="bg-white border-slate-200 text-slate-700 focus-visible:ring-indigo-600 rounded-xl"
            />
            {form.formState.errors.newTitle && <p className="text-xs text-rose-500">{form.formState.errors.newTitle.message}</p>}
          </div>

          <DialogFooter className="mt-6">
            <Button type="button" variant="ghost" onClick={onClose} disabled={cloneMutation.isPending} className="text-slate-500 hover:text-slate-800 rounded-xl">
              Hủy
            </Button>
            <Button type="submit" disabled={cloneMutation.isPending} className="bg-indigo-600 hover:bg-indigo-500 rounded-xl px-6 text-white">
              {cloneMutation.isPending && <Loader2 className="w-4 h-4 animate-spin mr-2" />}
              Tiến hành Nhân bản
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
