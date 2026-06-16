import { createSupabaseAdmin } from '@/lib/supabase/admin';

export type AuditAction =
  | 'template.delete'
  | 'template.bulk_delete'
  | 'template.publish'
  | 'template.unpublish'
  | 'category.delete'
  | 'asset.delete';

interface AuditLogEntry {
  action: AuditAction;
  target_type: 'template' | 'category' | 'asset';
  target_id: string;
  details?: Record<string, unknown>;
  actor_label?: string;
}

/**
 * Write an audit log entry to the `audit_logs` table.
 * Fails silently to avoid breaking the main operation flow.
 */
export async function writeAuditLog(entry: AuditLogEntry): Promise<void> {
  try {
    const supabase = createSupabaseAdmin();
    await supabase.from('audit_logs').insert({
      action: entry.action,
      target_type: entry.target_type,
      target_id: entry.target_id,
      details: entry.details || null,
      actor_label: entry.actor_label || 'admin_web',
      // user_id will be null until Auth is wired — can be updated later
      user_id: null,
    });
  } catch (err) {
    // Non-fatal: log to console but don't propagate
    console.error('[AuditLog] Failed to write audit log:', err);
  }
}
