import { NextResponse } from 'next/server';
import { CURRENT_SCHEMA_VERSION } from '@/lib/schema/template-contract';

export const dynamic = 'force-dynamic';

/**
 * Health check endpoint cho Android App.
 * App gọi endpoint này khi khởi động để kiểm tra:
 *  - Server còn hoạt động không
 *  - API contract version có tương thích không
 *  - Canvas schema version có vượt quá app hỗ trợ không
 *
 * Response:
 * {
 *   "ok": true,
 *   "apiVersion": 1,       // Phiên bản contract của REST API
 *   "schemaVersion": 1,    // Phiên bản schema của canvas_data mà server publish
 *   "ts": 1234567890000    // Server timestamp (ms) để debug clock skew
 * }
 */
export async function GET() {
  return NextResponse.json(
    {
      ok: true,
      apiVersion: 1,
      schemaVersion: CURRENT_SCHEMA_VERSION,
      ts: Date.now(),
    },
    {
      headers: {
        // Cache ngắn để app không gọi liên tục, nhưng không quá dài
        // để admin có thể bump schemaVersion và app sẽ nhận ra trong vài phút.
        'Cache-Control': 'public, max-age=300, stale-while-revalidate=60',
      },
    }
  );
}
