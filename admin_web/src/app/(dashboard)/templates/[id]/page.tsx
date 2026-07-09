'use client';

import React, { useEffect, useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import Link from 'next/link';
import { 
  Layers, 
  ArrowLeft, 
  FileArchive, 
  Trash2, 
  Edit3, 
  Loader2, 
  AlertTriangle,
  PlayCircle,
  FileCode2,
  CheckCircle2,
  Calendar,
  Grid,
  Info
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { CloudTemplate } from '@/types/cloud-template';
import { CloudCategory } from '@/domains/categories/category.types';

export default function TemplateDetailPage() {
  const router = useRouter();
  const { id } = useParams();

  const [template, setTemplate] = useState<any>(null);
  const [categories, setCategories] = useState<CloudCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Settings states
  const [title, setTitle] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [status, setStatus] = useState<'draft' | 'published'>('draft');
  const [environment, setEnvironment] = useState<'debug' | 'release' | 'all'>('all');
  const [isPremium, setIsPremium] = useState<boolean>(false);

  // Dialogs
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);

  const fetchTemplateDetails = async () => {
    try {
      setLoading(true);
      setError(null);

      // Fetch categories
      const catRes = await fetch('/api/categories');
      const catData = await catRes.json();
      if (!catRes.ok) throw new Error(catData.error || 'Failed to fetch categories');
      setCategories(catData.categories || []);

      // Fetch template
      const tplRes = await fetch(`/api/templates/${id}`);
      const tplData = await tplRes.json();
      if (!tplRes.ok) throw new Error(tplData.error || 'Failed to fetch template details');
      
      const tpl = tplData.template;
      setTemplate(tpl);
      setTitle(tpl.title);
      setCategoryId(tpl.category_id || '');
      setStatus(tpl.status || 'draft');
      setEnvironment(tpl.environment || 'all');
      setIsPremium(tpl.is_premium || false);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (id) {
      fetchTemplateDetails();
    }
  }, [id]);

  const handleSaveSettings = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setActionLoading(true);
      setError(null);

      // Also sync status and title in canvas_data metadata
      const updatedCanvasData = { ...template.canvas_data } as CloudTemplate;
      if (updatedCanvasData.metadata) {
        updatedCanvasData.metadata.title = title;
        updatedCanvasData.metadata.status = status;
        updatedCanvasData.metadata.environment = environment;
        updatedCanvasData.metadata.updatedAt = Date.now();
        updatedCanvasData.categoryId = categoryId;
      }

      const res = await fetch(`/api/templates/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title,
          category_id: categoryId,
          status,
          environment,
          is_premium: isPremium,
          canvas_data: updatedCanvasData,
          thumbnail_url: updatedCanvasData.canvas?.backgroundUrl || template.thumbnail_url
        }),
      });

      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Failed to update template settings');

      setTemplate(data.template);
      // Show success briefly
      alert('Đã cập nhật cấu hình template thành công!');
    } catch (err: any) {
      setError(err.message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleExportZip = () => {
    if (!template) return;
    // Trigger download by opening export link
    window.open(`/api/templates/${id}/export`, '_blank');
  };

  const handleDeleteTemplate = async () => {
    try {
      setActionLoading(true);
      setError(null);
      const res = await fetch(`/api/templates/${id}`, {
        method: 'DELETE',
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Failed to delete template');

      setIsDeleteOpen(false);
      router.push('/templates');
    } catch (err: any) {
      setError(err.message);
      setIsDeleteOpen(false);
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center py-40 gap-3 text-slate-400">
        <Loader2 className="w-8 h-8 animate-spin text-indigo-500" />
        <p className="text-sm">Đang tải chi tiết template...</p>
      </div>
    );
  }

  if (error && !template) {
    return (
      <div className="space-y-6">
        <Link href="/templates" className="flex items-center gap-1 text-xs text-slate-500 hover:text-slate-800">
          <ArrowLeft className="w-4 h-4" /> Quay lại Templates
        </Link>
        <div className="p-6 rounded-3xl bg-rose-500/10 border border-rose-500/20 text-rose-400 flex items-start gap-3">
          <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5" />
          <div>
            <p className="font-semibold text-sm">Lỗi tải dữ liệu</p>
            <p className="text-xs mt-0.5">{error}</p>
          </div>
        </div>
      </div>
    );
  }

  const canvasData = template.canvas_data as CloudTemplate;
  const layersCount = canvasData.layers?.length || 0;
  const placeholderCount =
    canvasData.layers?.filter(
      (l) => l.type?.includes('PLACEHOLDER') || l.payload?.replaceable === true
    ).length || 0;

  return (
    <div className="space-y-8 animate-in fade-in duration-500">
      {/* Back button */}
      <Link href="/templates" className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-indigo-400 transition-colors">
        <ArrowLeft className="w-4 h-4" /> Quay lại danh sách Templates
      </Link>

      {/* Top Banner Info */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6 border-b border-slate-200 pb-6">
        <div>
          <div className="flex items-center gap-3">
            <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-extrabold uppercase ${
              template.status === 'published' 
                ? template.canvas_data?.metadata?.environment === 'debug'
                  ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20'
                  : 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' 
                : 'bg-slate-100 border border-slate-300 text-slate-500'
            }`}>
              {template.status === 'published'
                ? template.canvas_data?.metadata?.environment === 'debug'
                  ? 'PUBLISHED (DEBUG)'
                  : 'PUBLISHED'
                : template.status}
            </span>
            <h1 className="text-3xl font-extrabold tracking-tight text-slate-800">{template.title}</h1>
          </div>
          <p className="text-slate-400 text-xs mt-1 font-mono">UUID: {template.id} | Template ID: {template.template_id}</p>
        </div>

        {/* Toolbar actions */}
        <div className="flex flex-wrap items-center gap-3">
          <Button 
            onClick={handleExportZip}
            variant="outline"
            className="border-slate-200 bg-white text-slate-600 hover:text-slate-800 hover:bg-slate-100 rounded-xl flex items-center gap-2 px-4"
          >
            <FileArchive className="w-4 h-4 text-emerald-400" /> Xuất ZIP Bundle
          </Button>
          <Link href={`/templates/${template.id}/edit`}>
            <Button className="bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl flex items-center gap-2 px-5">
              <PlayCircle className="w-4 h-4" /> Thiết kế Canvas
            </Button>
          </Link>
          <Button 
            onClick={() => setIsDeleteOpen(true)}
            variant="ghost"
            className="text-slate-500 hover:text-rose-500 hover:bg-slate-100 rounded-xl"
          >
            <Trash2 className="w-4 h-4" />
          </Button>
        </div>
      </div>

      {/* Grid details */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        
        {/* Left Side: Thumbnail Preview & Settings Form */}
        <div className="space-y-6 lg:col-span-1">
          {/* Thumbnail */}
          <Card className="bg-white border-slate-200 overflow-hidden rounded-3xl shadow-md">
            <div className="aspect-[9/16] w-full max-h-[300px] bg-white flex items-center justify-center relative border-b border-slate-850">
              {template.thumbnail_url ? (
                <img 
                  src={template.thumbnail_url} 
                  alt={template.title} 
                  className="w-full h-full object-cover"
                />
              ) : (
                <div className="flex flex-col items-center justify-center text-slate-700 gap-1.5">
                  <Grid className="w-10 h-10" />
                  <span className="text-xs uppercase font-bold tracking-wider">Không có Preview</span>
                </div>
              )}
            </div>
            
            {/* Quick Stats Grid */}
            <CardContent className="p-4 grid grid-cols-2 gap-3 text-center bg-white/50">
              <div className="p-2 rounded-xl bg-white/50 border border-slate-200">
                <p className="text-[10px] text-slate-400 uppercase font-bold tracking-wider">Tổng số Layers</p>
                <p className="text-xl font-extrabold text-slate-700 mt-0.5">{layersCount}</p>
              </div>
              <div className="p-2 rounded-xl bg-white/50 border border-slate-200">
                <p className="text-[10px] text-slate-400 uppercase font-bold tracking-wider">Placeholders</p>
                <p className="text-xl font-extrabold text-indigo-400 mt-0.5">{placeholderCount}</p>
              </div>
            </CardContent>
          </Card>

          {/* Form Settings */}
          <div className="p-6 rounded-3xl bg-white border border-slate-200 shadow-md space-y-4">
            <h3 className="font-bold text-slate-700 flex items-center gap-2">
              <Edit3 className="w-4 h-4 text-indigo-400" /> Cấu hình Thuộc tính
            </h3>
            
            <form onSubmit={handleSaveSettings} className="space-y-4">
              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-500">Tiêu đề template</label>
                <Input 
                  value={title} 
                  onChange={(e) => setTitle(e.target.value)}
                  className="bg-white border-slate-200 text-slate-700 focus-visible:ring-indigo-600 rounded-xl"
                  required
                />
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-500">Danh mục</label>
                <select
                  value={categoryId}
                  onChange={(e) => setCategoryId(e.target.value)}
                  className="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-sm text-slate-700 focus:outline-none focus:border-indigo-600"
                  required
                >
                  <option value="" disabled>Chọn danh mục</option>
                  {categories.map(cat => (
                    <option key={cat.id} value={cat.id}>{cat.name}</option>
                  ))}
                </select>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-500">Trạng thái phát hành</label>
                <select
                  value={status}
                  onChange={(e) => setStatus(e.target.value as any)}
                  className="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-sm text-slate-700 focus:outline-none focus:border-indigo-600"
                  required
                >
                  <option value="draft">Bản nháp (Draft)</option>
                  <option value="published">Xuất bản lên app (Published)</option>
                </select>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-500">Môi trường hiển thị</label>
                <select
                  value={environment}
                  onChange={(e) => setEnvironment(e.target.value as any)}
                  className="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-sm text-slate-700 focus:outline-none focus:border-indigo-600"
                  required
                >
                  <option value="all">Tất cả (All)</option>
                  <option value="debug">Bản thử nghiệm (Debug only)</option>
                  <option value="release">Bản chính thức (Release only)</option>
                </select>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-500">Phân loại (Free / Premium)</label>
                <select
                  value={isPremium ? 'premium' : 'free'}
                  onChange={(e) => setIsPremium(e.target.value === 'premium')}
                  className="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-sm text-slate-700 focus:outline-none focus:border-indigo-600"
                  required
                >
                  <option value="free">Miễn phí (Free)</option>
                  <option value="premium">Trả phí (Premium / PRO)</option>
                </select>
              </div>

              <Button 
                type="submit" 
                disabled={actionLoading}
                className="w-full bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl py-2 font-bold"
              >
                {actionLoading ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin mr-2" /> Đang cập nhật...
                  </>
                ) : (
                  'Lưu cấu hình'
                )}
              </Button>
            </form>
          </div>
        </div>

        {/* Right Side: JSON Schema Inspector */}
        <div className="lg:col-span-2 space-y-6">
          <div className="p-6 rounded-3xl bg-white border border-slate-200 shadow-md flex flex-col h-[580px]">
            <div className="flex items-center justify-between mb-4 pb-3 border-b border-slate-200/80">
              <div className="flex items-center gap-2">
                <FileCode2 className="w-5 h-5 text-indigo-400" />
                <h3 className="font-bold text-slate-700">Giám sát JSON CloudTemplate</h3>
              </div>
              <span className="text-[10px] text-slate-500 bg-white px-2.5 py-1 rounded-lg border border-slate-200 flex items-center gap-1 font-mono">
                <Info className="w-3 h-3" /> SCHEMA VERSION: {canvasData.metadata?.schemaVersion || 1}
              </span>
            </div>

            {/* Scrollable JSON Display */}
            <div className="flex-1 bg-white rounded-2xl p-4 overflow-y-auto font-mono text-xs text-indigo-300 border border-slate-850 scrollbar-thin scrollbar-thumb-slate-300">
              <pre className="whitespace-pre-wrap">
                {JSON.stringify(canvasData, null, 2)}
              </pre>
            </div>
          </div>
        </div>

      </div>

      {/* CONFIRM DELETE DIALOG */}
      <Dialog open={isDeleteOpen} onOpenChange={setIsDeleteOpen}>
        <DialogContent className="bg-white border border-slate-200 text-slate-800 rounded-2xl sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="text-lg font-bold text-rose-500 flex items-center gap-2">
              <AlertTriangle className="w-5 h-5" /> Xác nhận xóa Template?
            </DialogTitle>
            <DialogDescription className="text-xs text-slate-500 mt-2">
              Bạn có chắc muốn xóa template này? Hành động này sẽ xóa vĩnh viễn cấu hình thiết kế này khỏi DB Supabase.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter className="mt-6">
            <Button 
              type="button" 
              variant="ghost" 
              onClick={() => setIsDeleteOpen(false)}
              className="text-slate-500 hover:text-slate-800 rounded-xl"
            >
              Hủy
            </Button>
            <Button 
              type="button" 
              onClick={handleDeleteTemplate}
              disabled={actionLoading}
              className="bg-rose-600 hover:bg-rose-500 text-white rounded-xl px-6"
            >
              {actionLoading && <Loader2 className="w-4 h-4 animate-spin mr-2" />}
              Xác nhận Xóa
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
