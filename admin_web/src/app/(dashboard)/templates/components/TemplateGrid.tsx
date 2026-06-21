'use client';

import { useState, useEffect, useRef } from 'react';
import { Loader2, Layers } from 'lucide-react';
import { Template } from '@/hooks/useTemplates';
import { TemplateCard } from './TemplateCard';
import { useDashboardStore } from '@/store/dashboard.store';

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
  const { selectedTemplateIds, selectAll } = useDashboardStore();

  // Drag selection states
  const [isDragSelecting, setIsDragSelecting] = useState(false);
  const [startIndex, setStartIndex] = useState<number | null>(null);
  const [isSelectingMode, setIsSelectingMode] = useState(true);
  const [dragSelectedIds, setDragSelectedIds] = useState<Set<string>>(new Set());

  // Refs for event listener closures
  const isDragSelectingRef = useRef(isDragSelecting);
  const startIndexRef = useRef(startIndex);
  const dragSelectedIdsRef = useRef(dragSelectedIds);
  const isSelectingModeRef = useRef(isSelectingMode);
  const selectedTemplateIdsRef = useRef(selectedTemplateIds);

  useEffect(() => {
    isDragSelectingRef.current = isDragSelecting;
    startIndexRef.current = startIndex;
    dragSelectedIdsRef.current = dragSelectedIds;
    isSelectingModeRef.current = isSelectingMode;
    selectedTemplateIdsRef.current = selectedTemplateIds;
  }, [isDragSelecting, startIndex, dragSelectedIds, isSelectingMode, selectedTemplateIds]);

  const handleMouseUpGlobal = () => {
    if (isDragSelectingRef.current) {
      const next = new Set(selectedTemplateIdsRef.current);
      dragSelectedIdsRef.current.forEach((id) => {
        if (isSelectingModeRef.current) {
          next.add(id);
        } else {
          next.delete(id);
        }
      });
      selectAll(Array.from(next));

      setIsDragSelecting(false);
      setStartIndex(null);
      setDragSelectedIds(new Set());
    }
  };

  // Auto scroll window when dragging templates near boundaries
  useEffect(() => {
    let scrollInterval: any = null;

    const handleGlobalMouseMove = (e: MouseEvent) => {
      if (!isDragSelectingRef.current) return;

      const threshold = 120; // px
      const clientY = e.clientY;
      const viewHeight = window.innerHeight;

      if (scrollInterval) {
        clearInterval(scrollInterval);
        scrollInterval = null;
      }

      if (clientY > viewHeight - threshold) {
        // Dragging down -> scroll down
        const ratio = (clientY - (viewHeight - threshold)) / threshold;
        const speed = Math.max(5, Math.min(45, ratio * 45));
        scrollInterval = setInterval(() => {
          window.scrollBy(0, speed);
        }, 16);
      } else if (clientY < threshold) {
        // Dragging up -> scroll up
        const ratio = (threshold - clientY) / threshold;
        const speed = Math.max(5, Math.min(45, ratio * 45));
        scrollInterval = setInterval(() => {
          window.scrollBy(0, -speed);
        }, 16);
      }
    };

    const handleGlobalMouseUp = () => {
      handleMouseUpGlobal();
      if (scrollInterval) {
        clearInterval(scrollInterval);
        scrollInterval = null;
      }
    };

    window.addEventListener('mousemove', handleGlobalMouseMove);
    window.addEventListener('mouseup', handleGlobalMouseUp);

    return () => {
      window.removeEventListener('mousemove', handleGlobalMouseMove);
      window.removeEventListener('mouseup', handleGlobalMouseUp);
      if (scrollInterval) {
        clearInterval(scrollInterval);
      }
    };
  }, []);

  const handleMouseDown = (e: React.MouseEvent, index: number, templateId: string) => {
    // Only left click triggers drag selection
    if (e.button !== 0) return;

    // Ignore clicks on buttons, links, inputs, or checkbox overlays inside template card
    const target = e.target as HTMLElement;
    if (
      target.closest('button') || 
      target.closest('input') || 
      target.closest('a') || 
      target.closest('.checkbox-overlay')
    ) {
      return;
    }

    e.preventDefault();
    setIsDragSelecting(true);
    setStartIndex(index);

    const startIsSelected = selectedTemplateIds.includes(templateId);
    setIsSelectingMode(!startIsSelected);
    setDragSelectedIds(new Set([templateId]));
  };

  const handleMouseEnter = (index: number) => {
    if (!isDragSelecting || startIndex === null) return;

    const min = Math.min(startIndex, index);
    const max = Math.max(startIndex, index);

    const nextDragSelected = new Set<string>();
    for (let i = min; i <= max; i++) {
      if (templates[i]) {
        nextDragSelected.add(templates[i].id);
      }
    }
    setDragSelectedIds(nextDragSelected);
  };

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-32 gap-3 text-slate-400 bg-white/50 rounded-3xl border border-slate-200">
        <Loader2 className="w-8 h-8 animate-spin text-indigo-500" />
        <p className="text-sm">Đang tải danh sách templates...</p>
      </div>
    );
  }

  if (templates.length === 0) {
    return (
      <div className="text-center py-28 bg-white border border-slate-200 rounded-3xl">
        <Layers className="w-12 h-12 text-slate-700 mx-auto mb-4" />
        <p className="text-slate-600 font-semibold">Không tìm thấy Template nào</p>
        <p className="text-xs text-slate-400 mt-1 max-w-sm mx-auto">
          Hãy bắt đầu thiết kế bằng cách tạo template mới hoặc nhập file ZIP.
        </p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
      {templates.map((tpl, index) => {
        const isDragSelected = dragSelectedIds.has(tpl.id);
        const isSelected = isDragSelecting
          ? (isSelectingMode
              ? (selectedTemplateIds.includes(tpl.id) || isDragSelected)
              : (selectedTemplateIds.includes(tpl.id) && !isDragSelected))
          : selectedTemplateIds.includes(tpl.id);

        return (
          <TemplateCard
            key={tpl.id}
            template={tpl}
            onCloneClick={onCloneClick}
            onDeleteClick={onDeleteClick}
            onRenameClick={onRenameClick}
            isSelected={isSelected}
            onMouseDown={(e) => handleMouseDown(e, index, tpl.id)}
            onMouseEnter={() => handleMouseEnter(index)}
          />
        );
      })}
    </div>
  );
}
