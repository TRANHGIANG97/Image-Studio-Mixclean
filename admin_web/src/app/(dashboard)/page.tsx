import React from 'react';
import Link from 'next/link';
import { 
  Layers, 
  FolderHeart, 
  Image as ImageIcon, 
  Clock, 
  ArrowUpRight,
  Database,
  ArrowRight,
  Sparkles,
  FileCode2
} from 'lucide-react';
import { createSupabaseAdmin } from '@/lib/supabase';
import { Button } from '@/components/ui/button';

export const revalidate = 0; // Disable static cache for live counts

async function getDashboardData() {
  // Check if Supabase keys are configured
  if (!process.env.NEXT_PUBLIC_SUPABASE_URL || !process.env.SUPABASE_SERVICE_ROLE_KEY) {
    return {
      templatesCount: 0,
      categoriesCount: 0,
      assetsCount: 0,
      recentTemplates: [],
      error: 'SUPABASE_KEYS_MISSING'
    };
  }

  try {
    const supabase = createSupabaseAdmin();
    
    // Fetch counts in parallel
    const [templatesRes, categoriesRes, assetsRes, recentRes] = await Promise.all([
      supabase.from('templates').select('*', { count: 'exact', head: true }),
      supabase.from('categories').select('*', { count: 'exact', head: true }),
      supabase.from('assets').select('*', { count: 'exact', head: true }),
      supabase
        .from('templates')
        .select('*, categories(name)')
        .order('updated_at', { ascending: false })
        .limit(5)
    ]);

    return {
      templatesCount: templatesRes.count || 0,
      categoriesCount: categoriesRes.count || 0,
      assetsCount: assetsRes.count || 0,
      recentTemplates: recentRes.data || [],
      error: null
    };
  } catch (error: any) {
    console.error('Error fetching dashboard stats:', error);
    return {
      templatesCount: 0,
      categoriesCount: 0,
      assetsCount: 0,
      recentTemplates: [],
      error: error.message || 'DATABASE_CONNECTION_ERROR'
    };
  }
}

export default async function DashboardPage() {
  const data = await getDashboardData();

  // If credentials are not set, display setup onboarding
  if (data.error === 'SUPABASE_KEYS_MISSING') {
    return (
      <div className="space-y-8 animate-in fade-in duration-500">
        <div className="p-8 rounded-3xl bg-white border border-slate-200 shadow-2xl relative overflow-hidden">
          <div className="absolute top-0 right-0 w-96 h-96 bg-indigo-500/10 rounded-full blur-3xl -z-10" />
          <div className="absolute bottom-0 left-0 w-96 h-96 bg-cyan-500/10 rounded-full blur-3xl -z-10" />
          
          <div className="flex items-center gap-3 mb-4">
            <div className="p-2 rounded-xl bg-indigo-500/20 text-indigo-400">
              <Database className="w-6 h-6" />
            </div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-800">Cấu hình Cơ sở dữ liệu</h1>
          </div>
          
          <p className="text-slate-500 max-w-2xl mb-6">
            Dự án Next.js đã được tạo thành công! Để bắt đầu lưu trữ danh mục và thiết kế các templates, bạn cần kết nối ứng dụng với Supabase và khởi tạo Bucket lưu trữ bằng cách điền thông số môi trường.
          </p>

          <div className="space-y-4 max-w-3xl">
            <div className="p-5 rounded-2xl bg-white border border-slate-200">
              <h3 className="font-semibold text-slate-700 mb-2 flex items-center gap-2">
                <span className="flex items-center justify-center w-5 h-5 rounded-full bg-indigo-600 text-xs font-bold">1</span>
                Tạo file cấu hình .env.local
              </h3>
              <p className="text-xs text-slate-500 mb-3">
                Hãy tạo một file tên <code className="text-indigo-400">.env.local</code> ở thư mục gốc <code className="text-indigo-400">admin_web/</code> của bạn (dựa trên file mẫu <code className="text-slate-600">.env.local.example</code>) và điền các API key của bạn vào.
              </p>
              <pre className="text-xs bg-white p-4 rounded-xl text-slate-600 font-mono overflow-x-auto border border-slate-200">
{`NEXT_PUBLIC_SUPABASE_URL=https://your-project.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=your-anon-key
SUPABASE_SERVICE_ROLE_KEY=your-service-role-key`}
              </pre>
            </div>

            <div className="p-5 rounded-2xl bg-white border border-slate-200">
              <h3 className="font-semibold text-slate-700 mb-2 flex items-center gap-2">
                <span className="flex items-center justify-center w-5 h-5 rounded-full bg-indigo-600 text-xs font-bold">2</span>
                Khởi tạo Database Schema & Storage Bucket
              </h3>
              <p className="text-xs text-slate-500 mb-2">
                Copy nội dung trong file script SQL <Link href="/supabase_schema.sql" className="text-indigo-400 underline font-semibold">supabase_schema.sql</Link> ở thư mục gốc của dự án này, dán vào **SQL Editor** trong bảng điều khiển Supabase của bạn và nhấn **Run** để tự động khởi tạo các bảng, indexes và bucket lưu trữ công khai tên là <code className="text-indigo-400 font-semibold">assets</code>.
              </p>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // Dashboard Stats Cards
  const stats = [
    { name: 'Templates', value: data.templatesCount, href: '/templates', icon: Layers, color: 'from-pink-500 to-rose-500', shadow: 'shadow-rose-500/10' },
    { name: 'Categories', value: data.categoriesCount, href: '/categories', icon: FolderHeart, color: 'from-amber-500 to-orange-500', shadow: 'shadow-orange-500/10' },
    { name: 'Asset Library', value: data.assetsCount, href: '/assets', icon: ImageIcon, color: 'from-indigo-500 to-purple-500', shadow: 'shadow-purple-500/10' },
  ];

  return (
    <div className="space-y-8 animate-in fade-in duration-500">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight bg-gradient-to-r from-slate-100 to-slate-400 bg-clip-text text-transparent">
          Tổng quan Dashboard
        </h1>
        <p className="text-slate-500 text-sm mt-1">Chào mừng quay trở lại, hệ thống vận hành hoàn toàn ổn định.</p>
      </div>

      {/* Grid Stats */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {stats.map((stat) => {
          const Icon = stat.icon;
          return (
            <Link key={stat.name} href={stat.href} className="group">
              <div className={`p-6 rounded-3xl bg-white border border-slate-200 shadow-lg ${stat.shadow} transition-all duration-300 hover:border-slate-300 hover:translate-y-[-2px] relative overflow-hidden flex items-center justify-between`}>
                <div className="space-y-2">
                  <p className="text-sm font-medium text-slate-500 uppercase tracking-wider">{stat.name}</p>
                  <p className="text-4xl font-extrabold text-slate-800">{stat.value}</p>
                </div>
                <div className={`p-4 rounded-2xl bg-gradient-to-br ${stat.color} text-white transition-transform duration-300 group-hover:scale-110`}>
                  <Icon className="w-6 h-6" />
                </div>
              </div>
            </Link>
          );
        })}
      </div>

      {/* Recently Updated Templates */}
      <div className="p-6 rounded-3xl bg-white border border-slate-200 shadow-xl">
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-2">
            <Clock className="w-5 h-5 text-indigo-400" />
            <h2 className="text-lg font-bold text-slate-700">Templates Cập nhật Gần nhất</h2>
          </div>
          <Link href="/templates">
            <Button variant="ghost" size="sm" className="text-indigo-400 hover:text-indigo-300 flex items-center gap-1.5 rounded-xl">
              Tất cả Templates <ArrowRight className="w-4 h-4" />
            </Button>
          </Link>
        </div>

        {data.recentTemplates.length === 0 ? (
          <div className="text-center py-12 border border-dashed border-slate-200 rounded-2xl">
            <Layers className="w-10 h-10 text-slate-600 mx-auto mb-3" />
            <p className="text-slate-500 font-medium">Chưa có Template nào được tạo.</p>
            <p className="text-xs text-slate-400 mt-1 mb-4">Hãy tạo template mới để thiết kế ngay hôm nay.</p>
            <Link href="/templates">
              <Button size="sm" className="bg-indigo-600 hover:bg-indigo-500 rounded-xl">
                Tạo Template
              </Button>
            </Link>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-slate-200 text-xs text-slate-500 font-medium uppercase tracking-wider">
                  <th className="pb-3 pl-4">Tiêu đề</th>
                  <th className="pb-3">ID Template</th>
                  <th className="pb-3">Danh mục</th>
                  <th className="pb-3">Trạng thái</th>
                  <th className="pb-3 text-right pr-4">Cập nhật lúc</th>
                </tr>
              </thead>
              <tbody>
                {data.recentTemplates.map((tpl) => (
                  <tr key={tpl.id} className="border-b border-slate-200/50 hover:bg-slate-100/30 transition-colors group">
                    <td className="py-4 pl-4 font-semibold text-slate-700">
                      <Link href={`/templates/${tpl.id}`} className="hover:text-indigo-400 flex items-center gap-2">
                        {tpl.title}
                        <ArrowUpRight className="w-4 h-4 opacity-0 group-hover:opacity-100 transition-opacity" />
                      </Link>
                    </td>
                    <td className="py-4 font-mono text-xs text-indigo-400">{tpl.template_id}</td>
                    <td className="py-4 text-sm text-slate-600">{(tpl.categories as any)?.name || 'Chưa phân loại'}</td>
                    <td className="py-4">
                      <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                        tpl.status === 'published' 
                          ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' 
                          : 'bg-slate-100 text-slate-500 border border-slate-300'
                      }`}>
                        {tpl.status === 'published' ? 'Published' : 'Draft'}
                      </span>
                    </td>
                    <td className="py-4 text-right text-xs text-slate-500 pr-4">
                      {new Date(tpl.updated_at).toLocaleString('vi-VN')}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
