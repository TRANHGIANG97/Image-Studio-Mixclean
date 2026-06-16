import { createBrowserClient } from '@supabase/ssr';

/**
 * Client-side Supabase client (used in Client Components).
 * Uses the anon key - operates within RLS policies.
 */
export const createClientSideSupabase = () => {
  return createBrowserClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!
  );
};
