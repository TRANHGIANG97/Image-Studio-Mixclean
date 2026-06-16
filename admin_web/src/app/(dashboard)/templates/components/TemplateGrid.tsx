'use client';

import { Loader2, Layers } from 'lucide-react';
import { Template } from '@/hooks/useTemplates';
import { TemplateCard } from './TemplateCard';

interface TemplateGridProps {
  templates: Template[];
  isLoading: boolean;
  onCloneClick: (template: Template) => void;
  onDeleteClick: (templateId: string) => void;
  onRenameClick: (template: Template) => void;
}

export function TemplateGrid({
  templates,
  isLoading,
  onCloneClick,
  onDeleteClick,
  onRenameClick,
}: TemplateGridProps) {
  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-32 gap-3 text-slate-500 bg-slate-900/50 rounded-3xl border border-slate-800">
        <Loader2 className="w-8 h-8 animate-spin text-indigo-500" />
        <p className="text-sm">Đang tải danh sách templates...</p>
      </div>
    );
  }

  if (templates.length === 0) {
    return (
      <div className="text-center py-28 bg-slate-900 border border-slate-800 rounded-3xl">
        <Layers className="w-12 h-12 text-slate-700 mx-auto mb-4" />
        <p className="text-slate-300 font-semibold">Không tìm thấy Template nào</p>
        <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
          Hãy bắt đầu thiết kế bằng cách tạo template mới hoặc nhập file ZIP.
        </p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
      {templates.map((tpl) => (
        <TemplateCard
          key={tpl.id}
          template={tpl}
          onCloneClick={onCloneClick}
          onDeleteClick={onDeleteClick}
          onRenameClick={onRenameClick}
        />
      ))}
    </div>
  );
}
