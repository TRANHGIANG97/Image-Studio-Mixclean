'use client';

import React from 'react';

export default function EditorLoadingShimmer() {
  return (
    <div className="h-screen flex flex-col editor-workspace overflow-hidden">
      <div className="editor-shimmer shrink-0" style={{ height: 'var(--editor-topbar-h)' }} />
      <div className="flex-1 flex items-center justify-center p-8">
        <div
          className="editor-shimmer rounded-2xl"
          style={{
            width: 'min(420px, 55vw)',
            aspectRatio: '9 / 16',
            boxShadow: 'var(--editor-artboard-shadow)',
          }}
        />
      </div>
      <div className="editor-shimmer shrink-0" style={{ height: 'var(--editor-bottom-dock-h)' }} />
      <p className="text-center text-xs pb-4" style={{ color: 'var(--editor-text-secondary)' }}>
        Đang tải cấu hình thiết kế...
      </p>
    </div>
  );
}
