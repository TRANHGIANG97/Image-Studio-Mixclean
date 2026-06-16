'use client';

import { useState } from 'react';
import { Image as ImageIcon, UploadCloud, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAssets } from '@/hooks/useAssets';
import { AssetGallery } from './components/AssetGallery';
import { AssetFilter } from './components/AssetFilter';
import { AssetUploaderModal } from './components/AssetUploaderModal';
import { FontUploaderModal } from './components/FontUploaderModal';

export default function AssetsPage() {
  const [search, setSearch] = useState('');
  const [folder, setFolder] = useState('');
  const [mimeType, setMimeType] = useState('');
  const [sortBy, setSortBy] = useState('created_at');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');
  const [isUploaderOpen, setIsUploaderOpen] = useState(false);
  const [isFontUploaderOpen, setIsFontUploaderOpen] = useState(false);

  const { data, isLoading, hasNextPage, fetchNextPage, isFetchingNextPage } = useAssets({
    search,
    folder,
    mimeType,
    sortBy,
    sortOrder,
  });
  const assets = data?.pages.flatMap((page) => page.assets) || [];

  return (
    <div className="space-y-8 animate-in fade-in duration-500">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <ImageIcon className="w-6 h-6 text-indigo-400" />
            <h1 className="text-3xl font-extrabold tracking-tight text-slate-100">Media Library</h1>
          </div>
          <p className="text-slate-400 text-sm mt-1">
            Quản lý backgrounds, stickers, fonts và tài nguyên hình ảnh dùng cho Templates.
          </p>
        </div>

        <div className="flex items-center gap-2.5">
          <Button
            onClick={() => setIsFontUploaderOpen(true)}
            variant="outline"
            className="border-indigo-500/20 bg-indigo-500/10 text-indigo-300 hover:text-white hover:bg-indigo-500/20 rounded-xl flex items-center gap-2 px-5"
          >
            <UploadCloud className="w-4 h-4" /> Tải lên Font
          </Button>
          <Button
            onClick={() => setIsUploaderOpen(true)}
            className="bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl flex items-center gap-2 px-5"
          >
            <UploadCloud className="w-4 h-4" /> Tải lên tài nguyên
          </Button>
        </div>
      </div>

      {/* Filters */}
      <AssetFilter
        search={search}
        onSearchChange={setSearch}
        folder={folder}
        onFolderChange={setFolder}
        mimeType={mimeType}
        onMimeTypeChange={setMimeType}
        sortBy={sortBy}
        onSortByChange={setSortBy}
        sortOrder={sortOrder}
        onSortOrderChange={setSortOrder}
      />

      {/* Main Content */}
      <AssetGallery
        key={`${search}-${folder}-${mimeType}-${sortBy}-${sortOrder}`}
        assets={assets}
        isLoading={isLoading}
      />

      {/* Load More Button */}
      {hasNextPage && (
        <div className="flex justify-center mt-8">
          <Button
            onClick={() => fetchNextPage()}
            disabled={isFetchingNextPage}
            variant="outline"
            className="rounded-xl border-slate-700 text-slate-300 hover:text-white hover:bg-slate-800"
          >
            {isFetchingNextPage ? (
              <><Loader2 className="w-4 h-4 mr-2 animate-spin" /> Đang tải thêm...</>
            ) : (
              'Tải thêm Tài nguyên'
            )}
          </Button>
        </div>
      )}

      {/* Modals */}
      <AssetUploaderModal
        isOpen={isUploaderOpen}
        onClose={() => setIsUploaderOpen(false)}
        folder={folder || 'uncategorized'}
      />
      <FontUploaderModal
        isOpen={isFontUploaderOpen}
        onClose={() => setIsFontUploaderOpen(false)}
      />
    </div>
  );
}
