/**
 * Migration: validate + auto-fix template cũ trong DB theo Data Contract
 * (src/lib/schema/template-contract.ts — DATA_PIPELINE_PLAN.md Phase 2).
 *
 * Chạy (từ thư mục admin_web):
 *   npm run migrate:templates            → dry-run: chỉ báo cáo, KHÔNG ghi DB
 *   npm run migrate:templates -- --apply → ghi canvas_data đã fix về DB
 *   thêm --all để quét cả template draft (mặc định chỉ published)
 *
 * Với mỗi template:
 *  - validateCloudTemplate(canvas_data): clamp giá trị lệch, whitelist blendMode,
 *    fix bóng kép gradient, bơm schemaVersion/default còn thiếu.
 *  - errors  → KHÔNG tự sửa được, liệt kê để admin xử lý tay (không ghi DB).
 *  - warnings + data thay đổi → ứng viên auto-fix (ghi DB khi --apply).
 * Báo cáo chi tiết được ghi ra scripts/migrate-report-<timestamp>.json.
 */
import { createClient } from '@supabase/supabase-js';
import { config } from 'dotenv';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { validateCloudTemplate } from '../src/lib/schema/template-contract';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
config({ path: path.resolve(__dirname, '..', '.env.local') });

const APPLY = process.argv.includes('--apply');
const INCLUDE_DRAFTS = process.argv.includes('--all');
const PAGE_SIZE = 100;

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;

if (!supabaseUrl || !serviceKey) {
  console.error('Thiếu NEXT_PUBLIC_SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY trong .env.local');
  process.exit(1);
}

const supabase = createClient(supabaseUrl, serviceKey);

interface TemplateRow {
  id: string;
  template_id: string | null;
  title: string | null;
  status: string | null;
  canvas_data: unknown;
}

interface ReportEntry {
  id: string;
  templateId: string | null;
  title: string | null;
  status: string | null;
  outcome: 'ok' | 'fixed' | 'error' | 'skipped';
  errors: string[];
  warnings: string[];
  applied: boolean;
}

function stableEqual(a: unknown, b: unknown): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

async function fetchAllTemplates(): Promise<TemplateRow[]> {
  const rows: TemplateRow[] = [];
  for (let from = 0; ; from += PAGE_SIZE) {
    let query = supabase
      .from('templates')
      .select('id, template_id, title, status, canvas_data')
      .order('updated_at', { ascending: false })
      .range(from, from + PAGE_SIZE - 1);
    if (!INCLUDE_DRAFTS) {
      query = query.eq('status', 'published');
    }
    const { data, error } = await query;
    if (error) throw error;
    rows.push(...((data ?? []) as TemplateRow[]));
    if (!data || data.length < PAGE_SIZE) break;
  }
  return rows;
}

async function run() {
  console.log(
    `\n=== Migration validate template (${APPLY ? 'APPLY — sẽ ghi DB' : 'DRY-RUN — chỉ báo cáo'}) ===`
  );
  console.log(`Phạm vi: ${INCLUDE_DRAFTS ? 'tất cả template' : 'chỉ published'}\n`);

  const rows = await fetchAllTemplates();
  console.log(`Tìm thấy ${rows.length} template.\n`);

  const report: ReportEntry[] = [];
  let okCount = 0;
  let fixedCount = 0;
  let errorCount = 0;
  let skippedCount = 0;
  let appliedCount = 0;

  for (const row of rows) {
    const label = `${row.template_id ?? row.id} — "${row.title ?? '(không tên)'}"`;

    if (!row.canvas_data || typeof row.canvas_data !== 'object') {
      skippedCount++;
      report.push({
        id: row.id,
        templateId: row.template_id,
        title: row.title,
        status: row.status,
        outcome: 'skipped',
        errors: ['canvas_data trống hoặc không phải object.'],
        warnings: [],
        applied: false,
      });
      console.log(`[SKIP] ${label}: canvas_data trống.`);
      continue;
    }

    const result = validateCloudTemplate(row.canvas_data);

    if (!result.data) {
      errorCount++;
      report.push({
        id: row.id,
        templateId: row.template_id,
        title: row.title,
        status: row.status,
        outcome: 'error',
        errors: result.errors,
        warnings: result.warnings,
        applied: false,
      });
      console.log(`[ERROR] ${label}:`);
      result.errors.forEach((e) => console.log(`    - ${e}`));
      continue;
    }

    const changed = !stableEqual(result.data, row.canvas_data);

    if (!changed) {
      okCount++;
      report.push({
        id: row.id,
        templateId: row.template_id,
        title: row.title,
        status: row.status,
        outcome: 'ok',
        errors: [],
        warnings: result.warnings,
        applied: false,
      });
      continue;
    }

    fixedCount++;
    let applied = false;
    if (APPLY) {
      const { error: updateError } = await supabase
        .from('templates')
        .update({ canvas_data: result.data })
        .eq('id', row.id);
      if (updateError) {
        console.log(`[APPLY-FAIL] ${label}: ${updateError.message}`);
      } else {
        applied = true;
        appliedCount++;
      }
    }

    report.push({
      id: row.id,
      templateId: row.template_id,
      title: row.title,
      status: row.status,
      outcome: 'fixed',
      errors: [],
      warnings: result.warnings,
      applied,
    });

    console.log(`[FIX${applied ? '+APPLIED' : ''}] ${label}:`);
    if (result.warnings.length > 0) {
      result.warnings.forEach((w) => console.log(`    - ${w}`));
    } else {
      console.log('    - Chuẩn hóa default/schemaVersion (không có warning riêng).');
    }
  }

  const reportPath = path.resolve(
    __dirname,
    `migrate-report-${new Date().toISOString().replace(/[:.]/g, '-')}.json`
  );
  fs.writeFileSync(
    reportPath,
    JSON.stringify({ apply: APPLY, includeDrafts: INCLUDE_DRAFTS, summary: { total: rows.length, ok: okCount, fixed: fixedCount, errors: errorCount, skipped: skippedCount, applied: appliedCount }, entries: report }, null, 2),
    'utf8'
  );

  console.log('\n=== Tổng kết ===');
  console.log(`  Đạt chuẩn (không đổi): ${okCount}`);
  console.log(`  Cần fix (data thay đổi): ${fixedCount}${APPLY ? ` — đã ghi DB: ${appliedCount}` : ' — dry-run, chưa ghi'}`);
  console.log(`  Lỗi chặn (cần sửa tay): ${errorCount}`);
  console.log(`  Bỏ qua (canvas_data trống): ${skippedCount}`);
  console.log(`\nBáo cáo chi tiết: ${reportPath}`);

  if (!APPLY && fixedCount > 0) {
    console.log('\n→ Chạy lại với --apply để ghi các bản fix về DB: npm run migrate:templates -- --apply');
  }
}

run().catch((error) => {
  console.error('Migration thất bại:', error);
  process.exit(1);
});
