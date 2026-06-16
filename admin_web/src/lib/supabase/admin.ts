import { createClient } from '@supabase/supabase-js';

/**
 * Admin client to bypass Row Level Security (RLS) on backend API routes or scripts.
 * Uses the service_role key for full database access.
 *
 * WARNING: NEVER use this on the client side (only in server-side API routes).
 */
export const createSupabaseAdmin = () => {
  if (typeof window !== 'undefined') {
    throw new Error('Supabase admin client can only be used on the server.');
  }
  return createClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.SUPABASE_SERVICE_ROLE_KEY!
  );
};
