'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { createClientSideSupabase } from '@/lib/supabase/client';
import { Sparkles, Mail, Lock, Loader2, AlertTriangle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    const supabase = createClientSideSupabase();
    const { error: authError } = await supabase.auth.signInWithPassword({ email, password });

    if (authError) {
      setError(authError.message || 'Đăng nhập thất bại. Vui lòng kiểm tra lại email và mật khẩu.');
      setLoading(false);
      return;
    }

    router.push('/');
    router.refresh();
  };

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="flex flex-col items-center mb-10">
          <div className="w-14 h-14 rounded-2xl bg-indigo-600 flex items-center justify-center shadow-xl shadow-indigo-600/30 mb-4">
            <Sparkles className="w-7 h-7 text-white" />
          </div>
          <h1 className="text-2xl font-extrabold text-slate-800 tracking-tight">Studio Admin</h1>
          <p className="text-sm text-slate-500 mt-1">Đăng nhập để quản lý nội dung</p>
        </div>

        {/* Card */}
        <div className="bg-white border border-slate-200 rounded-3xl p-8 shadow-xl">
          <form onSubmit={handleLogin} className="space-y-5">
            <div className="space-y-2">
              <label className="text-xs font-semibold text-slate-600">Email</label>
              <div className="relative">
                <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                <Input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="admin@example.com"
                  required
                  className="pl-10 bg-white border-slate-200 text-slate-700 placeholder:text-slate-400 rounded-xl focus-visible:ring-indigo-600"
                />
              </div>
            </div>

            <div className="space-y-2">
              <label className="text-xs font-semibold text-slate-600">Mật khẩu</label>
              <div className="relative">
                <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                <Input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  required
                  className="pl-10 bg-white border-slate-200 text-slate-700 placeholder:text-slate-400 rounded-xl focus-visible:ring-indigo-600"
                />
              </div>
            </div>

            {error && (
              <div className="flex items-start gap-3 p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-600 text-sm">
                <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
                <span>{error}</span>
              </div>
            )}

            <Button
              type="submit"
              disabled={loading}
              className="w-full bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl py-3 font-semibold shadow-lg shadow-indigo-600/20 transition-all"
            >
              {loading ? (
                <><Loader2 className="w-4 h-4 animate-spin mr-2" /> Đang đăng nhập...</>
              ) : (
                'Đăng nhập'
              )}
            </Button>
          </form>
        </div>

        <p className="text-center text-xs text-slate-400 mt-6">
          Chỉ quản trị viên mới có quyền truy cập.
        </p>
      </div>
    </div>
  );
}
