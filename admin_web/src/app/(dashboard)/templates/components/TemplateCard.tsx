'use client';

import { useState } from 'react';
import Link from 'next/link';
import { Check, Grid, MoreVertical, PenTool, Settings, Copy, Smartphone, Globe, Ban, Trash2, Edit3 } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardFooter } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Template, useUpdateTemplateStatus } from '@/hooks/useTemplates';
import { useDashboardStore } from '@/store/dashboard.store';

interface TemplateCardProps {
  template: Template;
  onCloneClick: (template: Template) => void;
  onDeleteClick: (templateId: string) => void;
  onRenameClick: (template: Template) => void;
}

export function TemplateCard({
  template: tpl,
  onCloneClick,
  onDeleteClick,
  onRenameClick,
}: TemplateCardProps) {
  const updateStatusMutation = useUpdateTemplateStatus();
  const { activeDropdownId, setActiveDropdownId, selectedTemplateIds, toggleTemplateSelection } =
    useDashboardStore();

  const isSelected = selectedTemplateIds.includes(tpl.id);


  const handleUpdateStatus = (newStatus: 'draft' | 'published', newEnvironment: 'debug' | 'release' | 'all') => {
    setActiveDropdownId(null);
    const updatedCanvasData = { ...tpl.canvas_data };
    if (updatedCanvasData.metadata) {
      updatedCanvasData.metadata.status = newStatus;
      updatedCanvasData.metadata.environment = newEnvironment;
      updatedCanvasData.metadata.updatedAt = Date.now();
    }

    updateStatusMutation.mutate({
      id: tpl.id,
      status: newStatus,
      environment: newEnvironment,
      canvas_data: updatedCanvasData,
    });
  };

  return (
    <Card className="bg-slate-900 border-slate-800 rounded-xl hover:border-slate-700/80 transition-all duration-300 group flex flex-col justify-between relative">
      {/* Checkbox overlay */}
      <div
        className={`absolute top-3 left-3 z-10 transition-opacity duration-200 ${
          isSelected ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'
        }`}
        onClick={(e) => {
          e.stopPropagation();
          toggleTemplateSelection(tpl.id);
        }}
      >
        <div
          className={`w-5 h-5 rounded flex items-center justify-center border-2 transition-colors cursor-pointer ${
            isSelected
              ? 'bg-indigo-500 border-indigo-500 text-white'
              : 'bg-slate-900/50 border-slate-400 text-transparent hover:border-slate-300'
          }`}
        >
          {isSelected && <Check className="w-3.5 h-3.5" strokeWidth={3} />}
        </div>
      </div>

      {/* Thumbnail Area */}
      <div
        className="aspect-[9/16] w-full max-h-[220px] rounded-t-xl relative overflow-hidden flex items-center justify-center border-b border-slate-800"
        style={{
          background: '#ffffff',
          backgroundImage:
            'url("data:image/svg+xml,%3Csvg xmlns=\'http://www.w3.org/2000/svg\' width=\'16\' height=\'16\'%3E%3Crect width=\'8\' height=\'8\' fill=\'%23f3f4f6\'/%3E%3Crect x=\'8\' y=\'8\' width=\'8\' height=\'8\' fill=\'%23f3f4f6\'/%3E%3Crect x=\'8\' width=\'8\' height=\'8\' fill=\'%23e5e7eb\'/%3E%3Crect y=\'8\' width=\'8\' height=\'8\' fill=\'%23e5e7eb\'/%3E%3C/svg%3E")',
        }}
      >
        {tpl.thumbnail_url || tpl.canvas_data?.canvas?.backgroundUrl ? (
          <img
            src={tpl.thumbnail_url ?? tpl.canvas_data?.canvas?.backgroundUrl ?? undefined}
            alt={tpl.title}
            className="w-full h-full object-cover group-hover:scale-[1.02] transition-transform duration-300"
            loading="lazy"
          />
        ) : (
          <div className="flex flex-col items-center justify-center gap-2 px-4">
            <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center">
              <Grid className="w-6 h-6 text-slate-300" />
            </div>
            <span className="text-[9px] uppercase font-bold tracking-wider text-slate-400 text-center">
              Chưa có ảnh
            </span>
          </div>
        )}

        {/* Status badge */}
        <span
          className={`absolute top-3 right-3 px-2.5 py-0.5 rounded-full text-[10px] font-extrabold uppercase tracking-wider shadow-sm ${
            tpl.status === 'published'
              ? tpl.canvas_data?.metadata?.environment === 'debug'
                ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20'
                : 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
              : 'bg-slate-900/85 backdrop-blur-sm border border-slate-800 text-slate-400'
          }`}
        >
          {tpl.status === 'published'
            ? tpl.canvas_data?.metadata?.environment === 'debug'
              ? 'PUBLISHED (DEBUG)'
              : 'PUBLISHED'
            : tpl.status}
        </span>

        {/* Category label */}
        <span className="absolute bottom-3 left-3 px-2 py-0.5 rounded-lg text-[9px] font-extrabold uppercase bg-slate-900/85 backdrop-blur-sm text-indigo-400 border border-slate-800/80">
          {tpl.categories?.name || 'Uncategorized'}
        </span>
      </div>

      <CardHeader className="p-4 pb-2">
        <CardTitle className="text-sm font-bold text-slate-200 line-clamp-1">{tpl.title}</CardTitle>
        <div className="text-[10px] text-slate-500 font-mono flex items-center justify-between mt-1">
          <span>ID: {tpl.template_id}</span>
          <span>
            Size: {tpl.canvas_data?.canvas?.baseWidth || 1080}x{tpl.canvas_data?.canvas?.baseHeight || 1920}
          </span>
        </div>
      </CardHeader>

      <CardFooter className="p-4 pt-2 border-t border-slate-800/40 mt-auto flex items-center justify-between relative">
        <span className="text-[9px] text-slate-500">
          Sửa: {new Date(tpl.updated_at).toLocaleDateString('vi-VN')}
        </span>
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="icon"
            className="text-slate-400 hover:text-white hover:bg-slate-800 rounded-xl w-8 h-8"
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              setActiveDropdownId(activeDropdownId === tpl.id ? null : tpl.id);
            }}
          >
            <MoreVertical className="w-4 h-4" />
          </Button>
        </div>

        {/* Dropdown Menu */}
        {activeDropdownId === tpl.id && (
          <>
            <div
              className="fixed inset-0 z-40"
              onClick={(e) => {
                e.preventDefault();
                e.stopPropagation();
                setActiveDropdownId(null);
              }}
            />
            <div
              className="absolute bottom-12 right-4 w-56 bg-slate-900/95 backdrop-blur-xl border border-slate-700/80 rounded-2xl shadow-2xl z-50 flex flex-col p-1.5 animate-in fade-in zoom-in-95 duration-200"
              onClick={(e) => e.stopPropagation()}
            >
              <Link href={`/templates/${tpl.id}/edit`}>
                <button className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs font-semibold text-slate-200 hover:bg-indigo-600 hover:text-white transition-colors">
                  <PenTool className="w-4 h-4" /> Thiết kế Canvas
                </button>
              </Link>
              <Link href={`/templates/${tpl.id}`}>
                <button className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs font-medium text-slate-300 hover:bg-slate-800 transition-colors">
                  <Settings className="w-4 h-4" /> Chi tiết & Cấu hình
                </button>
              </Link>
              <button
                onClick={() => {
                  setActiveDropdownId(null);
                  onRenameClick(tpl);
                }}
                className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs font-medium text-slate-300 hover:bg-slate-800 transition-colors"
              >
                <Edit3 className="w-4 h-4" /> Đổi tên Template
              </button>
              <button
                onClick={() => {
                  setActiveDropdownId(null);
                  onCloneClick(tpl);
                }}
                className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs font-medium text-slate-300 hover:bg-slate-800 transition-colors"
              >
                <Copy className="w-4 h-4" /> Nhân bản (Clone)
              </button>

              <div className="h-px bg-slate-800/60 my-1 mx-2" />

              <button
                onClick={() => handleUpdateStatus('published', 'debug')}
                className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-[11px] font-medium text-amber-300 hover:bg-amber-500/10 transition-colors"
              >
                <Smartphone className="w-4 h-4" /> Publish (App Debug)
              </button>
              <button
                onClick={() => handleUpdateStatus('published', 'release')}
                className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-[11px] font-medium text-emerald-400 hover:bg-emerald-500/10 transition-colors"
              >
                <Globe className="w-4 h-4" /> Publish (App Release)
              </button>
              <button
                onClick={() => handleUpdateStatus('draft', 'all')}
                className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-[11px] font-medium text-slate-400 hover:bg-slate-800 transition-colors"
              >
                <Ban className="w-4 h-4" /> Thu hồi về Nháp (Draft)
              </button>

              <div className="h-px bg-slate-800/60 my-1 mx-2" />

              <button
                onClick={() => {
                  setActiveDropdownId(null);
                  onDeleteClick(tpl.id);
                }}
                className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl text-[11px] font-bold text-rose-400 hover:bg-rose-500/10 transition-colors"
              >
                <Trash2 className="w-4 h-4" /> Xóa Template
              </button>
            </div>
          </>
        )}
      </CardFooter>
    </Card>
  );
}
