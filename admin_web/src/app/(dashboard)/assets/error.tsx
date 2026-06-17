'use client';

import { useEffect } from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';
import { Button } from '@/components/ui/button';

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error('[Assets Error]', error);
  }, [error]);

  return (
    <div className="flex flex-col items-center justify-center py-32 gap-6 bg-rose-500/5 border border-rose-500/20 rounded-3xl">
      <div className="w-16 h-16 rounded-2xl bg-rose-500/10 flex items-center justify-center">
        <AlertTriangle className="w-8 h-8 text-rose-400" />
      </div>
      <div className="text-center">
        <h2 className="text-lg font-bold text-slate-800 mb-2">Không thể tải Media Library</h2>
        <p className="text-sm text-slate-500 max-w-sm">
          {error.message || 'Đã xảy ra lỗi không mong muốn. Vui lòng thử lại.'}
        </p>
      </div>
      <Button
        onClick={reset}
        className="bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl flex items-center gap-2 px-6"
      >
        <RefreshCw className="w-4 h-4" /> Thử lại
      </Button>
    </div>
  );
}
