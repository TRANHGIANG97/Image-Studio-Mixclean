/**
 * Supabase client factories.
 *
 * Re-exports from the split module structure for backward compatibility.
 * Prefer importing directly from the specific module:
 *   - @/lib/supabase/client  (browser client)
 *   - @/lib/supabase/server  (server-side cookies-based client)
 *   - @/lib/supabase/admin   (admin/service-role client)
 */

export { createClientSideSupabase } from '@/lib/supabase/client';
export { createServerSideSupabase } from '@/lib/supabase/server';
export { createSupabaseAdmin } from '@/lib/supabase/admin';
